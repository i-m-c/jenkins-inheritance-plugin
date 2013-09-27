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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


//Additional side panel elements for InheritanceProjects

def url = h.getNearestAncestorUrl(request,my)

l.tasks() {
	if(my.getIsTransient() == false) {
		if (my.configurable) {
			if (h.hasPermission(my, my.CONFIGURE)) {
				l.task(
						icon: "images/24x24/setting.png",
						title: _("CompoundCreation"),
						href: url + "/child-job-creation-config") 
			}
		}
		l.task(
				icon: "plugin/project-inheritance/images/48x48/versions.png",
				title: _("Configure versions"),
				href: url + "/showConfigureVersions"
		)
		l.task(
				icon: "plugin/project-inheritance/images/48x48/format-line-spacing-double.png",
				title: _("Show Diff between versions"),
				href: url + "/showDiffOfVersions"
		)
	}
	
	if (my.buildable) {
		l.task(
				icon: "images/24x24/clock.png",
				title: _("Build with specific version"),
				permission: my.BUILD,
				href: url + "/buildSpecificVersion"
		)
	}
	
	l.task(
			icon: "plugin/project-inheritance/images/48x48/BinaryTree.png",
			title: _("Show Parameter derivation"),
			href: url + "/showParameterDerivation"
	)
	
	l.task(
			icon: "plugin/project-inheritance/images/48x48/BinaryTree.png",
			title: _("Show References"),
			href: url + "/showReferencedBy"
	)
	
	l.task(
			icon: "images/24x24/setting.png",
			title: _("Show advanced options"),
			href: url + "/showAdvancedOptions"
	)
}
