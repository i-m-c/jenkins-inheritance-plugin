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

import hudson.plugins.project_inheritance.projects.InheritanceProject;


f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


l.layout(title: my.getDisplayName(), noRefresh: "true") {
	l.header() {
		style(type: "text/css",
				"""
				table.fixed {
					word-wrap: break-word;
					overflow:hidden;
					width:100%;
					min-width:500px;
					table-layout: fixed;
				}
				
				th.forceWrap { white-space:pre-wrap; word-wrap: break-word; overflow:hidden; }
				td.forceWrap { white-space:pre-wrap; word-wrap: break-word; overflow:hidden; }
				
				th.variable { width:auto; }
				th.wider { width:20em; }
				th.wide { width:15em; }
				th.medium { width:10em; }
				th.small { width:4em; }
				"""
		)
	}
		
	// Include side-panel (maybe load from root project)
	include(my, "sidepanel.jelly")
	
	
	// Add the main-panel containing the big table of parameters
	l.main_panel() {
		h1(my.getFullName() + " Parameter Derivation")
		
		table(class: "pane sortable bigtable fixed") {
			tr() {
				th(class: "pane-header wide forceWrap", initialSortDir: "down", _("Parameter"))
				th(class: "pane-header wider forceWrap",  _("From"))
				th(class: "pane-header small forceWrap",  _("Order"))
				th(class: "pane-header medium forceWrap",  _("Force default / assigned"))
				th(class: "pane-header variable forceWrap",  _("Default Value"))
			}
			for (e in my.getParameterDerivationList()) {
				pName = e.getProjectName()
				tr() {
					// Parameter name
					td (class:"pane forceWrap", e.getParameterName())
					// Project origin
					td (class:"pane forceWrap") {
						a(href: rootURL + "/job/" + pName, pName)
					}
					// Order 
					td (class:"pane forceWrap", e.getOrder())
					// Flags
					td (class:"pane forceWrap", e.getDetail())
					// Default
					td (class:"pane forceWrap", e.getDefault())
				}
			}
		}
	}
}
