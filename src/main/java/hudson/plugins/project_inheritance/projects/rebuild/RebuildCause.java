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

import hudson.model.Cause.UserIdCause;

/**
 * Wrapper around {@link UserIdCause} with some additonal parameters
 * 
 * @author jagmohan khulbe
 * @version 0.1
 */
public class RebuildCause extends UserIdCause {

	/** identify the build id of the project related to rebuild */
	int rebuildBuildId;

	/** url of the build id */
	String urlRebuildBuildId;

	/**
	 * Creates a new RebuildCause object.
	 * 
	 * @param rebuildBuildId identify the build id of the project related to rebuild
	 * @param urlRebuildBuildId url of the build id url
	 */
	public RebuildCause(int rebuildBuildId, String urlRebuildBuildId) {
		super();
		this.rebuildBuildId = rebuildBuildId;
		this.urlRebuildBuildId = urlRebuildBuildId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getShortDescription() {
		return "[job-dependency] - Rebuild Cause";
	}

	/**
	 * url of the build id
	 * 
	 * @return url of the build id
	 */
	public String getUrlRebuildBuildId() {
		return urlRebuildBuildId;
	}

	/**
	 * identify the build id of the project related to rebuild
	 * 
	 * @return identify the build id of the project related to rebuild
	 */
	public int getRebuildBuildId() {
		return rebuildBuildId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "RebuildCause [rebuildBuildId=" + rebuildBuildId + ", urlRebuildBuildId="
				+ urlRebuildBuildId + "]";
	}
}
