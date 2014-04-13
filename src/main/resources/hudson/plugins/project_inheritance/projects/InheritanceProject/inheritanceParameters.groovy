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
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import java.text.SimpleDateFormat;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);
ct = namespace(lib.CustomTagLib);
h2("Further information")

if (my.getAssignedLabelString()) {
	span("In order to run, the project "+ my.getPronoun() + " requires hosts which have the following setting: "+my.getAssignedLabelString());
} else {
	span("The project "+ my.getPronoun() +" runs on any build host.")
}

if (my.getParameters().size() != 0) {
	exists = false
	for (pd in my.getParameters()) {
		if (pd instanceof InheritableStringParameterDefinition) {
			if (pd.getInheritanceMode() != IModes.FIXED){
				if(getMustBeAssigned()){
					if(!pd.getDefaultValue()){
						exists = true
					}
				}
			}
		}else if (pd instanceof StringParameterDefinition) {
			if (!pd.getDefaultValue()) {
				exists = true
			}
		}
	}
	if (exists) {
		br()
		span("Parameters required to fill in:")
		ul(){
			for(pd in my.getParameters()){
				if (pd instanceof InheritableStringParameterDefinition){
					if (pd.getInheritanceMode() != IModes.FIXED){
						if (pd.getMustBeAssigned() == true) {
							if (!pd.getDefaultValue()) {
								li(pd.getName()+": "+pd.getDescription());
							}
						}
					}
				}else if (pd instanceof StringParameterDefinition) {
					if (!pd.getDefaultValue()) {
						li(pd.getName()+": "+pd.getDescription());
					}
				}
			}
		}
		
		f.form() {
			f.advanced(title: "Show optional parameters...", align: "left"){
				f.block() {
					span("Optional parameters:")
					ul(){
						for(pd in my.getParameters()){
							if (pd instanceof InheritableStringParameterDefinition){
								if (pd.getInheritanceMode() != IModes.FIXED){
									if (pd.getMustBeAssigned() == false) {
										li(pd.getName()+": "+pd.getDescription());
									} else {
										if (pd.getDefaultValue()) {
											li(pd.getName()+": "+pd.getDescription());
										}
									}
								}
							}else if (pd instanceof StringParameterDefinition) {
								if (pd.getDefaultValue()) {
									li(pd.getName()+": "+pd.getDescription());
								}
							}
						}
					}
				}
			}
		}
	}
}