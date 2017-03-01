/*
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
f = namespace(lib.FormTagLib)
l = namespace(lib.LayoutTagLib)
ct = namespace(lib.CustomTagLib)

p = namespace("/lib/hudson/project")

f.section(title: _("Project Inheritance Options")) {
	f.invisibleEntry(field: "isTransient", title: _("Is Transient Project")) {
		f.checkbox()
		/*
		//Descriptions of invisibleEntries are still rendered!
		f.description(
			"If this is checked, the project will not be stored on disk." +
			" Do note that this flag is read-only. Only projects automatically" +
			" created by the 'Project Creation Engine' have this flag set."
		)
		*/
	}
	f.entry(field: "isAbstract", title: _("Is abstract project")) {
		f.checkbox()
		f.description(
				"If this is checked, the project will be marked as" +
				" abstract and can not	be run directly. While this" +
				" superficially sounds similar to just deactivating" + 
				" the project, it additionally relaxes certain checks" +
				" related to inheritance of values. One example is" +
				" the error that occurs if you've not given a default" +
				" value to a mandatory variable."
		)
	}
}
