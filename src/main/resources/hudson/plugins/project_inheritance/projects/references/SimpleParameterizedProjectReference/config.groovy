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

include(AbstractProjectReference, "config")

f.advanced(align: "left") {
	f.block() {
		f.nested() {
			div(
					style: "font-weight:bold; border-bottom: 1px solid black; margin-bottom:0.2em; margin-top:0.4em",
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
