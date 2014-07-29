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

package hudson.plugins.project_inheritance.projects.view;

import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.ParameterValue;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.util.PathMapping;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.BatchFile;
import hudson.util.ListBoxModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.io.Files;

/**
 * This class implements the action that allows you to see and download all the
 * build steps that make up a particular run of a project.
 * 
 */
public class InheritanceViewAction implements Action, Describable<InheritanceViewAction> {
	private transient InheritanceBuild build;
	

	/**
	 * Returns the {@link InheritanceProject} associated with the current
	 * {@link StaplerRequest}.
	 * 
	 * @return null, if no {@link InheritanceProject} is associated with the
	 * given request.
	 */
	public InheritanceProject getProject() {
		return this.getBuild().getParent();
		
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
	
	public InheritanceBuild getBuild() {
		if (this.build == null) {
			StaplerRequest req = Stapler.getCurrentRequest();
			return this.getBuild(req);
		}
		return build;
	}
	
	public InheritanceBuild getBuild(StaplerRequest req) {
		if (build == null && req != null) {
			this.build = req.findAncestorObject(InheritanceBuild.class);
		}
		return build;
	}
	
	private boolean isApplicableFor(InheritanceProject ip) {
		return (
				ip != null &&
				ip.isBuildable() &&
				!(ip.isDisabled())
		);
	}
	
	public String getIconFileName() {
		//Checking if the project associated with the current request is valid
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
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
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
			return "View/Download full build flow";
		} else {
			return null;
		}
	}

	public String getUrlName() {
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
			return "view";
		} else {
			return null;
		}
	}
	
	
	// === REST API CALLS ===
	
	public ReadOnlyConfigurationArchive doDownload() {
		Map<String, Long> versions = (build != null) ? build.getProjectVersions() : null;
			if (versions != null) {
			InheritanceProject.setVersioningMap(versions);
		}
		List<Builder> builders =
				this.getProject().getBuildersList(IMode.INHERIT_FORCED).toList();
		try {
			File archive = this.generateExecutableCompoundScript(
					builders,
					this.getProject().getName(),
					this.getBuild().getBuildVariables()
			);
			return new ReadOnlyConfigurationArchive(archive);
		} catch (IOException ex) {
			//TODO: Log this error
			return null;
		}
	}
	
	
	// === READ ONLY VIEW HELPER METHODS ===
	
	public File generateExecutableCompoundScript(
			List<Builder> builders,
			String archiveName,
			Map<String, String> params
	) throws IOException {
		List<File> generatedScripts = new ArrayList<File>();
		
		File srcDir = Files.createTempDir();
		
		//Dump the build steps as script files
		boolean isLinux = this.dumpBuildSteps(
				builders, generatedScripts, srcDir
		);
		
		//Dump the main script that calls the steps above
		this.dumpMasterScript(isLinux, generatedScripts, srcDir);
		
		File dstFile = File.createTempFile(archiveName + "_", ".tgz");
		if (dstFile.getParentFile() != null) {
			dstFile.getParentFile().mkdirs();
		}
		
		try {
			this.createTgzArchive(
					dstFile,
					srcDir.getAbsolutePath(),
					generatedScripts
			);
		} catch (IOException e) {
			// The file could not be generated
			return null;
		} finally {
			//Nuke the input directory
			try {
				FileUtils.deleteDirectory(srcDir);
			} catch (IOException e) {
				//The directory probably does not exist anymore
			}
		}
		return dstFile;
	}
	
	private void dumpMasterScript(
			boolean isLinux,
			List<File> generatedScripts,
			File srcDir
	) throws IOException {
		Map<String, String> parameters = getResolvedBuildParameters(this.getBuild());
		
		String scrFile = (isLinux) ? "build.sh" : "build.bat";
		String scrPreamble = (isLinux) ? "#!/bin/bash" : "@echo off";
		String scrSetCmd = (isLinux) ? "export" : "set";
		String scrInvoke = (isLinux) ? "./" : "CALL ";
		String lineEnd = (isLinux) ? "\n" : "\r\n";
		
		File mainScript = new File(srcDir, scrFile);
		BufferedWriter out = new BufferedWriter(
			new FileWriter(mainScript, true)
		);
		out.write(scrPreamble);
		out.write(lineEnd);
		
		if (parameters.size() > 0) {
			for (String parameter : parameters.keySet()) {
				out.write(String.format(
						"%s %s=\"%s\"%s",
						scrSetCmd, parameter, parameters.get(parameter),
						lineEnd
				));
			}
		}
		
		for (File script : generatedScripts) {
			if (isLinux) {
				out.write(String.format(
						"echo 'Will run %s now ...'%s",
						script.getName(),
						lineEnd
				));
			} else {
				out.write(String.format(
						"echo Will run %s now ...%s",
						script.getName(),
						lineEnd
				));
			}
			out.write(String.format(
					"%s%s%s",
					scrInvoke,
					script.getName(),
					lineEnd
			));
		}
		if (isLinux) {
			mainScript.setExecutable(true, false);
			mainScript.setWritable(true, false);
			mainScript.setReadable(true, false);
		}
		out.close();
		generatedScripts.add(mainScript);
	}
	
	private boolean dumpBuildSteps(
			List<Builder> builders,
			List<File> generatedScripts,
			File srcDir
	) throws IOException {
		boolean isLinux = true;
		int i = 0;
		for (Builder builder : builders) {
			if (!(builder instanceof CommandInterpreter)) {
				continue;
			}
			CommandInterpreter ci = (CommandInterpreter) builder;
			File builderScript;
			if (builder instanceof BatchFile) {
				isLinux = false;
				builderScript = new File(srcDir, String.format("step_%d.bat", i++));
			} else {
				builderScript = new File(srcDir, String.format("step_%d.sh", i++));
			}
			
			BufferedWriter out = new BufferedWriter(
					new FileWriter(builderScript, true)
			);
			String cmd = ci.getCommand();
			if (!isLinux) {
				//Ensure windows line-endings
				cmd = cmd.replaceAll("\r\n", "\n").replaceAll("\n", "\r\n");
			}
			out.write(cmd);
			builderScript.setExecutable(true, false);
			builderScript.setReadable(true, false);
			builderScript.setWritable(true, false);
			out.close();
			generatedScripts.add(builderScript);
		}
		return isLinux;
	}
	
	public void createTgzArchive(
			File dstFile, String srcDir, List<File> srcFiles
	) throws IOException {
		//Creating the output ZipFile
		FileOutputStream fos = new FileOutputStream(dstFile);
		GZIPOutputStream gzos = new GZIPOutputStream(fos);
		TarOutputStream tos = new TarOutputStream(gzos);
		
		try {
			byte[] buffer = new byte[8192];
			for (File srcFile : srcFiles) {
				if (!srcFile.exists()) {
					throw new IOException("No such file " + srcFile.getPath());
				}
				//Strip the prefix off the srcFile
				String relPath = PathMapping.getRelativePath(
						srcFile.getPath(), srcDir, File.separator
				);
				
				TarEntry entry = new TarEntry(relPath);
				entry.setMode(0777);
				entry.setSize(srcFile.length());
				//Put the entry into the ZIP file
				tos.putNextEntry(entry);
				//Dump the data
				FileInputStream fis = new FileInputStream(srcFile);
				try {
					while (true) {
						int read = fis.read(buffer);
						if (read <= 0) { break; }
						tos.write(buffer, 0, read);
					}
				} finally {
					fis.close();
				}
				//Close the entry
				tos.closeEntry();
			}
		} finally {
			if (tos != null) {
				tos.close();
			}
		}
	}
	
	public static Map<String, String> getResolvedBuildParameters(
			InheritanceBuild build
	) {
		//FIXME: Should we really resolve parameters here? After all, Jenkins
		//does not do that either!
		Map<String, String> result = new HashMap<String, String>();
		
		Map<String, String> buildVariables = build.getBuildVariables();
		for (String pName : buildVariables.keySet()) {
			String resolved = Util.replaceMacro(
					buildVariables.get(pName), buildVariables
			);
			result.put(pName, resolved);
		}
		return result;
	}
	
	public static Map<String, String> getResolvedBuildParameters(
			InheritanceProject project
	) {
		Map<String, String> result = new HashMap<String, String>();
		
		List<ParameterDefinition> parameters = project.getParameters(
				IMode.INHERIT_FORCED
		);
		for (ParameterDefinition pDef : parameters) {
			ParameterValue pVal = pDef.getDefaultParameterValue();
			if (pVal instanceof StringParameterValue) {
				result.put(
						pDef.getName(),
						((StringParameterValue) pVal).value
				);
			}
		}
		//We do not resolve parameters; as they are most likely not complete anyway
		return result;
	}

	
	public InheritanceViewActionDescriptor getDescriptor() {
		return (InheritanceViewActionDescriptor)
				Jenkins.getInstance().getDescriptorOrDie(this.getClass());
	}
	
	public static InheritanceViewActionDescriptor getDescriptorStatic() {
		return (InheritanceViewActionDescriptor)
				Jenkins.getInstance().getDescriptorOrDie(InheritanceViewAction.class);
	}
	
	@Extension(ordinal = 1000)
	public static final class InheritanceViewActionDescriptor extends Descriptor<InheritanceViewAction> {

		public InheritanceViewActionDescriptor() {
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
