package hudson.plugins.project_inheritance.projects.references.filters;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.CreationMating;


/**
 * Filters jobs according to whether or not the mating is valid.
 * <p>
 * Do note, that this might very well deny all jobs that come its way.
 * 
 * @author mhschroe
 */
public class MatingReferenceFilter implements IProjectReferenceFilter {
	private final String ownClass;

	/**
	 * Filters jobs down to those, that the given project can mate with.
	 * @param ip
	 */
	public MatingReferenceFilter(InheritanceProject ip) {
		if (ip == null) {
			ownClass = null;
		} else {
			ownClass = ip.getCreationClass();
		}
	}
	
	@Override
	public boolean isApplicable(InheritanceProject project) {
		//Grab the creation class of the other project
		String otherClass = (project != null) ? project.getCreationClass() : null;
		if (ownClass == null || otherClass == null) {
			return false;
		}
		//Loop over all matings, and check if it's a good match
		for (CreationMating mate : ProjectCreationEngine.instance.getMatings()) {
			if (!ownClass.equals(mate.firstClass)) {
				continue;
			}
			if (!otherClass.equals(mate.secondClass)) {
				continue;
			}
			//Valid mating
			return true;
		}
		return false;
	}

}
