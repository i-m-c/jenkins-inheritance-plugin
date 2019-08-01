/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2015-2017 Intel Deutschland GmbH
 * Copyright (c) 2011-2015 Intel Mobile Communications GmbH
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
