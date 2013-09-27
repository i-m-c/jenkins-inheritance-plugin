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
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


f.section(title: _("Parent Projects")) {
	f.description(
			"This section is used to define the parent projects of this" +
			" project. All properties of a parent will apply to this project" +
			" and depending	on the type of property may be extended, replaced" +
			" or chained with the actual properties defined on this project."
	)
	f.advanced(title: "Expand parents", align: "left") {
		f.block() {
			//This call fetches all ProjectReference-based descriptors -->
			pDescriptors = AbstractProjectReference.all(SimpleProjectReference.class)
			
			//This list displays/configures the configured parent references
			f.hetero_list(
					items: my.getParentReferences(),
					name: "projects",
					hasHeader: "true",
					descriptors: pDescriptors,
					addCaption: _("Add parent project")
			)
		}
	}
}
