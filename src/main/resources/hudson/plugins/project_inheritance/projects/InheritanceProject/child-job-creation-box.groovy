/**
 * Copyright (c) 2011-2013, Intel Mobile Communications GmbH
 *
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.	If not, see <http://www.gnu.org/licenses/>.
 */

import org.kohsuke.stapler.Stapler;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.filters.MatingReferenceFilter;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);



/* Fetch the current stapler request and add a parameter, that signifies that
 * certain fields should be rendered differently than normal.
 */
request = Stapler.getCurrentRequest();
if (request != null) {
	request.setAttribute("FIELDS_READ_ONLY", !(ProjectCreationEngine.instance.currentUserMayRename()))
}

f.section(title: _("ConfigureCompounds")) {
	
	//Description field to tell the user what this does
	f.description(
		"This section allows you to create child-jobs that are spawned" +
		" automatically during restart. You simply select another project that" +
		" serves as the second parent, optionally give the 'mating' a name to" +
		" allow the system to tell it apart from the others and set the" +
		" additional parameters, if any."
	)
	
	//List of projects to mate with
	referenceFilter = new MatingReferenceFilter(my);
	f.entry() {
		f.hetero_list(
				items: my.compatibleProjects,
				name: "compatibleProjects",
				hasHeader: "true",
				descriptors: AbstractProjectReference.all(
						ParameterizedProjectReference.class
				),
				addCaption: _("AddCompound"),
				capture: "referenceFilter"
		)
	}
}
