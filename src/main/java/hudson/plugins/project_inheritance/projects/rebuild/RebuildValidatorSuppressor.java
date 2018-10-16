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

import hudson.Extension;
import hudson.model.Run;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;

import com.sonyericsson.rebuild.RebuildValidator;


@Extension
public class RebuildValidatorSuppressor extends RebuildValidator {
	private static final long serialVersionUID = -8397781633311893664L;

	/**
	 * Method for acknowledge that another plug-ins wants handle the Rebuild functionality itself.
	 *
	 * @param build Build to use when verifying applicability
	 * @return true if the plug-in provides its own rebuild functionality. E.g. disable the rebuild action.
	 */
	@SuppressWarnings("rawtypes")
	public boolean isApplicable(Run build) {
		if (build instanceof InheritanceBuild) {
			return true;
		}
		return false;
	}
}
