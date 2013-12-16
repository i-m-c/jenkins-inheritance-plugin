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

import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.view.InheritanceViewAction;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);
ct = namespace(lib.CustomTagLib);

//Create the Constants object that is read by the IView page
class Constants {
	static build;
	static project;
	static showDownload;
	static descriptor;
}

l.layout(title: my.displayName) {
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
		script(
				type:"text/javascript",
				src: resURL + "/plugin/project-inheritance/scripts/InheritanceViewAction/toggleVisibility.js"
		)
	}

	include(my, "sidepanel")
	
	//Main panel with lots of plugin-contributed data
	l.main_panel() {
		include(InheritanceViewAction, "view")
	}
}
