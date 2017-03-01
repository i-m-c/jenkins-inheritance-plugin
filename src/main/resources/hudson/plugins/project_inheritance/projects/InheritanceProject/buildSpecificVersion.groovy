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

import jenkins.model.Jenkins;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.view.BuildViewExtension;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
st = namespace("jelly:stapler");

/**
 * This method renders a table of project versions with the given title
 * (as a section) and content.
 */
def renderTable(title, versions) {
	f.section(title: title) {
		f.entry() {
			table(class: "pane sortable bigtable fixed") {
				thead() {
					tr() {
						th(class: "pane-header small", _("Version"))
						th(initialSortDir: "down", class: "pane-header large", _("Project"))
						th(class: "pane-header auto", _("Description"))
					}
				}
				tbody() {
					for (e in versions) {
						//Ignore projects, that only have one version
						if (e.getVersions().size() <= 1) { continue; }
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
							def pName = e.project.getFullName()
							td(class: "pane", data: pName) {
								a(href: rootURL + "/job/" + pName, pName)
								f.textbox(
										style: "display:none",
										name: "project",
										value: pName
								)
							}
							
							//Versioning annotation
							td(class: "pane", data:e.description, e.description)
						}
					}
				}
			}
		}
	}
}

def splitVersionsByPronoun(versions) {
	out = new HashMap();
	for (e in versions) {
		pronoun = e.project.getPronoun()
		if (pronoun == null || pronoun.isEmpty()) { pronoun = "Project"; }
		lst = out.get(pronoun)
		if (lst == null) { lst = new LinkedList();
		}
		lst.add(e)
		out.put(pronoun, lst)
	}
	return out
}

def j = Jenkins.getInstance();

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
		delayArg = (delay == null || delay.isEmpty()) ? "" : "?delay=" + delay
		f.form(
				name: "versions",
				action: "buildSpecificVersion" + delayArg,
				tableClass: "versions", method: "post"
		) {

			// Get all build extension fields and add them
			/* Not currently possible, as the data would be duplicated on the build() page
			buildExts = j.getExtensionList(BuildViewExtension.class);
			for (ext in buildExts) {
				include(ext, ext.getValuePage())
			}
			*/

			//Fetch all versions from the current project
			versions = my.getAllInheritedVersionsList();
			//Split it by pronoun
			split = splitVersionsByPronoun(versions);

			for (key in split.keySet()) {
				renderTable(key, split.get(key));
			}


			// The submit and refresh buttons for the version selection
			f.block() {
				f.submit(name: "doBuild", value: _("Build"))
				f.submit(name: "doRefresh", value: _("Refresh tree"))
			}
		}
	}
}
