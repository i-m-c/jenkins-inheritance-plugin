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

import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);

helpRoot = "/plugin/project-inheritance/help/ProjectReference"

//Check if the parent scope has set the read-only flag
try {
	isReadOnly = Constants.READ_ONLY
} catch (e) {
	isReadOnly = false
}


include(AbstractProjectReference, "config")


f.entry(field: "variance", title: _("Variance")) {
	if (isReadOnly) {
		f.textbox(default: "", readonly:"readonly")
	} else {
		f.textbox(default: "")
	}
	f.description(
			"This field is only used when you specify the same compatibility"
			+ " with another project multiple times."
			+ " Do note that this feature may have been deactivated in the"
			+ " Project Creation Engine, in which case only the first"
			+ " of compatibility defined here will be used and this field will"
			+ " be ignored. This also happens, if the field is left empty on more"
			+ " than one of the multiple mentions."
	)
}


f.entry(field: "assignedLabelString", title: _("Label Expression")) {
	if (isReadOnly) {
		f.textbox(default: "", readonly:"readonly")
	} else {
		f.textbox(default: "")
	}
	f.description(
			"Restricts where this compound can be run. "
			+"If you are configuring existing compound you need to delete it and recreate for the change to take effect."
	)
}


f.advanced(align: "left") {
	f.block() {
		f.nested() {
			div(style: "font-weight:bold; border-bottom: 1px solid black; margin-bottom:0.2em; margin-top:0.4em",
					_("Additional Parameters")
			)
		}
		f.nested() {
			f.hetero_list(
					items: (instance == null) ? null : instance.parameters,
					name: "parameters",
					hasHeader: "true",
					descriptors: h.getParameterDescriptors(),
					addCaption: _("Add parameter")
			)
		}
	}
}
