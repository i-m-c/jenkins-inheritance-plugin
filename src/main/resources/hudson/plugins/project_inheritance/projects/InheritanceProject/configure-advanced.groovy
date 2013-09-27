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
p = namespace("/lib/hudson/project")


helpRoot = "/plugin/project-inheritance/help"

//Add the default stub for the custom workspace
p.config_customWorkspace()

//Add the parameterized workspace
f.optionalBlock(
		title: _("Use parameterized workspace"),
		help: helpRoot + "/InheritanceProject/ParameterizedWorkspace.html",
		name: "parameterizedWorkspace",
		checked: my.parameterizedWorkspace != null
) {
	f.entry(title: _("Directory")) {
		f.textbox(
				field: "parameterizedWorkspace",
				name: "parameterizedWorkspace.directory"
		)
	}
}
f.block() {
	div(style: "width:90%;margin-left:auto;margin-right:auto;") {
		hr()
	}
}
