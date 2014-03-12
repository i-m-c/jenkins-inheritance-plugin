/**
 * Copyright (c) 2011-2014, Intel Mobile Communications GmbH
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

// Namespaces
f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);



l.layout(title: my.displayName, norefresh: "true") {
	include(my, "sidepanel.jelly")
	
	l.main_panel() {
		h1("Creation results") {
			img(height: "48", alt: "", width: "48", src: imagesURL + "/48x48/accept.png")
		}
		table(class: "pane sortable bigtable") {
			tr() {
				th(initialSortDir: "down", class: "pane-header", _("Name"))
				th(class: "pane-header", _("Status"))
			}
			for (e in my.getLastCreationState()) {
				tr() {
					td(class: "pane") {
						a(href: rootURL + "/job/" + e.getKey()) {
							span(e.getKey())
						}
					}
					td(class: "pane", e.getValue())
				}
			}
		}
		form(id: "confirmation",
				action: "/project_creation/leaveCreationResult",
				method: "post"
		) {
			div(style: "margin-top:5em;margin-bottom:5em")
			f.submit(value: _("Okay, I have seen it."))
		}
	}
	l.header()
}
