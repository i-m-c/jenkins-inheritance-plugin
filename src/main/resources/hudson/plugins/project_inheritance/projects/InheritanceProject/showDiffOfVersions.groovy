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


l.layout(
		title: "${my.displayName} Version Differences",
		permission: my.EXTENDED_READ,
		norefresh: "true"
) {
	// Include the standard side-panel for this project
	include(my, "sidepanel")
	
	l.header() {
		
		//Necessary CSS classes for fixed font
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
		
		// JS function to alter the content of the diff-div field
		script(
				type:"text/javascript",
				src: resURL + "/plugin/project-inheritance/scripts/computeDiff.js"
		)
	}
	
	//Main panel showing a table with the version selection and one for the diffs 
	l.main_panel() {
		//Grab the version informazion
		versions = my.getVersions()
		latest = my.getLatestVersion()
		stable = my.getStableVersion()
		
		// Render the version-selection table
		table() {
			tr() {
				th(_("Left version")) 
				th(_("Right version")) 
			}
			tr() {
				// Selection box for the left version
				td() {
					select(id: "leftVersion", style: "width:100%;overflow:hidden;") {
						for (ev in versions) { 
							if(ev.id.equals(stable)) {
								option(selected: "true", value: ev.id, ev.id) 
							}
							else{
								option(value: ev.id, ev.id)
								}
							}
						}
					}
				
				// Selection box for the right version
				td() {
					select(id: "rightVersion", style: "width:100%;overflow:hidden;") {
						for (ev in versions) {
							if(ev.id.equals(latest)) {
								option(selected: "true", value: ev.id, ev.id)
							}
							else{
								option(value: ev.id, ev.id)
							}
						}
					}
				}
			}
		}
		
		// The buttons to compute the diff and display it in the text field below
		button(
				onclick: "JavaScript:computeDiff('" + my.getName() + "', 'unified')",
				type: "button", _("Compute Unified Diff")
		)
		button(
				onclick: "JavaScript:computeDiff('" + my.getName() + "', 'side')",
				type: "button", _("Compute Side-by-Side Diff")
		)
		button(
				onclick: "JavaScript:computeDiff('" + my.getName() + "', 'raw')",
				type: "button", _("Display Raw")
		)
		
		// Empty whitespace
		div(style: "margin-top:5em;")
		
		// Text field that will contain the diff
		div(id: "diffBox", class: "diff", "The diff will show up here. :)") 
	}
}
