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

f = namespace(lib.FormTagLib)
l = namespace(lib.LayoutTagLib)
ct = namespace(lib.CustomTagLib)

p = namespace("/lib/hudson/project")


if (my.getIsTransient() == false) {
	// Add the box with the sub-job definitions
	ct.colored_block(backCol: "Bisque", borderCol: "navy") {
		f.section(title: _("CreationClass")) {}
		f.entry(field: "creationClass") {
			f.select(default: "")
			f.description(
					"Select the type of this project. You can use this to" +
					" group projects by their type.<br/> It can also be used" +
					" to allow Jenkins to automatically produce transient" +
					" projects from two or more project definitions." +
					" The list of available types can be managed in the" +
					" 'Inheritance Configuration' section of Jenkins'" +
					" management page."
			)
		}
	}
	
	ct.colored_block(backCol: "PowderBlue", borderCol: "navy") {
		f.section(title: _("Project Inheritance Options")) {
			f.invisibleEntry(field: "isTransient", title: _("Is Transient Project")) {
				f.checkbox()
				/*
				//Descriptions of invisibleEntries are still rendered!
				f.description(
					"If this is checked, the project will not be stored on disk." +
					" Do note that this flag is read-only. Only projects automatically" +
					" created by the 'Project Creation Engine' have this flag set."
				)
				*/
			}
			f.entry(field: "isAbstract", title: _("Is abstract project")) {
				f.checkbox()
				f.description(
						"If this is checked, the project will be marked as" +
						" abstract and can not	be run directly. While this" +
						" superficially sounds similar to just deactivating" + 
						" the project, it additionally relaxes certain checks" +
						" related to inheritance of values. One example is" +
						" the error that occurs if you've not given a default" +
						" value to a mandatory variable."
				)
			}
		}
	}
}

ct.colored_block(backCol: "LightGreen", borderCol: "navy") {
	include(my, "configure-inheritance")
}


//Now, the base configuration; we copy it instead of reference it, to make sure
//that labels can be generated quickly
f.section(title: _("Base Project Configuration")) {
	//include(my, "/hudson/model/Project/configure-entries")
	
	include(my, "base/configure-base-settings")

	p.config_trigger() {
		p.config_upstream_pseudo_trigger()
	}

	p.config_buildWrappers()
	p.config_builders()
	p.config_publishers2()
}

