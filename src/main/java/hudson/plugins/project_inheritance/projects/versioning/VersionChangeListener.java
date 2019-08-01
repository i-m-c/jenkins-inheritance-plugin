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
package hudson.plugins.project_inheritance.projects.versioning;

import hudson.ExtensionPoint;

import hudson.model.Item;

import jenkins.model.Jenkins;

import java.util.Collection;

/**
 * This class tracks whenever a change is done on show version related item.
 * 
 * @author Jagmohan Khulbe
 * @since 2015.11.18
 */
public abstract class VersionChangeListener implements ExtensionPoint {

	/**
	 * Called after a job has its configuration updated on show all versions
	 * items
	 * 
	 * @param item
	 *            item that is undergoing the change
	 */
	public abstract void onUpdated(Item item);

	/**
	 * Returns all the registered {@link VersionChangeListener} that construct
	 * classes derived from this abstract base class.
	 * 
	 * @return all the registered {@link VersionChangeListener}
	 */
	public static Collection<VersionChangeListener> all() {
		return Jenkins.get().getExtensionList(VersionChangeListener.class);
	}
}
