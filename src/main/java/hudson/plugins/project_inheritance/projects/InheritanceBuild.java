/**
 * Copyright (c) 2011-2013, Intel Mobile Communications GmbH
 * 
 * 
 * This file is part of the Inheritance plug-in for Jenkins.
 * 
 * The Inheritance plug-in is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation in version 3
 * of the License
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package hudson.plugins.project_inheritance.projects;

import hudson.FilePath;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.Job;
import hudson.model.Node;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.util.PathMapping;
import hudson.plugins.project_inheritance.util.Resolver;
import hudson.plugins.project_inheritance.util.ThreadAssocStore;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;

import java.io.File;
import java.io.IOException;
import java.util.Map;


public class InheritanceBuild extends Build<InheritanceProject, InheritanceBuild> {
	
	protected transient Map<String, Long> projectVersions;
	
	public InheritanceBuild(InheritanceProject project) throws IOException {
		super(project);
	}

	public InheritanceBuild(InheritanceProject project, File buildDir) throws IOException {
		super(project, buildDir);
	}
	
	public synchronized void save() throws IOException {
		super.save();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public File getRootDir() {
		return super.getRootDir();
	}
	
	public Map<String, Long> getProjectVersions() {
		if (projectVersions == null) {
			//Looping through the actions to see if a specific version is
			//associated to it
			VersioningAction verAction = getAction(VersioningAction.class);
			if (verAction != null) {
				projectVersions = verAction.versionMap;
			}
		}
		return projectVersions;
	}
	
	
	/**
	 * This method schedules the execution of this build object.
	 * <p>
	 * It suppresses the unchecked conversion warning, because the parent
	 * class {@link Build} makes use of the {@link BuildExecution} object
	 * which fakes its way to be compatible with both the deprecated {@link Runner}
	 * class and the new {@link RunExecution} class.
	 * </p>
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void run() {
		//Making sure that we set the desired versions correctly
		Map<String, Long> versions = getProjectVersions();
		if (versions != null) {
			InheritanceProject.setVersioningMap(versions);
		}
		try {
			super.execute(new InheritanceBuildExecution());
		} finally {
			//At the end; remove the versioning data stored in the current thread
			ThreadAssocStore.getInstance().clear(Thread.currentThread());
		}
	}
	
	protected class InheritanceBuildExecution extends BuildExecution {
		@Override
		protected Result doRun(BuildListener listener) throws Exception {
			/* Please note: The version MUST have been set up by the
			 * run() method above! Otherwise, you will only get stable/latest
			 * versions
			 */
			//Call the regular build response
			return super.doRun(listener);
		}
		
		/**
		 * This function will check if the parent project is set up to
		 * construct the workspace path from a string containing assigned
		 * variables, instead of a fixed path.
		 * <p>
		 * This means that multiple projects can share their workspace, as long
		 * as they use the same parameter values. This is highly useful for
		 * projects that have to check-out code from large repositories or are
		 * making use of a lot of common files.
		 * <p>
		 * If the project does not have the property set, it falls back to the
		 * native Jenkins approach.
		 * <p>
		 * TODO: This might be useful to be sent upstream as a Jenkins-core
		 * feature!
		 */
		@Override
		protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
			Job<?, ?> job = this.getProject();
			if (job == null && !(job instanceof InheritanceProject)) {
				//Invalid job; fall-back
				return super.decideWorkspace(n, wsl);
			}
			InheritanceProject ip = (InheritanceProject) job;
			
			String path = ip.getParameterizedWorkspace();
			if (path == null || path.isEmpty()) {
				return super.decideWorkspace(n, wsl);
			}
			
			//Resolve the path's variables
			String resolv = Resolver.resolveSingle(
					this.getBuild().getEnvironment(this.getListener()), path
			);
			if (resolv == null) {
				return super.decideWorkspace(n, wsl);
			}
			resolv = resolv.trim();
			if (resolv.isEmpty()) {
				return super.decideWorkspace(n, wsl);
			}
			
			//Check if the path looks absolute; if not put it under the node
			if (PathMapping.isAbsolute(resolv) == false) {
				FilePath ws = n.getWorkspaceFor((TopLevelItem)getProject());
				if (ws != null) {
					FilePath root = ws.getParent();
					if (root != null) {
						resolv = PathMapping.join(root.getRemote(), resolv);
					}
				}
			}
			
			//We have the path; create a variable Lease
			return wsl.allocate(
					new FilePath(n.getChannel(), resolv),
					getBuild()
			);
		}
	}
}


