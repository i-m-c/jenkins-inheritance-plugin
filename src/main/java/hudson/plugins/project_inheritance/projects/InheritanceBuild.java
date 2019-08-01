/**
 * Copyright (c) 2018-2019 Intel Corporation
 * Copyright (c) 2015-2017 Intel Deutschland GmbH
 * Copyright (c) 2011-2015 Intel Mobile Communications GmbH
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import hudson.FilePath;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.util.BuildDiscardPreventer;
import hudson.plugins.project_inheritance.util.NodeFileSeparator;
import hudson.plugins.project_inheritance.util.PathMapping;
import hudson.plugins.project_inheritance.util.Resolver;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;


public class InheritanceBuild extends Build<InheritanceProject, InheritanceBuild> {
	private static final Logger LOGGER = Logger.getLogger(InheritanceBuild.class.getName());
	
	private static final Pattern truncatePattern = Pattern.compile("(?i)<truncate\\s*/>");
	
	protected transient Map<String, Long> projectVersions;
	
	public InheritanceBuild(InheritanceProject project) throws IOException {
		super(project);
	}

	public InheritanceBuild(InheritanceProject project, File buildDir) throws IOException {
		super(project, buildDir);
	}
	
	@Override
	public @CheckForNull String getWhyKeepLog() {
		String fromSuper = super.getWhyKeepLog();
		if (fromSuper != null) { return fromSuper; }
		
		for (BuildDiscardPreventer bdp : BuildDiscardPreventer.all()) {
			String msg = bdp.getWhyKeepLog(this);
			if (msg != null) { return msg; }
		}
		//Can be safely deleted
		return null;
	}
	
	@Override
	public @Nonnull String getTruncatedDescription() {
		//Fetch the full description
		String desc = this.getDescription();
		if (StringUtils.isEmpty(desc)) { return ""; }
		//Try to find a <truncate/> tag
		Matcher m = truncatePattern.matcher(desc);
		if (m.find()) {
			//Truncate tag was found, trusting the user's choice
			return desc.substring(0, m.start());
		}
		//Otherwise, revert to the regular behaviour
		return super.getTruncatedDescription();
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
		//Clear current set of versions
		VersionHandler.clearVersions();
		
		//Check if a versioning action is present
		Map<String, Long> map = this.getProjectVersions();
		if (map != null) {
			VersionHandler.initVersions(map);
			return;
		}
		
		//Otherwise, we init from our parent
		map = VersionHandler.initVersions(this.getParent());
		this.addAction(new VersioningAction(map));
	}
	
	private void unsetVersions() {
		VersionHandler.clearVersions();
	}
	
	public static FilePath getWorkspacePathFor(
			Node n, InheritanceProject project, Map<String, String> values) {
		if (n == null || project == null) { return null; }
		
		final NodeFileSeparator nfi = NodeFileSeparator.instance;
		
		//Check if a custom workspace is demanded
		String customWorkspace = project.getCustomWorkspace();
		if (customWorkspace != null) {
			customWorkspace = nfi.ensurePathCorrect(n, customWorkspace);
			FilePath root = n.getRootPath();
			return new FilePath(root, customWorkspace);
		}
		
		String path = project.getParameterizedWorkspace();
		if (path != null && ! path.isEmpty()) {
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
			resolv = nfi.ensurePathCorrect(n, resolv);
			return new FilePath(n.getChannel(), resolv);
		}
		
		//Use the workspace locator extensions, if no parameterized WS is present
		return n.getWorkspaceFor(project);
	}
	
	
	
	/**
	 * This method schedules the execution of this build object.
	 * <p>
	 * It suppresses the unchecked conversion warning, because the parent
	 * class {@link Build} makes use of the {@link hudson.model.Build.BuildExecution}
	 * class which fakes its way to be compatible with both the deprecated
	 * Runner class and the new {@link hudson.model.Run.RunExecution} class.
	 * </p>
	 */
	@Override
	public void run() {
		//Making sure that we set the desired versions correctly
		this.setVersions();
		try {
			this.onRun();
		} finally {
			this.unsetVersions();
		}
	}
	
	public void onRun() {
		super.execute(new InheritanceBuildExecution());
	}
	
	/**
	 * This exception is raised, when an {@link Error} had to be converted
	 * into an {@link Exception} to work around a Jenkins file handle leak in
	 * {@link Run}.execute().
	 * <p>
	 * When {@link BuildExecution#cleanUp(BuildListener)} raises an error
	 * instead of an exception, the build logger (its underlying file stream)
	 * is closed in Run.execute(), but its overlaying listener is not closed.
	 * <p>
	 * When the GC does not finalize those objects because it has enough
	 * memory, an open file handle might be leaked.
	 * <p>
	 * This needs to be fixed upstream in Jenkins, but until then it must be
	 * worked-around by converting the Error into an Exception.
	 * <p>
	 * One source of such an Error can be {@link WorkspaceList}.release().
	 */
	public static class HadToConvertErrorException extends Exception {
		private static final long serialVersionUID = 4793047694447530842L;
		
		public HadToConvertErrorException(Throwable error) {
			super(error);
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
						+ " InheritanceBuild. Versioning and inheritance"
						+ " can't be trusted."
				);
				throw new RunnerAbortedException();
			}
			
			//Check if all the parameters were set right
			InheritanceParametersDefinitionProperty.checkParameterSanity(
					this.getBuild(),
					listener
			);
			
			/* Call the regular build response; note: an InterruptedException may
			 * be raised to the caller in case of a job abort.
			 * The default behaviour of Jenkins is to emit a stack trace on
			 * the log output. We don't want that, and thus catch the IE here.
			 */
			try {
				return super.doRun(listener);
			} catch (InterruptedException e) {
				//Exception handling code copied from Run.execute()
				result = Executor.currentExecutor().abortResult();
				listener.getLogger().println(Messages.Run_BuildAborted());
				Executor.currentExecutor().recordCauseOfInterruption(
						run, listener
				);
				//Log the abort, but not the exception
				LOGGER.log(Level.INFO, run + " aborted");
				return result;
			}
		}
		
		@Override
		public void cleanUp(@Nonnull BuildListener listener) throws Exception {
			try {
				super.cleanUp(listener);
			} catch (Error error) {
				//See JavaDoc of HTCEE
				throw new HadToConvertErrorException(error);
			}
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
			Collection<ParameterValue> values =
					InheritanceParametersDefinitionProperty.getParameterValues(
							this.getBuild()
					);
			for (ParameterValue pv : values) {
				if (!(pv instanceof StringParameterValue)) {
					continue;
				}
				StringParameterValue spv = (StringParameterValue) pv;
				if (spv.getName().equalsIgnoreCase("WORKSPACE_REUSE_PATH")) {
					//Fetch the workspace and do not care about locking, as it is handled externally
					return Lease.createDummyLease(
							new FilePath(n.getChannel(), spv.getValue().toString())
					);
				}
			}
			
			//Check if we deal with an inheritance job
			Job<?, ?> job = this.getProject();
			if (job == null && !(job instanceof InheritanceProject)) {
				//Invalid job; fall-back
				return super.decideWorkspace(n, wsl);
			}
			InheritanceProject ip = (InheritanceProject) job;
			
			//Check if a custom workspace is demanded
			String customWorkspace = ip.getCustomWorkspace();
			if (customWorkspace != null) {
				//The parent knows what to do
				return super.decideWorkspace(n, wsl);
			}
			
			//Calling the static workspace path finder
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
	}
}

