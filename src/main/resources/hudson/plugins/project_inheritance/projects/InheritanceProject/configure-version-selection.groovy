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
import hudson.plugins.project_inheritance.util.VersionsNotification;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


// JS function to alter the current URL with new versions
script(
		type:"text/javascript",
		src: resURL + "/plugin/project-inheritance/scripts/versionSelection.js"
)

f.section(title: _("Version Control")) {
	//Print a warning message that outlines what kind of version you've loaded
	note = my.getCurrentVersionNotification()
	f.block() {
		color = (note.isWarning) ? "darkred" : "darkgreen"
		span(style: "font-size:125%; font-weight:bold; color:" + color) {
			msg = note.getNotificationMessage();
			span(style: "margin-right:1em", msg)
		}
	}
	
	//The actual version selection box
	f.entry(
			field: "userDesiredVersion",
			title: _("Select version to display")
	) {
		f.select(
				onchange: "window.location = alterUrlToVersion(document.URL, this.options[this.selectedIndex].value)"
		)
		f.description(
			"This dropdown box allows you to select an older version" +
			" to view. If you then save this version, it is in effect a \"revert\"."
		)
	}
	
	//Add an invisible versioning message box; this is filled with a value during form submission
	f.invisibleEntry() {
		f.textbox(name: "versionMessageString")
	}
}
