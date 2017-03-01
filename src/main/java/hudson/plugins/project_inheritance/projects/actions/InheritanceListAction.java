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
package hudson.plugins.project_inheritance.projects.actions;

import java.util.List;

import hudson.model.Action;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.actions.service.InheritanceListService;


/**
 * Action used for showing the list of inherited from, and inherited
 * by jobs for a given job.
 *
 * <p>This action is transient, and associated with one concrete
 * job. It will not have any associated icon/link in the Jenkins left
 * navigation menu. Its purpose is to display a section in the job
 * detail page.</p>
 */
public final class InheritanceListAction implements Action {

	private final InheritanceProject project;
	private final InheritanceListService service;


	/**
	 * @param project The job that we will provide information about.
	 */
	public InheritanceListAction(
			final InheritanceProject project,
			final InheritanceListService service) {
		this.project = project;
		this.service = service;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getIconFileName() {
		return null;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDisplayName() {
		return null;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getUrlName() {
		return null;
	}


	/**
	 * Retrieves the jobs that are the immediate parents of our job.
	 *
	 * <p>This method is intended to be called from within the Jelly
	 * view for this action.</p>
	 *
	 * @return A list with the jobs that are the immediate parents of
	 * our job.
	 */
	public List<InheritanceProject> getParentJobs() {
		List<InheritanceProject> result =
				this.service.getParentJobs(this.project);
		return result;
	}


	/**
	 * Retrieves the jobs that are the immediate descendants of our
	 * job.
	 *
	 * <p>This method is intended to be called from within the Jelly
	 * view for this action.</p>
	 *
	 * @return A list with the jobs that are the immediate descendants
	 * of our job.
	 */
	public List<InheritanceProject> getChildJobs() {
		List<InheritanceProject> result =
				this.service.getChildJobs(this.project);
		return result;
	}
}
