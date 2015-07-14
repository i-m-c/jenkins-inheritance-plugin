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

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


l.layout(title: my.displayName, norefresh: true) {
	
	// Stuff that belongs in the frame header
	l.header() {
		// JS function to alter the content of the diff-div field
		script(type: "text/javascript",
			src: resURL + "/plugin/project-inheritance/scripts/showSVGRelGraph.js"
		)
		
		// Necessary CSS classes for fixed table sizes
		link(
			rel: "stylesheet", type: "text/css",
			href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
	}
	
	// Include the standard side-panel for the PCE
	include(my, "sidepanel")
	
	// Then, a very simple main panel with just a simple table
	l.main_panel() {
		h1 {
			img (src: imagesURL + "/48x48/error.png", alt: "", height: "48", width: "48")
			span ("The project " + my.fullName + " is still referenced by:")
		}
		
		//Add the relationship tables in verbose mode
		verbose = true
		include(my, "inheritanceRelationTables")
		
		// Then, we include the page that loads the table of created jobs
		form (id: "confirmation", method: "post", action: my.getAbsoluteUrl()) {
			div (style: "margin-top:1em;margin-bottom:1em") {}
			f.submit (value: _("Okay, I have seen it.")) {}
		}
		
		// The box where the SVG-graph will be put into (see JS above!)
		br ()
		h2 (_("SVG Representation"))
		div (
			id: "svgRelGraph",
			style:"border: 1px solid black; width:100%; overflow:auto; margin-top:1em; margin-bottom:1em"
		)
	}
}
