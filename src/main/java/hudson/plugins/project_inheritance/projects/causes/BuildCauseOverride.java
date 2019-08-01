/**
 * Copyright (C) 2019 Intel Corporation
 */
package hudson.plugins.project_inheritance.projects.causes;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Cause;
import jenkins.model.Jenkins;

/**
 * This class serves as an extension point to enable plugins to contribute
 * additional entries for {@link Cause}.
 * 
 * 
 * @author kmilliga
 *
 */
public abstract class BuildCauseOverride implements ExtensionPoint {
	public abstract List<Cause> getBuildCauseOverride(StaplerRequest req);

	/**
	 * Compiles a list of all {@link Cause} to be contributed to the build.
	 * 
	 * @param req The {@link StaplerRequest} for the build request.
	 * @return A list of {@link Cause} that have been identified by all defined contributors.
	 */
	public static List<Cause> getBuildCauseOverrideByAll(StaplerRequest req) {
		List<Cause> causeList = new ArrayList<Cause>();

		for (BuildCauseOverride bco : BuildCauseOverride.all()) {
			causeList.addAll(bco.getBuildCauseOverride(req));
		}

		return causeList;

	}

	public static ExtensionList<? extends BuildCauseOverride> all() {
		return Jenkins.get().getExtensionList(BuildCauseOverride.class);
	}

}
