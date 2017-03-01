/**
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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
