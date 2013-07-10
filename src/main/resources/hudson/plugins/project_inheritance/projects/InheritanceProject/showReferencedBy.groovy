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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


l.layout(title: my.displayName, norefresh: true) {
	
	// Stuff that belongs in the frame header
	l.header() {
		// JS function to alter the content of the diff-div field
		script (language: "JAVASCRIPT", type: "TEXT/JAVASCRIPT", """
			var inlineSVG = function() {
				// Fetch the two versions from the select boxes
				var graphDiv = document.getElementById("svgRelGraph")
				
				var xhr = new XMLHttpRequest();
				if (!xhr) {
					alert("Could not open XMLHttpRequest. Consider not using IE5/6.");
					return;
				}
				var url = "renderSVGRelationGraph";
				
				xhr.open('GET', url, true);
				xhr.onreadystatechange = function () {
					if (xhr.readyState != 4) { return; }
					if (graphDiv) {
						graphDiv.innerHTML = xhr.responseText;
					}
				};
				xhr.send(null);
			}
			
			Event.observe(window, "load", inlineSVG);
			"""
		)
		
		style (type: "text/css", """
			table.fixed {
				word-wrap: break-word;
				overflow:hidden;
				width:50%;
				min-width:500px;
				table-layout: fixed;
			}
			
			th.forceWrap { white-space:pre-wrap; word-wrap: break-word; overflow:hidden; }
			td.forceWrap { white-space:pre-wrap; word-wrap: break-word; overflow:hidden; }
			
			th.variable { width:auto; }
			th.wider { width:20em; }
			th.wide { width:15em; }
			th.medium { width:10em; }
			th.small { width:5em; }
			"""
		)
	}
	
	// Include the standard side-panel for the PCE
	include(my, "sidepanel.jelly")
	
	// Then, a very simple main panel with just a simple table
	l.main_panel() {
		h1 {
			img (src: imagesURL + "/48x48/error.png", alt: "", height: "48", width: "48")
			span ("The project " + my.name + " is still referenced by:")
		}
		
		// Rendering the table of elements
		table (class: "pane sortable bigtable fixed") {
			tr {
				th (class: "pane-header variable forceWrap", initialSortDir: "down", _("Project Name"))
				th (class: "pane-header medium forceWrap", initialSortDir: "down", _("As"))
				th (class: "pane-header medium forceWrap", initialSortDir: "down", _("Distance"))
			}
			for (e in my.getRelatedProjects()) {
				job = e.get(0)
				url = InheritanceProject.getJobActionURL(job, "")
				tr {
					td (class:"pane forceWrap") {
						a (href : url, job) {}
					}
					td (class:"pane forceWrap", e.get(1)) {}
					td (class:"pane forceWrap", e.get(2)) {}
				}
			}
		}
		
		// Then, we include the page that loads the table of created jobs
		url = InheritanceProject.getJobActionURL(my.name, "")
		form (id: "confirmation", method: "post", action: url) {
			div (style: "margin-top:1em;margin-bottom:1em") {}
			f.submit (value: _("Okay, I have seen it.")) {}
		}
		
		// The box where the SVG-graph will be put into (see JS above!)
		br ()
		div (
			id: "svgRelGraph",
			style:"border: 1px solid black; width:100%; overflow:auto; margin-top:1em; margin-bottom:1em"
		)
	}
}
