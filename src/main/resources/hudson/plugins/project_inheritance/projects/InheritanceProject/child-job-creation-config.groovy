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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);

st = namespace("jelly:stapler")

adjunctPrefix = "hudson.plugins.project_inheritance.projects.InheritanceProject.adjunct"

/* This page creates a config-page with only those project details that are
 * necessary to configure transient child jobs.
 */
l.layout(
		title: my.getDisplayName() + " Child Job Creation",
		permission: my.EXTENDED_READ, norefresh: "true"
) {
	
	// Add the standard-side-panel for projects
	include(my, "sidepanel")
	
	f.breadcrumb_config_outline()
	
	//Load additional Javascript
	st.adjunct(includes: adjunctPrefix + ".confirmChangesPopup")
	//Prompt the user to enter a version message
	if (my.areAllVersionsUnstable()) {
		//buttonMsg = _("Warning! Your changes will get effective immediately if you press OK.\nPlease type in short description of what you changed:")
		buttonMsg = _("Warning! Your changes will get effective immediately if you press OK.\\nPlease type in short description of what you changed:")
	} else {
		buttonMsg = _("Please type in short description of what you changed:")
	}
	saveButtonFunction = "return confirmChangesAndEnterVersion(this, '" + buttonMsg + "')"
	
	
	l.main_panel() {
		div(class: "behavior-loading", _("LOADING"))
		
		include(my, "transient-job-fields")
		
		ct.form(name: "config", action: "submitChildJobCreation", method: "post", onsubmit: saveButtonFunction) {
			descriptor = my.descriptor
			instance = my
			
			//Add an invisible versioning message box; this is filled with a value during form submission
			f.invisibleEntry() {
				f.textbox(name: "versionMessageString")
			}
			
			// Add the version-selection box
			ct.colored_block(backCol: "Khaki", borderCol: "navy") {
				include(my, "configure-version-selection")
			}
			
			// Job property configurations; copied from root-project
			// This basically boils down to the parameter properties
			f.entry(
				f.descriptorList(
						field: "properties",
						forceRowSet: "true",
						descriptors: my.getJobPropertyDescriptors(my.getClass())
				)
			)
			
			// Add the box with the sub-job definitions
			ct.colored_block(backCol: "Bisque", borderCol: "navy") {
				include(my, "child-job-creation-box.jelly")
			}
			
			if(h.hasPermission(my, my.CONFIGURE)) {
				f.block() {
					div(id: "bottom-sticker") {
						div(class: "bottom-sticker-inner") {
							f.submit(value: _("Save"))
							if (ProjectCreationEngine.instance.getEnableApplyButton()) {
								f.apply()
							}
						}
					}
				}
			}
		}
	}
}

