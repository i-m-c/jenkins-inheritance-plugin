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
package hudson.plugins.project_inheritance.projects.actions.service;

import java.util.List;

import hudson.plugins.project_inheritance.projects.InheritanceProject;


/**
 * Service that provides information on job inheriting from, and jobs
 * inherited by for a given job.
 *
 * <p>This is a service that provides data to the controller. The
 * controller will in turn pass the data to the view.</p>
 */
public interface InheritanceListService {
	
	/**
	 * Retrieves the immediate parents of the given job.
	 *
	 * @param job The job for which we are to retrieve its parents.
	 *
	 * @return A list with the jobs that are immediate parents of the
	 * given job. Ths list will be ordered alphabetically by job name.
	 */
	List<InheritanceProject> getParentJobs(InheritanceProject job);

	/**
	 * Retrieves the immediate descents of the given job. That is, the
	 * jobs that inherit from the given job.
	 *
	 * @param job The job for which we are to retrieve its children.
	 *
	 * @return A list with the jobs that are the immediate descendants
	 * of the given job. This list will be ordered alphabetically by job
	 * name.
	 */
	List<InheritanceProject> getChildJobs(InheritanceProject job);
}
