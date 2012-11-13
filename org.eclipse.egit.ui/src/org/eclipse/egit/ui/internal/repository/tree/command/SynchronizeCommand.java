/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *    Dariusz Luksza <dariusz@luksza.org>
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import static org.eclipse.egit.core.synchronize.dto.GitSynchronizeData.WORKING_TREE;
import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.IOException;
import java.util.Set;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.egit.ui.internal.synchronize.GitModelSynchronize;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.NLS;

/**
 * Implements "Synchronize"
 */
public class SynchronizeCommand extends
		RepositoriesViewCommandHandler<RepositoryTreeNode> {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final RepositoryTreeNode node = getSelectedNodes(event).get(0);
		final String refName = getRefName(node);
		if (refName == null)
			return null;

		final String leftRefName;
		if (getSelectedNodes(event).size() == 2) {
			leftRefName = getRefName(getSelectedNodes(event).get(1));
			if (leftRefName == null)
				return null;
		} else if(getSelectedNodes(event).size() == 1) {
			// compare with working tree
			leftRefName = WORKING_TREE;
		} else {
			// none or three or more selected .. fall back to HEAD?
			// FIXME: this "magic" seams wrong; should sync abort instead?
			leftRefName = HEAD;
		}

		final Repository repo = node.getRepository();
		Job job = new Job(NLS.bind(UIText.SynchronizeCommand_jobName,
				repo.getDirectory())) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				GitSynchronizeData data;
				try {
					data = new GitSynchronizeData(repo, leftRefName, refName);

					Set<IProject> projects = data.getProjects();
					IResource[] resources = projects
							.toArray(new IResource[projects.size()]);

					GitModelSynchronize.launch(data, resources);
				} catch (IOException e) {
					Activator.logError(e.getMessage(), e);
				}

				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();

		return null;
	}

	private String getRefName(final RepositoryTreeNode node) {
		Object object = node.getObject();
		if (!(object instanceof Ref))
			return null;

		Ref ref = (Ref) object;
		return ref.getName();
	}

}
