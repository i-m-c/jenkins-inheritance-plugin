package hudson.plugins.project_inheritance.projects.references.filters;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;

/**
 * This interface defines methods needed to filter a list of Jenkins jobs.
 * <p>
 * {@link AbstractProjectReference}s are used in several parts of the GUI.
 * Sometimes they reference parent jobs, sometimes siblings, sometimes jobs
 * of specific classes.
 * <p>
 * To allow the same descriptors and Groovy/Jelly files to be used, an
 * additional filter must be run, which selects which job names go into
 * the "select" combobox to specify the reference.
 * <p>
 * Just override this interface, and pass the object into
 * {@link hudson.plugins.project_inheritance.projects.references.AbstractProjectReference.ProjectReferenceDescriptor#doFillNameItems(String)}
 * 
 * @author mhschroe
 *
 */
public interface IProjectReferenceFilter {
	public boolean isApplicable(InheritanceProject project);
}
