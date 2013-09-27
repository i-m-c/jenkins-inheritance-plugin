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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);


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
	
	l.main_panel() {
		div(class: "behavior-loading", _("LOADING"))
		
		include(my, "transient-job-fields")
		
		f.form(name: "config", action: "submitChildJobCreation", method: "post") {
			descriptor = my.descriptor
			instance = my
			
			/*
			if (my.isNameEditable()) {
				f.entry(title: _(my.pronoun)) {
					f.readOnlyTextbox(
							name: "name", value: my.name, readonly: "readonly"
					)
				}
			}
			
			f.invisibleEntry(title: _("Description"), help: app.markupFormatter.helpUrl) {
				f.textarea(
						codemirror_config: app.markupFormatter.codeMirrorConfig,
						name: "description", value: my.description,
						previewEndpoint: "/markupFormatter/previewDescription",
						codemirror_mode: app.markupFormatter.codeMirrorMode
				)
			}
			*/
			
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
							f.apply()
						}
					}
				}
			}
		}
	}
}
