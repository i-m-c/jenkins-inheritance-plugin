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


// JS function to alter the current URL with new versions
script(
		type:"text/javascript",
		src: resURL + "/plugin/project-inheritance/scripts/versionSelection.js"
)

f.section(title: _("Version Control")) {
	f.entry(
			field: "userDesiredVersion",
			title: _("Select version to display")
	) {
		f.select(
				onchange: "window.location = alterUrlToVersion(document.URL, this.options[this.selectedIndex].value)"
		)
		f.description(
			"This dropdown box allows you to select an older version" +
			"to view. If you then save this version, it is in effect a \"revert\"."
		)
	}
}
