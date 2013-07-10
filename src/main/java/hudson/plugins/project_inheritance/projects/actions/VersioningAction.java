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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package hudson.plugins.project_inheritance.projects.actions;

import hudson.model.Action;

import java.util.Map;

public class VersioningAction implements Action {
	public final Map<String, Long> versionMap;
	
	public VersioningAction(Map<String, Long> versionMap) {
		this.versionMap = versionMap;
	}
	
	public String getIconFileName() {
		// This kind of action is not visible
		return null;
	}

	public String getDisplayName() {
		// This kind of action has no name
		return null;
	}

	public String getUrlName() {
		// This kind of action has URL to respond to
		return null;
	}

}
