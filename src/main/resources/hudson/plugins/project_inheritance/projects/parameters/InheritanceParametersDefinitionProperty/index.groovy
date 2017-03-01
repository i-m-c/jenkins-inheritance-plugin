/**
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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

import com.google.common.base.Joiner;
import jenkins.model.Jenkins;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.projects.view.BuildViewExtension;

// Namespaces
f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
st = namespace("jelly:stapler");


//This view is rendered as /hudson/job/XYZ/build

/* Send back 4xx code so that machine agents don't confuse this form with
 * successful build triggering.
 * 405 is "Method Not Allowed" and this fits here because we need POST.
 */
st.statusCode(value: "405")

def j = Jenkins.getInstance();

l.layout(title: my.displayName, norefresh: "true") {
	include(my.project, "sidepanel")
	
	l.main_panel() {
		//Loading scrim
		div(class: "behavior-loading", _("LOADING"))
		
		//Header of the job
		h1(my.owner.pronoun + " " + my.owner.displayName)
		
		//Encoding the additional arguments: delay and versions
		args = new LinkedList();
		
		delay = request.getParameter('delay')
		if (delay != null && !delay.isEmpty()) {
			args.add("delay=" + delay)
		}
		
		/* The incoming GET URL may have a "versions" or "version" parameter,
		 * that may need to be passed along to the POST, to set the correct
		 * build variant.
		 * Do note that we do not wish to create a full table of versions here
		 * if none is given, since these URLs can become excessively long.
		 */
		Map vMap = VersionHandler.getFromUrlParameter();
		//Encode that back as 'versions' GET parameter
		String versionArg = VersionHandler.getFullUrlParameter(vMap);
		if (versionArg != null && !versionArg.isEmpty()) {
			args.add(versionArg)
		}
		
		// Join the full string
		String argsStr = Joiner.on("&").join(args);
				
		f.form(name: "parameters", action: "build?" + argsStr, tableClass: "parameters", method: "post") {
			
			// Get all build extension fields and add them
			buildExts = j.getExtensionList(BuildViewExtension.class);
			for (ext in buildExts) {
				include(ext, ext.getValuePage())
			}
			
			//Do note, the below parameters will fill themselves into a "parameters" array
			f.section(title: _("Parameters")) {
				for (parameterDefinition in my.getParameterDefinitionSubset(false)) {
					include(
							parameterDefinition,
							parameterDefinition.descriptor.valuePage
					)
				}
				
				// Hide "hidden" parameters behind an advanced button
				f.advanced(title: "Show hidden variables", align: "left") {
					for (parameterDefinition in my.getParameterDefinitionSubset(true)) {
						include(
								parameterDefinition,
								parameterDefinition.descriptor.valuePage
						)
					}
				}
			}
			
			tbody() {
				f.block() {
					f.submit(value: _("Build"))
				}
			}
		}
	}
}
