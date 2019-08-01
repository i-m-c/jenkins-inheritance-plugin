/**
 * Copyright (c) 2016, Intel Deutschland GmbH
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

import jenkins.model.Jenkins;

import java.util.concurrent.atomic.AtomicInteger;

import hudson.tasks.Builder;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.CommandInterpreter;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.view.BuildFlowScriptAction;
import hudson.plugins.project_inheritance.projects.view.BuildFlowScriptExtension;


f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);


//NOTICE: AVOID USING 'my' HERE! This file is used by
//BuildFlowScriptAction as well as InheritanceProject

//Load the javascript files
script(
		type:"text/javascript",
		src: resURL + "/plugin/project-inheritance/scripts/BuildFlowScriptAction/toggleVisibility.js"
)

//Fetching variables from different sources; depending on what 'my' is
if (my instanceof BuildFlowScriptAction) {
	//We're on a build page
	build = my.getBuild();
	project = build.getParent();
	showDownload = true;
	descriptor = my.getDescriptor();
} else if (my instanceof InheritanceProject) {
	//We're on a project's view page
	build = null;
	project = my;
	showDownload = false;
	descriptor = BuildFlowScriptAction.getDescriptorStatic();
} else {
	//TODO: This should render an error -- even though it should never be reached
	return;
}

//FIXME: Add support for SMS21663005

def showBuildParametersTable() {
	buildParametersHashMap = BuildFlowScriptAction.getResolvedBuildParameters(
			(build != null) ? build : project
	)
	if (buildParametersHashMap.size() > 0) {
		table(class:"bigtable pane sortable", style:"width:50%") {
			thead() {
				tr() {
					th(class: "pane-header wider forceWrap", initialSortDir: "down", _("Parameter"))
					th(class: "pane-header wider forceWrap", _("Value"))
				}
			}
			tbody() {
				for (e in buildParametersHashMap.entrySet()) {
					parameterName = e.getKey()
							parameterValue = e.getValue()
							
							tr() {
						td(class: "pane forceWrap", parameterName)
						td(class: "pane", parameterValue)
					}
				}
			}
		}
	}
}

def renderBuildersFromRef(salt, ref, buildMap, ciDescriptors) {
	//Fetch identifies of that project
	pName = ref.getName()
	pNoun = ref.getProject().pronoun
	pClass = ref.getProject().getCreationClass()
	//Fetch the build steps for this project reference
	items = buildMap.get(ref)
	iState = ((items.isEmpty()) ? "empty" : "full")
	
	uid = salt.getAndIncrement()
	
	//Generate a unique ID for this section and sub-block
	sectionUID = sectionPrefix + iState + "-" + pName + "-" + uid + "-" + pClass
	blockUID = blockPrefix + iState + "-" + pName + "-" + uid + "-" + pClass
	
	//Default style is to be hidden for empty projects
	defStyle = (items.isEmpty()) ? "display:none" : "display:display"
	
	//Create the header for the section
	if (pNoun != null && !(pNoun.isEmpty())) {
		header = ("Build Steps for: " + pName + " (" + pNoun + ")");
	} else {
		header = "Build Steps for: " + pName;
	}
	//Add the section block
	ct.id_block(id: sectionUID, row_style: defStyle) {
		div(class: "section-header", id: sectionUID, header)
	}
	
	//Add the build-steps block
	if (items.isEmpty()) {
		ct.id_block(id: blockUID, row_style: defStyle) {
			div(_("No Build steps"))
		}
	} else {
		//Add a hide/show button
		f.block() {
			input(type: "button", class: "yui-button",
					onClick : "toggleElem('" + blockUID + "')",
					value: _("Show/Hide")
			)
		}
		//The toggleable block itself
		ct.id_block(id: blockUID, row_style: defStyle) {
			//This list displays/configures the configured parent references
			//It is customized not to have add/delete buttons
			ct.hetero_list(
					items: items,
					name: "projects",
					hasHeader: "false",
					descriptors: ciDescriptors
			)
		}
	}
}

def renderBuildStepsList(salt, project, versions, ciDescriptors) {
	buildMap = project.getBuildersFor(
			versions, CommandInterpreter.class
	);
	hasValues = false;
	for (l in buildMap.values()) { hasValues |= !(l.isEmpty()); }
	if (hasValues) {
		for (ref in buildMap.keySet()) {
			renderBuildersFromRef(salt, ref, buildMap, ciDescriptors)
		}
	} else {
		f.block() { div("No build steps") }
	}
}

def renderBuildStepsSections() {
	//Fetch the command interpreter descriptors for use in hetero lists
	ciDescriptors = Jenkins
			.getInstance()
			.<BuildStep, BuildStepDescriptor<Builder>>
			getDescriptorList(CommandInterpreter.class);
	
	
	//Add a show/hide buttons for empty sections
	f.block() {
		sections = [
				sectionPrefix + "empty",
				blockPrefix + "empty",
		]
		jsCmd = ""
		for (s in sections) {
			jsCmd += "toggleAll(\"tr\", \"" + s + "\");"
		}
		table() {
			tr() {
				td() {
					input(type: "button", class: "yui-button",
							onClick : jsCmd,
							value: _("Show/Hide empty projects")
					)
				}
			}
		}
	}
	
	versions = (build != null) ? build.getProjectVersions() : null;
	
	salt = new AtomicInteger();
	
	// Render the local build steps
	f.section(title: _("Local build steps")) {
		table(style: "padding-left: 2em; width:100%") {
			renderBuildStepsList(salt, project, versions, ciDescriptors);
		}
	}
	//Render the recursive build steps from dependent jobs
	f.section(title: _("Indirectly called build steps")) {
		deps = BuildFlowScriptExtension.getDependentProjectsFor(
				project,
				(build != null) ? build.getProjectVersions() : null
		);
		for (p in deps) {
			table(style: "padding-left: 2em; width:100%") {
				f.section(title: p.getFullDisplayName()) {
					table(style: "padding-left: 4em; width:100%") {
						renderBuildStepsList(salt, p, versions, ciDescriptors);
					}
				}
			}
		}
	}
}


def render() {
	if (build != null) {
		h1(project.displayName + " #" + build.number + " Full Build Flow")
	} else {
		h1(project.displayName + " Full Build Flow")
	}
	
	//The prefix for all sections and sub-blocks
	sectionPrefix = "builds-section-"
	blockPrefix = "builds-section-block-"
	
	f.form(name: "readonlyConfiguration",
			action: "download",
			method: "post",
			enctype="multipart/form-data") {
		
		//Show the Build Step selection dialog
		f.section(title: _("Build Step Visibility Selection")) {
			f.block() {
				f.entry(field: "projectClass", title: _("Expand only builders from:")) {
					f.select(
							default: "",
							onchange: "changeAllBuilderVisibility('tr', '" + blockPrefix + "full" + "', this.value)"
					)
					f.description() {
						span("You can configure the available classes ")
						a(href: rootURL + "/project_creation", "here")
					}
				}
			}
		}
		
		f.block() {
			div(style: "margin-top:2em;")
		}
		
		
		//Fetch and display all parameters
		f.section(title: _("Parameters")) {
			f.block() {
				showBuildParametersTable()
			}
		}
		
		f.block() {
			div(style: "margin-top:2em;")
		}
		
		f.section(title: _("Build Steps")) {
			renderBuildStepsSections()
		}
		
		if (showDownload) {
			f.bottomButtonBar() {
				f.submit(value:_("Download"))
			}
		}
	}
}

//Call the main rendering entry point
render();