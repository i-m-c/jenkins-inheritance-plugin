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
		
		
		f.section(title: _("Configuration Page Sanity Checks")) {
			f.entry(
					title: _("Acceptable error text URL patterns"),
					field: "acceptableErrorUrls"
			) {
				f.textarea()
			}
		}
		
		f.section(title: _("Creation Options")) {
			f.entry(
					title: _("Enable Job Creation"),
					help: helpURL + "/EnableCreation.html") {
				f.checkbox(field: "enableCreation")
			}
			f.entry(
					title: _("Create Jobs when Jenkins is started"),
					help: helpURL + "/TriggerOnStartup.html") {
				f.checkbox(field: "triggerOnStartup")
			}
			f.entry(
					title: _("Create on new/changed/deleted Jobs"),
					help: helpURL + "/TriggerOnChange.html") {
				f.checkbox(field: "triggerOnChange")
			}
			f.entry(
					title: _("Copy job on renamed parents"),
					help: helpURL + "/CopyOnRename.html") {
				f.checkbox(field: "copyOnRename")
			}
			f.entry(
					title: _("Restrict compound renaming"),
					help: helpURL + "/RestrictRenaming.html") {
				f.select(field: "renameRestriction")
			}
		}
		
		/* This section defines and shows which classes are available
		 * for creation matching
		 */
		f.section(title: _("Project Types")) {
			f.entry(help: "${helpRoot}/${pce}/CreationClasses.html")
			f.block() {
				f.hetero_list(
						items: my.creationClasses,
						name: "creationClasses",
						hasHeader: "true",
						descriptors: my.getCreationClassesDescriptors(),
						addCaption: _("Add new creation class type")
				)
			}
		}
		f.section(title: _("Mating Defintions")) {
			f.entry(help: "${helpRoot}/${pce}/CreationMatings.html")
			f.block() {
				f.hetero_list(
						items: my.matings,
						name: "matings",
						hasHeader: "true",
						descriptors: my.getMatingDescriptors(),
						addCaption: _(
							"Mark two classes as being able to produce a new project derived from both."
						)
				)
			}
		}
		
		/* The very last entry is the floating save button.
		 * Do note that it does not move when help text or sections
		 * are expanded. As such, if the page does not fill the screen,
		 * it will not stick to the bottom. In that case, a spacer
		 * like this is needed, when the page is too short:
		 * 
		 * <div style="margin-top:10em;margin-bottom:10em" />
		 */
		f.block() {
			div(style: "margin-top:20em;margin-bottom:20em")
			div(id: "bottom-sticker") {
				div(class: "bottom-sticker-inner") {
					f.submit(value: _("Save"))
				}
			}
		}
	}
}
