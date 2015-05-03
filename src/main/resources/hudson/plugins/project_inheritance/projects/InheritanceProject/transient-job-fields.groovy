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
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);

if (my.getIsTransient()) {
	h1("Read-only Configuration of " + my.pronoun + " " + my.displayName)
	
	refURL = rootURL + "/job/" + my.displayName + "/showReferencedBy"
	h3(style: "color:red") {
		span("Please note that this view is a read only view of the job" +
		" configuration. You cannot edit fields in this view. In order" +
		" to change the displayed values, open the list of "
		)
		a(href: refURL, "references")
	}
	
	script(
			type:"text/javascript",
			src: resURL + "/plugin/project-inheritance/scripts/markAllReadOnly.js"
	)
}
else { // Non-transients
	h1("Configuration of " + my.pronoun + " " + my.displayName)
	
	ct.colored_block(backCol: "Khaki", borderCol: "navy") {
		include(my, "configure-version-selection")
	}
}

// Printing a humongous warning if the project has a cyclic dependency
if (my.hasCyclicDependency()) {
	h2(style: "color:red") {
		span("This project has a")
		a(style: "color:red", href: "http://en.wikipedia.org/wiki/Cycle_detection", "cyclic")
		a(style: "color:red", href: "http://en.wikipedia.org/wiki/Diamond_problem", "diamond")
		span("or repeated dependency!")
	}
}
