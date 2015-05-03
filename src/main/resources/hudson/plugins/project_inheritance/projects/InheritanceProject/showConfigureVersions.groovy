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
st = namespace("jelly:stapler")

adjunctPrefix = "hudson.plugins.project_inheritance.projects.InheritanceProject.adjunct"

l.layout(
		title: my.displayName + " Versioning Config",
		permission: my.EXTENDED_READ, norefresh: "true"
) {
	
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
		
		//Load additional JS/CSS for the diffing functionality
		st.adjunct(includes: adjunctPrefix + ".selectDiff")
	}
	
	// Include the standard side-panel for this project
	include(my, "sidepanel")
	
	
	l.main_panel() {
		f.form(
				name: "configVersions", action: "configVersionsSubmit", method: "post"
		) {
			descriptor = my.descriptor
			instance = my
			
			f.entry() {
				table(class: "pane sortable bigtable fixed vTable") {
					tr() {
						th(initialSortDir: "down", class: "pane-header small", _("Version"))
						th(initialSortDir: "down", class: "pane-header small", _("Stable?"))
						th(initialSortDir: "down", class: "pane-header medium", _("Created on"))
						th(initialSortDir: "down", class: "pane-header wide", _("Created by"))
						th(initialSortDir: "down", class: "pane-header auto", _("Description"))
					}
					for (e in my.getVersions()) {
						tr(onclick:"selectForDiff(event)") {
							// Version number
							td(class: "pane") {
								a(href: "configure?version=" + e.id, e.id)
								f.textbox(style: "visibility:hidden", name: "versionID", value: e.id)
							}
							
							// Stability flag
							td(class: "pane") {
								f.checkbox(name: "stable", checked: e.getStability())
							}
							
							// Timestamp
							td(class: "pane", e.getLocalTimestamp())
							
							// User that committed the version
							td(class: "pane") {
								a(href: rootURL + "/user/" + e.getUsername(), e.getUsername())
							}
							
							// Description of that version
							td(class: "pane") {
								div(class: "tdcenter") {
									f.textbox(name: "description", value: e.description)
								}
							}
						}
					}
				}
			}
			if (h.hasPermission(my, my.VERSION_CONFIG)) {
				f.block() {
					div(style: "margin-top:20em;margin-bottom:20em")
					div(id: "bottom-sticker") {
						div(class: "bottom-sticker-inner") {
							f.submit(value: _("Save"))
							if (ProjectCreationEngine.instance.getEnableApplyButton()) {
								f.apply()
							}
							input(type: "button", class: "yui-button",
									value: _("Compute Diff"),
									onclick: "executeDiff('vTable')"
							)
						}
					}
				}
			}
		}
	}
}
