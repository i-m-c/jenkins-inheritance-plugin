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
package hudson.plugins.project_inheritance.projects.creation;

import static hudson.init.InitMilestone.JOB_LOADED;

import java.util.Map;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import jenkins.model.Jenkins;

@Extension
public class CreationItemListener extends ItemListener {
	private static final Logger log = Logger.getLogger(
			ProjectCreationEngine.class.toString() //Intentional use of the PCE class
	);
	
	
	public CreationItemListener() {
		// Nothing to do
	}
	
	
	public void onCreated(Item item) {
		if (!(item instanceof InheritanceProject)) { return; }
		pce().notifyProjectNew((InheritanceProject)item);
	}
	
	public void onRenamed(Item item, String oldName, String newName) {
		if (!(item instanceof InheritanceProject)) { return; }
		pce().notifyProjectChange((InheritanceProject)item);
	}
	
	public void onUpdated(Item item) {
		if (!(item instanceof InheritanceProject)) { return; }
		pce().notifyProjectChange((InheritanceProject)item);
	}
	
	public void onDeleted(Item item) {
		if (!(item instanceof InheritanceProject)) { return; }
		pce().notifyProjectDelete((InheritanceProject)item);
	}
	
	
	// === JENKINS STARTUP HANDLING ===
	
	public void onLoaded() {
		//This happens shortly before Jenkins has completely loaded; run a
		//last PCE, to absolutely ensure that all jobs are present
		pce().notifyJenkinsStartupComplete();
		
		//And rebuild the job graph
		Jenkins.getInstance().rebuildDependencyGraph();
	}
	
	/**
	 * This function will wait until it <b>thinks</b> all static jobs have
	 * been loaded and then tries to generate the transients before
	 * letting Jenkins attain the {@link InitMilestone#JOB_LOADED} milestone.
	 * <p>
	 * Do note that this is just a time-based heuristic and is the only way
	 * to reliably trigger <b>before</b> Jenkins calls
	 * {@link Queue#init(Jenkins)} and restores aborted Runs from it.
	 * <p>
	 * But since that is unreliable, we trigger the recreation again via
	 * {@link #onJenkinsJobsGuaranteedLoaded()}.
	 */
	@Initializer(before=JOB_LOADED,fatal=false)
	public static void onJenkinsStart() {
		//We must wait until all static jobs have been loaded
		Jenkins j = Jenkins.getInstance();
		
		int currItems = 0;
		while (true) {
			//Sleeping a full second
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				//We were probably told to shut down
				break;
			}
			//Then, we fetch the current map of items (jobs)
			Map<String,TopLevelItem> items = j.getItemMap();
			int nextItems = items.size();
			if (nextItems == currItems) {
				//We assume we're done
				break;
			}
			currItems = Math.max(currItems, nextItems);
		}
		
		//Triggering the automatic loading of transients
		if (ProjectCreationEngine.instance != null) {
			ProjectCreationEngine.instance.notifyJenkinsStartupComplete();
		} else {
			//This should never happen
			log.severe(
				"Issue during loading of transient jobs; PCE not yet initialized!"
			);
		}
	}
	
	/**
	 * This method does the same as {@link #onJenkinsStart()}, except that
	 * it has no reason to wait, since all static Jobs are guaranteed to
	 * have been created by then. Unfortunately, Jenkins has most likely
	 * already called {@link Queue#init(Jenkins)} by this point, so the
	 * other function is also important.
	 * <p>
	 * In other words, this functions ensures the reliable creation of all
	 * transient jobs; whereas the other one tries to promise a reliable
	 * restoration of jobs that were stuck in the previous Queue.
	 */
	@Initializer(after=JOB_LOADED,fatal=false)
	public static void onJenkinsJobsGuaranteedLoaded() {
		//Triggering the automatic loading of transients
		if (ProjectCreationEngine.instance != null) {
			ProjectCreationEngine.instance.notifyJenkinsStartupComplete();
		} else {
			//This should never happen
			log.severe(
				"Issue during loading of transient jobs; PCE not yet initialized!"
			);
		}
		//Now, that all jobs are present; we rebuild the Jenkins job graph
		Jenkins.getInstance().rebuildDependencyGraph();
	}
	
	
	
	// === STATIC HELPER METHODS ===
	
	private static ProjectCreationEngine pce() {
		return ProjectCreationEngine.instance;
	}
}
