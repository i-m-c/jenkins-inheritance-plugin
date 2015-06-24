package hudson.plugins.project_inheritance.projects.references.filters;

import hudson.plugins.project_inheritance.projects.InheritanceProject;

/**
 * Filters jobs based on them being transient or not.
 * 
 * @author mhschroe
 */
public class TransienceFilter implements IProjectReferenceFilter {
	private final boolean trans;
	
	/**
	 * Sets up the filter to <b>keep</b> only those jobs that <b>match</b> the
	 * given transience value.
	 * 
	 * @param transienceValue the state of the transience flag to filter for.
	 */
	public TransienceFilter(boolean transienceValue) {
		this.trans = transienceValue;
	}

	@Override
	public boolean isApplicable(InheritanceProject project) {
		return (project != null && project.getIsTransient() == this.trans);
	}

}
