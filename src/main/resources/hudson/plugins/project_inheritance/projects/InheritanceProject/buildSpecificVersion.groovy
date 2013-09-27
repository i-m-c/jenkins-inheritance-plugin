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
st = namespace("jelly:stapler");


st.statusCode(value: "405")
l.layout(title: "Build ${my.displayName}", norefresh: "true") {
	
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
	}
	
	//Add the standard side-panel
	include(my, "sidepanel")
	
	//Add the main panel with version selection & parameters
	l.main_panel() {
		div(class: "behavior-loading", _("LOADING"))
		
		h1("Build Version Selection - " + my.pronoun + " " + my.displayName)
		
		delay = request.getParameter('delay')
		delayArg = (empty(delay)) ? "" : "?delay=" + delay
		f.form(
				name: "versions",
				action: "buildSpecificVersion" + delayArg,
				tableClass: "versions", method: "post"
		) {
			/* Fetch & display a list of versions of all associated jobs
			 * in order of their inheritance.
			 * Do note that the parameter passed to this page's request
			 * determine the versions returned by this function, as it
			 * decodes the StaplerRequest.
			 */
			
			f.entry() {
				table(class: "pane sortable bigtable fixed") {
					thead() {
						tr() {
							th(initialSortDir: "down", class: "pane-header small", _("Version"))
							th(initialSortDir: "down", class: "pane-header large", _("Project"))
							th(initialSortDir: "down", class: "pane-header auto", _("Description"))
						}
					}
					tbody() {
						for (e in my.getAllInheritedVersionsList()) {
							tr() {
								//Version number
								td(class: "pane") {
									select(
											style: "width:100%;overflow:hidden;",
											name: "version"
									) {
										for (ev in e.getVersions()) {
											if (ev.equals(e.version)) {
												option(selected: "true", value: ev, ev)
											} else {
												option(value: ev, ev)
											}
										}
									}
								}
								
								//Project Name
								td(class: "pane") {
									a(href: rootURL + "/job/" + e.project, e.project)
									f.textbox(
											style: "visibility:hidden",
											name: "project",
											value: e.project
									)
								}
								
								//Versioning annotation
								td(class: "pane", e.description)
							}
						}
					}
				}
			}
			
			// The submit and refresh buttons for the version selection
			f.block() {
				f.submit(name: "doBuild", value: _("Build"))
				f.submit(name: "doRefresh", value: _("Refresh tree"))
			}
		}
	}
}
