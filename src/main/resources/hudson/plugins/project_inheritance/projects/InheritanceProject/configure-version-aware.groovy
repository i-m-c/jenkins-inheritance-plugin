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

import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;

// Namespaces
f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);

st = namespace("jelly:stapler")


adjunctPrefix = "hudson.plugins.project_inheritance.projects.InheritanceProject.adjunct"


l.layout(title: my.getDisplayName() + " Config", permission: my.EXTENDED_READ, norefresh: "true") {
	include(my, "sidepanel.jelly")
	
	//Initialize the breadcrumps for the config-sections
	f.breadcrumb_config_outline()
	
	//Render the main panel
	l.main_panel() {
		//Show a scrim while loading
		div(class: "behavior-loading", _("LOADING"))
		
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
		
		//Now, add the actual form (copied from the default config.jelly of "Job")
		ct.form(
				name: "config", action: "configSubmit", method: "post",
				onsubmit: saveButtonFunction
		) {
			descriptor = my.getDescriptor()
			instance = my
			
			if (my.isNameEditable()) {
				if (my.pronoun != null && !my.pronoun.isEmpty()) {
					f.entry(title: my.getPronoun() + " " + _("name")) {
						f.textbox(name: "name", value: my.name)
					}
				} else {
					f.entry(title: _("Project name")) {
						f.textbox(name: "name", value: my.name)
					}
				}
			}
			
			f.entry(title: _("Description"), help: app.markupFormatter.helpUrl) {
				f.textarea(
						"codemirror-config": app.markupFormatter.codeMirrorConfig,
						"codemirror-mode": app.markupFormatter.codeMirrorMode,
						name: "description",
						value: my.description,
						previewEndpoint: "/markupFormatter/previewDescription"
				)
			}
			
			if (my.supportsLogRotator()) {
				f.optionalBlock(
						help: "/help/project-config/log-rotation.html",
						title: _("Discard Old Builds"),
						name: "logrotate", inline: "true",
						checked: my.buildDiscarder!=null) {
					f.dropdownDescriptorSelector(field: "buildDiscarder", title: _("Strategy"))
				}
			}
			
			f.descriptorList(
					field: "properties", forceRowSet: "true",
					descriptors: h.getJobPropertyDescriptors(my.getClass())
			)
			
			//Add an invisible versioning message box; this is filled with a value during form submission
			f.invisibleEntry() {
				f.textbox(name: "versionMessageString")
			}
			
			//Load the extended configuration
			include(my, "configure-entries.groovy")
			
			//Show the save/apply buttons, if allowed
			if (h.hasPermission(it, my.CONFIGURE)) {
				f.bottomButtonBar() {
					f.submit(value: _("Save"))
					pce = ProjectCreationEngine.instance
					if (pce.getEnableApplyButton()) {
						f.apply()
					}
				}
			}
		}
	}
}
