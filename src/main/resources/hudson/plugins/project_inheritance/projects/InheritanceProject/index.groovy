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
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);


helpRoot = "/plugin/project-inheritance/help"

l.layout(title: my.displayName) {
	
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
	}
	
	//Standard project sidepanel
	include(my, "sidepanel")
	
	//Main panel with lots of plugin-contributed data
	l.main_panel() {
		h1(my.pronoun + " " + my.displayName)
		
		// Printing a humongous warning if the project has a cyclic dependency
		if (my.hasCyclicDependency()) {
			h2(style: "color:red") {
				span("This project has a")
				a(style: "color:red", href: "http://en.wikipedia.org/wiki/Cycle_detection", "cyclic")
				a(style: "color:red", href: "http://en.wikipedia.org/wiki/Diamond_problem", "diamond")
				span("or repeated dependency!")
			}
		}
		
		//Checking current parameters for consistency
		paramCheck = my.getParameterSanity()
		if (paramCheck.getKey() == false) {
			h2(style: "color:red", "This project has a parameter inconsistency!")
			span("Reason: " + paramCheck.getValue())
		}

		//Printing invalid project references
		for (i in my.getProjectReferenceIssues()) {
			h3(style: "color:red", i)
		}
		
		//Inject main index page contents from supertype
		t.editableDescription(permission: my.CONFIGURE)
		include(my, "jobpropertysummaries")
		include(my, "main")
		include(my, "permalinks")
		
		// Add an hbar, followed by a sortable table of direct parents & childs
		hr()
		relationsMap = my.getRelationships()
		for (type in Relationship.Type.values()) {
			//Ignore "mated" projects
			if (type == Relationship.Type.MATE) { continue }
			
			//Check if the table would be empty
			typeEmpty = true;
			for (e in relationsMap.entrySet()) {
				if (e.getValue().type == type) {
					typeEmpty = false
					break
				}
			}
			if (typeEmpty) { continue }
			
			h2(type.getDescription())
			
			table(class: "pane sortable bigtable fixed", style:"min-width:600px; width:75%") {
				thead() {
					tr() {
						th(initialSortDir: "down", class: "pane-header auto forceWrap", _("Name"))
						th(initialSortDir: "down", class: "pane-header small", _("Distance"))
						th(initialSortDir: "down", class: "pane-header medium", _("Compound or<br/>Transient?"))
						th(initialSortDir: "down", class: "pane-header wide forceWrap", _("Class"))
					}
				}
				tbody() {
					for (e in relationsMap.entrySet()) {
						project = e.getKey()
						rel = e.getValue()
						
						if (type == Relationship.Type.PARENT) {
							//If we look at parents, only show dist = 1
							if (rel.distance != 1) { continue; }
						} else if (type == Relationship.Type.CHILD) {
							//Only show leafs
							if (!rel.isLeaf) { continue; }
						}
						
						if (rel.type == type) {
							tr() {
								td(class: "pane forceWrap") {
									a(href: rootURL + "/job/" + project.getName(),
											project.getName()
									)
								}
								td(class: "pane", rel.distance)
								td(class: "pane", project.getIsTransient())
								td(class: "pane", project.getCreationClass())
							}
						}
					}
				}
			}
		}
	}
}
