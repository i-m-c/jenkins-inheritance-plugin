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
package hudson.plugins.project_inheritance.projects.actions.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.actions.service.InheritanceListService;


/**
 * Concrete implementation of <code>InheritanceListService</code> that
 * obtains inheritance data from the job itself.
 * 
 * <p>NOTE: This class will evolve to also generate inheritance
 * diagrams for a given job. At that poing the "Caching" part of this
 * class name will begun to be real.
 */
public final class CachingInheritanceListService
		implements InheritanceListService {

	private static final Logger log = Logger.getLogger(
			CachingInheritanceListService.class.toString()
	);

	/**
	 * Comparator, that orders jobs alphabetically by name in a case-insensitive
	 * manner.
	 */
	private static final Comparator<InheritanceProject> JOB_COMPARATOR =
			new Comparator<InheritanceProject>() {
				@Override
				public int compare(final InheritanceProject p1,
								   final InheritanceProject p2) {
					String s1 = p1.getFullName();
					String s2 = p2.getFullName();
					return s1.compareToIgnoreCase(s2);
				}
			};
	

	public CachingInheritanceListService() {
        log.info(String.format(
        		"Service %s instantiated", this.getClass().getSimpleName()
        ));
	}


	/**
	 * {@inheritDoc}
	 */
	public List<InheritanceProject> getParentJobs(InheritanceProject myJob) {

		long startTime = System.currentTimeMillis();

		List<InheritanceProject> result = myJob.getParentProjects();
		Collections.sort(result, JOB_COMPARATOR);

		long delay = System.currentTimeMillis() - startTime;
		log.fine(String.format(
				"Job '%s' inherits from %d other jobs (%d ms):",
				myJob.getFullName(),
				result.size(),
				delay
		));
		for ( InheritanceProject job : result ) {
			log.fine(String.format("\t%s", job.getFullName()));
		}

		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public List<InheritanceProject> getChildJobs(InheritanceProject myJob) {

		long startTime = System.currentTimeMillis();

		List<InheritanceProject> result = myJob.getChildrenProjects();
		Collections.sort(result, JOB_COMPARATOR);

		long delay = System.currentTimeMillis() - startTime;
		log.fine(String.format(
				"Job '%s' is inherited by %d other jobs (%d ms):",
				myJob.getFullName(),
				result.size(),
				delay
		));
		for ( InheritanceProject job : result ) {
			log.fine(String.format("\t%s", job.getFullName()));
		}

		return result;
	}
}
