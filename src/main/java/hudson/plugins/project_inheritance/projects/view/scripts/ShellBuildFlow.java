/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects.view.scripts;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.project_inheritance.projects.view.BuildFlowScriptExtension;
import hudson.plugins.project_inheritance.util.Resolver;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;

@Extension
public class ShellBuildFlow extends BuildFlowScriptExtension {

	@Override
	public void init() {
		//Nothing to do
	}

	@Override
	public void cleanUp() {
		//Nothing to do
	}
	
	@Override
	public void filterEnvironment(Map<String, String> env) {
		env.remove("WORKSPACE");
	}
	
	@Override
	public List<MetaScript> getScriptsFor(
			File prefix,
			AbstractProject<?, ?> project,
			AbstractBuild<?,?> build,
			Builder step,
			Map<String, String> env,
			AtomicInteger cnt
	) {
		if (!(step instanceof CommandInterpreter)) {
			return Collections.emptyList();
		}
		
		CommandInterpreter ci = (CommandInterpreter) step;
		
		boolean isWindowsCmd = (ci instanceof BatchFile);
		
		String cmd = ci.getCommand();
		if (isWindowsCmd) {
			//Ensure windows line-endings
			cmd = cmd.replaceAll("\r\n", "\n").replaceAll("\n", "\r\n");
		}
		
		//A non-windows command may lead with a shebang that can contain variables
		//This is only permissible in Jenkins, not in a real shell
		String shebang = this.getShebang(cmd);
		if (!StringUtils.isEmpty(shebang)) {
			//Attempt variable replacement in the shebang only
			String newShebang = Resolver.resolveSingle(env, shebang);
			if (!StringUtils.equals(shebang, newShebang)) {
				//Modify the cmd to alter the shebang
				cmd = newShebang + cmd.substring(shebang.length());
				shebang = newShebang;
			}
		}
		//Drop the leading "#!" from the shebang -- if present
		if (shebang.startsWith("#!")) { shebang = shebang.substring(2); }
		
		String stepFile = String.format(
				"step_%d%s",
				cnt.getAndIncrement(),
				(isWindowsCmd) ? ".bat" : this.getExtensionFor(shebang)
		);
		
		return Collections.singletonList(new MetaScript(
				shebang,
				cmd,
				new File(prefix, stepFile)
		));
	}
	
	private String getShebang(String cmd) {
		if (StringUtils.isBlank(cmd)) { return ""; }
		if (!cmd.startsWith("#!")) { return ""; }
		//Get and decode the shebang line
		return cmd.split("\r?\n", 2)[0];
	}
	
	private String getExtensionFor(String shebang) {
		if (StringUtils.isBlank(shebang)) { return ".sh"; }
		
		if (shebang.contains("python")) {
			return ".py";
		} else if (shebang.contains("perl")) {
			return ".pl";
		} else {
			//Assume bourne-shell compatible script
			return ".sh";
		}
	}
	
	@Override
	public List<AbstractProject<?, ?>> getDependentProjects(
			AbstractProject<?, ?> p) {
		return Collections.emptyList();
	}
}
