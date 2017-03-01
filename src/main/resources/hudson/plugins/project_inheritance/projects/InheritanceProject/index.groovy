/**
 * Copyright (c) 2015-2016, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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
t = namespace(lib.JenkinsTagLib);
ct = namespace(lib.CustomTagLib);

helpRoot = "/plugin/project-inheritance/help"

l.layout(title: my.displayName) {
	
	l.header() {
		// Necessary CSS classes for fixed table sizes
		link(
				rel: "stylesheet", type: "text/css",
				href: resURL + "/plugin/project-inheritance/styles/table-monospace.css"
		)
	}
	
	//Standard project sidepanel
	include(my, "sidepanel")
	
	//Main panel with lots of plugin-contributed data
	l.main_panel() {
		// Render the headline for this job, that identifies it
		include(my, "index-headline")
		
		//Include warning messages
		include(my, "index-warnings")
		
		//Inject main index page contents from supertype
		t.editableDescription(permission: my.CONFIGURE)
		
		include(my, "index-further-info")
		
		include(my, "jobpropertysummaries")
		include(my, "main")
	}
}
