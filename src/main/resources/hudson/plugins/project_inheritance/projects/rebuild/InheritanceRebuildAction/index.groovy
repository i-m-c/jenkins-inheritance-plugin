/**
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
 * 
 * Copyright 2010 Sony Ericsson Mobile Communications.All rights reserved.
 * 
 * Copyright (c) 2012-2015 Intel Mobile Communications GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/

import jenkins.model.Jenkins;
import hudson.model.Job;
import hudson.plugins.project_inheritance.projects.view.BuildViewExtension;

// Namespaces
f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
st = namespace("jelly:stapler");


def j = Jenkins.getInstance();

l.layout(title: _("Rebuild"), permission: Job.BUILD, norefresh: "true") {
	
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
	}
	
	// Add a very simply side panel that only allows moving back
	l.side_panel() {
		l.tasks() {
			l.task(icon: "images/24x24/up.gif", title: _("Back to Dashboard"), href: rootURL)
			l.task(icon: "images/24x24/up.gif", title: _("Back to Project"), href: rootURL + "/job/" + my.getProject().getFullName())
		}
	}
	
	// Add the dynamically generated main-panel
	l.main_panel() {
		
		// The configuration form for the rebuilds
		f.form(
				name: "config", action: "configSubmit",
				tableClass: "parameters", method: "post") {
			
			// Get all build extension fields and add them
			buildExts = j.getExtensionList(BuildViewExtension.class);
			for (ext in buildExts) {
				include(ext, ext.getValuePage())
			}
			
			// Add the table for the versions to use
			def versionsMap = my.getProject().getAllInheritedVersionsList(my.getBuild())
			
			tbody() {
				f.block() {
					h3("Please select the versions to use for the rebuild:")
				}
			}
			f.block() {
				table(class: "pane sortable bigtable fixed") {
					thead() {
						tr() {
							th(initialSortDir: "down", class: "pane-header small", _("Version"))
							th(initialSortDir: "down", class: "pane-header variable", _("Project"))
							th(initialSortDir: "down", class: "pane-header variable", _("Description"))
						}
					}
					for (e in versionsMap) {
						//Ignore projects, that only have one version
						if (e.getVersions().size() <= 1) { continue; }
						tr() {
							// Version number
							td(class: "pane") {
								select(style: "width:100%;overflow:hidden;", name: "version") {
									for (ev in e.getVersions()) {
										if(ev.equals(e.version)) {
											option(selected: "true", value: ev, ev)
										} else {
											option(value: ev, ev)
										}
									}
								}
							}
							
							// Project Name
							def pName = e.project.getFullName()
							td(class: "pane") {
								a(href: "/job/" + pName) {
									st.out(value: pName)
								}
								f.textbox(style: "visibility:hidden", name: "project", value: pName)
							}
							
							// Versioning annotation
							td(class: "pane", e.description)
						}
					}
				}
			}
		
			// The refresh button, to update the page with new parameters
			tbody() {
				f.block() {
					div(style: "margin-top:0em;")
					f.submit(name: "doRefresh", value: _("Refresh tree"))
				}
			}
			
			// The parameters, taken from the previous build
			tbody() {
				f.block() {
					hr(style: "margin-top:1em; margin-bottom:em")
				}
			}
			f.block() {
				h3("Please select the parameters to use for the rebuild:")
			}
			
			// Non-hidden parameters
			tbody() {
				f.block() {
					for (parameterDefinition in my.getParametersFor(request, false)) {
						tbody() {
							include(
									parameterDefinition,
									parameterDefinition.descriptor.valuePage
							)
						}
					}
				}
			}
			
			// Hidden parameters
			tbody() {
				f.block() {
					f.advanced(title: "Show hidden variables", align: "left") {
						for (parameterDefinition in my.getParametersFor(request, true)) {
							include(
									parameterDefinition,
									parameterDefinition.descriptor.valuePage
							)
						}
					}
				}
			}
			
			
			// Finally, the rebuild-submission button
			f.block() {
				f.submit(name: "doRebuild", value: _("Rebuild"))
			}
		}
	}
}
