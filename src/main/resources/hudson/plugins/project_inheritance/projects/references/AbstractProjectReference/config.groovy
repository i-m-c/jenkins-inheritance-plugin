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

import hudson.model.AbstractProject;

import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.filters.IProjectReferenceFilter;

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);


//Check if the parent scope has set the read-only flag
filterKey = "";
isReadOnly = false;

request = Stapler.getCurrentRequest();
if (request != null) {
	//Fetch the read-only flag from the request
	//TODO: Convert this to a proper AJAX Capture based approach later
	o = request.getAttribute("FIELDS_READ_ONLY");
	isReadOnly = (o != null && o instanceof Boolean) ? o : false;
}

// Check if there's a reference filter, if so, add it to the descriptor to
//filter the select box below
try {
	filterKey = UUID.randomUUID().toString();
	descriptor.addReferenceFilter(filterKey, referenceFilter);
} catch (Exception ex) {
	//No filter
}


f.invisibleEntry() {
	f.readOnlyTextbox(default: filterKey, name: "filterKey")
}

/* A different name (targetJob) is given to the select box, to properly work
 * with 'InheritableStringParameterReferenceDefinition' instances, which need to
 * look up this field, but can't properly handle duplicate "QueryParameters".
 * 
 * Do note, that in Java, the field will be read from "name", but in JSON, it
 * will show up as "targetJob" and must be named as such in the
 * @DataBoundConstructor as well as all methods using @QueryParameter.
 */

f.entry(field: "name", title: _("Name")) {
	if (isReadOnly) {
		f.select(default: "", disabled: "disabled", name: "targetJob")
	} else {
		f.select(default: "", name: "targetJob")
	}
}
