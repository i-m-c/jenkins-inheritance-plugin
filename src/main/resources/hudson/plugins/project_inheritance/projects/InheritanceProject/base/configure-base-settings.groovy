/**
 * Copyright (c) 2019 Intel Corporation
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

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;


// Namespaces

f = namespace(lib.FormTagLib)
l = namespace(lib.LayoutTagLib)

p = namespace("/lib/hudson/project")
st = namespace("jelly:stapler")



// Main body

p.config_disableBuild()
p.config_concurrentBuild()


//Add the JDK selection
jdks = app.JDKs
if (jdks.size() > 1) {
	f.entry(title: "JDK", description: _("JDK to be used for this project")) {
		select(name: "jdk",
				class: "setting-input validated",
				checkUrl: "'" + rootURL + "/defaultJDKCheck?value='+this.value"
		) {
			f.option(_("default.value"))
			for (jdk in jdks) {
				f.option(
						selected: (my.JDK != null && jdk.name == my.JDK.name),
						value: jdk.name,
						jdk.name
				)
			}
		}
	}
}

//Retrieve only the labels; either local or fully inherited
if (my.isTransient) {
	lbl = my.getAssignedLabel(IMode.INHERIT_FORCED)
} else {
	lbl = my.getAssignedLabel(IMode.LOCAL_ONLY)
}
if (app.labels.size() > 1 || app.clouds.size() > 0 || (lbl != null && lbl != app.selfLabel)) {
	f.optionalBlock(
			title: _("Restrict where this project can be run"),
			name: "hasSlaveAffinity",
			checked: lbl != null,
			field: "slaveAffinity",
			inline: "true"
	) {
		f.entry(field: "label", title: _("Label Expression")) {
			f.textbox(
					autoCompleteDelimChar: " ",
					value: my.getAssignedLabelString()
			)
		}
	}
}


f.section(title: _("Advanced Project Options")) {
	f.advanced() {
		p.config_quietPeriod()
		p.config_retryCount()
		p.config_blockWhenUpstreamBuilding()
		p.config_blockWhenDownstreamBuilding()
		
		st.include(page: "configure-advanced", optional: "true")
		
		f.entry(field: "displayNameOrNull", title: _("Display Name")) {
			escapedName = h.jsStringEscape(my.name)
			f.textbox(
					checkUrl: "'" + rootURL + "/checkDisplayName?displayName=' + encodeURIComponent(this.value) + '&jobName=' + encodeURIComponent('" + escapedName + "')"
			)
		}
	}
}

p.config_scm()
