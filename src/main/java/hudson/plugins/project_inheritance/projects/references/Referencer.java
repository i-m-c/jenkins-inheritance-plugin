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
package hudson.plugins.project_inheritance.projects.references;

import java.util.Set;

import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;

/**
 * This interface can be added to all {@link BuildWrapper}s, {@link Builder}s and
 * {@link Publisher}s, that reference other {@link AbstractProject}s.
 * <p>
 * By doing so, these classes indicate, that their referenced project will be
 * used somewhere during the build and that it thus needs to be added to the
 * set of jobs that is added to the versioning map, whenever a job is built.
 * 
 * @author Martin Schroeder
 */
public interface Referencer {
	/**
	 * This method returns the set of all job names referenced by this class.
	 * <p>
	 * The job names can point to jobs that do not exist, to allow the
	 * caller to scan for configuration errors of these jobs and emit a
	 * suitable warning.
	 * 
	 * @return a set of project names. May be empty, but never null.
	 */
	public Set<String> getReferencedJobs();
	
}
