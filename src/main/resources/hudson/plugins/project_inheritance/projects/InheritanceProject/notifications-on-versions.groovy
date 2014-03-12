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
import hudson.plugins.project_inheritance.projects.InheritanceProject.VersionsNotification;
import java.util.LinkedList;

versionsNotification = my.notifyOnCurrentVersionStatus()

h3(style: "color:darkgreen") {
	span(versionsNotification.getNotificationMessage())
	if (versionsNotification.getVersions().size() > 0) {
		select(
				id:"notificationVersionSelector",
				style: "color:darkgreen",
				name: "recommendedVersion",
				onchange: "window.location = alterUrlToVersion(document.URL, this.options[this.selectedIndex].value)"
			) {
				option(value: "Select a recommended version", "Select a recommended version")
				for (v in versionsNotification.getVersions()) {
					option(value: v.id, v.id)
				}
			}
	}
}

//JS function to alter the current URL with new versions
script(
		type:"text/javascript",
		src: resURL + "/plugin/project-inheritance/scripts/versionSelection.js"
)
