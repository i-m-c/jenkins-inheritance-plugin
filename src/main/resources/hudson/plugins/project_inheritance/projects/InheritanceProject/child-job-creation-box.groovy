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

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;


f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);

/* Create the class that allows us to communicate with sub-scripts.
 * Do note that classes are put into a global scope; whereas variables are not
 * 
 * This is for example used to set whether the user is allowed to alter
 * pre-existing fields or not.
 */
class Constants {
	static READ_ONLY = false;
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
	
	//Set the read-only scope (if need be)
	Constants.READ_ONLY= !(ProjectCreationEngine.instance.currentUserMayRename())
	
	//List of projects to mate with
	f.entry() {
		f.hetero_list(
				items: my.compatibleProjects,
				name: "compatibleProjects",
				hasHeader: "true",
				descriptors: AbstractProjectReference.all(
						ParameterizedProjectReference.class
				),
				addCaption: _("AddCompound")
		)
	}
	//Unset the read-only state, to not pollute others and let newly added fields
	//be read-writeable
	Constants.READ_ONLY = false
}
