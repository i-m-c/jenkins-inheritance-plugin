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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


l.layout(title: my.displayName) {
	
	include(my, "sidepanel")
	l.main_panel() {
		h1 (my.pronoun + " " + my.displayName + " Advanced Options")
		l.tasks() {
			l.task(
					icon: "images/24x24/setting.png",
					title: _("Get full XML configuration"),
					href: "getConfigAsXML?depth=1"
			)
			
			l.task(
					icon: "images/24x24/setting.png",
					title: _("Get XML configuration of local project only"),
					href: "getConfigAsXML"
			)
			
			if (my.getIsTransient() == false) {
				l.task(
						icon: "plugin/project-inheritance/images/48x48/versions.png",
						title: _("Get all versions as XML"),
						href: "getVersionsAsXML")
				
				l.task(
						icon: "plugin/project-inheritance/images/48x48/versions.png",
						title: _("Get all versions as compressed XML"),
						href: "getVersionsAsCompressedXML"
				)
			}
			l.task(
					icon: "images/24x24/document-properties.png",
					title: _("Get parameter defaults as XML"),
					href: "getParamDefaultsAsXML"
			)
			l.task(
					icon: "images/24x24/document-properties.png",
					title: _("Get parameter expansions as XML"),
					href: "getParamExpansionsAsXML"
			)
		}
	}
}
