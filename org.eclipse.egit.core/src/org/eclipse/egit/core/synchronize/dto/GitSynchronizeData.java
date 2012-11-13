/*******************************************************************************
 * Copyright (C) 2010, 2011 Dariusz Luksza <dariusz@luksza.org> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize.dto;

import static org.eclipse.core.runtime.Assert.isNotNull;
import static org.eclipse.core.runtime.Assert.isTrue;
import static org.eclipse.egit.core.RevUtils.getCommonAncestor;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_BRANCH_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MERGE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Simple data transfer object containing all necessary information for
 * launching synchronization
 */
public class GitSynchronizeData {

	private static final IWorkspaceRoot ROOT = ResourcesPlugin.getWorkspace()
			.getRoot();

	/**
	 * Matches all strings that start from R_HEADS
	 */
	public static final Pattern BRANCH_NAME_PATTERN = Pattern
			.compile("^" + R_HEADS + ".*?"); //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * special source reference to identify synchronization with files in the working
	 * tree (workspace)
	 */
	public static final String WORKING_TREE = "WORKING_TREE"; //$NON-NLS-1$

	/** special reference to identify synchronization with files in the index */
	public static final String INDEX = "INDEX"; //$NON-NLS-1$

	private final Repository repo;
	private final String srcRev;

	private final String dstRev;

	private final RemoteBranchInfo srcRemoteConfig;

	private final RemoteBranchInfo dstRemoteConfig;

	private RevCommit srcRevCommit;

	private RevCommit dstRevCommit;

	private RevCommit ancestorRevCommit;

	private final Set<IProject> projects;

	private Set<IContainer> includedPaths;

	private final String repoParentPath;

	private TreeFilter pathFilter;

	private RemoteConfig fetchFromRemote;

	private static class RemoteBranchInfo {
		final String remote;
		final String merge;

		public RemoteBranchInfo(String remote, String merge) {
			this.remote = remote;
			this.merge = merge;
		}
	}

	private static boolean shouldUseHead(String refName) {
		return WORKING_TREE.equals(refName) || INDEX.equals(refName);
	}

	/**
	 * Constructs {@link GitSynchronizeData} object
	 *
	 * @param repository
	 * @param srcRev
	 * @param dstRev
	 * @throws IOException
	 */
	public GitSynchronizeData(Repository repository, String srcRev,
			String dstRev) throws IOException {
		isNotNull(repository);
		isNotNull(srcRev);
		isNotNull(dstRev);
		isTrue(!WORKING_TREE.equals(dstRev)); // limitation/assumption in Team UI
		repo = repository;
		this.srcRev = srcRev;
		this.dstRev = dstRev;

		srcRemoteConfig = extractRemoteName(shouldUseHead(srcRev) ? HEAD
				: srcRev);
		dstRemoteConfig = extractRemoteName(shouldUseHead(dstRev) ? HEAD
				: dstRev);

		repoParentPath = repo.getDirectory().getParentFile().getAbsolutePath();

		projects = new HashSet<IProject>();
		final IProject[] workspaceProjects = ROOT.getProjects();
		for (IProject project : workspaceProjects) {
			RepositoryMapping mapping = RepositoryMapping.getMapping(project);
			if (mapping != null && mapping.getRepository() == repo)
				projects.add(project);
		}
		updateRevs();
	}

	/**
	 * Recalculates source, destination and ancestor Rev commits
	 *
	 * @throws IOException
	 */
	public void updateRevs() throws IOException {
		ObjectWalk ow = new ObjectWalk(repo);
		try {
			srcRevCommit = getCommit(shouldUseHead(srcRev) ? HEAD : srcRev, ow);
			dstRevCommit = getCommit(shouldUseHead(dstRev) ? HEAD : dstRev, ow);
		} finally {
			ow.release();
		}

		if (this.dstRevCommit != null && this.srcRevCommit != null)
			this.ancestorRevCommit = getCommonAncestor(repo, this.srcRevCommit,
					this.dstRevCommit);
		else
			this.ancestorRevCommit = null;
	}

	/**
	 * @return instance of repository that should be synchronized
	 */
	public Repository getRepository() {
		return repo;
	}

	/**
	 * @return name of source remote or {@code null} when source branch is not a
	 *         remote branch
	 */
	public String getSrcRemoteName() {
		return srcRemoteConfig.remote;
	}

	/**
	 * @return ref specification of source merge branch
	 */
	public String getSrcMerge() {
		return srcRemoteConfig.merge;
	}

	/**
	 * @return name of destination remote or {@code null} when destination
	 *         branch is not a remote branch
	 */
	public String getDstRemoteName() {
		return dstRemoteConfig.remote;
	}

	/**
	 * @return ref specification of destination merge branch
	 */
	public String getDstMerge() {
		return dstRemoteConfig.merge;
	}

	/**
	 * @return synchronize source rev name
	 */
	public RevCommit getSrcRevCommit() {
		return srcRevCommit;
	}

	/**
	 * @return synchronize destination rev name
	 */
	public RevCommit getDstRevCommit() {
		return dstRevCommit;
	}

	/**
	 * @return list of project's that are connected with this repository
	 */
	public Set<IProject> getProjects() {
		return Collections.unmodifiableSet(projects);
	}

	/**
	 * @param file
	 * @return <true> if given {@link File} is contained by this repository
	 */
	public boolean contains(File file) {
		return file.getAbsoluteFile().toString().startsWith(repoParentPath);
	}

	/**
	 * @return <code>true</code> if local changes should be included in
	 *         comparison
	 * @deprecated use {@link #WORKING_TREE} or {@link #INDEX}
	 */
	public boolean shouldIncludeLocal() {
		return WORKING_TREE.equals(srcRev);
	}

	/**
	 * @return common ancestor commit
	 */
	public RevCommit getCommonAncestorRev() {
		return ancestorRevCommit;
	}

	/**
	 * @param includedPaths
	 *            list of containers to be synchronized
	 */
	public void setIncludedPaths(Set<IContainer> includedPaths) {
		this.includedPaths = includedPaths;
		Set<String> paths = new HashSet<String>();
		RepositoryMapping rm = RepositoryMapping.findRepositoryMapping(repo);
		for (IContainer container : includedPaths) {
			String repoRelativePath = rm.getRepoRelativePath(container);
			if (repoRelativePath.length() > 0)
				paths.add(repoRelativePath);
		}

		if (!paths.isEmpty())
			pathFilter = PathFilterGroup.createFromStrings(paths);
	}

	/**
	 * @return set of included paths or {@code null} when all paths should be
	 *         included
	 */
	public Set<IContainer> getIncludedPaths() {
		return includedPaths;
	}

	/**
	 * Disposes all nested resources
	 */
	public void dispose() {
		if (projects != null)
			projects.clear();
		if (includedPaths != null)
			includedPaths.clear();
	}

	/**
	 * @return instance of {@link TreeFilter} when synchronization was launched
	 *         from nested node (like folder) or {@code null} otherwise
	 */
	public TreeFilter getPathFilter() {
		return pathFilter;
	}

	/**
	 * @return synchronization source rev
	 */
	public String getSrcRev() {
		return srcRev;
	}

	/**
	 * @return synchronization destination rev
	 */
	public String getDstRev() {
		return dstRev;
	}

	private RemoteBranchInfo extractRemoteName(String rev) {
		if (rev.contains(R_REMOTES)) {
			String remoteWithBranchName = rev.replaceAll(R_REMOTES, ""); //$NON-NLS-1$
			int firstSeparator = remoteWithBranchName.indexOf("/"); //$NON-NLS-1$

			String remote = remoteWithBranchName.substring(0, firstSeparator);
			String name = remoteWithBranchName.substring(firstSeparator + 1,
					remoteWithBranchName.length());

			return new RemoteBranchInfo(remote, R_HEADS + name);
		} else {
			String realName;
			Ref ref;
			try {
				ref = repo.getRef(rev);
			} catch (IOException e) {
				ref = null;
			}
			if (ref != null && ref.isSymbolic())
				realName = ref.getTarget().getName();
			else
				realName = rev;
			String name = BRANCH_NAME_PATTERN.matcher(realName).replaceAll(""); //$NON-NLS-1$
			String remote = repo.getConfig().getString(CONFIG_BRANCH_SECTION,
					name, CONFIG_KEY_REMOTE);
			String merge = repo.getConfig().getString(CONFIG_BRANCH_SECTION,
					name, CONFIG_KEY_MERGE);

			return new RemoteBranchInfo(remote, merge);
		}
	}

	private RevCommit getCommit(String rev, ObjectWalk ow) throws IOException {
		if (rev.length() > 0) {
			ObjectId id = repo.resolve(rev);
			return id != null ? ow.parseCommit(id) : null;
		} else
			return null;
	}

	/**
	 * @return the remote configuration to fetch from before synchronizing
	 */
	public RemoteConfig getFetchFromRemote() {
		return fetchFromRemote;
	}

	/**
	 * @param remote the remote configuration to fetch from before synchronizing data
	 */
	public void setFetchFromRemote(RemoteConfig remote) {
		this.fetchFromRemote = remote;
	}

}
