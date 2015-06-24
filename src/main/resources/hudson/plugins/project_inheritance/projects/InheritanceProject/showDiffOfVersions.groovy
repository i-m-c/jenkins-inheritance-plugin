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
import org.kohsuke.stapler.Stapler;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
st = namespace("jelly:stapler")


adjunctPrefix = "hudson.plugins.project_inheritance.projects.InheritanceProject.adjunct"

l.layout(
		title: "${my.displayName} Version Differences",
		permission: my.EXTENDED_READ,
		norefresh: "true"
) {
	// Include the standard side-panel for this project
	include(my, "sidepanel")
	
	l.header() {
		//Necessary CSS classes for fixed font
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
		
		//Load additional JS/CSS for the comparison table
		st.adjunct(includes: adjunctPrefix + ".computeDiff")
	}
	
	//Main panel showing a table with the version selection and one for the diffs 
	l.main_panel() {
		//Grab all versions of the current job
		versions = my.getVersions()
		
		//Fetch the current stapler request (if any)
		req = Stapler.getCurrentRequest()
		//Check if there's a "left" and a "right" parameter
		left = null;
		right = null;
		try {
			left = Long.parseLong(req.getParameter("left"));
			right = Long.parseLong(req.getParameter("right"));
		} catch (NumberFormatException ex) {
			left = null;
			right = null;
		}
		
		curr = null;
		prev = null
		if (left != null && right != null) {
			prev = my.getVersionedObjectStore().getVersion(left)
			curr = my.getVersionedObjectStore().getVersion(right)
		}
		
		if (curr == null || prev == null) {
			allStables = my.getStableVersions()
			if (allStables.size() >= 2) {
				iter = allStables.descendingIterator();
				curr = iter.next()
				prev = iter.next()
			} else if (allStables.size() == 1) {
				curr = versions.getLast()
				prev = allStables.getLast()
			} else if (versions.size() >= 2) {
				//No stables at all, comparing the last two versions
				iter = versions.descendingIterator();
				curr = iter.next()
				prev = iter.next()
			} else {
				curr = versions.getLast()
				prev = curr
			}
		}
		
		// Render the version-selection table
		table() {
			tr() {
				th(_("Left version")) 
				th(_("Right version")) 
			}
			tr() {
				// Selection box for the left version
				td() {
					select(id: "leftVersion", style: "width:100%;overflow:hidden;") {
						for (ev in versions) { 
							msg = ev.toString(24)
							if (ev.id.equals(prev.id)) {
								option(selected: "true", value: ev.id, msg)
							} else {
								option(value: ev.id, msg)
							}
						}
					}
				}
				
				// Selection box for the right version
				td() {
					select(id: "rightVersion", style: "width:100%;overflow:hidden;") {
						for (ev in versions) {
							msg = ev.toString(24)
							if (ev.id.equals(curr.id)) {
								option(selected: "true", value: ev.id, msg)
							} else {
								option(value: ev.id, msg)
							}
						}
					}
				}
			}
		}
		
		// The buttons to compute the diff and display it in the text field below
		input(type: "button", class: "yui-button",
				value: _("Compute Unified Diff"),
				onclick: "computeDiff('" + my.getName() + "', 'unified')"
		)
		
		/**
		 * Disabled until we actually implement a side-by-side diff
		input(type: "button", class: "yui-button",
				value: _("Compute Side-by-Side Diff")
				onclick: "JavaScript:computeDiff('" + my.getName() + "', 'side')"
		)
		*/
		
		input(type: "button", class: "yui-button",
			value: _("Display Raw"),
			onclick: "computeDiff('" + my.getName() + "', 'raw')"
		)
		
		// Empty whitespace
		div(style: "margin-top:5em;")
		
		// Text field that will contain the diff
		div(id: "diffBox", class: "diff", "The diff will show up here. :)")
		
		//At the end, executing the JS for computing the current diff
		script(type:"text/javascript",
				"computeDiff('" + my.getName() + "', 'unified')"
		)
	}
}
