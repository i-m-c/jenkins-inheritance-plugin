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

import jenkins.model.Jenkins

// Namespaces
f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
t = namespace(lib.JenkinsTagLib);


l.main_panel() {
	h1(my.getDisplayName())
	
	f.form(name: "config", action: "configSubmit", method: "post") {
		//Ensure that the descriptor is set correctly
		instance = my
		descriptor = my.getDescriptor()
		
		helpURL = helpRoot + "/" + pce
		
		
		f.section(title: _("SanityCheckSection")) {
			f.entry(
					title: _("UrlErrorPatternTitle"),
					field: "acceptableErrorUrls"
			) {
				f.textarea()
			}
		}
		
		f.section(title: _("CreationSection")) {
			f.entry(
					title: _("CreationEnableTitle"),
					help: helpURL + "/EnableCreation.html") {
				f.checkbox(field: "enableCreation")
			}
			f.entry(
					title: _("CreationOnStartTitle"),
					help: helpURL + "/TriggerOnStartup.html") {
				f.checkbox(field: "triggerOnStartup")
			}
			f.entry(
					title: _("CreationOnModTitle"),
					help: helpURL + "/TriggerOnChange.html") {
				f.checkbox(field: "triggerOnChange")
			}
			f.entry(
					title: _("CreationOnRenameTitle"),
					help: helpURL + "/CopyOnRename.html") {
				f.checkbox(field: "copyOnRename")
			}
			f.entry(
					title: _("CreationRestrictTitle"),
					help: helpURL + "/RestrictRenaming.html") {
				f.select(field: "renameRestriction")
			}
		}
		
		/* This section configures Templates to be used in the Project-Wizard
		 * on the "New Item" page.
		 */
		f.section(title: _("TemplatesSection")) {
			f.entry(help: "${helpRoot}/${pce}/Templates.html")
			f.block() {
				f.repeatableProperty(
						field: "templates",
						add: _("AddTemplate"),
						header: _("Template")
				) {
					f.entry(title: "") {
						div(align: "right") {
							f.repeatableDeleteButton()
						}
					}
				}
			}
		}
		
		/* This section defines and shows which classes are available
		 * for creation matching
		 */
		f.section(title: _("ProjectTypesSection")) {
			f.entry(help: "${helpRoot}/${pce}/CreationClasses.html")
			f.block() {
				f.hetero_list(
						items: my.creationClasses,
						name: "creationClasses",
						hasHeader: "true",
						descriptors: my.getCreationClassesDescriptors(),
						addCaption: _("TypeAddTitle")
				)
			}
		}
		f.section(title: _("MatingsSection")) {
			f.entry(help: "${helpRoot}/${pce}/CreationMatings.html")
			f.block() {
				f.hetero_list(
						items: my.matings,
						name: "matings",
						hasHeader: "true",
						descriptors: my.getMatingDescriptors(),
						addCaption: _("MatingsCaption")
				)
			}
		}
		
		/* The very last entry is the floating save button. */
		f.block() {
			if (h.hasPermission(Jenkins.ADMINISTER)) {
				f.bottomButtonBar() {
					f.submit(value: _("Save"))
				}
			}
		}
	}
}
