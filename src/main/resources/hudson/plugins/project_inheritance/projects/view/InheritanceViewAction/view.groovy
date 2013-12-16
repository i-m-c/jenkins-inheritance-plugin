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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
*/

import jenkins.model.Jenkins;
import hudson.tasks.Builder;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.CommandInterpreter;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.view.InheritanceViewAction;


f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);


//NOTICE: AVOID USING 'my' HERE! This file is used by
//InheritanceViewAction as well as InheritanceProject

//Fetching the constants passed in from the outside
build = Constants.build
project = Constants.project
showDownload = Constants.showDownload
descriptor = Constants.descriptor


def showBuildParametersTable() {
	buildParametersHashMap = InheritanceViewAction.getResolvedBuildParameters(
			(build != null) ? build : project
	)
	if (buildParametersHashMap.size() > 0) {
		table(class:"bigtable pane sortable", style:"width:50%") {
			thead() {
				tr() {
					th(class: "pane-header wider forceWrap", _("Parameter"))
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

if (build != null) {
	h1("Read-only view for build: " + project.displayName + " #" + build.getNumber())
} else {
	h1("Read-only view for project: " + project.displayName)
}

f.form(name: "readonlyConfiguration",
		action: "download",
		method: "post",
		enctype="multipart/form-data") {
	
	//Show the Build Step selection dialog
	f.section(title: _("Build Step Visibility Selection")) {
		f.block() {
			f.entry(field: "projectClass", title: _("Only show builders from:")) {
				f.select(
						default: "",
						onchange: "changeAllBuilderVisibility(this.value)"
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
	f.section(title: _("All build parameters")) {
		f.block() {
			showBuildParametersTable()
		}
	}
	
	f.block() {
		div(style: "margin-top:2em;")
	}
	
	//Fetch a map of all builders for the current build
	buildMap = project.getBuildersFor(
			(build != null) ? build.getProjectVersions() : null,
			CommandInterpreter.class
	)
	
	//Fetch the command interpreter descriptors
	ciDescriptors = Jenkins
			.getInstance()
			.<BuildStep, BuildStepDescriptor<Builder>>
			getDescriptorList(CommandInterpreter.class);
	
	for (ref in buildMap.keySet()) {
		pronoun = ref.getProject().pronoun;
		if (pronoun != null && !(pronoun.isEmpty())) {
			header = ("Build Steps for: " + ref.getName() + " (" + pronoun + ")");
		} else {
			header = "Build Steps for: " + ref.getName();
		}
		f.section(title: header) {
			//Create a unique block ID that contains the project's class
			//This is used by the above select box's "onChange" method
			//to hide/show these fields
			blockID = "buildStepsBlock-" + ref.getProject().getCreationClass() + "-" + ref.getName()
			
			//Add a hide/show button
			f.block() {
				input(type: "button", class: "yui-button",
						onClick : "toggleVisibility('" + blockID + "')",
						value: _("Show/Hide")
				)
			}
			
			//And the toggleable block
			ct.id_block(id: blockID) {
				//This list displays/configures the configured parent references
				//It is customized not to have add/delete buttons
				ct.hetero_list(
						items: buildMap.get(ref),
						name: "projects",
						hasHeader: "false",
						descriptors: ciDescriptors
				)
			}
		}
	}
	
	if (showDownload) {
		f.block() {
			// Empty whitespace
			div(style: "margin-top:5em;")
			f.submit(value:_("Download"))
		}
	}
}