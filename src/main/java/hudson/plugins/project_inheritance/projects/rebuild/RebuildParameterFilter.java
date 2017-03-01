/**
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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
package hudson.plugins.project_inheritance.projects.rebuild;

import java.util.List;

import hudson.ExtensionPoint;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import jenkins.model.Jenkins;

/**
 * This extension point allows you, to specify whether a given parameter
 * should be included in a rebuild of a job, or not.
 * <p>
 * It can be used to remove automatically generated parameters, so that the
 * rebuild can progress safely.
 * <p>
 * Each {@link RebuildParameterFilter} is called for each parameter of the
 * previous build, that is about to be rebuilt. If any filter returns false
 * when queried for {@link #isParameterAllowed(ParameterValue)}, the
 * value is not copied into the new build.
 * <p>
 * Do note, that the parameter may still be present -- only that in these cases
 * the default value from the project will be used, as if there were no
 * value from the previous build.
 * <p>
 * Also do note, that the current rebuild implementation filters out all
 * parameters, that are not {@link StringParameterValue}s (or subclasses). This
 * is not guaranteed though, which is why this class accepts generic
 * {@link ParameterValue}s.
 * 
 * @author Martin Schroeder
 *
 */
public abstract class RebuildParameterFilter implements ExtensionPoint {
	
	public RebuildParameterFilter() {
		//Nothing to do
	}
	
	/**
	 * Check if the given parameter value is permitted to be used in a rebuild.
	 * <p>
	 * As soon as one {@link RebuildParameterFilter} denies a value, it is
	 * ignored when generating rebuilds.
	 * <p>
	 * Do note, that the parameter may still be added to the next build, if
	 * the project itself defines it. All that is removed, is the user-defined
	 * value of the parameter from the previous build.
	 * 
	 * @param val the value to verify.
	 * @return true, if the parameter is permitted to be used in a rebuild. False otherwise.
	 */
	public abstract boolean isParameterAllowed(ParameterValue value);
	
	
	/**
	 * Wrapper around {@link #isParameterAllowed(ParameterValue)} that loops
	 * over all filters return by {@link #all()}.
	 * 
	 * @param val the value to verify.
	 * @return true, if the parameter is permitted to be used in a rebuild. False otherwise.
	 */
	public static boolean isParameterAllowedByAll(ParameterValue val) {
		for (RebuildParameterFilter rpf : RebuildParameterFilter.all()) {
			if (!rpf.isParameterAllowed(val)) {
				return false;
			}
		}
		return true;
	}
	
	public static List<RebuildParameterFilter> all() {
		return Jenkins.getInstance().getExtensionList(
				RebuildParameterFilter.class
		);
	}
}
