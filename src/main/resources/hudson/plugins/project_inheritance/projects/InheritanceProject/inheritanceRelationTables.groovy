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

try { if (ignoreRelations) {} } catch (e) { ignoreRelations = [] }

try { if (verbose) {} } catch (e) { verbose = false }

relationsMap = my.getRelationships()
for (type in Relationship.Type.values()) {
	//Check if the current relation needs to be ignored
	ignore = false
	for (rel in ignoreRelations) {
		if (type == rel) { ignore = true; break; }
	}
	if (ignore) { continue }
	
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
	
	if (verbose) {
		tblStyle = "min-width:600px; width:75%"
	} else {
		tblStyle = "min-width:600px; width:50%"
	}
	
	table(class: "pane sortable bigtable fixed", style: tblStyle) {
		if (verbose) {
			thead() {
				tr() {
					th(initialSortDir: "down", class: "pane-header auto forceWrap", _("Name"))
					th(initialSortDir: "down", class: "pane-header small", _("Distance"))
					th(initialSortDir: "down", class: "pane-header medium", _("Compound?"))
					th(initialSortDir: "down", class: "pane-header wide forceWrap", _("Class"))
				}
			}
			tbody() {
				for (e in relationsMap.entrySet()) {
					project = e.getKey()
					rel = e.getValue()
					
					if (rel.type == type) {
						tr() {
							td(class: "pane forceWrap") {
								a(href: rootURL + "/job/" + project.getName(),
										project.getName()
								)
							}
							td(class: "pane", rel.distance)
							td(class: "pane", rel.isLeaf)
							td(class: "pane", project.getIsTransient())
						}
					}
				}
			}
		} else {
			thead() {
				tr() {
					th(initialSortDir: "down", class: "pane-header auto forceWrap", _("Name"))
					th(initialSortDir: "down", class: "pane-header wider forceWrap", _("Class"))
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
							td(class: "pane", project.getCreationClass())
						}
					}
				}
			}
		}
		
		
	}
}