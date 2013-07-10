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

package hudson.plugins.project_inheritance.projects.parameters;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.StringParameterValue;

public class InheritableStringParameterValue extends StringParameterValue {
	private static final long serialVersionUID = 8213596823348305910L;

	private boolean mustHaveValueSet = false;
	
	@DataBoundConstructor
	public InheritableStringParameterValue(String name, String value) {
		super(name, value);
	}

	public InheritableStringParameterValue(String name, String value,
			String description) {
		super(name, value, description);
	}

	public void setMustHaveValueSet(boolean mustHaveValueSet) {
		this.mustHaveValueSet = mustHaveValueSet;
	}
	
	public boolean getMustHaveValueSet() {
		return mustHaveValueSet;
	}
	
	public boolean isSane() {
		if (mustHaveValueSet && (this.value == null || this.value.isEmpty())) {
			return false;
		}
		return true;
	}
	
}
