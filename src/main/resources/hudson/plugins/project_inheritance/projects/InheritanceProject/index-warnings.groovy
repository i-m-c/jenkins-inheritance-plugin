/**
 * Copyright (c) 2015, Intel Deutschland GmbH
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
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;


f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);
ct = namespace(lib.CustomTagLib);

warnMessage = my.warnUserOnUnstableVersions();
if (null != warnMessage) {
	h3(style: "color:darkgreen") {
		span(warnMessage)
		br()
		a(href:rootURL + "/job/" + my.displayName + "/showConfigureVersions",
			_("click here to change it"))
	}
}

missingDeps = my.getMissingDependencies();
if (!missingDeps.isEmpty()) {
	h2(style: "color:red") {
		raw("The project has missing dependencies to: ")
		ul(style: "margin-top:0") {
			for (dep in missingDeps) {
				li() {
					//Breadcrumbs (how to reach the missing dependency)
					for (trace in dep.trace) {
						raw(trace + " \u2192 ")
					}
					//The actually missing dependency
					raw(dep.ref)
				}
			}
		}
	}
}

// Printing a humongous warning if the project has a cyclic dependency
if (my.hasCyclicDependency()) {
	h2(style: "color:red") {
		raw("This project has a ")
		a(style: "color:red", href: "http://en.wikipedia.org/wiki/Cycle_detection", "cyclic")
		raw(", ")
		a(style: "color:red", href: "http://en.wikipedia.org/wiki/Diamond_problem", "diamond")
		raw(" or repeated dependency!")
	}
}


//Checking current parameters for consistency
paramCheck = my.getParameterSanity()
if (paramCheck.getKey() == false) {
	h2(style: "color:red", "This project has a parameter inconsistency!")
	span("Reason: " + paramCheck.getValue())
}

