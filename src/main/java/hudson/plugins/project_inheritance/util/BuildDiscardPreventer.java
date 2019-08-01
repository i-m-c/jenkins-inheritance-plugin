/**
 * Copyright (c) 2018-2019 Intel Corporation
 */
package hudson.plugins.project_inheritance.util;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;

/**
 * This class servers as an extension point which, when extended, allow a
 * plugin to prevent the deletion of a build.
 * <p>
 * Do note: Explicit calls to {@link Run#delete()} will still remove the build,
 * but deletions triggered by a {@link BuildDiscarder} or via the GUI will be
 * prevented.
 * 
 * @author mhschroe
 */
public abstract class BuildDiscardPreventer implements ExtensionPoint {
	
	/**
	 * Same behaviour and role as {@link Run#getWhyKeepLog()} -- only as an
	 * extension point.
	 * 
	 * @param build the build for which to decide keeping the log or not.
	 * @return null if the run can be deleted, or a message if it must be kept.
	 */
	public abstract String getWhyKeepLog(InheritanceBuild build);
	
	/**
	 * Simple wrapper around {@link Jenkins#getExtensionList(Class)}.
	 * 
	 * @return the list of registered extensions.
	 */
	public static ExtensionList<BuildDiscardPreventer> all() {
		return Jenkins.get().getExtensionList(BuildDiscardPreventer.class);
	}
}
