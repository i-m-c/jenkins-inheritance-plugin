/**
 * Copyright (c) 2019 Intel Corporation
 * Copyright (c) 2015-2017 Intel Deutschland GmbH
 * Copyright (c) 2011-2015 Intel Mobile Communications GmbH
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

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.StringParameterValue;

/**
 * Deprecated class -- remove following 2.1 public release.
 * <p>
 * Use the normal {@link StringParameterValue} instead.
 * @author mhschroe
 * @deprecated since v2.1
 */
@Deprecated
public class InheritableStringParameterValue extends StringParameterValue {
	private static final long serialVersionUID = 8213596823348305910L;

	@SuppressWarnings("unused")
	private transient boolean mustHaveValueSet = false;
	
	@DataBoundConstructor
	public InheritableStringParameterValue(String name, String value) {
		super(StringUtils.trim(name), value);
	}

	public InheritableStringParameterValue(String name, String value, String description) {
		super(StringUtils.trim(name), value, description);
	}
	
	public Object readResolve() {
		//ISPVs are deprecated -- convert them into normal SPVs
		return new StringParameterValue(this.getName(), this.value);
	}
}
