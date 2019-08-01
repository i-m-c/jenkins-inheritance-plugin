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
package hudson.plugins.project_inheritance.projects.actions;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;


import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.actions.InheritanceListAction;
import hudson.plugins.project_inheritance.projects.actions.service.CachingInheritanceListService;
import hudson.plugins.project_inheritance.projects.actions.service.InheritanceListService;


/**
 * Jenkins extension for creating actions that will be used for
 * displaying the inheritance diagram of jobs of type inheritance
 * project.
 *
 * <p>One instance of this class will be created by automatically
 * created by the Jenkins server at start up time. The Jenkins server
 * will then call the <code>{@link #createFor(AbstractProject)}</code>
 * method for each existing job. That method will return one single
 * <code>{@link InheritanceListAction}</code> instance. Jenkins
 * will then add it as a transient action to the given project.</p>
 */
@Extension
public final class InheritanceListActionFactory
		extends TransientProjectActionFactory {

	private static final Logger log = Logger.getLogger(
			InheritanceListActionFactory.class.toString()
	);

	private final InheritanceListService service =
			new CachingInheritanceListService();

	public InheritanceListActionFactory() {
		log.info(String.format(
				"Extension %s instantiated", this.getClass().getSimpleName()
		));
	}


	/**
	 * Creates and returns one single <code>{@link
	 * InheritanceListAction}</code> that will be added to the
	 * given job.
	 * <p>
	 * The returned <code>{@link InheritanceListAction}</code>
	 * will be added by Jenkins to the given job as a transient
	 * action. Transient actions are not persisted.
	 * </p><p>
	 * The purpose of the returned <code>{@link
	 * InheritanceListAction}</code> is to add a section to the job
	 * detail page with an inheritance diagram in case the job is of
	 * type inheritance project.
	 * </p><p>
	 * This method is automatically called by Jenkins during the
	 * initialisation of a job object.
	 * </p>
	 *
	 * @param target The job for which we are to create our action.
	 *
	 * @return A collection with one single <code>{@link
	 * InheritanceListAction}</code> instance.
	 */
	@Override
	public Collection<? extends Action> createFor(
			@SuppressWarnings("rawtypes") final AbstractProject target
	) {
		Collection<Action> result;
		
		if (target instanceof InheritanceProject) {
			InheritanceProject job = (InheritanceProject) target;
			InheritanceListService service = getService();
			Action action = new InheritanceListAction(job, service);
			result = Collections.singleton(action);

			log.fine(String.format(
					"Added %s to job '%s'",
					InheritanceListAction.class.getSimpleName(),
					job.getFullName()
			));
		} else {
			result = Collections.emptyList();
		}
		return result;
	}


	/**
	 * Returns the default {@link InheritanceListService} used by this factory
	 */
	private InheritanceListService getService() {
		return this.service;
	}
}
