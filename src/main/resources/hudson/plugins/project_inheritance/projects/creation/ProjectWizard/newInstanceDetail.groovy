/**
 * Copyright (c) 2017, Intel Deutschland GmbH
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
import hudson.util.ListBoxModel;
import static hudson.plugins.project_inheritance.projects.creation.ProjectWizard.DESCRIPTOR;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);
st = namespace("jelly:stapler")

/* Note:
 * The HTML/Groovy in this script is handled differently from all other parts
 * of Jenkins, since the content is rendered into a filtered DIV field. So only
 * static HTML & JS is allowed.
 * 
 * This means that most Jenkins Jelly/Groovy tags can't be used, since their
 * scripts would render as raw text.
 * 
 * But some scripting is permitted, as long as it is kept simple
 */

//Description first
div(_("ProjectWizard.Description"))

//script(type: "text/javascript", src: rootURL + "/plugin/project-inheritance/scripts/ProjectWizard/description.js")

//Then the configuration
table(style: "width: 100%") {
	tr() {
		td(style: "width: 8em", "Template Name")
		td() {
			select(id: "templateName", name: "templateName", style: "width: 100%;") {
				lbm = DESCRIPTOR.doFillNameItems();
				for (opt in lbm) {
					if (opt.selected) {
						option(value: opt.value, selected: "selected", opt.name)
					} else {
						option(value: opt.value, opt.name)
					}
				}
			}
		}
	}
}
