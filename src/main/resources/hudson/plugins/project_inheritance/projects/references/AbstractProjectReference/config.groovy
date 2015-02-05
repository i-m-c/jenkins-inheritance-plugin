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

import java.util.UUID;
import org.kohsuke.stapler.Stapler;

import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.filters.IProjectReferenceFilter;

//Check if the parent scope has set the read-only flag
filterKey = "";
isReadOnly = false;

request = Stapler.getCurrentRequest();
if (request != null) {
	//Fetch the read-only flag from the request
	o = request.getAttribute("FIELDS_READ_ONLY");
	isReadOnly = (o != null && o instanceof Boolean) ? o : false;
	
	//Fetch the naming filter from the request (if any)
	o = request.getAttribute("REFERENCE_FILTER");
	filter = (o != null && o instanceof IProjectReferenceFilter) ? o : null;
	if (filter != null) {
		filterKey = UUID.randomUUID().toString();
		descriptor.addReferenceFilter(filterKey, filter);
	}
}

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);


helpRoot = "/plugin/project-inheritance/help/ProjectReference"

f.invisibleEntry() {
	f.readOnlyTextbox(default: my.name, name: "projectName")
}

f.invisibleEntry() {
	f.readOnlyTextbox(default: filterKey, name: "filterKey")
}

f.entry(field: "name", title: _("Name")) {
	if (isReadOnly) {
		f.select(default: my.name, disabled: "disabled")
	} else {
		f.select(default: my.name)
	}
}
