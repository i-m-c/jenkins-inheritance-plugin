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
	
	//Add an invisible div element, that stores which validationErrors can be safely ignored
	div(
			style: "display:none",
			class: "acceptable-error-url-data",
			ProjectCreationEngine.instance.getAcceptableErrorUrls()
	)
	
	//Load additional Javascript
	st.adjunct(includes: adjunctPrefix + ".detectValidationErrors")
	st.adjunct(includes: adjunctPrefix + ".confirmChangesPopup")
	//Prompt the user to enter a version message
	if (my.getCurrentVersionNotification().areAllVersionsUnstable()) {
		buttonMsg = _("Warning! Your changes will get effective immediately if you press OK.\\nPlease type in short description of what you changed:")
	} else {
		buttonMsg = _("Please type in short description of what you changed:")
	}
	saveButtonFunction = "return confirmChangesAndEnterVersion(this, '" + buttonMsg + "')"
	
	
	l.main_panel() {
		div(class: "behavior-loading", _("LOADING"))
		
		ct.form(name: "config", action: "submitChildJobCreation", method: "post", onsubmit: saveButtonFunction) {
			descriptor = my.descriptor
			instance = my
			
			include(my, "configure-header-warnings")
			
			ct.colored_block(backCol: "LightGoldenRodYellow ", borderCol: "navy") {
				f.section(title: _("Parameters")) {}
				//This description list only renders correctly, if there's an
				//entry (even a blank one) in the same table
				ct.blankEntry()
				f.descriptorList(
						field: "properties",
						forceRowSet: "true",
						descriptors: my.getJobPropertyDescriptors(my.getClass(), false, "ParametersDefinitionProperty")
				)
			}
			
			// Add the box with the sub-job definitions
			ct.colored_block(backCol: "Bisque", borderCol: "navy") {
				include(my, "child-job-creation-box.jelly")
			}
			
			if(h.hasPermission(my, my.CONFIGURE)) {
				f.block() {
					div(id: "bottom-sticker") {
						div(class: "bottom-sticker-inner") {
							f.submit(value: _("Save"))
						}
					}
				}
			}
		}
	}
}

