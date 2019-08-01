/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2017 Intel Deutschland GmbH
 */
package hudson.plugins.project_inheritance.projects.view;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.projects.view.scripts.MetaScript;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import jenkins.model.Jenkins;

/**
 * This class is an extension point to add new handlers for turning certain
 * {@link Builder} into executable shell scripts
 * 
 * @author mhschroe
 *
 */
public abstract class BuildFlowScriptExtension implements ExtensionPoint {
	/**
	 * This method returns all extensions for this extension point.
	 * 
	 * @return a list, can be empty but never null.
	 */
	public static ExtensionList<BuildFlowScriptExtension> all() {
		return Jenkins.get().getExtensionList(
				BuildFlowScriptExtension.class
		);
	}
	
	
	/**
	 * This function is called before the first external call of
	 * {@link #getScriptsFor(File, AbstractProject, AbstractBuild, Builder, Map, AtomicInteger)}
	 * is initiated.
	 * <p>
	 * It is useful, because some {@link BuildFlowScriptExtension}s need access
	 * to a more persistent storage during the runtime of their script
	 * generation and that storage is usually a {@link ThreadLocal} field.
	 * <p>
	 * A simple example is a script that should only be emitted once, no matter
	 * how often a property is encountered during script generation. The method
	 * itself can't make this storage available easily, without adding fields
	 * to the method signature that most extensions wouldn't even use.
	 */
	public static void initThread() {
		
	}
	
	/**
	 * Extension counterpart of {@link #initThread()}.
	 */
	public abstract void init();
	
	
	/**
	 * The logical counterpart to {@link #initThread()}, but called after the
	 * last external call to the script generation is done.
	 */
	public static void cleanUpThread() {
		
	}
	
	/**
	 * Extension counterpart of {@link #cleanUpThread()}.
	 */
	public abstract void cleanUp();
	
	/**
	 * Static wrapper for {@link #filterEnvironment(Map)} over all extensions.
	 * 
	 * @param params the environment to filter. Must be mutable.
	 * @return the filtered environment.
	 */
	public static Map<String, String> filterEnv(Map<String, String> params) {
		TreeMap<String, String> env = new TreeMap<String, String>(params);
		for (BuildFlowScriptExtension handler : all()) {
			handler.filterEnvironment(env);
		}
		return env;
	}
	
	/**
	 * Override this method to filter environment variables set at the
	 * beginning of the script.
	 * <p>
	 * Variables can be added, modified or removed.
	 * 
	 * @param env the environment to alter. Always mutable and never null.
	 */
	public abstract void filterEnvironment(Map<String,String> env);
	
	
	
	/**
	 * This method asks the extension to handle the given builder and return
	 * a valid set of scripts along with some metadata of how to handle it.
	 * <p>
	 * If the extension does not handle that step type, it must return an empty
	 * list.
	 * 
	 * @param prefix if not null, the prefix for the script files. If null, no
	 *  prefix is used.
	 * @param project the project that owns the builders. Is not necessarily
	 * 	the parent of build.
	 * @param build the build for which the scripts should be generated. Is not
	 * 	necessarily a build of the given project.
	 * @param step the build to generate a script for.
	 * @param env the environment for the build script.
	 * @param cnt a counter shared between all scripts of a build, so that you
	 * 		unique file names can be generated more easily.
	 * 
	 * @return a list of {@link MetaScript} instances, may be empty, but never
	 *  null.
	 */
	public abstract List<MetaScript> getScriptsFor(
			File prefix,
			AbstractProject<?, ?> project,
			AbstractBuild<?,?> build,
			Builder step,
			Map<String,String> env,
			AtomicInteger cnt
	);
	
	/**
	 * Uses the registered {@link BuildFlowScriptExtension} instances to turn
	 * the list of builders into a list of {@link MetaScript} instances that
	 * can be used in a script.
	 * <p>
	 * @param prefix if not null, the prefix for the script files. If null, no
	 *  prefix is used.
	 * @param project the project that owns the builders. Is not necessarily
	 * 	the parent of build.
	 * @param build the build for which the scripts should be generated. Is not
	 * 	necessarily a build of the given project.
	 * @param builders the build steps to convert.
	 * @param params the environment variables/parameters of the job
	 * @param cnt a counter to generate unique IDs with
	 * 
	 * @return the list of scripts that are the result of conversion. May be
	 * 	empty, but never null.
	 */
	public static List<MetaScript> getScriptsFor(
			File prefix,
			AbstractProject<?, ?> project,
			AbstractBuild<?,?> build,
			List<Builder> builders,
			Map<String, String> params,
			AtomicInteger cnt
	) {
		List<MetaScript> out = new LinkedList<>();
		
		for (Builder builder : builders) {
			List<MetaScript> ms = null;
			for (BuildFlowScriptExtension handler : all()) {
				ms = handler.getScriptsFor(
						prefix, project, build, builder, params, cnt
				);
				if (ms != null && !ms.isEmpty()) { break; }
			}
			if (ms == null || ms.isEmpty()) { continue; }
			out.addAll(ms);
		}
		return out;
	}
	
	/**
	 * This method returns the dependent projects and will be called by
	 * {@link #getDependentProjectsFor(AbstractProject, Map)}, so that the
	 * individual extension does not have to care about versioning.
	 * 
	 * @see #getDependentProjectsFor(AbstractProject, Map)
	 * @param p the project to target
	 * @return a list of projects, may be empty, but is never null.
	 */
	public abstract List<AbstractProject<?,?>> getDependentProjects(AbstractProject<?, ?> p);
	
	/**
	 * This method returns the list of dependent projects for the given project
	 * and versioning map.
	 * <p>
	 * A dependent project is one that is started by (or after) the given project
	 * as a form of flow. The generated list should be identical to the one
	 * generated in the process of creating the list of scripts.
	 * <p>
	 * Its main use is to allow the WebUI to enumerate all the
	 * {@link CommandInterpreter} instances that will be run in response to the
	 * given job starting.
	 * 
	 * @param p the project to target.
	 * @param verMap the versions to use.
	 * @return a list of projects. Will always contain at least the value of p.
	 */
	public static List<AbstractProject<?,?>> getDependentProjectsFor(
			AbstractProject<?, ?> p,
			Map<String, Long> verMap
	) {
		//Set the desired versions
		Map<String, Long> oldVerMap = VersionHandler.getVersions();
		try {
			//Set the current thread's versioning map
			if (verMap != null && !verMap.isEmpty()) {
				VersionHandler.initVersions(verMap);
			}
			
			List<AbstractProject<?,?>> out = new LinkedList<>();
			for (BuildFlowScriptExtension ext : all()) {
				out.addAll(ext.getDependentProjects(p));
			}
			return out;
		} finally {
			//re-initialise with old versions map
			VersionHandler.initVersions(oldVerMap);
		}
	}
}
