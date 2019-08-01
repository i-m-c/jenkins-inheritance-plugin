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
	 * @param ip the project under reference
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
