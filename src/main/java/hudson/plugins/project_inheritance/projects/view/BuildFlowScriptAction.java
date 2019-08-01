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
package hudson.plugins.project_inheritance.projects.view;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Items;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.projects.view.scripts.MetaScript;
import hudson.plugins.project_inheritance.util.PathMapping;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;

/**
 * This class implements the action that allows you to see and download all the
 * build steps that make up a particular run of a project.
 * 
 */
public class BuildFlowScriptAction
		implements RunAction2, Describable<BuildFlowScriptAction> {
	private static final Logger log =
			Logger.getLogger(BuildFlowScriptAction.class.toString());
	
	private static final Charset UTF8 = Charset.forName("utf8");
	
	private static final String LABEL_DISPLAY_NAME = "Full Build Flow";
	
	
	private transient AbstractBuild<?,?> build;
	
	@Override
	public void onAttached(Run<?, ?> r) {
		if (r instanceof AbstractBuild<?,?>) {
			this.build = (AbstractBuild<?,?>)r;
		} else {
			build = null;
		}
	}

	@Override
	public void onLoad(Run<?, ?> r) {
		if (r instanceof AbstractBuild<?,?>) {
			this.build = (AbstractBuild<?,?>)r;
		} else {
			build = null;
		}
	}
	
	@Initializer(after=InitMilestone.PLUGINS_STARTED)
	public static void registerXStream() {
		//TODO: Don't remove this until after v2.0 is long superseded in OpenSource release
		XStream2[] xsa = { Jenkins.XSTREAM2, Items.XSTREAM2, Build.XSTREAM2 };
		for (XStream2 xs : xsa) {
			xs.addCompatibilityAlias(
					"hudson.plugins.project_inheritance.projects.view.InheritanceViewAction",
					BuildFlowScriptAction.class
			);
		}
	}
	
	/**
	 * Returns the project associated with the build this action is configured on.
	 * 
	 * @return null, if no build is present from which the project can be grabbed.
	 */
	public AbstractProject<?,?> getProject() {
		AbstractBuild<?, ?> b = this.getBuild();
		return (b != null) ? b.getParent() : null;
		
	}
	
	/**
	 * This method returns the {@link InheritanceProject} associated with the
	 * given request; if any are.
	 * 
	 * Otherwise, returns null.
	 * 
	 * @param request the request to check for an {@link InheritanceProject}
	 * @return null, if no such project is associated with the request
	 */
	public InheritanceProject getProject(StaplerRequest request) {
		return InheritanceProject.DESCRIPTOR.getConfiguredProject(request);
	}
	
	public AbstractBuild<?,?> getBuild() {
		if (this.build == null) {
			//Fallback, in case the job was not loaded properly
			StaplerRequest req = Stapler.getCurrentRequest();
			return this.getBuild(req);
		}
		return this.build;
	}
	
	public AbstractBuild<?,?> getBuild(StaplerRequest req) {
		if (req != null) {
			return req.findAncestorObject(AbstractBuild.class);
		}
		return this.build;
	}
	
	private boolean isApplicableFor(AbstractProject<?,?> p) {
		return (
				p != null &&
				p.isBuildable() &&
				!(p.isDisabled())
		);
	}
	
	public String getIconFileName() {
		//Checking if the project associated with the current request is valid
		AbstractProject<?,?> p = this.getProject();
		if (this.isApplicableFor(p)) {
			//The rebuild action is allowed to be displayed and gets the
			//default icon for rebuilds
			return "notepad.png";
		} else {
			//Not assigning an icon automagically causes the build action to
			//not be displayed.
			return null;
		}
	}

	public String getDisplayName() {
		AbstractProject<?,?> p = this.getProject();
		if (this.isApplicableFor(p)) {
			return LABEL_DISPLAY_NAME;
		} else {
			return null;
		}
	}

	public String getUrlName() {
		AbstractProject<?,?> p = this.getProject();
		if (this.isApplicableFor(p)) {
			return "view";
		} else {
			return null;
		}
	}
	
	
	// === REST API CALLS ===
	
	/**
	 * Creates an {@link HttpResponse} that will send a TGZ containing build
	 * scripts.
	 * 
	 * @return null, if the file could not be generated, otherwise a valid {@link HttpResponse}.
	 */
	public ReadOnlyConfigurationArchive doDownload() {
		AbstractProject<?,?> proj = this.getProject();
		AbstractBuild<?,?> build = this.getBuild();
		
		//Use the versions from that build (if present)
		Map<String, Long> versions = null;
		if (build instanceof InheritanceBuild) {
			versions = (build != null)
					? ((InheritanceBuild)build).getProjectVersions()
					: null;
		}
		if (versions != null) {
			VersionHandler.initVersions(versions);
		}
		
		String name = (build != null)
				? String.format("%s_%d", proj.getFullName(), build.getNumber())
				: proj.getFullName();
		name = PathMapping.getSafePath(name);
		
		try {
			File archive = this.generateExecutableCompoundScript(
					build,
					this.getBuildersFor(proj),
					name,
					getResolvedBuildParameters(this.getBuild())
			);
			String ext = FilenameUtils.getExtension(archive.getName());
			return new ReadOnlyConfigurationArchive(
					String.format("%s.%s", name, ext),
					archive
			);
		} catch (IOException ex) {
			log.warning(String.format(
					"Failed to generate script download for %s. Reason: %s",
					build,
					ex.getMessage()
			));
			return null;
		} finally {
			VersionHandler.clearVersions();
		}
	}
	
	
	// === SCRIPT ARCHIVE HELPER METHODS ===
	
	/**
	 * Returns the list of build steps for a given project, or an empty list
	 * if the project is invalid or has no builders.
	 * 
	 * @param p the project to scan
	 * @return a list, never null but may be empty.
	 */
	protected List<Builder> getBuildersFor(AbstractProject<?,?> p) {
		if (p instanceof InheritanceProject) {
			InheritanceProject ip = (InheritanceProject) p;
			return ip.getBuildersList(IMode.INHERIT_FORCED).toList();
		} else if (p instanceof Project<?, ?>) {
			return ((Project<?,?>)p).getBuilders();
		} else {
			return Collections.emptyList();
		}
	}
	
	public File generateExecutableCompoundScript(
			AbstractBuild<?,?> build,
			List<Builder> builders,
			String archiveName,
			Map<String, String> params
	) throws IOException {
		//Allow extensions to filter the environment variables
		params = BuildFlowScriptExtension.filterEnv(params);
		
		//Convert the builders into scripts
		final List<MetaScript> scripts;
		try {
			BuildFlowScriptExtension.initThread();
			scripts = BuildFlowScriptExtension.getScriptsFor(
					null,
					build.getProject(),
					build,
					builders,
					params,
					new AtomicInteger()
			);
		} finally {
			BuildFlowScriptExtension.cleanUpThread();
		}
		
		//Fetch parameters for this build, to set in the control files
		Map<String, String> env = getResolvedBuildParameters(this.getBuild());
		
		//Get the Windows and Bash control files for these scripts
		MetaScript bashCtrl = getBashControlFile(null, null, scripts, env, true);
		MetaScript cmdCtrl = getCmdControlFile(null, null, scripts, env, true);
		
		//Un-set the callable flag of the generated scripts (as they have been "called")
		for (MetaScript ms : scripts) { ms.setCallable(false); }
		
		//Add the two control scripts (which stay callable)
		scripts.add(bashCtrl);
		scripts.add(cmdCtrl);
		
		//Create a TGZ file for the user to download
		File dstFile = File.createTempFile(archiveName + "_", ".tgz");
		if (dstFile.getParentFile() != null) {
			dstFile.getParentFile().mkdirs();
		}
		
		//Dump all scripts into a TGZ for the user to download
		try {
			this.createTgzArchive(dstFile, scripts);
		} catch (IOException ex) {
			// The file could not be generated
			log.warning(String.format(
					"Failed to generate script files for %s. Reason: %s",
					build,
					ex.getMessage()
			));
			return null;
		}
		return dstFile;
	}
	
	/**
	 * This method generates a "build.bat" windows CMD control file for the
	 * given script files
	 * @param prefix the directory the control file will be created in. May be null.
	 * @param scriptName the name for the control file, defaults to "build" if null or blank.
	 * @param scripts the scripts for which to create the control file
	 * @param env the environment variables to be set in this control file
	 * @param setWorkspaceVars if true, the script will set and create the WORKSPACE variable.
	 * 
	 * @return a script containing a Windows CMD control file
	 */
	public static MetaScript getCmdControlFile(
			File prefix,
			String scriptName,
			List<MetaScript> scripts,
			Map<String, String> env,
			boolean setWorkspaceVars
	) {
		StringBuilder out = new StringBuilder();
		
		if (StringUtils.isBlank(scriptName)) { scriptName = "build"; }
		
		//Writing the preamble (shebang)
		out.append("@echo off\r\n");
		
		//Writing the parameter assignments
		if (env.size() > 0) {
			for (String key : env.keySet()) {
				out.append(String.format(
						"set %s=%s\r\n",
						key, env.get(key)
				));
			}
		}
		out.append("\r\n");
		
		if (setWorkspaceVars) {
			//Override the WORKSPACE variable, create the dir and change into it
			out.append("set ROOT=%cd%\r\n");
			out.append("set WORKSPACE=%ROOT%\\workspace\r\n");
			out.append("mkdir \"%WORKSPACE%\"\r\n");
			out.append("cd \"%WORKSPACE%\"\r\n");
			out.append("\r\n");
		}
		
		//Dumping the meta-scripts to disk
		for (MetaScript script : scripts) {
			if (!script.isCallable()) { continue; }
			
			//Do note invoke "*.sh" scripts
			if (script.file.getName().endsWith(".sh")) { continue; }
			
			String fName = FilenameUtils.separatorsToWindows(script.file.getPath());
			out.append(String.format(
					"echo Will run %s now ...\r\n",
					fName
			));
			//The invocation -- need to handle shebang here
			if (StringUtils.isBlank(script.shebang)) {
				out.append(String.format(
						"call '%s'\r\n",
						fName
				));
			} else {
				//TODO: Linux Shebangs are usually useless on Windows; find a better way
				out.append(String.format(
						"%s '%%ROOT%%\\%s'\r\n",
						script.shebang,
						fName
				));
			}
			out.append("if errorlevel 1 ( exit 1 )\r\n");
			out.append("\r\n");
		}
		
		File scriptfile = (prefix != null)
				? new File(prefix, scriptName + ".bat")
				: new File(scriptName + ".bat");
		return new MetaScript(
				"", out.toString(), scriptfile
		);
	}
	
	/**
	 * This method generates a "build.sh" Bash control file for the
	 * given script files.
	 * @param prefix the directory the control file will be created in. May be null.
	 * @param scriptName the name for the control file, defaults to "build" if null or blank.
	 * @param scripts the scripts for which to create the control file
	 * @param env the environment variables to be set in this control file
	 * @param setWorkspaceVars if true, the script will set and create the WORKSPACE variable.
	 * 
	 * @return a script containing a Bash control file
	 */
	public static MetaScript getBashControlFile(
			File prefix,
			String scriptName,
			List<MetaScript> scripts,
			Map<String, String> env,
			boolean setWorkspaceVars
	) {
		StringBuilder out = new StringBuilder();
		
		if (StringUtils.isBlank(scriptName)) { scriptName = "build"; }
		
		//Writing the preamble (shebang)
		out.append("#!/bin/bash\n\n");
		
		//Writing the parameter assignments
		if (env.size() > 0) {
			for (String key : env.keySet()) {
				out.append(String.format(
						"export %s=\"%s\"\n",
						key, env.get(key)
				));
			}
		}
		out.append('\n');
		
		if (setWorkspaceVars) {
			//Override the WORKSPACE variable, create the dir and change into it
			out.append("export ROOT=\"$(pwd)\"\n");
			out.append("export WORKSPACE=\"$ROOT/workspace\"\n");
			out.append("mkdir -p \"$WORKSPACE\"\n");
			out.append("cd \"$WORKSPACE\"\n");
			out.append('\n');
		}
		
		//Dumping the meta-scripts to disk
		for (MetaScript script : scripts) {
			if (!script.isCallable()) { continue; }
			
			//Do note invoke "*.bat" scripts
			if (script.file.getName().endsWith(".bat")) { continue; }
			
			String fName = FilenameUtils.separatorsToUnix(script.file.getPath());
			out.append(String.format(
					"echo 'Will run %s now ...'\n",
					fName
			));
			//The invocation -- no need to handle shebang, as Bash does that for us
			out.append(String.format(
					"$ROOT/%s\n",
					fName
			));
			out.append("if [[ $? -ne 0 ]]; then exit 1; fi\n");
			out.append('\n');
		}
		
		File scriptfile = (prefix != null)
				? new File(prefix, scriptName + ".sh")
				: new File(scriptName + ".sh");
		return new MetaScript(
				"/bin/bash", out.toString(), scriptfile
		);
	}
	
	public void createTgzArchive(File dstFile, List<MetaScript> scripts)
			throws IOException {
		//Creating the output ZipFile
		FileOutputStream fos = new FileOutputStream(dstFile);
		GZIPOutputStream gzos = new GZIPOutputStream(fos);
		TarOutputStream tos = new TarOutputStream(gzos);
		tos.setLongFileMode(TarOutputStream.LONGFILE_GNU);
		
		try {
			for (MetaScript script : scripts) {
				//Grab the byte[] for the content of the script
				byte[] content = script.content.getBytes(UTF8);
				
				//Create a new tarEntry for the path the metaScript wants
				String path = FilenameUtils.separatorsToUnix(script.file.getPath());
				TarEntry entry = new TarEntry(path);
				//Mark the script file as executable
				entry.setMode(0777);
				entry.setSize(content.length);
				//Put the entry into the ZIP file
				tos.putNextEntry(entry);
				//Dump the data
				tos.write(content);
				//Close the entry
				tos.closeEntry();
			}
		} finally {
			if (tos != null) {
				tos.close();
			}
		}
	}
	
	
	// === PARAMETER RESOLUTION METHODS ===
	
	public static Map<String, String> getResolvedBuildParameters(
			AbstractBuild<?,?> build
	) {
		if (build == null) { return Collections.emptyMap(); }
		
		//Get the list of sensitive parameters that should not be leaked
		Set<String> sensitive = build.getSensitiveBuildVariables();
		
		//Adding parameters into a sorted tree map
		Map<String, String> result = new TreeMap<String, String>();
		for (Entry<String, String> e : build.getBuildVariables().entrySet()) {
			//Do not leak sensitive values
			String val = (sensitive.contains(e.getKey())) ? "" : e.getValue();
			result.put(e.getKey(), val);
		}
		
		
		/* Add some known variables that Jenkins adds in 
		 * build.getEnvironment(), but not in getBuildVariables()
		 * Remember: Add, not override.
		 */
		for (EnvironmentContributingAction a : build.getActions(EnvironmentContributingAction.class)) {
			EnvVars env = new EnvVars();
			a.buildEnvironment(build, env);
			for (Entry<String, String> e : env.entrySet()) {
				//Do not overwrite existing values
				if (result.containsKey(e.getKey())) { continue; }
				//Do not leak sensitive values
				String val = (sensitive.contains(e.getKey())) ? "" : e.getValue();
				result.put(e.getKey(), val);
			}
		}
		//And some that Jenkins adds only for build hosts
		result.put("JENKINS_HOME", Jenkins.get().getRootDir().getAbsolutePath());
		
		//Repeatedly resolve values, until all values are stable
		//or the same key has been resolved >10 times
		Map<String, Integer> seen = new HashMap<>();
		Deque<String> open = new LinkedList<>(result.keySet());
		while (!open.isEmpty()) {
			String key = open.pop();
			String value = result.get(key);
			//Trying to substitute variables
			String resolved = Util.replaceMacro(value, result);
			if (!resolved.equals(value)) {
				//A value has been substituted, replace value
				result.put(key, resolved);
				
				//Check if recursion depth for that key was reached
				//If not, schedule it for another round
				Integer depth = seen.get(key);
				if (depth == null) { depth = 0; }
				if (depth < 10) {
					seen.put(key, depth+1);
					open.addLast(key);
				}
			}
		}
		
		return result;
	}
	
	public static Map<String, String> getResolvedBuildParameters(
			InheritanceProject project
	) {
		Map<String, String> result = new TreeMap<String, String>();
		
		List<ParameterDefinition> parameters = project.getParameters(
				IMode.INHERIT_FORCED
		);
		for (ParameterDefinition pDef : parameters) {
			ParameterValue pVal = pDef.getDefaultParameterValue();
			//Only emit string-like values
			if (pVal instanceof StringParameterValue) {
				//Do not emit sensitive values
				String val = (pVal.isSensitive())
						? ""
						: ((StringParameterValue) pVal).getValue().toString();
				result.put(pDef.getName(), val);
			}
		}
		//Note: Parameters are not resolved; as they are most likely not complete anyway
		return result;
	}


	// === DESCRIPTOR METHODS AND CLASSES ===
	
	public BuildFlowScriptActionDescriptor getDescriptor() {
		return (BuildFlowScriptActionDescriptor)
				Jenkins.get().getDescriptorOrDie(this.getClass());
	}
	
	public static BuildFlowScriptActionDescriptor getDescriptorStatic() {
		return (BuildFlowScriptActionDescriptor)
				Jenkins.get().getDescriptorOrDie(BuildFlowScriptAction.class);
	}
	
	@Extension(ordinal = 1000)
	public static final class BuildFlowScriptActionDescriptor extends Descriptor<BuildFlowScriptAction> {

		public BuildFlowScriptActionDescriptor() {
			//Does nothing yet
		}
		
		@Override
		public String getDisplayName() {
			return "Inheritance View";
		}
		
		public ListBoxModel doFillProjectClassItems() {
			return InheritanceProject.DESCRIPTOR.doFillCreationClassItems();
		}
	}
}
