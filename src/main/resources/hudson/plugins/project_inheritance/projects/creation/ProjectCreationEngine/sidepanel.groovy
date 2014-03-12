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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);


l.side_panel() {
	l.tasks() {
		l.task(
				icon: "images/24x24/up.png",
				title: _("Back to Dashboard"),
				href: rootURL
		)
		l.task(
				icon: "images/24x24/setting.png",
				title: _("Manage Jenkins"),
				permission: app.ADMINISTER,
				href: rootURL + "/manage"
		)
		l.task(
				icon: "images/24x24/clock.png",
				title: _("Create Projects"),
				permission: app.ADMINISTER,
				href: rootURL + "/project_creation/createProjects"
		)
		l.task(
				icon: "images/24x24/clipboard.png",
				title: _("Show last creation results"),
				permission: app.ADMINISTER,
				href: rootURL + "/project_creation/showCreationResults"
		)
	}
	t.queue(items: app.queue.items)
	//t.executors
}
