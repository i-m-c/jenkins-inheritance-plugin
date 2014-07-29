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
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterValue;
import hudson.plugins.project_inheritance.util.PathMapping;
import hudson.plugins.project_inheritance.util.Resolver;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
	
	private void setVersions() {
		Map<String, Long> versions = getProjectVersions();
		if (versions != null) {
			//Set the normal versioning (will not register values in thread)
			InheritanceProject.setVersioningMap(versions);
			//Save the versioning also in the local thread, since by this point
			//we might not have an HTTPRequest to use as a storage anymore
			InheritanceProject.setVersioningMapInThread(versions);
		}
	}
	
	private void unsetVersions() {
		InheritanceProject.unsetVersioningMap();
	}
	
	public static FilePath getWorkspacePathFor(
			Node n, InheritanceProject project, Map<String, String> values) {
		String path = project.getParameterizedWorkspace();
		if (path == null || path.isEmpty()) {
			return null;
		}
		
		//Resolve the path's variables
		String resolv = Resolver.resolveSingle(values, path);
		if (resolv == null) { return null; }
		
		resolv = resolv.trim();
		if (resolv.isEmpty()) { return null; }
		
		//Check if the path looks absolute; if not put it under the node
		if (PathMapping.isAbsolute(resolv) == false) {
			FilePath root = n.getWorkspaceFor(project);
			if (root != null) {
				root = root.getParent();
				resolv = PathMapping.join(root.getRemote(), resolv);
			}
		}
		return new FilePath(n.getChannel(), resolv);
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
		this.setVersions();
		try {
			super.execute(new InheritanceBuildExecution());
		} finally {
			this.unsetVersions();
		}
	}
	
	protected class InheritanceBuildExecution extends BuildExecution {
		@Override
		protected Result doRun(BuildListener listener) throws Exception {
			/* Please note: The version MUST have been set up by the
			 * run() method above! Otherwise, you will only get stable/latest
			 * versions!
			 */
			Run<?,?> run = this.getBuild();
			if (!(run instanceof InheritanceBuild)) {
				listener.fatalError(
						"InheritanceBuildExecution was not started by an"
						+ " InheritanceBuild. Versioning and Inheritance"
						+ " can't be trusted."
				);
				throw new RunnerAbortedException();
			}
			
			/* Check if all the parameters were set right; and somebody did not
			 * forget to set a parameter correctly.
			 */
			for (ParameterValue pv : this.getParameterValues()) {
				if (!(pv instanceof InheritableStringParameterValue)) {
					continue;
				}
				InheritableStringParameterValue ispv =
						(InheritableStringParameterValue) pv;
				//Check if a value should've been set, but was not
				if (!ispv.getMustHaveValueSet()) { continue; }
				if (ispv.value == null || ispv.value.isEmpty()) {
					//We detected an invalid value
					listener.fatalError(String.format(
							"Parameter '%s' has no value, but was required to be set. Aborting!",
							ispv.getName()
							));
					throw new RunnerAbortedException();
				}
			}
			
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
			//Check if a parameter itself sets the workspace path
			for (ParameterValue pv : this.getParameterValues()) {
				if (!(pv instanceof StringParameterValue)) {
					continue;
				}
				StringParameterValue spv = (StringParameterValue) pv;
				if (spv.getName().equalsIgnoreCase("WORKSPACE_REUSE_PATH")) {
					//Fetch the workspace (do not care about locking)
					return Lease.createDummyLease(
							new FilePath(n.getChannel(), spv.value)
					);
				}
			}
			
			//Otherwise, derive the workspace path "normally" with locking
			
			Job<?, ?> job = this.getProject();
			if (job == null && !(job instanceof InheritanceProject)) {
				//Invalid job; fall-back
				return super.decideWorkspace(n, wsl);
			}
			InheritanceProject ip = (InheritanceProject) job;
			
			FilePath ws = InheritanceBuild.getWorkspacePathFor(
					n, ip,
					this.getBuild().getEnvironment(this.getListener())
			);
			if (ws == null) {
				return super.decideWorkspace(n, wsl);
			}
			
			//We have the path; create a variable Lease
			return wsl.allocate(ws, getBuild());
		}
		
		
		protected Collection<ParameterValue> getParameterValues() {
			Map<String, ParameterValue> map = new HashMap<String, ParameterValue>();
			Run<?,?> run = this.getBuild();
			List<ParametersAction> actions =
					run.getActions(ParametersAction.class);
			
			for (ParametersAction pa : actions) {
				for (ParameterValue pv : pa.getParameters()) {
					map.put(pv.getName(), pv);
				}
			}
			
			return map.values();
		}
	}
}

