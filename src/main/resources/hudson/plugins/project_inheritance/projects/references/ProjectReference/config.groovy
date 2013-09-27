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

include(AbstractProjectReference, "config")

f.advanced(title: "Expand prioities", align: "left") {
	f.entry(
			field: "parameterPriority",
			title: _("Parameter Order"),
			help: helpRoot + "/ParameterOrder.html") {
		ct.range(default: "0", minValue: "-10", step: "1", maxValue: "10")
	}
	f.entry(
			field: "buildWrapperPriority",
			title: _("Pre-Build Step Order"),
			help: helpRoot + "/BuildWrapperOrder.html") {
		ct.range(default: "0", minValue: "-10", step: "1", maxValue: "10")
	}
	f.entry(
			field: "builderPriority",
			title: _("Build Step Order"),
			help: helpRoot + "/BuilderOrder.html") {
		ct.range(default: "0", minValue: "-10", step: "1", maxValue: "10")
	}
	f.entry(
			field: "publisherPriority",
			title: _("Post-Build Step Order"),
			help: helpRoot + "/PublisherOrder.html") {
		ct.range(default: "0", minValue: "-10", step: "1", maxValue: "10")
	}
	f.entry(
			field: "miscPriority",
			title: _("Misc. Property Order"),
			help: helpRoot + "/MiscOrder.html") {
		ct.range(default: "0", minValue: "-10", step: "1", maxValue: "10")
	}
}
