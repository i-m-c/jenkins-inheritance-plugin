/**
 * Copyright (c) 2011-2013, Intel Mobile Communications GmbH
 * 
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
import hudson.BulkChange;
import hudson.Extension;
import hudson.Functions;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Label;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.ItemListener;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * This class encapsulates the properties and actions of the project creation
 * mechanism.
 * <p>
 * Since it is a global mechanism, this is a singleton that can be configured
 * from a central configuration page and accessed from all objects.
 * 
 * @author Martin Schroeder
 */
public class ProjectCreationEngine extends ManagementLink implements Saveable, Describable<ProjectCreationEngine> {
	
	private static final Logger log = Logger.getLogger(
			ProjectCreationEngine.class.toString()
	);
	
	// === STATIC MEMBER CLASSES ===
	
	public static enum RenameRestriction {
		ALLOW_ALL, ALLOW_ADMIN, DISALLOW_ALL;
		
		public String toString() {
			switch(this) {
				case ALLOW_ALL:
					return "Always allow renaming";
				case ALLOW_ADMIN:
					return "Only allow for Admins";
				case DISALLOW_ALL:
					return "Disallow completely";
				default:
					return "N/A";
			}
		}
	}
	
	/**
	 * This class describes the fundamental properties of a creation class type.
	 * <p>
	 * Apart from a name and short description, it also stores certain global
	 * properties of that class of projects and is capable of displaying
	 * certain purely informative data.
	 * <p>
	 * For example, it will calculate how many projects are already using this
	 * class. This allows its deletion to be prevented until all the given
	 * jobs have been reassigned or deleted.
	 * 
	 * @author mhschroe
	 */
	public static class CreationClass
			extends AbstractDescribableImpl<CreationClass> {
		public final String name;
		public final String description;
		
		
		@DataBoundConstructor
		public CreationClass(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		public int getNumberOfProjects() {
			Map<String, LinkedList<InheritanceProject>> map = 
					ProjectCreationEngine.instance.getProjectsByClass();
			try {
				return map.get(this.name).size();
			} catch (NullPointerException ex) {
				return 0;
			}
		}
		
		// == DESCRIPTOR CLASS ===
		@Extension(ordinal = 1000)
		public static final Descriptor<CreationClass> DESCRIPTOR
			= new DescriptorImpl();
		
		public static final class DescriptorImpl extends Descriptor<CreationClass> {
			@Override
			public String getDisplayName() {
				return Messages.CreationClass_DisplayName();
			}
			
			@Override
			public CreationClass newInstance(
					StaplerRequest req, JSONObject formData) throws FormException {
				CreationClass ref = super.newInstance(req, formData);
				return ref;
			}
			
		}
	}
	
	/**
	 * This class references two creation class names and marks them as to be
	 * mated by this creation engine.
	 * 
	 * @author mhschroe
	 */
	public static class CreationMating
			extends AbstractDescribableImpl<CreationMating> {
		public final String firstClass;
		public final String secondClass;
		
		public final String description;
		
		
		@DataBoundConstructor
		public CreationMating(String firstClass, String secondClass, String description) {
			this.firstClass = firstClass;
			this.secondClass = secondClass;
			this.description = description;
		}
		
		
		// == DESCRIPTOR CLASS ===
		@Extension(ordinal = 1000)
		public static final Descriptor<CreationMating> DESCRIPTOR
			= new DescriptorImpl();
		
		public static final class DescriptorImpl extends Descriptor<CreationMating> {
			@Override
			public String getDisplayName() {
				return Messages.CreationMating_DisplayName();
			}
			
			@Override
			public CreationMating newInstance(
					StaplerRequest req, JSONObject formData) throws FormException {
				CreationMating ref = super.newInstance(req, formData);
				return ref;
			}
			
			public FormValidation doCheckFirstClass(
					@QueryParameter String firstClass,
					@QueryParameter String secondClass) {
				//Checking if the two names conflict
				if (firstClass.isEmpty() || secondClass.isEmpty()) {
					return FormValidation.error(
							"You need to specify two valid class names."
					);
				} else if (firstClass.equals(secondClass)) {
					return FormValidation.error(
							"You may not mate a type with itself."
					);
				}
				return FormValidation.ok();
			}
			
			public FormValidation doCheckSecondClass(
					@QueryParameter String firstClass,
					@QueryParameter String secondClass) {
				return doCheckFirstClass(firstClass, secondClass);
			}
			
			public FormValidation doCheckNumberOfMates(
					@QueryParameter("firstClass") String firstClass,
					@QueryParameter("secondClass") String secondClass) {
				int numOfMates =
						ProjectCreationEngine.instance.getNumOfMates(
								firstClass, secondClass
						);
				
				String msg = "Will generate " + numOfMates + " mated projects";
				if (numOfMates > 0) {
					return FormValidation.okWithMarkup(msg);
				} else { 
					return FormValidation.error(msg);
				}
			}
			
			public ListBoxModel doFillFirstClassItems() {
				ListBoxModel names = new ListBoxModel();
				for (CreationClass cl : ProjectCreationEngine.instance.creationClasses) {
					names.add(cl.name);
				}
				return names;
			}
			
			public ListBoxModel doFillSecondClassItems() {
				return this.doFillFirstClassItems();
			}
		}
	}
	
	/**
	 * Restores the queue content during the start up.
	 */
	@Extension
	public static class JenkinsStartupCompleteListener extends ItemListener {
		public void onLoaded() {
			//Does nothing; purely decorative; may acquire a use later on
		}
		
		/**
		 * This function will wait until it <b>thinks</b> all static jobs have
		 * been loaded and then tries to generate the transiens before
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
			//Now, that all jobs are present; we rebuild the Jenkins job
			//dependency graph
			Jenkins.getInstance().rebuildDependencyGraph();
		}
	}
	
	@Deprecated
	public static enum TriggerInheritance {
		INHERIT, NO_INHERIT_WARN, NO_INHERIT_NO_WARN;
		
		public String toString() {
			switch (this) {
				case INHERIT:
					return "Inherit triggers";
				case NO_INHERIT_WARN:
					return "Do not inherit triggers, but print notification";
				case NO_INHERIT_NO_WARN:
					return "Do not inherit triggers, do not print notification";
				default:
					return "N/A";
			}
		}
	}

	
	// === STATIC MEMBER FIELDS ===
	
	/**
	 * The singleton instance used throughout a Jenkins-run. As such, this
	 * field is created by Jenkins through the Extension annotation during
	 * startup.
	 * Do note that as this is a Singleton accessible through a static field,
	 * the {@link Extension} annotation must be on THIS field.
	 */
	@Extension(ordinal=100)
	public static final ProjectCreationEngine instance = new ProjectCreationEngine();
		
	protected static LinkedList<Descriptor<CreationClass>> creationClassesDescriptors =
			null;
	
	protected static LinkedList<Descriptor<CreationMating>> matingDescriptors =
			null;
	
	
	
	// === MEMBER FIELDS ===	
	
	protected LinkedList<CreationClass> creationClasses =
			new LinkedList<CreationClass>();
	
	protected LinkedList<CreationMating> matings =
			new LinkedList<ProjectCreationEngine.CreationMating>();
	
	protected boolean enableCreation = false;
	protected boolean triggerOnChange = true;
	protected boolean triggerOnStartup = true;
	protected boolean copyOnRename = true;
	protected String magicNodeLabelForTesting = null;
	protected boolean unescapeEqualsCharInParams = false; 
	
	protected RenameRestriction renameRestriction = RenameRestriction.ALLOW_ALL;
	
	/**
	 * This field is only present for one version; as such it is immediately
	 * marked as deprecated. It basically tells the system whether or not to
	 * use inheritance for triggers, or not.
	 * @deprecated only used for one version, to ease transition to trigger inheritance
	 */
	protected TriggerInheritance triggerInheritance = TriggerInheritance.NO_INHERIT_WARN;
	
	
	//This value is deprecated; as it is nowadays assumed to be always true
	@Deprecated
	protected transient boolean allowMultipleCreation = true;
	
	protected transient Map<String, String> lastCreationState =
			new ConcurrentHashMap<String, String>();
	
	protected final transient Executor creationExecutor =
			Executors.newFixedThreadPool(1);
	
	// === CONSTRUCTORS ===
	
	private ProjectCreationEngine() {
		if (instance != null) {
			throw new IllegalStateException(
				"Not allowed to create a second instance of ProjectCreationEngine"
			);
		}
		
		//Checking if there is a configuration file to load
		XmlFile xml = new XmlFile(Jenkins.XSTREAM, this.getConfigFile());
		if (xml.exists()) {
			try {
				//Deserialising from disk
				Object obj = xml.read();
				if (obj instanceof ProjectCreationEngine) {
					//And finally copying the details
					ProjectCreationEngine pce = (ProjectCreationEngine) obj;
					boolean success = this.copyFrom(pce);
					if (!success) {
						log.severe("Could not use PCE configuration loaded from disk");
					}
				}
			} catch (IOException e) {
				log.severe("Could not read PCE configuration from disk: " + e.toString());
			}
		}
	}
	
	private boolean copyFrom(ProjectCreationEngine other) {
		Class<? extends ProjectCreationEngine> cl = this.getClass();
		
		for (Field f : cl.getDeclaredFields()) {
			//Checking if it's a non-loaded field
			if (f.getName().equals("instance")) {
				continue;
			} else if (f.getName().equals("createdClassesDescriptors")) {
				continue;
			} else if (f.getName().equals("matingDescriptors")) {
				continue;
			}
			
			//Checking if it's a field actually derived from us
			if (f.isSynthetic()) {
				//Ignoring synthetic fields introduced by the compiler 
				continue;
			}
			Object otherVal;
			try {
				otherVal = f.get(other);
				f.set(this, otherVal);
			} catch (IllegalArgumentException e) {
				//Do Nothing
			} catch (IllegalAccessException e) {
				//Do nothing
			} catch (ExceptionInInitializerError e) {
				//Do nothing
			} catch (NullPointerException e) {
				//Do nothing
			}
		}
		return true;
	}
	
	
	
	// === CONFIGURATION SUBMISSION AND SAVING ===
	
	public synchronized void save() throws IOException {
		//Checking if we should hold back a change
		if (BulkChange.contains(this)) {
			return;
		}
		//Creating the configuration XML-File to serialise ourselves into
		XmlFile xml = new XmlFile(Jenkins.XSTREAM, this.getConfigFile());
		//And dumping this object to disk
		xml.write(this);
	}
	
	public synchronized void doConfigSubmit(
			StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, FormException {
		BulkChange bc = new BulkChange(this);
		try {
			//We assume that reading the configuration is valid
			boolean result = true;
			
			JSONObject json = req.getSubmittedForm();
			
			//Reading the state of the checkboxes
			try {
				this.enableCreation = json.getBoolean("enableCreation");
			} catch (JSONException ex) {
				this.enableCreation = false;
			}
			
			try {
				this.triggerOnChange = json.getBoolean("triggerOnChange");
			} catch (JSONException ex) {
				this.triggerOnChange = false;
			}
			
			try {
				this.triggerOnStartup = json.getBoolean("triggerOnStartup");
			} catch (JSONException ex) {
				this.triggerOnStartup = false;
			}
			
			try {
				this.copyOnRename = json.getBoolean("copyOnRename");
			} catch (JSONException ex) {
				this.copyOnRename = false;
			}
			
			try {
				this.magicNodeLabelForTesting = json.getString("magicNodeLabelForTesting");
			} catch (JSONException ex) {
				this.magicNodeLabelForTesting = null;
			}
			
			try {
				this.unescapeEqualsCharInParams = json.getBoolean("unescapeEqualsCharInParams");
			} catch (JSONException ex) {
				this.unescapeEqualsCharInParams = false;
			}
			
			try {
				this.renameRestriction = RenameRestriction.valueOf(
						json.getString("renameRestriction")
				);
			} catch (JSONException ex) {
				this.renameRestriction = RenameRestriction.ALLOW_ALL;
			}
			
			try {
				this.triggerInheritance = TriggerInheritance.valueOf(
						json.getString("triggerInheritance")
				);
			} catch (JSONException ex) {
				this.triggerInheritance = TriggerInheritance.NO_INHERIT_WARN;
			}
			
			//Then, we read the hetero-list of creation classes and create them
			try {
				Object obj = json.get("creationClasses");
				if (obj == null) {
					throw new JSONException("No such key: creationClasses");
				}
				List<CreationClass> refs = 
						CreationClass.DescriptorImpl.newInstancesFromHeteroList(
						req, obj, getCreationClassesDescriptors()
				);
				this.creationClasses.clear();
				this.creationClasses.addAll(refs);
			} catch (JSONException ex) {
				this.creationClasses.clear();
			}
			
			//And do the same with the matings
			try {
				Object obj = json.get("matings");
				if (obj == null) {
					throw new JSONException("No such key: matings");
				}
				List<CreationMating> refs =
						CreationMating.DescriptorImpl.newInstancesFromHeteroList(
						req, obj, getMatingDescriptors()
				);
				
				//Removing doubles
				HashMap<String, CreationMating> unifier =
						new HashMap<String, ProjectCreationEngine.CreationMating>();
				for (CreationMating mating : refs) {
					if (mating.firstClass == null ||
							mating.firstClass.isEmpty() ||
							mating.secondClass == null ||
							mating.secondClass.isEmpty()) {
						continue;
					}
					String key =
							"first:" + mating.firstClass +
							",second:" + mating.secondClass;
					unifier.put(key, mating);
				}
				
				this.matings.clear();
				this.matings.addAll(unifier.values());
			} catch (JSONException ex) {
				this.matings.clear();
			}
			
			if (result) {
				// go to the top management page
				rsp.sendRedirect(req.getContextPath()+"/manage");
			} else {
				// back to config
				rsp.sendRedirect("project_creation");
			}
		} finally {
			bc.commit();
		}
	}
	
	
	// === ACTION HANDLERS ===
	
	private static class ProjectDerivationRunner implements Runnable {
		private final InheritanceProject[] parents;
		private final String variance;
		private final Map<String, String> reportMap;
		private final Authentication auth;
		
		/**
		 * This lock is necessary to avoid deadlocks due to Java synchronization
		 * on certain operations on projects -- like deleting them.
		 */
		private static final ReentrantLock lock = new ReentrantLock();
		
		
		/**
		 * Creates a runner that creates a single output project that inherits
		 * its parameters from the given projects.
		 * 
		 * @param parents
		 * @param second
		 * @param variance
		 * @param reportMap
		 * @param auth
		 */
		public ProjectDerivationRunner(
				InheritanceProject[] parents,
				String variance,
				Map<String, String> reportMap,
				Authentication auth) {
			if (parents == null || parents.length <= 0) {
				throw new IllegalArgumentException(
					"You must offer at least one parent to create a new derived project."
				);
			}
			this.parents = parents;
			this.variance = variance;
			this.reportMap = reportMap;
			this.auth = auth;
		}
		
		@SuppressWarnings("unchecked")
		public void run() {
			//Generating the name for the new project
			LinkedList<String> parNames = new LinkedList<String>();
			for (InheritanceProject ip : this.parents) {
				if (ip == null) { continue; }
				parNames.add(ip.getName());
			}
			String pName = generateNameFor(variance, parNames);
			
			//Fetching the map of already existing projects
			Map<String, TopLevelItem> itemMap =
					Jenkins.getInstance().getItemMap();
			
			SecurityContext oldAuthContext = null;
			
			lock.lock();
			try {
				//Applying the ACLs from the auth object given to us
				if (auth != null) {
					oldAuthContext = ACL.impersonate(this.auth);
				}
				//Checking if the job to be generated already exists
				if (itemMap.containsKey(pName)) {
					reportMap.put(pName, "Job already exists");
					return;
				}
				
				//Checking if we've tried to create such a project already
				if (reportMap.containsKey(pName)) { return; }
				
				//Making sure that the IP Descriptor knows that we want to create
				//the job as transient
				InheritanceProject.DESCRIPTOR.addProjectToBeCreatedTransient(pName);
				
				TopLevelItem item = null;
				InheritanceProject ip = null;
			
				//Then we use that constructor to create a suitable transient job
				item = Jenkins.getInstance().createProject(
						InheritanceProject.DESCRIPTOR, pName 
				);
				if (item == null || !(item instanceof InheritanceProject)) {
					//Invalid job created; we must kill it
					item.delete();
					reportMap.put(
							pName, "Failed, wrong project type generated"
					);
					return;
				}
				ip = (InheritanceProject) item;
				
				
				//Adding the references generated above
				int i = 0;
				for (InheritanceProject par : this.parents) {
					if (par == null) { continue; }
					ip.addParentReference(
							new ProjectReference(par.getName(), --i)
					);
				}
				
				//Setting the variance, if any
				if (variance != null && !variance.isEmpty()) {
					ip.setVarianceLabel(variance);
				}
				
				//Then, we check whether the newly created job is sane and buildable
				boolean isSane = false;
				String insanityMessage = null;
				
				AbstractMap.SimpleEntry<Boolean, String> sanity =
						ip.getParameterSanity();
				
				if (sanity.getKey() == false) {
					insanityMessage =
							"Failed, resulting project has parameter error: " +
							sanity.getValue();
				} else if (ip.hasCyclicDependency()) {
					insanityMessage = "Failed, resulting project has cyclic dependency.";
				} else if (ip.isBuildable() == false) {
					insanityMessage = "Failed, resulting project is not buildable.";
				} else {
					isSane = true;
				}
				
				//Loading the additional properties
				if (ip != null) {
					ip.onLoad(ip.getParent(), ip.getName());
				}
				
				if (!isSane) {
					reportMap.put(pName, insanityMessage);
				} else {
					reportMap.put(pName, "Success");
				}
			} catch (IllegalArgumentException ex) {
				//The name already exist
				reportMap.put(pName, "Job already exists");
			} catch (IOException ex) {
				//This is semi-bad; as the job wasn't created due to a misc. error
				log.warning("Could not generate project " + pName + " due to I/O-Error");
				reportMap.put(pName, "Failed, I/O Error");
			} catch (InterruptedException ex) {
				//This is bad, as the item might not have been deleted!
				log.severe("Created broken project " + pName + " but could not remove it.");
				reportMap.put(pName, "FATAL! Wrong project created; but could not delete");
				
			} finally {
				InheritanceProject.DESCRIPTOR.dropProjectToBeCreatedTransient(
						pName
				);
				if (oldAuthContext != null) {
					SecurityContextHolder.setContext(oldAuthContext);
				}
				lock.unlock();
			}
		}
	}
	
	protected synchronized Map<String, String> triggerCreateProjects() {
		//Clearing the old creation state
		ConcurrentHashMap<String, String> reportMap =
				new ConcurrentHashMap<String, String>();
		
		if (!enableCreation) {
			return reportMap;
		}
		
		long startTime = System.currentTimeMillis();
		
		//Creating a fixed thread-pool to create/load projects for us
		//It uses max(1, n-1) threads; where n is the number of CPU cores
		int numExecs = Runtime.getRuntime().availableProcessors();
		if (numExecs > 1) { numExecs -= 1; }
		ExecutorService exec = Executors.newFixedThreadPool(numExecs);
		
		LinkedList<Future<Boolean>> futures = new LinkedList<Future<Boolean>>();
		
		//First, we fetch the map of all project names and their actual objects
		Map<String, InheritanceProject> pMap =
				InheritanceProject.getProjectsMap();
		
		//We iterate through that mapping to get compatible classes
		for (InheritanceProject firstP : pMap.values()) {
			// Iterating over the compatible matings
			List<AbstractProjectReference> refs = firstP.getCompatibleProjects();
			for (AbstractProjectReference ref : refs) {
				InheritanceProject secondP = ref.getProject();
				if (secondP == null) { continue; }
				
				String variance =
						(ref instanceof ParameterizedProjectReference)
						? ((ParameterizedProjectReference)ref).getVariance()
						: null;
				
				//Creating the mates generated by that pairing
				InheritanceProject[] parents = {
					firstP, secondP
				};
				ProjectDerivationRunner pdr = new ProjectDerivationRunner(
						parents, variance, reportMap, ACL.SYSTEM
				);
				futures.add(exec.submit(pdr, true));
			}
		}
		
		// Then, we wait until all threads have finished
		exec.shutdown();
		Future<Boolean> f = null;
		while (!futures.isEmpty()) {
			try {
				f = futures.pop();
				Boolean result = f.get(10, TimeUnit.SECONDS);
				if (result == null || result.booleanValue() == false) {
					// Retrying that result later
					log.warning("Future object for creation did not return in time");
					futures.addLast(f);
				}
			} catch (InterruptedException ex) {
				log.severe(
					"Transient project creation was interruped!"
				);
			} catch (ExecutionException ex) {
				log.severe(
					"Transient project creation failed!"
				);
				log.severe(ex.toString());
			} catch (TimeoutException ex) {
				// Retrying that result later
				if (f != null) {
					futures.addLast(f);
				}
				//Unsetting f, to preserve anonymity across loops
				f = null;
			}
		}
		
		long endTime = System.currentTimeMillis();
		double diffSecs = ((double) (endTime - startTime)) / 1000;
		
		DecimalFormat form = new DecimalFormat();
		form.setMaximumFractionDigits(3);
		
		log.info(
			"Transient project creation was finished in " + form.format(diffSecs) + " seconds"
		);
		return reportMap;
	}
	
	/**
	 * This starts the job creation and redirects the user to the result page.
	 * <p>
	 * <b>Do NOT call this directly</b>, if not triggered by the user. Instead
	 * call {@link #triggerCreateProjects()}.
	 */
	public void doCreateProjects() {
		//Trigger the project creation
		this.lastCreationState = this.triggerCreateProjects();
		
		Jenkins j = Jenkins.getInstance();
		String rootURL = j.getRootUrlFromRequest();
		
		//Redirect to the status page for job creation
		try {
			StaplerResponse rsp = Stapler.getCurrentResponse();
			rsp.sendRedirect(rootURL + "/project_creation/showCreationResults");
		} catch (IOException ex) {
			//Ignore
		} catch (NullPointerException ex) {
			//Ignore
		}
	}
	
	public void doLeaveCreationResult() {
		//Redirect back to the central managment page
		try {
			Jenkins j = Jenkins.getInstance();
			String rootURL = j.getRootUrlFromRequest();
			StaplerResponse rsp = Stapler.getCurrentResponse();
			rsp.sendRedirect(rootURL + "/manage");
		} catch (IOException ex) {
			//Ignore
		} catch (NullPointerException ex) {
			//Ignore
		}
	}
	
	
	// === NOTIFIER METHODS ===
	
	public void notifyJenkinsStartupComplete() {
		if (enableCreation && triggerOnStartup) {
			//Do note that this is not run in a separate thread, to ensure that
			//Jenkins does not do anything until the first batch of projects
			//are created.
			lastCreationState = this.triggerCreateProjects();
		}
	}
	
	public void notifyProjectChange(InheritanceProject project) {
		//TODO: Instead of a full recreation, do a limited run here
		if (enableCreation && triggerOnChange) {
			//This is run in a separate thread to prevent the GUI from freezing
			creationExecutor.execute(
				new Runnable() {
					public void run() {
						lastCreationState = triggerCreateProjects();
					}
				}
			);
		}
	}
	
	public void notifyProjectNew(InheritanceProject project) {
		//TODO: Instead of a full recreation, do a limited run here
		if (enableCreation && triggerOnChange) {
			//This is run in a separate thread to prevent the GUI from freezing
			creationExecutor.execute(
				new Runnable() {
					public void run() {
						lastCreationState = triggerCreateProjects();
					}
				}
			);
		}
	}
	
	public void notifyProjectDelete(InheritanceProject project) {
		//Placeholder if ever necessary; you should not be able to delete a
		//still referenced project
	}
	
	
	// === PROPERTY GETTERS ===
	
	public String getDisplayName() {
		return Messages.ProjectCreationEngine_DisplayName();
	}

	@Override
	public String getIconFileName() {
		return "/plugin/project-inheritance/images/48x48/BinaryTree.png";
	}

	@Override
	public String getUrlName() {
		return "project_creation";
	}
	
	@Override
	public String getDescription() {
		return Messages.ProjectCreationEngine_Description();
	}
	
	
	public String getMagicNodeLabelForTesting() {
		return this.magicNodeLabelForTesting;
	}
	
	public Label getMagicNodeLabelForTestingValue() {
		String str = this.getMagicNodeLabelForTesting();
		if (str == null) { return null; }
		Set<LabelAtom> nonTestSet = Label.parse(str);
		
		Label out = null;
		for (LabelAtom la : nonTestSet) {
			out = (out == null) ? la : out.and(la);
		}
		return out;
	}
	
	/**
	 * @return whether or not the results of some expensive reflection calls
	 * ({@link Class#isAssignableFrom(Class)}) should be cached.
	 */
	public boolean getEnableReflectionCaching() {
		//TODO: Make this configurable
		return true;
	}
	
	public boolean getUnescapeEqualsCharInParams() {
		return this.unescapeEqualsCharInParams;
	}
	
	public boolean getEnableCreation() {
		return this.enableCreation;
	}
	
	public boolean getTriggerOnChange() {
		return this.triggerOnChange;
	}
	
	public boolean getTriggerOnStartup() {
		return this.triggerOnStartup;
	}
	
	public boolean getCopyOnRename() {
		return this.copyOnRename;
	}

	public RenameRestriction getRenameRestrictionValue() {
		if (this.renameRestriction == null) {
			return RenameRestriction.ALLOW_ALL;
		}
		return this.renameRestriction;
	}
	
	public String getRenameRestriction() {
		return this.getRenameRestrictionValue().name();
	}
	
	public boolean currentUserMayRename() {
		switch (this.getRenameRestrictionValue()) {
			default:
			case ALLOW_ALL:
				return true;
			
			case DISALLOW_ALL:
				return false;
			
			case ALLOW_ADMIN:
				try {
					return Functions.hasPermission(Jenkins.ADMINISTER);
				} catch (IOException e) {
					return false;
				} catch (ServletException e) {
					return false;
				}
		}
	}
	
	
	/**
	 * Always returns true, as disabling this functionality has been deprecated
	 * 
	 * @Deprecated since v1.5, this always returns true.
	 */
	@Deprecated
	public boolean getAllowMultipleCreation() {
		return true;
	}
	
	/**
	 * @see #triggerInheritance
	 * @deprecated
	 */
	public TriggerInheritance getTriggersAreInherited() {
		if (this.triggerInheritance == null) {
			return TriggerInheritance.NO_INHERIT_WARN;
		}
		return this.triggerInheritance;
	}
	
	/**
	 * @see #triggerInheritance
	 * @deprecated
	 */
	public String getTriggerInheritance() {
		return this.getTriggersAreInherited().name();
	}
	
	public List<CreationClass> getCreationClasses() {
		return this.creationClasses;
	}
	
	public static List<Descriptor<CreationClass>> getCreationClassesDescriptors() {
		if (creationClassesDescriptors == null) {
			creationClassesDescriptors =
					new LinkedList<Descriptor<CreationClass>>();
			creationClassesDescriptors.add(CreationClass.DESCRIPTOR);
		}
 		return creationClassesDescriptors;
 	}
	
	public List<CreationMating> getMatings() {
		return this.matings;
	}
	
	public static List<Descriptor<CreationMating>> getMatingDescriptors() {
		if (matingDescriptors == null) {
			matingDescriptors =
					new LinkedList<Descriptor<CreationMating>>();
			matingDescriptors.add(CreationMating.DESCRIPTOR);
		}
 		return matingDescriptors;
 	}
	
	
	protected File getConfigFile() {
		File root = Jenkins.getInstance().getRootDir();
		return new File(root, "config_project_creation.xml");
	}

	
	public Map<String, String> getLastCreationState() {
		return lastCreationState;
	}
	
	

	// === PROJECT CREATION ===
	
	private Map<String, LinkedList<InheritanceProject>> getProjectsByClass() {
		//Preparing the return value
		HashMap<String, LinkedList<InheritanceProject>> classMap = 
				new HashMap<String, LinkedList<InheritanceProject>>();
		
		//Fetching a list of all InheritanceProjects
		Map<String, InheritanceProject> projectMap =
				InheritanceProject.getProjectsMap();
		
		//And categorizing the projects by map
		for (InheritanceProject ip : projectMap.values()) {
			String dc = ip.getCreationClass();
			if (dc == null || dc.isEmpty()) {
				continue;
			}
			if (classMap.containsKey(dc)) {
				classMap.get(dc).push(ip);
			} else {
				LinkedList<InheritanceProject> lst =
						new LinkedList<InheritanceProject>();
				lst.push(ip);
				classMap.put(dc, lst);
			}
		}
		return classMap;
	}
	
	private int getNumOfMates(String firstClass, String secondClass) {
		//Fetching a map matching classes to projects
		Map<String, LinkedList<InheritanceProject>> map = this.getProjectsByClass();
		
		//Checking if the given classes are defined
		if (map.containsKey(firstClass) && map.containsKey(secondClass)) {
			LinkedList<InheritanceProject> firsts = map.get(firstClass);
			LinkedList<InheritanceProject> seconds = map.get(secondClass);
			
			return firsts.size() * seconds.size();
		} else {
			//No mates possible
			return 0;
		}
	}



	// === STATIC HELPER FUNCTIONS ===
	
	public static final String generateNameFor(String variance, List<String> projects) {
		if (projects == null || projects.size() == 0) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		Iterator<String> iter = projects.iterator();
		while (iter.hasNext()) {
			b.append(iter.next());
			if (iter.hasNext()) {
				b.append('_');
			}
		}
		
		if (variance != null && !variance.isEmpty()) {
			b.append('_');
			b.append(variance);
		}
		return b.toString();
	}
	
	public static final String generateNameFor(String variance, String... projects) {
		if (projects == null || projects.length == 0) {
			return null;
		}
		return generateNameFor(variance, Arrays.asList(projects));
	}



	// === DESCRIPTOR FIELDS AND METHODS ===
	
	public Descriptor<ProjectCreationEngine> getDescriptor() {
		return (ProjectCreationEngineDescriptor) Jenkins.getInstance().getDescriptorOrDie(
				ProjectCreationEngine.class
		);
	}
	
	@Extension
	public static class ProjectCreationEngineDescriptor extends Descriptor<ProjectCreationEngine> {

		public ListBoxModel doFillRenameRestrictionItems() {
			ListBoxModel m = new ListBoxModel();
			
			for (RenameRestriction r : RenameRestriction.values()) {
				m.add(r.toString(), r.name());
			}
			
			return m;
		}

		@Deprecated
		public ListBoxModel doFillTriggerInheritanceItems() {
			ListBoxModel m = new ListBoxModel();
			for (TriggerInheritance r : TriggerInheritance.values()) {
				m.add(r.toString(), r.name());
			}
			return m;
		}
		
		@Override
		public String getDisplayName() {
			//Dummy value; never really called; see class-method above!
			return Messages.ProjectCreationEngine_DisplayName();
		}
		
	}

}
