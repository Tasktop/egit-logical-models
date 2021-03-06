/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize.model;

import java.util.Map;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.synchronize.GitCommitsModelCache.Change;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jgit.lib.Repository;

/**
 * Representation of working tree in EGit ChangeSet model
 */
public class GitModelWorkingTree extends GitModelCache {

	/**
	 * @param parent
	 *            parent object
	 * @param repo
	 *            repository associated with this object
	 * @param cache
	 *            list of cached changes
	 */
	public GitModelWorkingTree(GitModelRepository parent, Repository repo,
			Map<String, Change> cache) {
		super(parent, repo, cache, new FileModelFactory() {
			public GitModelBlob createFileModel(
					GitModelObjectContainer objParent, Repository nestedRepo,
					Change change, IPath path) {
				return new GitModelWorkingFile(objParent, nestedRepo, change,
						path);
			}

			public boolean isWorkingTree() {
				return true;
			}
		});
	}

	@Override
	public String getName() {
		return UIText.GitModelWorkingTree_workingTree;
	}

	@Override
	public int getKind() {
		// changes in working tree are always outgoing modifications
		return Differencer.RIGHT | Differencer.CHANGE;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;

		if (obj == null)
			return false;

		if (obj.getClass() != getClass())
			return false;

		GitModelCache left = (GitModelCache) obj;
		return left.getParent().equals(getParent());
	}

	@Override
	public int hashCode() {
		return getParent().hashCode() + 31;
	}

	@Override
	public String toString() {
		return "ModelWorkingTree"; //$NON-NLS-1$
	}

}
