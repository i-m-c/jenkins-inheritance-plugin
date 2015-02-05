package hudson.plugins.project_inheritance.projects.references.filters;

import hudson.model.Job;
import hudson.plugins.project_inheritance.projects.InheritanceProject;

/**
 * This filters a list of projects down to those, that can be potentially
 * executed.
 * <p>
 * This <b>is not</b> the same thing as it being buildable. It means that the
 * job <b>could</b> be built, if the circumstances are right. In other words,
 * it ignores the {@link Job#isBuildable()} value.
 * 
 * @author mhschroe
 *
 */
public class ExecutabilityFilter implements IProjectReferenceFilter {

	public ExecutabilityFilter() {
		// Nothing to do
	}

	@Override
	public boolean isApplicable(InheritanceProject project) {
		if (project == null) { return false; }
		if (project.isAbstract) { return false; }
		
		return true;
	}

}
