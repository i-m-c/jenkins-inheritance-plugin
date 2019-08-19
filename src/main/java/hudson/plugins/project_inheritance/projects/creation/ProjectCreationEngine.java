/**
 * Copyright (c) 2018-2019 Intel Corporation
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
package hudson.plugins.project_inheritance.projects.creation;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.google.common.base.Joiner;

import hudson.BulkChange;
import hudson.Extension;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.ItemListener;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

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
	
	@Extension
	public static class RenameWatcher extends ItemListener {
		@Override
		public void onDeleted(Item item) {
			ListIterator<ProjectTemplate> iter =
					ProjectCreationEngine.instance.getTemplates().listIterator();
			while (iter.hasNext()) {
				ProjectTemplate t = iter.next();
				if (t.getName().equalsIgnoreCase(item.getFullName())) {
					iter.remove();
				}
			}
		}
		
		@Override
		public void onRenamed(Item item, String oldName, String newName) {
			//Apply the rename to the templates, too
			ListIterator<ProjectTemplate> iter =
					ProjectCreationEngine.instance.getTemplates().listIterator();
			while (iter.hasNext()) {
				ProjectTemplate t = iter.next();
				if (t.getName().equalsIgnoreCase(oldName)) {
					iter.set(new ProjectTemplate(
							newName, t.getShortDescription()
					));
				}
			}
			
			try {
				ProjectCreationEngine.instance.save();
			} catch (IOException e) {}
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
	
	protected boolean disallowVanillaArchiver = false;
	
	protected boolean enableCreation = false;
	protected boolean triggerOnChange = true;
	protected boolean triggerOnStartup = true;
	protected boolean copyOnRename = true;
	protected boolean enableApplyButton = true;
	
	/**
	 * TODO: Remove after rollout of 19.05.01
	 * @deprecated since 19.05.01
	 */
	protected transient final Boolean enableLeakedLogCleaner = null;
	
	protected List<String> acceptableErrorUrls;
	
	protected RenameRestriction renameRestriction = RenameRestriction.ALLOW_ALL;
	
	protected transient Map<String, String> lastCreationState =
			new ConcurrentHashMap<String, String>();
	
	protected final transient Executor creationExecutor =
			Executors.newFixedThreadPool(1);
	
	/**
	 * The list of jobs to be used as templates for the {@link ProjectWizard}.
	 */
	protected List<ProjectTemplate> templates = 
			new LinkedList<>();
	
	
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
	
	public Object readResolve() {
		if (this.templates == null) {
			this.templates = new LinkedList<>();
		}
		return this;
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
	
	@RequirePOST
	public synchronized void doConfigSubmit(
			StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, FormException {
		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		
		try (BulkChange bc = new BulkChange(this)) {
			//We assume that reading the configuration is valid
			boolean result = true;
			
			JSONObject json = req.getSubmittedForm();
			
			//Reading the state of the checkboxes
			try {
				this.disallowVanillaArchiver = json.getBoolean("disallowVanillaArchiver");
			} catch (JSONException ex) {
				this.disallowVanillaArchiver = false;
			}
			
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
				this.enableApplyButton = json.getBoolean("enableApplyButton");
			} catch (JSONException ex) {
				this.enableApplyButton = true;
			}
			
			try {
				this.renameRestriction = RenameRestriction.valueOf(
						json.getString("renameRestriction")
				);
			} catch (JSONException ex) {
				this.renameRestriction = RenameRestriction.ALLOW_ALL;
			}
			
			try {
				List<String> acceptUrls = new LinkedList<>();
				for (String line : json.getString("acceptableErrorUrls").split("\n")) {
					line = line.trim();
					if (StringUtils.isNotBlank(line)) {
						acceptUrls.add(line);
					}
				}
				this.acceptableErrorUrls = acceptUrls;
			} catch (JSONException ex) {
				//Not null, because null would then return a default list, not an empty one
				this.acceptableErrorUrls = Collections.emptyList();
			}
			
			//Then, we read the hetero-list of creation classes and create them
			Object obj = json.get("creationClasses");
			if (obj != null) {
				List<CreationClass> refs =
						CreationClass.DescriptorImpl.newInstancesFromHeteroList(
						req, obj, getCreationClassesDescriptors()
				);
				this.creationClasses.clear();
				this.creationClasses.addAll(refs);
			} else {
				this.creationClasses.clear();
			}
			
			
			//Read the matings config
			obj = json.get("matings");
			if (obj != null) {
				List<CreationMating> mates =
						CreationMating.DescriptorImpl.newInstancesFromHeteroList(
						req, obj, getMatingDescriptors()
				);
				
				//Removing doubles
				HashMap<String, CreationMating> unifier =
						new HashMap<String, ProjectCreationEngine.CreationMating>();
				for (CreationMating mating : mates) {
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
			} else {
				this.matings.clear();
			}
			
			
			// Read the templates config
			obj = json.get("templates");
			if (obj != null) {
				List<ProjectTemplate> templates = req.bindJSONToList(ProjectTemplate.class, obj);
				this.templates.clear();
				this.templates.addAll(templates);
				
				//Loop over the templates and remove duplicates
				HashSet<String> seen = new HashSet<>();
				Iterator<ProjectTemplate> iter = this.templates.iterator();
				while (iter.hasNext()) {
					String name = iter.next().getName();
					if (seen.add(name) == false) {
						//The name was already seen before, it is safe to drop this
						iter.remove();
						continue;
					}
				}
			} else {
				this.templates.clear();
			}
			
			if (result) {
				// go to the top management page
				rsp.sendRedirect(req.getContextPath()+"/manage");
			} else {
				// back to config
				rsp.sendRedirect("project_creation");
			}
			
			//And commit the change, so that save() is called
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
		 * @param parents the list of parents in their natural order
		 * @param variance the unique variance to be used for the compound
		 * @param reportMap the map used to report creation success/fail
		 * @param auth the authentication to use for creation
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
			this.variance = (variance != null) ? variance.trim() : null;
			this.reportMap = reportMap;
			this.auth = auth;
		}
		
		@SuppressWarnings("unchecked")
		public void run() {
			ProjectCreationEngine pce = ProjectCreationEngine.instance;
			LinkedList<String> parNames = new LinkedList<String>();
			List<String> classes = new LinkedList<String>();
			
			//Fetch the name & class of each parent
			for (InheritanceProject ip : this.parents) {
				if (ip == null) { continue; }
				String pName = ip.getFullName();
				String cName = ip.getCreationClass();
				if (pName == null || cName == null || cName.isEmpty()) { continue; }
				parNames.add(pName);
				classes.add(cName);
			}
			
			//Generate the full name of the child to be created
			String pName = generateNameFor(variance, parNames);
			
			
			//Check if the mating that is supposed to be created is allowed
			if (classes.size() < 2 || classes.contains(null)) {
				this.reportMap.put(pName, "At least one parent is not a member of a class");
				return;
			}
			
			String leftClass = classes.get(0);
			String rightClass = classes.get(1);
			boolean isValidMate = false;
			for (CreationMating mate : pce.getMatings()) {
				if (!mate.firstClass.equals(leftClass)) {
					continue;
				}
				if (!mate.secondClass.equals(rightClass)) {
					continue;
				}
				isValidMate = true;
				break;
			}
			if (!isValidMate) {
				this.reportMap.put(pName, String.format(
						"Parents have incompatible classes: %s<->%s",
						leftClass, rightClass
				));
				return;
			}
			
			//Fetch the map of already existing projects
			Map<String, TopLevelItem> itemMap =
					Jenkins.get().getItemMap();
			
			SecurityContext oldAuthContext = null;
			
			//Everything following this must be serialised via the global lock
			//as it sets and accesses certain global fields
			
			lock.lock();
			try {
				//Apply the ACLs from the auth object given to us
				if (auth != null) {
					oldAuthContext = ACL.impersonate(this.auth);
				}
				//Check if the job to be generated already exists
				if (itemMap.containsKey(pName)) {
					reportMap.put(pName, "Job already exists");
					return;
				}
				
				//Check if we've tried to create such a project already
				if (reportMap.containsKey(pName)) { return; }
				
				//Make sure that the IP Descriptor knows that we want to create
				//the job as transient
				InheritanceProject.DESCRIPTOR.addProjectToBeCreatedTransient(pName);
				
				TopLevelItem item = null;
				InheritanceProject ip = null;
			
				//Use that constructor to create a suitable transient job
				item = Jenkins.get().createProject(
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
				
				
				//Add the references generated above
				int i = 0;
				for (InheritanceProject par : this.parents) {
					if (par == null) { continue; }
					ip.addParentReference(
							new ProjectReference(par.getFullName(), --i)
					);
				}
				
				//Set the variance, if any
				if (variance != null && !variance.isEmpty()) {
					ip.setVarianceLabel(variance);
				}
				
				//Check whether the newly created job is sane and buildable
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
				
				//Load the additional properties
				if (ip != null) {
					ip.onLoad(ip.getParent(), ip.getFullName());
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
	
	/**
	 * Triggers creation of automatically generated projects; if enabled.
	 * <p>
	 * Note: This does not check if the user has enough permissions to create
	 * jobs. It is up to the caller to ensure that.
	 * 
	 * @return a map containing the results of the generation with entries:
	 *         (project-name, human-readable-result)
	 */
	public synchronized Map<String, String> triggerCreateProjects() {
		//Clear the old creation state report
		ConcurrentHashMap<String, String> reportMap =
				new ConcurrentHashMap<String, String>();
		
		if (!enableCreation) {
			return reportMap;
		}
		
		long startTime = System.currentTimeMillis();
		
		//Create a fixed thread-pool to create/load projects for us
		//It uses max(1, n-1) threads; where n is the number of CPU cores
		int numExecs = Runtime.getRuntime().availableProcessors();
		if (numExecs > 1) { numExecs -= 1; }
		ExecutorService exec = Executors.newFixedThreadPool(numExecs);
		
		LinkedList<Future<Boolean>> futures = new LinkedList<Future<Boolean>>();
		
		//Fetch the map of all project names and their actual objects
		Map<String, InheritanceProject> pMap =
				InheritanceProject.getProjectsMap();
		
		//Iterate through that mapping to get compatible classes
		for (InheritanceProject firstP : pMap.values()) {
			//Get & iterate over the compatible matings defined on that project
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
				
				//Now, starting the asynchronous task of creating this new job
				//DO NOTE: It will check if the creation is valid at all, and
				//         refuse to create something if it's not valid.
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
	@RequirePOST
	public void doCreateProjects(StaplerRequest req, StaplerResponse rsp) {
		try {
			//Only permit running this when the user has jobCreate rights
			if (!Jenkins.get().hasPermission(Job.CREATE)) {
				rsp.sendError(SC_FORBIDDEN, "User lacks the Job.CREATE permission");
				return;
			}
			
			//Trigger the project creation
			this.lastCreationState = this.triggerCreateProjects();
			
			Jenkins j = Jenkins.get();
			String rootURL = j.getRootUrlFromRequest();
			
			/* Redirect to the status page for job creation.
			 * 
			 * Note: If the URL is called via a "task" tag, this will
			 * not work. The browser will do the request, but show nothing to
			 * the user. Instead, a custom "redirectTask" tag needs to be used.
			 * 
			 * If the user arrived via browser built-in "retry with POST"
			 * feature it should also work, even without JavaScript magic,
			 * because of the POST-REDIRECT-GET logic.
			 * 
			 * See: https://en.wikipedia.org/wiki/Post/Redirect/Get
			 */
			rsp.sendRedirect(
					rootURL + "/project_creation/showCreationResults"
			);
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
		//TODO: When a product definition gets deleted, all jobs that
		//reference it should be informed about this loss.
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
	
	
	/**
	 * @return whether or not the results of some expensive reflection calls
	 * ({@link Class#isAssignableFrom(Class)}) should be cached.
	 */
	public boolean getEnableReflectionCaching() {
		//TODO: Make this configurable
		return true;
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

	public boolean getEnableApplyButton() {
		return this.enableApplyButton;
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
	
	public boolean getDisallowVanillaArchiver() {
		return disallowVanillaArchiver;
	}
	
	/**
	 * Returns the list of error URLs that are safe to ignore when checking the
	 * validation fields of the job configuration files.
	 * 
	 * See: resources/hudson/plugins/project_inheritance/projects/InheritanceProject/adjunct/detectValidationErrors.js
	 * 
	 * @return the value of {@link #getAcceptableErrorUrlsList()} joined with
	 * 		'\n' as the separator.
	 */
	public String getAcceptableErrorUrls() {
		return Joiner.on('\n').join(this.getAcceptableErrorUrlsList());
	}
	
	/**
	 * Returns the list of error URLs that are safe to ignore when checking the
	 * validation fields of the job configuration files.
	 * 
	 * 
	 * @return the list of acceptable URLs. May be empty but never null.
	 * 		Returns a default list when the backing field is null.
	 */
	public List<String> getAcceptableErrorUrlsList() {
		if (this.acceptableErrorUrls == null) {
			//Use a default list with 2 "known-bad" plugins that show warnings as errors
			this.acceptableErrorUrls = new LinkedList<>(Arrays.asList(
					"hudson.tasks.ArtifactArchiver",
					"hudson.tasks.junit.JUnitResultArchiver"
			));
		}
		return this.acceptableErrorUrls;
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
	
	public boolean isFirstInCreationMating(String creationClass) {
		for (CreationMating creationMating : this.matings) {
			if (creationMating.firstClass != null && creationMating.firstClass.equals(creationClass)) {
				return true;
			}
		}
		return false;
	}
	
	public static List<Descriptor<CreationMating>> getMatingDescriptors() {
		if (matingDescriptors == null) {
			matingDescriptors =
					new LinkedList<Descriptor<CreationMating>>();
			matingDescriptors.add(CreationMating.DESCRIPTOR);
		}
 		return matingDescriptors;
 	}
	
	/**
	 * @return the list of templates. May be empty, but never null
	 */
	public List<ProjectTemplate> getTemplates() {
		if (this.templates == null) {
			return Collections.emptyList();
		} else {
			return this.templates;
		}
	}
	
	
	protected File getConfigFile() {
		File root = Jenkins.get().getRootDir();
		return new File(root, "config_project_creation.xml");
	}

	
	public Map<String, String> getLastCreationState() {
		return lastCreationState;
	}
	
	
	// === PROPERTY SETTERS - USE WITH CARE ===
	
	public void setEnableCreation(boolean enabled) {
		this.enableCreation = enabled;
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
			b.append(variance.trim());
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
		return (ProjectCreationEngineDescriptor) Jenkins.get().getDescriptorOrDie(
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
		
		
		@Override
		public String getDisplayName() {
			//Dummy value; never really called; see class-method above!
			return Messages.ProjectCreationEngine_DisplayName();
		}
		
	}

}
