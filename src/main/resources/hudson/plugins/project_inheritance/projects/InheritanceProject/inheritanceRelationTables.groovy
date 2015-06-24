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
import java.text.SimpleDateFormat;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);
ct = namespace(lib.CustomTagLib);

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
		tblStyle = "min-width:800px; width:90%"
	} else {
		tblStyle = "min-width:600px; width:85%"
	}
	
	table(class: "pane sortable bigtable fixed", style: tblStyle) {
		if (verbose) {
			thead() {
				tr() {
					th(class: "pane-header variable forceWrap", _("Name"))
					th(class: "pane-header small", _("Distance"))
					th(class: "pane-header medium", _("Compound?"))
					th(class: "pane-header wider forceWrap", _("Class"))
					if (type == Relationship.Type.CHILD) {
						th(class: "pane-header wider forceWrap", _("Last Build Date"))
					}
				}
			}
			tbody() {
				for (project in my.getRelationshipsOfType(type)) {
					if (relationsMap.get(project).type == type) {
						tr() {
							td(class: "pane forceWrap") {
								a(href: rootURL + "/" + project.getUrl(),
										project.getName()
								)
							}
							td(class: "pane forceWrap", relationsMap.get(project).distance)
							td(class: "pane forceWrap", relationsMap.get(project).isLeaf)
							td(class: "pane forceWrap", project.getCreationClass())
							if (type == Relationship.Type.CHILD) {
								last = project.getLastBuild()
								if (last) {
									td(class: "pane") {
										ct.buildtime(
												link: rootURL + "/" + project.getUrl() + last.number,
												buildtime: last.getTime(),
												buildStatusUrl: project.getBuildStatusUrl(),
												buildDisplayId: last.number)
									}
								} else {
									td(class: "pane", "N/A")
								}
							}
						}
					}
				}
			}
		} else {
			thead() {
				tr() {
					th(class: "pane-header variable forceWrap", _("Name"))
					
					if (type != Relationship.Type.CHILD) {
						th(class: "pane-header wider forceWrap", _("Class"))
					} else {
						th(class: "pane-header wider forceWrap", _("Last Build Date"))
					}
					
				}
			}
			tbody() {
				for (project in my.getRelationshipsOfType(type)) {
					
					if (type == Relationship.Type.PARENT) {
						//If we look at parents, only show dist = 1
						if (relationsMap.get(project).distance != 1) { continue; }
					} else if (type == Relationship.Type.CHILD) {
						//Only show leaves
						if (!relationsMap.get(project).isLeaf) { continue; }
					}
					
					if (relationsMap.get(project).type == type) {
						tr() {
							td(class: "pane forceWrap") {
								a(href: rootURL + "/" + project.getUrl(),
										project.getName()
								)
							}
							if (type != Relationship.Type.CHILD) {
								td(class: "pane forceWrap", project.getCreationClass())
							} else {
								last = project.getLastBuild()
								if (last) {
									td(class: "pane") {
										ct.buildtime(
												link: rootURL + "/" + getUrl() + last.number,
												buildtime: last.getTime(),
												buildStatusUrl: project.getBuildStatusUrl(),
												buildDisplayId: last.number)
									}
								} else {
									td(class: "pane", "N/A")
								}
							}
						}
					}
				}
			}
		}
		
		
	}
}