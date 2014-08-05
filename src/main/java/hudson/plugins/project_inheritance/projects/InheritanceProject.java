/**
 * Copyright (c) 2011-2013, Intel Mobile Communications GmbH
 * Copyright © 2014 Contributor.  All rights reserved.
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

package hudson.plugins.project_inheritance.projects;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterValue;
import hudson.model.TopLevelItem;
import hudson.model.TransientProjectActionFactory;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue.WaitingItem;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.model.queue.SubTaskContributor;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship.Type;
import hudson.plugins.project_inheritance.projects.actions.FilteredTransientActionFactoryHelper;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.TriggerInheritance;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.CreationClass;
import hudson.plugins.project_inheritance.projects.inheritance.InheritanceGovernor;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterReferenceDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty.ScopeEntry;
import hudson.plugins.project_inheritance.projects.rebuild.InheritanceRebuildAction;
import hudson.plugins.project_inheritance.projects.references.SimpleProjectReference;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference.PrioComparator;
import hudson.plugins.project_inheritance.projects.references.ProjectReference.PrioComparator.SELECTOR;
import hudson.plugins.project_inheritance.projects.view.InheritanceViewAction;
import hudson.plugins.project_inheritance.util.Helpers;
import hudson.plugins.project_inheritance.util.ThreadAssocStore;
import hudson.plugins.project_inheritance.util.TimedBuffer;
import hudson.plugins.project_inheritance.util.VersionedObjectStore;
import hudson.plugins.project_inheritance.util.VersionedObjectStore.Version;
import hudson.plugins.project_inheritance.util.svg.Graph;
import hudson.plugins.project_inheritance.util.svg.SVGNode;
import hudson.plugins.project_inheritance.util.svg.renderers.SVGTreeRenderer;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.LogRotator;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.ListBoxModel;
import hudson.widgets.Widget;
import hudson.widgets.HistoryWidget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringEscapeUtils;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.w3c.dom.Document;

import com.sun.mail.util.BASE64EncoderStream;
import com.thoughtworks.xstream.XStreamException;

import difflib.DiffUtils;
import difflib.Patch;

/**
 * A simple base class for all inheritable jobs/projects.
 * 
 * TODO: Create suitable JavaDoc description for this class
 * 
 * @author Martin Schroeder
 *
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class InheritanceProject	extends Project<InheritanceProject, InheritanceBuild>
		implements TopLevelItem, Comparable<Project>, SVGNode {
	
	private static final Logger log = Logger.getLogger(
			InheritanceProject.class.toString()
	);
	
	private static ReentrantLock globalProjectLock = new ReentrantLock();
	
	// === NESTED CLASS AND ENUM DEFINITIONS ===
	
	/**
	 * A very simple enum for the possible relationship states between
	 * to projects.
	 */
	public static class Relationship {
		public enum Type {
			PARENT, MATE, CHILD;
			
			public String toString() {
				switch (this) {
					case PARENT:
						return Messages.InheritanceProject_Relationship_Type_Parent();
					case MATE:
						return Messages.InheritanceProject_Relationship_Type_Mate();
					case CHILD:
						return Messages.InheritanceProject_Relationship_Type_Child();
					default:
						return "N/A";
				}
			}
			
			public String getDescription() {
				switch (this) {
					case PARENT:
						return Messages.InheritanceProject_Relationship_Type_ParentDesc();
					case MATE:
						return Messages.InheritanceProject_Relationship_Type_MateDesc();
					case CHILD:
						return Messages.InheritanceProject_Relationship_Type_ChildDesc();
					default:
						return "N/A";
				}
			}
		}
		public final Type type;
		public final int distance;
		public final boolean isLeaf;
		
		public Relationship(Type type, int distance, boolean isLeaf) {
			this.type = type;
			this.distance = distance;
			this.isLeaf = isLeaf;
		}
	}
	
	public class ParameterDerivationDetails implements Comparable<ParameterDerivationDetails> {
		private final String parameterName;
		private final String projectName;
		private final String detail;
		private final Object defaultValue;
		private int order = 0;
		
		public ParameterDerivationDetails(
				String paramName, String projectName, String detail, Object defaultValue) {
			this.parameterName = paramName;
			this.projectName = projectName;
			this.detail = detail;
			this.defaultValue = defaultValue;
			
			if (this.parameterName == null || this.projectName == null) {
				throw new NullPointerException();
			}
		}
		
		public String getParameterName() {
			return parameterName;
		}
		
		public String getProjectName() {
			return this.projectName;
		}
		
		public String getDetail() {
			return this.detail;
		}
		
		public String getProjectAndDetail() {
			if (this.detail != null && this.detail.length() > 0) {
				return this.projectName + "(" + detail + ")";
			} else {
				return this.projectName;
			}
		}
		
		public String getDefault() {
			if (this.defaultValue == null) {
				return "NULL";
			} else {
				return this.defaultValue.toString();
			}
		}
		
		public int getOrder() {
			return order;
		}
		
		public void setOrder(int order) {
			this.order = order;
		}
		
		public boolean equals(Object other) {
			if (!(other instanceof ParameterDerivationDetails)) {
				return false;
			}
			ParameterDerivationDetails o = (ParameterDerivationDetails) other;
			
			return (
				Helpers.bothNullOrEqual(parameterName, o.parameterName) &&
				Helpers.bothNullOrEqual(projectName, o.projectName) &&
				Helpers.bothNullOrEqual(detail, o.detail) &&
				Helpers.bothNullOrEqual(defaultValue, o.defaultValue)
			);
		}

		public int compareTo(ParameterDerivationDetails o) {
			if (!Helpers.bothNullOrEqual(parameterName, o.parameterName)) {
				return parameterName.compareTo(parameterName);
			}
			if (!Helpers.bothNullOrEqual(projectName, o.projectName)) {
				return projectName.compareTo(projectName);
			}
			if (!Helpers.bothNullOrEqual(detail, o.detail)) {
				return detail.compareTo(detail);
			}
			if (!Helpers.bothNullOrEqual(defaultValue, o.defaultValue)) {
				return defaultValue.toString().compareTo(defaultValue.toString());
			}
			return 0;
		}
	
		
	}
	
	public static enum IMode {
		LOCAL_ONLY, INHERIT_FORCED, AUTO;
	}
	
	/**
	 * A simple enum for the possible notifications a user can get on an inheritance
	 * project configuration page.
	 */
	public static class VersionsNotification {
		boolean isNewest;
		boolean isStable;
		boolean stablesBefore;
		boolean stablesAfter;
		private boolean highlightWarning = false;
		private String notificationMessage;
		public final List<Version> versions;

		public VersionsNotification(boolean isNewest,
									boolean isStable,
									boolean stablesBefore,
									boolean stablesAfter,
									List<Version> versions) {
			this.isNewest = isNewest;
			this.isStable = isStable;
			this.stablesBefore = stablesBefore;
			this.stablesAfter = stablesAfter;
			this.versions = versions;
			if (false == isNewest &&
					true == isStable &&
						true == stablesBefore &&
							true == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_OLDER_STABLE_VERSION();
				highlightWarning = true;
			} else if (false == isNewest &&
						false == isStable &&
							true == stablesBefore &&
								true == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_UNSTABLE_VERSION_BUT_STABLE_AVAILABLE();
				highlightWarning = true;
			} else if (false == isNewest &&
						false == isStable &&
							false == stablesBefore &&
								false == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_UNSTABLE_VERSION_BUT_MORE_UNSTABLE_AVAILABLE();
				highlightWarning = true;
			} else if (false == isNewest &&
						true== isStable &&
							true == stablesBefore &&
								false == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_LATEST_STABLE_VERSION();
			} else if (true == isNewest &&
						false == isStable &&
							false == stablesBefore &&
								false == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_IMPLICIT_STABLE_VERSION();
			} else if (true == isNewest &&
						true == isStable &&
							false == stablesBefore &&
								false == stablesAfter) {
				notificationMessage = Messages.InheritanceProject_VersionsNotification_EDITING_LATEST_STABLE_AND_LAST_VERSION();
			}
		}

		public boolean isNewest() {
			return isNewest;
		}

		public boolean isStable() {
			return isStable;
		}

		public boolean isStablesAfter() {
			return stablesAfter;
		}
		
		public String getNotificationMessage() {
			return notificationMessage;
		}
		
		public List<Version> getVersions() {
			return versions;
		}

		public boolean isHighlightWarning() {
			return highlightWarning;
		}
	}
	
	// === PRIVATE/PROTECTED STATIC FIELDS ===
	
	/**
	 * This buffer is used for objects that don't need to be repeatedly
	 * generated, as long as the configuration of this project or its
	 * parents has not changed.
	 * 
	 * This class ensures that this buffer is cleared whenever the project or
	 * its parents are changed.
	 * 
	 * @see #clearBuffers(InheritanceProject)
	 */
	protected static TimedBuffer<InheritanceProject, String> onInheritChangeBuffer = null;
	
	/**
	 * Same as {@link #onSelfChangeBuffer}, but this buffer is cleared only
	 * when the project itself is changed.
	 * 
	 * @see #clearBuffers(InheritanceProject)
	 */
	protected static TimedBuffer<InheritanceProject, String> onSelfChangeBuffer = null;
	
	/**
	 * Same as {@link #onSelfChangeBuffer}, but this buffer is cleared when
	 * <i>any</i> project is changed or loaded anew.
	 * 
	 * @see #clearBuffers(InheritanceProject)
	 */
	protected static TimedBuffer<InheritanceProject, String> onChangeBuffer = null;
	
	public static Permission VERSION_CONFIG = new Permission(
			PERMISSIONS, "ConfigureVersions",
			Messages._InheritanceProject_VersionsConfigPermissionDescription(),
			Jenkins.ADMINISTER,
			PermissionScope.ITEM
	);
	
	// === PRIVATE/PROTECTED MEMBER FIELDS ===
	
	/**
	 * This field is only valid for transient jobs.
	 * It carries the additional, optional "variance" part as assigned by the
	 * {@link ProjectCreationEngine} during its creation.
	 */
	protected transient String variance = null;
	
	/**
	 * This {@link VersionedObjectStore} is used to version all configurable
	 * properties of this class.
	 * <p>
	 * Do note that transient projects (see {@link #isTransient}) do not do
	 * versioning and always have an empty store. This is because they don't
	 * actually have a configuration of their own.
	 */
	protected transient VersionedObjectStore versionStore = null;
	
	
	// === FIELDS SET BY JELLY FORM TAGS ===
	
	/**
	 * Flag to denote a transient project that is not serialized to disk.
	 */
	protected final boolean isTransient;
	
	/**
	 * Flag to denote a project that can't be built directly; but contrary to
	 * to the {@link #isBuildable()} value, additionally means that certain
	 * checks for inheritance consistence are relaxed.
	 * 
	 * Not hidden, because the getting/setting this value is not checked anyway.
	 */
	public boolean isAbstract = false;
	
	/**
	 * This stores the name of the creation class this project falls in.
	 * @see ProjectCreationEngine
	 */
	protected String creationClass = null;
	
	/**
	 * This list stores references to the projects this project was marked as
	 * being compatible with. For each project referenced in this list, the
	 * {@link ProjectCreationEngine} will try to create a new, transient
	 * project derived from both this project and the referenced one.
	 * It also checks if:
	 * <ol>
	 *   <li>
	 *	 The referenced project is compatible with this project
	 *	 (see {@link #creationClass}),
	 *   </li>
	 *   <li>all parameters are correctly set,</i>
	 *   <li>no circular, diamond or multiple inheritance is created,</li>
	 *   <li>the resulting project is buildable and</li>
	 *   <li>the newly created job does not already exist.</li>
	 * </ol>
	 */
	protected LinkedList<AbstractProjectReference> compatibleProjects =
			new LinkedList<AbstractProjectReference>();
	
	/**
	 * This list stores the adjacency relationship of this project to its
	 * parents. The order of objects is in <i>most</i> cases unimportant, as
	 * the {@link ProjectReference} class itself stores priorization details.
	 * <p>
	 * Do note that any {@link AbstractProjectReference} not derived from
	 * {@link ProjectReference} does not carry priority information and thus
	 * treated as having a priority of 0 everywhere.
	 */
	protected LinkedList<AbstractProjectReference> parentReferences =
			new LinkedList<AbstractProjectReference>();
	
	protected String parameterizedWorkspace;
	
	
	
	// === CONSTRUCTORS AND CONSTRUCTION HELPERS ===
	
	public InheritanceProject(ItemGroup parent, String name, boolean isTransient) {
		super(parent, name);
		this.isTransient = isTransient;
		
		//Creating the static buffers, if necessary
		createBuffers();
		
		this.versionStore = this.loadVersionedObjectStore();
		
		//Generating a new IP causes a refresh of the project map and buffers
		clearBuffers(null);
		
		//And we notify the PCE about the new project, if we're not transient
		if (!isTransient) {
			ProjectCreationEngine.instance.notifyProjectNew(this);
		}
		clearBuffers(null);
	}

	public int compareTo(Project o) {
		return this.name.compareTo(o.getName());
	}
	
	@Override
	public String toString() {
		return this.getName();
	}
	
	
	@Override
	protected Class<InheritanceBuild> getBuildClass() {
		return InheritanceBuild.class;
	}
	
	/**
	 * This method returns a mapping of project names to the
	 * {@link InheritanceProject} objects that carry that name.
	 * 
	 * Do note that this method is using aggressive buffering, to make sure
	 * that repeated access is running in O(1), instead of having to scan
	 * all defined projects again and again.
	 * 
	 * The downside of this, is that you have to call
	 * {@link #forceProjectsMapRefresh()} whenever a change to this mapping
	 * might have occurred.
	 * 
	 * @return a map of names to projects with guaranteed O(1) performance on
	 * repeated read access. The first invocation might run in O(n), where n
	 * is the number of Projects defined in Jenkins.
	 * 
	 * @deprecated Do not use this function anymore, as its caching is
	 * somewhat unreliable in certain situations and it might cause deadlocks
	 * as it iterates over all items registered in Jenkins.
	 */
	public static Map<String, InheritanceProject> getProjectsMap() {
		Object obj = onChangeBuffer.get(null, "getProjectsMap");
		if (obj != null && obj instanceof Map) {
			return (Map) obj;
		}
		
		HashMap<String, InheritanceProject> pMap =
				new HashMap<String, InheritanceProject>();
		for (AbstractProject p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
			// We ensure that we may only inherit from actually inheritable,
			// non-transient projects
			if (p instanceof InheritanceProject) {
				pMap.put(p.getName(), (InheritanceProject) p);
			}
		}
		
		onChangeBuffer.set(null, "getProjectsMap", pMap);
		return pMap;
	}
	
	public static InheritanceProject getProjectByName(String name) {
		TopLevelItem item = Jenkins.getInstance().getItem(name);
		if (item instanceof InheritanceProject) {
			return (InheritanceProject) item;
		}
		return null;
	}
	
	public static void createBuffers() {
		if (onChangeBuffer == null) {
			onChangeBuffer = new TimedBuffer<InheritanceProject, String>();
		}
		if (onSelfChangeBuffer == null) {
			onSelfChangeBuffer = new TimedBuffer<InheritanceProject, String>();
		}
		if (onInheritChangeBuffer == null) {
			onInheritChangeBuffer = new TimedBuffer<InheritanceProject, String>();
		}
	}
	
	public static void clearBuffers(InheritanceProject root) {
		//Ensuring that the buffers are present
		createBuffers();
		
		if (root == null) {
			//Nuke all
			onChangeBuffer.clearAll();
			onSelfChangeBuffer.clearAll();
			onInheritChangeBuffer.clearAll();
			return;
		}
		
		//First clearing the cross-project change buffer
		onChangeBuffer.clearAll();
		//Then clearing the self-change buffer
		onSelfChangeBuffer.clear(root);
		
		//Then we need to clear the inheritable changes for the root and its children
		//Do note that the root MUST be cleared first, as otherwise we may
		//fetch an "unclean" relationship set
		onInheritChangeBuffer.clear(root);
		Map<InheritanceProject, Relationship> relMap = root.getRelationships();
		for (Map.Entry<InheritanceProject, Relationship> e : relMap.entrySet()) {
			//We ignore siblings
			if (e.getValue().type == Relationship.Type.MATE) {
				continue;
			}
			//Otherwise, we clear that project's inheritance buffer
			onInheritChangeBuffer.clear(e.getKey());
		}
	}
	
	
	
	// === PROJECT CONFIGURATION METHODS ===
	
	public void doConfigSubmit(StaplerRequest req,
			StaplerResponse rsp) throws IOException, ServletException, FormException {
		//Check if we're transient; in which case a submit does nothing
		if (this.isTransient) {
			return;
		}
		//FIXME: Possible deadlock due to the interaction between:
		// - this.doConfigSubmit() --> unsynchronized
		// - this.buildDependencyGraph() --> unsynchronized
		// - this.getPublishersList() --> SYNCHRONIZED
		
		//Calling the super implementation; will ultimately call submit()
		super.doConfigSubmit(req, rsp);
	}
	
	/**
	 * This method evaluates the form request created by the Descriptor and
	 * adjusts the properties of this project accordingly.
	 */
	@Override
	protected void submit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, FormException {
		//Check if we're transient; in which case a submit does nothing
		if (this.isTransient) {
			return;
		}
		
		/* A submit might cause property changes across projects, and since
		 * the relationships between projects may change during a reconfigure,
		 * we need to nuke the buffers at three stages:
		 * 
		 * 1.) Before any change -- FULL NUKE
		 * 2.) Before saving versions -- LOCAL NUKE
		 * 3.) After saving versions -- LOCAL NUKE (to get version IDs right)
		 * 4.) After all changes applied -- FULL NUKE
		 */
		clearBuffers(this);
		
		/* Apply the configuration inherited from the superclass.
		 * Do note that the behaviour of that function might change erratically
		 * with each new Jenkins version.
		 * 
		 * One such change is that -- starting with v1.492 -- the BuildWrappers,
		 * Builders and Publisher fields are changed in-place instead of
		 * reassigned. This broke versioning as that causes new fields to be
		 * returned on each call; so that no in-place change can ever work.
		 */
		super.submit(req, rsp);
		
		JSONObject json = req.getSubmittedForm();
		
		if (json.has("isAbstract")) {
			this.isAbstract = json.getBoolean("isAbstract");
		} else {
			this.isAbstract = false;
		}
		
		if (json.has("projects")) {
			Object obj = json.get("projects");
			List<AbstractProjectReference> refs =
				AbstractProjectReference
					.ProjectReferenceDescriptor
					.newInstancesFromHeteroList(
							req, obj, AbstractProjectReference.all()
					);
			if (this.parentReferences != null) {
				this.parentReferences.clear();
			} else {
				this.parentReferences = new LinkedList<AbstractProjectReference>();
			}
			this.parentReferences.addAll(refs);
		} else {
			if (this.parentReferences != null) {
				this.parentReferences.clear();
			}
		}
		
		if(req.hasParameter("parameterizedWorkspace")) {
			this.parameterizedWorkspace = Util.fixEmptyAndTrim(
					req.getParameter("parameterizedWorkspace.directory")
			);
		} else {
			this.parameterizedWorkspace = null;
		}
		
		//Read the class of this project for listing and derivation purposes
		if (json.has("creationClass")) {
			this.creationClass = json.getString("creationClass");
		} else {
			this.creationClass = null;
		}
		
		//LOCAL NUKE before versioning is saved 
		clearBuffers(this);
		
		//After everything was altered, we generate a new version
		if (json.has("versionMessageString")) {
			this.dumpConfigToNewVersion(json.getString("versionMessageString"));
		} else {
			this.dumpConfigToNewVersion();
		}
		
		//LOCAL NUKE after versioning is saved 
		clearBuffers(this);
		
		//And at the very end, we notify the PCE about our changes
		ProjectCreationEngine.instance.notifyProjectChange(this);
	}
	
	@Override
	public void updateByXml(Source source) throws IOException {
		//Instruct the parent to update us
		super.updateByXml(source);
		//Then, save a new version
		
		clearBuffers(this);
		this.dumpConfigToNewVersion("New version uploaded as XML via API/CLI");
		clearBuffers(this);
		
		//Notify the PCE about our changes
		ProjectCreationEngine.instance.notifyProjectChange(this);
	}
	
	@RequirePOST
	public synchronized void doSubmitChildJobCreation(
			StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, FormException {
		//Check if we're transient; in which case a submit does nothing
		if (this.isTransient) {
			return;
		}
		
		JSONObject json = req.getSubmittedForm();
		
		// FULL NUKE before configuration change
		clearBuffers(null);
		
		//Decode the new properties
		if (json.has("properties")) {
			
			//Saving the old properties; except the parameter props and
			//removing them all from the current list
			List<JobProperty<? super InheritanceProject>> oldProps =
					new LinkedList<JobProperty<? super InheritanceProject>>();
			for (JobProperty jobProperty : this.properties) {
				if (!(jobProperty instanceof ParametersDefinitionProperty)) {
					oldProps.add(jobProperty);
				}
			}
			
			//Then, we read the new list from the JSON submission
			DescribableList<JobProperty<?>, JobPropertyDescriptor> newProps =
					new DescribableList<JobProperty<?>, JobPropertyDescriptor>(NOOP);
			newProps.rebuild(
					req,
					json.optJSONObject("properties"),
					JobPropertyDescriptor.getPropertyDescriptors(this.getClass())
			);
			
			//Then, we nuke the list, and add the rebuilt ones
			properties.clear();
			for (JobProperty p : newProps) {
				//Must use this.addProperty() to set correct owner
				this.addProperty(p);
			}
			
			//Finally, add the old properties; we don't need to call
			//this.addProperty(), because the old properties should already be
			//owned by this project
			this.properties.addAll(oldProps);
		}
		
		//Read the compatible projects
		if (json.has("compatibleProjects")) {
			Object obj = json.get("compatibleProjects");
			List<AbstractProjectReference> refs =
					AbstractProjectReference
						.ProjectReferenceDescriptor
						.newInstancesFromHeteroList(
								req, obj, AbstractProjectReference.all()
						);
			if (this.compatibleProjects != null) {
				this.compatibleProjects.clear();
			} else {
				this.compatibleProjects = new LinkedList<AbstractProjectReference>();
			}
			this.compatibleProjects.addAll(refs);
		} else {
			if (this.compatibleProjects != null) {
				this.compatibleProjects.clear();
			}
		}
		
		// FULL NUKE after configuration change
		clearBuffers(null);
		
		//Save data and send the redirect
		this.save();
		
		rsp.sendRedirect(this.getAbsoluteUrl());
		
		//After everything was altered, we generate a new version
		if (json.has("versionMessageString")) {
			this.dumpConfigToNewVersion(json.getString("versionMessageString"));
		} else {
			this.dumpConfigToNewVersion();
		}
		
		//LOCAL NUKE after versioning is saved 
		clearBuffers(this);
		
		//And at the very end, we notify the PCE about our changes
		ProjectCreationEngine.instance.notifyProjectChange(this);
	}
	
	
	@Override
	public void renameTo(String newName) throws IOException {
		if (this.name.equals(newName)) {
			return;
		}
		
		//Check if the user has the permission to rename transient projects
		//Do note that currently, that is impossible via the GUI for everyone anyway
		if (this.getIsTransient() &&
				!ProjectCreationEngine.instance.currentUserMayRename()) {
			throw new IOException(
					"Current user is not allowed to rename transient projects"
			);
		}
		
		//Recording our old project name
		String oldName = this.name;
		
		//Executing the rename
		super.renameTo(newName);
		
		//This means, that we need to force a refresh various buffers
		clearBuffers(this);
		
		//And then fixing all named references
		for (InheritanceProject p : getProjectsMap().values()) {
			for (AbstractProjectReference ref : p.getParentReferences()) {
				if (ref.getName().equals(oldName)) {
					ref.switchProject(this);
				}
			}
			for (AbstractProjectReference ref : p.compatibleProjects) {
				if (ref.getName().equals(oldName)) {
					ref.switchProject(this);
				}
			}
		}
	}
	
	/**
	 * Adds the given {@link ProjectReference} as a parent to this node.
	 * <p>
	 * TODO: The fact that this function is public is really nasty.
	 * Basically, references should only be used through the validated
	 * frontend, or set by the equally validated {@link ProjectCreationEngine}.
	 * <p>
	 * Of course, since the user can just scribble around in the XML -- if
	 * the job isn't transient -- we can't prevent broken references
	 * anyway.
	 * <p>
	 * Do note that this change will not trigger any versioning or saving to
	 * disk. If you use this, you need to know exactly what you're doing; for
	 * example calling this in proper UnitTests.
	 * 
	 * @param ref the reference to add
	 * @param noDuplicateCheck set to false, if no duplication check shall be done.
	 * 		This is only useful in Unit-tests and nowhere else.
	 */
	public void addParentReference(AbstractProjectReference ref, boolean duplicateCheck) {
		//Checking if we already have such a reference
		if (duplicateCheck) {
			for (AbstractProjectReference ourRef : this.getParentReferences()) {
				if (ourRef.getName().equals(ref.getName())) {
					//No point in duplicated references
					return;
				}
			}
		}
		//Otherwise, we can add it. Of course, it might still lead to circular
		//references, or simply and plainly not exist
		this.parentReferences.push(ref);
		
		//And invalidating all caches
		clearBuffers(this);
	}
	
	/**
	 * Wrapper around
	 * {@link #addParentReference(AbstractProjectReference, boolean)} with
	 * duplication check enabled.
	 * 
	 * @param ref the references to add as a parent.
	 */
	public void addParentReference(AbstractProjectReference ref) {
		this.addParentReference(ref, true);
	}
	
	/**
	 * Removes a parent reference.
	 * <p>
	 * Same caveats apply as for {@link #addParentReference(AbstractProjectReference)}.
	 * 
	 * @param name the name of the project for which to remove one parent reference.
	 * @return true, if a parent reference was removed.
	 */
	public boolean removeParentReference(String name) {
		Iterator<AbstractProjectReference> iter = this.parentReferences.iterator();
		while (iter.hasNext()) {
			AbstractProjectReference apr = iter.next();
			if (apr.getName().equals(name)) {
				iter.remove();
				clearBuffers(this);
				return true;
			}
		}
		return false;
	}
	
	public void setVarianceLabel(String variance) {
		if (this.isTransient) {
			this.variance =
					(variance == null || variance.isEmpty())
					? null : variance;
		}
	}
	
	public void setCreationClass(String creationClass) {
		//Checking if such a class exists at all
		if (creationClass == null) { return; }
		for (CreationClass cc : ProjectCreationEngine.instance.getCreationClasses()) {
			if (cc.name.equals(creationClass)) {
				this.creationClass = creationClass;
				break;
			}
		}
	}
	
	/**
	 * This method is called after a save to restructure the dependency graph.
	 * The triggering method is
	 * {@link #doConfigSubmit(StaplerRequest, StaplerResponse)}.
	 * <p>
	 * Unfortunately, it is wholly unsynchronized and can thus lead to a bad
	 * case of deadlock if two rebuilds happen at the same time, explore the
	 * inheritance tree and call a synchronized function.
	 * <p>
	 * The most likely culprit will be {@link #getPublishersList()}.
	 */
	protected void buildDependencyGraph(DependencyGraph graph) {
		//Fetch the global lock of all projects
		//TODO: Use an interruptible lock here?
		globalProjectLock.lock();
		try {
			super.buildDependencyGraph(graph);
		} finally {
			globalProjectLock.unlock();
		}
	}
	
	
	// === SAVING/LOADING METHODS ===
	
	/**
	 * This method serializes this object to offline storage. The default
	 * implementation of Jenkins is XML-File based, but that can be
	 * overridden herein. Of course, if you override the saving method,
	 * you will also have to override the loading method from
	 * {@link InheritanceProject.DescriptorImpl}.
	 */
	@Override
	public synchronized void save() throws IOException {
		//Checking if we're marked as transient; which causes no saving to occur
		if (this.isTransient) { return; }
		
		//Invoking the super constructor to save use
		super.save();
		//TODO: Save the version store to disk here
	}
	
	/**
	 * This method restores transient fields that could not be deserialized.
	 * Do note that there is no guaranteed order of deserialization, so
	 * don't expect other objects to be present, when this method is called.
	 */
	@Override
	public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
		//Creating & clearing buffers, if necessary
		createBuffers();
		clearBuffers(null);
		
		/* We need to create a dummy version store first, as we can't get the
		 * project root directory before super() is executed (as no name is
		 * set yet); but that one needs a version store available to load
		 * certain values reliably without a null pointer access.
		 */
		this.versionStore = new VersionedObjectStore();
		
		//Then loading the elements defined in the parent
		//TODO: What to do if a transient job is attempted to be loaded?
		super.onLoad(parent, name);
		
		//Loading the correct version store
		this.versionStore = this.loadVersionedObjectStore();
		
		//And clearing the buffers again, as a new job with new props is available
		clearBuffers(null);
	}
	
	/**
	 * This method tells this class and all its superclass's which directory
	 * to use for storing stuff.
	 * 
	 * For regular jobs this is the default Jenkins path for jobs ([root]/jobs).
	 * For transient jobs; this is redirected to ([root]/transient_jobs) to
	 * make them more invisible to Jenkins.
	 */
	public File getRootDir() {
		if (!this.isTransient) {
			return super.getRootDir();
		}
		File standardRoot = this.getParent().getRootDir();
		//Otherwise, we alter the last path segment
		String pathSafeJobName = this.getName().replaceAll("[/\\\\]", "_");
		File newRoot = new File(
				standardRoot.getAbsolutePath() +
				File.separator + "transient_jobs" + File.separator +
				pathSafeJobName
		);
		return newRoot;
	}
	
	protected File getVersionFile() {
		//Transient jobs do not have a concept of versions
		if (this.isTransient) {
			return null;
		}
		//TODO: This somewhat assumes, that the file will be compressed
		return new File(this.getRootDir(), "versions.xml.gz");
	}
	
	/**
	 * {@inheritDoc}
	 */
	public File getBuildDir() {
		return super.getBuildDir();
	}
	
	
	
	// === URL-BOUND ACTION METHODS ===
	
	/**
	 * This method displays the configuration as a complete XML dump.
	 * 
	 * @return raw XML string
	 */
	public String doGetConfigAsXML(StaplerRequest req, StaplerResponse rsp) {
		//Check if the user only wants the local data
		String depth = req.getParameter("depth");
		int iDepth = 0;
		if (depth != null && !depth.isEmpty()) {
			try {
				iDepth = Integer.valueOf(depth);
			} catch (NumberFormatException ex) { }
		}
		if (iDepth <= 0) {
			Object obj = onSelfChangeBuffer.get(this, "doGetConfigAsXML");
			if (obj != null && obj instanceof String) {
				return (String) obj;
			}
			String str = Jenkins.XSTREAM2.toXML(this);
			onSelfChangeBuffer.set(this, "doGetConfigAsXML", str);
			return str;
		} else {
			Map<String, InheritanceProject> projs = new LinkedHashMap();
			for (AbstractProjectReference apr : this.getAllParentReferences(SELECTOR.BUILDER)) {
				InheritanceProject ip = apr.getProject();
				if (ip == null) { continue; }
				projs.put(ip.getFullName(), ip);
			}
			//Adding ourselves last
			projs.put(this.getFullName(), this);
			return Jenkins.XSTREAM2.toXML(projs);
		}
	}
	
	/**
	 * This method dumps the full expansion of all parameters (even derived
	 * ones) based on their default values into an XML file.
	 * <p>
	 * If you only want the default values of the last definition of each
	 * parameter, use {@link #doGetParamDefaultsAsXML()}
	 * 
	 * @return raw XML string
	 */
	public String doGetParamExpansionsAsXML() {
		/*
		Object obj = onInheritChangeBuffer.get(this, "doGetParamExpansionsAsXML");
		if (obj != null && obj instanceof String) {
			return (String) obj;
		}
		*/
		
		//Fetching a list of unique parameters
		List<ParameterDefinition> defLst = this.getParameters(IMode.INHERIT_FORCED);
		
		LinkedList<ParameterValue> valLst =
				new LinkedList<ParameterValue>();
		
		//Then, we fetch the expansion of these based on their defaults
		for (ParameterDefinition pd : defLst) {
			ParameterValue pv = null;
			if (pd instanceof InheritableStringParameterDefinition) {
				InheritableStringParameterDefinition ispd =
						(InheritableStringParameterDefinition) pd;
				pv = ispd.createValue(ispd.getDefaultValue());
			} else {
				pv = pd.getDefaultParameterValue();
			}
			if (pv != null) {
				valLst.add(pv);
			}
		}
		
		String str = Jenkins.XSTREAM2.toXML(valLst);
		//onInheritChangeBuffer.set(this, "doGetParamExpansionsAsXML", str);
		return str;
	}
	
	/**
	 * This method dumps the default values of all parameters (even derived
	 * ones) into an XML file.
	 * <p>
	 * Do note that this does not do any expansion,
	 * it merely outputs the last default value defined for the given
	 * parameter. If you want the full expansion, call
	 * {@link #doGetParamExpansionsAsXML()}
	 * 
	 * @return raw XML string
	 */
	public String doGetParamDefaultsAsXML() {
		Object obj = onInheritChangeBuffer.get(this, "doGetParamDefaultsAsXML");
		if (obj != null && obj instanceof String) {
			return (String) obj;
		}
		
		//Fetching a list of unique parameters
		List<ParameterDefinition> defLst =
				this.getParameters(IMode.INHERIT_FORCED);
		
		LinkedList<ParameterValue> valLst =
				new LinkedList<ParameterValue>();
		
		//Then, we fetch the expansion of these based on their defaults
		for (ParameterDefinition pd : defLst) {
			ParameterValue pv = pd.getDefaultParameterValue();
			if (pv != null) {
				valLst.add(pv);
			}
		}
		
		String str = Jenkins.XSTREAM2.toXML(valLst);
		onInheritChangeBuffer.set(this, "doGetParamDefaultsAsXML", str);
		return str;
	}
	
	/**
	 * This method dumps the version store as serialized XML.
	 * @return
	 */
	public String doGetVersionsAsXML() {
		if (this.versionStore == null) {
			return "";
		}
		return this.versionStore.toXML();
	}
	
	/**
	 * This method dumps the version store as serialized,
	 * GZIP compressed, Base64 encoded XML.
	 * @return
	 */
	public String doGetVersionsAsCompressedXML() {
		if (this.versionStore == null) {
			return "";
		}
		String xml = this.versionStore.toXML();
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
			BASE64EncoderStream b64s = new BASE64EncoderStream(baos);
			GZIPOutputStream gos = new GZIPOutputStream(b64s);
			gos.write(xml.getBytes());
			gos.finish();
			gos.close();
			return baos.toString();
		} catch (IOException ex) {
			return "";
		}
		
	}
	
	@Override
	public void doDoDelete(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, InterruptedException {
		//Checking if this project is still referenced by another project
		for (Relationship rel : this.getRelationships().values()) {
			if (rel.type == Type.CHILD || rel.type == Type.MATE) {
				//Abort and redirect to error page
				rsp.sendRedirect(
					this.getAbsoluteUrl() + "/showReferencedBy"
				);
				return;
			}
		}
		//If we reach this spot, we can safely delete the project
		super.doDoDelete(req, rsp);
		
		//Then, we refresh the project map and buffers
		clearBuffers(this);
	}
	
	public String doComputeVersionDiff(StaplerRequest req, StaplerResponse rsp) {
		//Checking if the two necessary parameters are set
		if (!req.hasParameter("l") || !req.hasParameter("r")) {
			return "<span style=\"color:red\"><b>No left/right version selected!</b></span>";
		}
		
		Long l = null;
		Long r = null;
		String mode = "unified";
		try {
			l = Long.parseLong(req.getParameter("l"), 10);
			r = Long.parseLong(req.getParameter("r"), 10);
			if (req.hasParameter("mode")) {
				mode = req.getParameter("mode");
			}
		} catch (NumberFormatException ex) {
			return "<span style=\"color:red\"><b>Left/right version is not a number!</b></span>";
		}
		
		//Fetch the value maps of both versions
		Map<String, Object> lMap = this.versionStore.getValueMapFor(l);
		if (lMap == null) {
			return "<span style=\"color:red\"><b>Left version does not exist!</b></span>";
		}
		
		Map<String, Object> rMap = this.versionStore.getValueMapFor(r);
		if (rMap == null) {
			return "<span style=\"color:red\"><b>Right version does not exist!</b></span>";
		}
		
		//Turning them into escaped XML
		String lXml = Jenkins.XSTREAM2.toXML(lMap);
		String rXml = Jenkins.XSTREAM2.toXML(rMap);
		
		StringBuilder b = new StringBuilder();
		if (mode.equals("unified")) {
			return computeUnifiedDiff(
				5,
				new AbstractMap.SimpleEntry(l, lXml),
				new AbstractMap.SimpleEntry(r, rXml)
			);
		} else if (mode.equals("side")) {
			b.append("<span style=\"color:red\"><b>");
			b.append("Side-by-Side diff not yet implemented.");
			b.append("</b></span>");
		} else if (mode.equals("raw")) {
			return computeRawTable(
					new AbstractMap.SimpleEntry(l, lXml),
					new AbstractMap.SimpleEntry(r, rXml)
				);
		} else {
			b.append("<span style=\"color:red\"><b>");
			b.append("Select a valid diff mode: 'unified', 'side' (for side-by-side), or 'raw'.");
			b.append("</b></span>");
		}
		return b.toString();
	}
	
	public String warnUserOnUnstableVersions() {
		String warnMessage = null;
		if (this.isAbstract) {
			Deque<Version> stableVersions = getStableVersions();
			Long latestVersion = getLatestVersion();
			if (stableVersions.size() > 0) {
				if (!this.versionStore.getVersion(latestVersion).getStability()) {
					warnMessage = Messages.InheritanceProject_OlderVersionMarkedAsStable();
				}
			} else {
				warnMessage = Messages.InheritanceProject_NoVersionMarkedAsStable();
			}
		}
		return warnMessage;
	}

	/**
	 * @return True or False, depending if all versions of the project are unstable,
	 * meaning no version has been marked as stable
	 */
	public boolean areAllVersionsUnstable() {
		VersionsNotification notifyOnCurrentVersionStatus = notifyOnCurrentVersionStatus();
		if ((notifyOnCurrentVersionStatus.isNewest &&
				!notifyOnCurrentVersionStatus.isStable &&
				!notifyOnCurrentVersionStatus.stablesBefore &&
				!notifyOnCurrentVersionStatus.stablesAfter) ||
			!notifyOnCurrentVersionStatus.isNewest &&
				!notifyOnCurrentVersionStatus.isStable &&
				!notifyOnCurrentVersionStatus.stablesBefore &&
				!notifyOnCurrentVersionStatus.stablesAfter) {
			return true;
		}
		return false;
	}

	/**
	 * This method notifies the user on what type of version is he currently
	 * editing:
	 * 1. user is editing an unstable version (Warning)
	 * 2. user is editing the last stable version which is not the last version (Warning)
	 * 3. user is editing the last stable version which is the last version (Info)
	 * 4. user is editing some stable version which is not the latest stable version (Warning)
	 * @return
	 */
	public VersionsNotification notifyOnCurrentVersionStatus() {
		VersionsNotification versionsNotification = null;
		LinkedList<Version> versions = new LinkedList<Version>();
		
		Long latestVersionId = getLatestVersion();
		Long latestStableVersionId = getStableVersion();
		Long userSelectedVersionId = getUserDesiredVersion();
		Version stableVersion = versionStore.getVersion(latestStableVersionId);

		/*	user 
		 *		didn't select the latest stable version
		 *	and
		 *		latest version is marked as stable
		 */
		if (userSelectedVersionId != latestStableVersionId && stableVersion.getStability()) {
			Version userDesiredVersion = versionStore.getVersion(userSelectedVersionId);
			versions.add(versionStore.getVersion(latestStableVersionId));
			//at this point of check user selected an older stable version
			if (userDesiredVersion.getStability()) {
				versionsNotification = new VersionsNotification(
						//VersionsNotification.Type.EDITING_OLDER_STABLE_VERSION,
						false, true, true, true,
						versions);
			} else { //at this point of check user selected an older unstable version
				versionsNotification = new VersionsNotification(
						//VersionsNotification.Type.EDITING_UNSTABLE_VERSION_BUT_STABLE_AVAILABLE,
						false, false, true, true,
						versions);
			}
		} else if (userSelectedVersionId != latestStableVersionId && !stableVersion.getStability()) {
			/*	there are no stable versions
			 *		and 
			 *			this is the case where the user choose an old version
			 *			from all the unstable versions
			 */
			versions = this.versionStore.getAllVersionsSince(userSelectedVersionId);
			versionsNotification = new VersionsNotification(
					//VersionsNotification.Type.EDITING_UNSTABLE_VERSION_BUT_MORE_UNSTABLE_AVAILABLE,
					false, false, false, false,
					versions);
		} else {
			/*
			 * at this point
			 * user selected latest stable version
			 * 		but
			 * 			not the latest version
			 */
			if (latestVersionId != latestStableVersionId) {
				versions = this.versionStore.getAllVersionsSince(userSelectedVersionId);
				versionsNotification = new VersionsNotification(
						//VersionsNotification.Type.EDITING_LATEST_STABLE_VERSION,
						false, true, true, false,
						versions);
			} else {
				//this.versionStore.getLatestStable() in case no version is stable
				//returns as default the latest version, even if not marked as stable
				//by user, so we treat this special case letting the user know
				//there is no marked as stable version, but the last one is considered
				//as stable
				if (this.versionStore != null) {
					Version v = this.versionStore.getLatestStable();
					if (null == v || !v.getStability()) {
						versionsNotification = new VersionsNotification(
								//VersionsNotification.Type.EDITING_IMPLICIT_STABLE_VERSION,
								true, false, false, false,
								versions);
					} else {
						versionsNotification = new VersionsNotification(
								//VersionsNotification.Type.EDITING_LATEST_STABLE_AND_LAST_VERSION,
								true, true, false, false,
								versions);
					}
				}
			}
		}
		return versionsNotification;
	}
	
	// === DIFF COMPUTATION METHODS ===
	
	private static String escapeHTMLFull(String str) {
		return StringEscapeUtils.escapeHtml(str);
	}
	
	private String computeRawTable(Map.Entry<Long, String>... versions) {
		String headFmt =
				"<tr><th $c style=\"width:3em\">#</th><th $c>Version %d</th><th $c style=\"width:3em\">#</th><th $c>Version %d</th></tr>"
				.replace("$c", "class=\"mono\"");
		String rowFmt =
				"<tr><td $c>%d</td><td $c>%s</td><td $c>%d</td><td $c>%s</td></tr>"
				.replace("$c", "class=\"mono\"");
		
		StringBuilder b = new StringBuilder();
		
		//We print both files in a table next to each other
		b.append("<table frame=\"void\" rules=\"cols\" width=\"100%\"");
		b.append("class=\"mono\">");
		b.append(String.format(
				headFmt, versions[0].getKey(), versions[1].getKey()
		));
		
		final String[] lArr = versions[0].getValue().split("\n");
		final String[] rArr = versions[1].getValue().split("\n");
		String[] lines = new String[2];
		int max = Math.max(lArr.length, rArr.length);
		for (int i = 0; i < max; i++) {
			lines[0] = (i < lArr.length) ? escapeHTMLFull(lArr[i]) : "";
			lines[1] = (i < rArr.length) ? escapeHTMLFull(rArr[i]) : "";
			b.append(String.format(
					rowFmt, i, lines[0], i, lines[1]
			));
		}
		b.append("</table>");
		
		return b.toString();
	}
	
	private String computeUnifiedDiff(int context, Map.Entry<Long, String>... versions) {
		if (versions.length != 2) {
			throw new IllegalArgumentException("You must pass exactly two versions");
		}
		if (context < 0) {
			context = 0;
		}
		
		StringBuilder b = new StringBuilder();
		
		//Splitting texts along newlines
		List<String> lLst = Arrays.asList(versions[0].getValue().split("\n"));
		List<String> rLst = Arrays.asList(versions[1].getValue().split("\n"));
		
		//We use Google's diff utils to create the diff patch
		Patch p = DiffUtils.diff(lLst, rLst);
		
		//Then, we display a unified diff
		List<String> outLst = DiffUtils.generateUnifiedDiff(
				"Version " + versions[0].getKey(),
				"Version " + versions[1].getKey(),
				lLst, p, context
		);
		
		for (String line : outLst) {
			boolean hasColour = false;
			if (line.startsWith("++")) {
				b.append("<span style=\"color:orange\">");
				hasColour = true;
			} else if (line.startsWith("+")) {
				b.append("<span style=\"color:green\">");
				hasColour = true;
			} else if (line.startsWith("--")) {
				b.append("<span style=\"color:blue\">");
				hasColour = true;
			} else if (line.startsWith("-")) {
				b.append("<span style=\"color:red\">");
				hasColour = true;
			}
			String mod = escapeHTMLFull(line);
			b.append(mod);
			if (hasColour) {
				b.append("</span>");
			}
			b.append("<br/>");
		}
		
		return b.toString();
	}
	
	
	
	// === BUILD STARTING METHODS ===
	
	/**
	 * Executes a build started from the GUI.
	 * <p>
	 * Queries for parameters on an HTTP/GET, tries to decode submitted
	 * parameters on a HTTP/POST.
	 * <p>
	 * Before we can call the actual build, we must make sure that
	 * parameters are properly inherited; as the super implementation will
	 * NOT query {@link #isParameterized()}, but instead rely on querying
	 * whether the Project has a {@link ParametersDefinitionProperty} property.
	 * <p>
	 * As we need to treat Parameters created by ourselves different from
	 * those assigned by parents, we must override {@link #getProperty(Class)}
	 * to produce a suitable {@link ParametersDefinitionProperty} reference
	 * on the spot.
	 * <p>
	 * Do note that the {@link ParametersAction} objects that store the actual
	 * values will be created by
	 * {@link ParametersDefinitionProperty#_doBuild(StaplerRequest, StaplerResponse)}
	 * later on. Also do note that we can't extend
	 * {@link ParametersDefinitionProperty} or {@link ParametersAction}, because
	 * {@link AbstractProject} only checks for exact class matches.
	 */
	@Override
	public void doBuild(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay)
			throws IOException, ServletException {
		//Purge whatever's stored in the thread from a previous run
		//ThreadAssocStore.getInstance().clear(Thread.currentThread());
		
		//The delay parameter might be null in case somebody used a custom URL
		if (delay == null) {
			delay = new TimeDuration(0);
		}
		
		//Checking if we can fetch a fully defined versioning parameter
		if (req.getMethod().equals("POST")) {
			//Checking if parameters are set -- non-parameterized builds
			//will throw a nasty exception on req.getSubmittedForm; since no
			//form will have been submitted.
			Map pMap = req.getParameterMap();
			String cType = req.getContentType();
			boolean hasFormContent = (
					(pMap != null && pMap.containsKey("json")) ||
					(cType != null && cType.startsWith("multipart/"))
			);
			if (hasFormContent) {
				//Retrieving the submitted form
				JSONObject jForm = req.getSubmittedForm();
				
				//Trying to retrieve a versioning field
				try {
					String jVerMap = jForm.getString("versionMap");
					if (jVerMap != null && !jVerMap.isEmpty()) {
						Map<String, Long> vMap = 
								InheritanceParametersDefinitionProperty.
								decodeVersioningMap(jVerMap);
						if (vMap != null && !vMap.isEmpty()) {
							InheritanceProject.setVersioningMap(vMap);
						}
					}
				} catch (JSONException ex) {
					//No version map set as a parameter; will default to stable versions
				}
			}
		}
		
		/* We can't call the super-function, as it does not allow us to
		 * add a custom action to rescue the versioning over to the start of
		 * the actual build.
		*/
		//super.doBuild(req, rsp, delay);
		
		
		/* === START COPY OF SUPER FUNCTION ===  */
		
		//TODO: Check if this ACL check actually does the same as the commented
		//instruction below
		ACL acl = Jenkins.getInstance().getACL();
		acl.checkPermission(BUILD);
		//BuildAuthorizationToken.checkPermission(this, getAuthToken(), req, rsp);

		// if a build is parameterized, let that take over
		ParametersDefinitionProperty pp = getProperty(ParametersDefinitionProperty.class);
		if (pp != null) {
			pp._doBuild(req,rsp);
			return;
		}

		if (!isBuildable()) {
			throw HttpResponses.error(
					SC_INTERNAL_SERVER_ERROR,new IOException(getFullName()+" is not buildable")
			);
		}
		
		Jenkins.getInstance().getQueue().schedule(
				this, delay.getTime(), this.getBuildCauseOverride(req),
				new VersioningAction(this.getAllVersionsFromCurrentState())
		);
		
		//Send the user back, except if "rebuildNoRedirect" is set
		if (req.getAttribute("rebuildNoRedirect") == null) {
			rsp.forwardToPreviousPage(req);
		}
		/* === END COPY OF SUPER FUNCTION ===  */
	}
	
	public void doBuildSpecificVersion(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		//Purge whatever's stored in the thread from a previous run
		//ThreadAssocStore.getInstance().clear(Thread.currentThread());
		
		//If we did not submit a form; just display the initial data
		if(!req.getMethod().equals("POST")) {
			req.getView(this, "buildSpecificVersion.jelly").forward(req,rsp);
			return;
		}
		
		Map<String, Long> verMap = InheritanceRebuildAction.decodeVersionMap(
				req
		);
		
		String verMapStr = 
				InheritanceParametersDefinitionProperty
				.encodeVersioningMap(verMap);
		
		if (verMapStr == null || verMapStr.isEmpty()) {
			//TODO: Redirect to error page
			rsp.sendRedirect(".");
		}
		
		//TODO: Think about passing the verMapStr compressed
		String verUrlParm = "versions=\"" + verMapStr + "\"";
		
		//Checking if the user wants to build or refresh the page
		if (req.hasParameter("doRefresh")) {
			//Refreshing the page with the newly selected versions
			rsp.sendRedirect("buildSpecificVersion?" + verUrlParm);
		} else {
			//Triggering a nice build with the given version map
			rsp.sendRedirect("build?" + verUrlParm);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		//Purge whatever's stored in the thread from a previous run
		//ThreadAssocStore.getInstance().clear(Thread.currentThread());
		
		//TODO: The below function did not have the TimeDuration param previously
		TimeDuration td = new TimeDuration(0);
		super.doBuildWithParameters(req, rsp, td);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>Do note:</b> This method is <i>not</i> calling the super-implementation,
	 * because it is not aware that the default values for parameters must be
	 * derived via inheritance, if the method is called directly (instead of
	 * via the CLI).
	 * 
	 * @see InheritableStringParameterDefinition#getDefaultParameterValue()
	 */
	public QueueTaskFuture<InheritanceBuild> scheduleBuild2(
			int quietPeriod, Cause c, Collection<? extends Action> actions) {
		//Purge whatever's stored in the thread from a previous run
		//ThreadAssocStore.getInstance().clear(Thread.currentThread());
		
		//Checking if a version-setting action is present,
		boolean hasVersioningAction = false;
		for (Action a : actions) {
			if (a instanceof VersioningAction) {
				//Storing that version-map in the thread
				setVersioningMap(((VersioningAction)a).versionMap);
				hasVersioningAction = true;
				break;
			}
		}
		
		//Checking if we have a parameter set which overrides everything 
		for (Action a : actions) {
			if (!(a instanceof ParametersAction)) {
				continue;
			}
			
			ParametersAction pa = (ParametersAction) a;
			ParameterValue pv = pa.getParameter(
					InheritanceParametersDefinitionProperty.VERSION_PARAM_NAME
			);
			if (pv == null || !(pv instanceof StringParameterValue)) {
				continue;
			}
			StringParameterValue spv = (StringParameterValue) pv;
			Map<String, Long> vMap = 
					InheritanceParametersDefinitionProperty
					.decodeVersioningMap(spv.value);
			if (vMap == null) { continue; }
			
			//The decoded map is registered in the thread
			setVersioningMap(vMap);
		}
		
		//If we need to create a new versioning action; we do this now
		//Do note that this will retrieve versions previously stored in the thread!
		if (!hasVersioningAction) {
			LinkedList<Action> newActions = new LinkedList<Action>(actions);
			newActions.add(
					new VersioningAction(this.getAllVersionsFromCurrentState())
			);
			actions = newActions;
		}
		
		//The buildable check must be done after versioning assignment
		if (!isBuildable()) { return null; }

		List<Action> queueActions = new ArrayList<Action>(actions);
		if (isParameterized() && Util.filter(queueActions, ParametersAction.class).isEmpty()) {
			List<ParameterValue> pvLst = new ArrayList<ParameterValue>();
			for (ParameterDefinition def : this.getParameters()) {
				if (def instanceof InheritableStringParameterDefinition) {
					InheritableStringParameterDefinition ispd =
							(InheritableStringParameterDefinition) def;
					pvLst.add(ispd.createValue(ispd.getDefaultValue()));
				} else {
					pvLst.add(def.getDefaultParameterValue());
				}
			}
			queueActions.add(new ParametersAction(pvLst));
		}
		
		if (c != null) {
			queueActions.add(new CauseAction(c));
		}
		
		WaitingItem i = Jenkins.getInstance().getQueue().schedule(
				this, quietPeriod, queueActions
		);
		return (i == null) ? null : (QueueTaskFuture)i.getFuture();
	}
	
	/**
	 * This is a copy (not a wrapper) of getBuildCause() in
	 * {@link AbstractProject}. This is necessary, because we can't access
	 * that field as our parent is loaded by a different class loader.
	 * <p>
	 * The function is used, because we need to splice-in one additional
	 * {@link Action} for creation of Builds: {@link VersioningAction}.
	 * <p>
	 * FIXME: The ideal solution to this is to simply add an Extension Point
	 * into Jenkins, that allows one to contribute additional actions.
	 * 
	 * @param req
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public CauseAction getBuildCauseOverride(StaplerRequest req) {
		Cause cause;
		if (getAuthToken() != null &&
				getAuthToken().getToken() != null &&
				req.getParameter("token") != null) {
			// Optional additional cause text when starting via token
			String causeText = req.getParameter("cause");
			cause = new RemoteCause(req.getRemoteAddr(), causeText);
		} else {
			cause = new UserIdCause();
		}
		return new CauseAction(cause);
	}
	
	
	
	// === VERSIONING HANDLING AND DERIVATION METHODS ===
	
	@RequirePOST
	public void doConfigVersionsSubmit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, FormException {
		checkPermission(VERSION_CONFIG);
		
		class Entry {
			Long id;
			String desc;
			boolean stable;
			
			public Entry(Long id, String desc, boolean stable) {
				this.id = id;
				this.desc = desc;
				this.stable = stable;
			}
		}
		
		LinkedList<Entry> fields = new LinkedList<Entry>();
		
		try {
			//Decoding the form data to structured JSON
			JSONObject json = req.getSubmittedForm();
			
			//Checking if the JSON has all necessary fields
			String[] keys = { "versionID", "description", "stable" };
			for (String key : keys) {
				if (!json.has(key)) {
					log.warning("Got submission of broken version config form.");
					return;
				}
			}
			try {
				JSONArray vArr = json.getJSONArray("versionID");
				JSONArray dArr = json.getJSONArray("description");
				JSONArray sArr = json.getJSONArray("stable");
				//Sanity check
				if (vArr.size() != dArr.size() || vArr.size() != sArr.size()) {
					log.warning("Field in version config form differ in length.");
					return;
				}
				
				//Then, we decode each tuple and alter the version referenced
				for (int i = 0; i < vArr.size(); i++) {
					try {
						fields.add(
							new Entry(
								Long.valueOf(vArr.getString(i), 10),
								dArr.getString(i),
								sArr.getBoolean(i)
							)
						);
					} catch (JSONException ex) {
						log.warning("Invalid value in version config at index " + i);
					} catch (NumberFormatException ex) {
						log.warning("Invalid id in version config at index " + i);
					}
				}
			} catch (JSONException ex) {
				try {
					// One field in version config form was not an array; trying strings
					Long jv = Long.valueOf(json.getString("versionID"), 10);
					String jd = json.getString("description");
					Boolean js = json.getBoolean("stable");
					fields.add(new Entry(jv, jd, js));
				} catch (JSONException ex2) {
					log.warning("Invalid value in version config!");
					return;
				} catch (NumberFormatException ex2) {
					log.warning("Invalid id in version config!");
					return;
				}
			}
			
			//After having decoded the fields, we alter the versions appropriately
			for (Entry e : fields) {
				//Fetching version
				Version v = this.versionStore.getVersion(e.id);
				if (v == null) {
					log.warning("No such version " + e.id + " for " + this.getName());
					continue;
				}
				v.setStability(e.stable);
				v.setDescription(e.desc);
			}
			
			//Saving the altered versions to disk
			this.versionStore.save(this.getVersionFile());
			
			//The selection of the stable version might have changed. So we need
			//to clear the local buffers
			clearBuffers(this);
			
			//Finally, triggering generation of new projects
			ProjectCreationEngine.instance.notifyProjectChange(this);
			
			//At the end, we mark the forms as successfully submitted
			FormApply.success(".").generateResponse(req, rsp, null);
		} catch (JSONException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println("Failed to parse form data. Please report this problem as a bug!");
			pw.println("JSON=" + req.getSubmittedForm());
			pw.println();
			e.printStackTrace(pw);
			
			rsp.setStatus(SC_BAD_REQUEST);
			sendError(sw.toString(), req, rsp, true);
		}
	}
	
	protected VersionedObjectStore loadVersionedObjectStore() {
		//TODO: This should read stuff from disk / DB
		File vFile = this.getVersionFile();
		if (vFile == null || !vFile.isFile()) {
			//Creating an empty VOS, in case none is stored anywhere
			return new VersionedObjectStore();
		}
		//Otherwise, we attempt to load it from disk
		VersionedObjectStore vos = null;
		try {
			vos = VersionedObjectStore.load(vFile);
		} catch (IOException ex) {
			log.warning(
					"No versions loaded for " + this.getName() + ". " +
					ex.getLocalizedMessage()
			);
			return new VersionedObjectStore();
		} catch (IllegalArgumentException ex) {
			log.warning(
					"No versions loaded for " + this.getName() + ". " +
					ex.getLocalizedMessage()
			);
			return new VersionedObjectStore();
		} catch (XStreamException ex) {
			log.warning(
					"Could not load Version for: " + this.getName() + ". " +
					ex.getLocalizedMessage()
			);
			return new VersionedObjectStore();
		}
		
		//Since we'll add/remove lots of properties, we put Jenkins in
		//bulk-change mode
		BulkChange bc = new BulkChange(this);
		try {
			//Then, we need to patch up certain fields in the store
			for (HashMap<String, Object> m : vos.getAllValueMaps()) {
				Object obj = m.get("properties");
				if (obj != null && obj instanceof List) {
					List<JobProperty<Job<?,?>>> lst = (List<JobProperty<Job<?,?>>>) obj;
					for (JobProperty<Job<?,?>> prop : lst) {
						//Adding the property to us
						this.addProperty(prop);
						//And immediately removing the property
						this.removeProperty(prop);
					}
				}
			}
			bc.commit();
		} catch (IOException e) {
			//Do nothing but fall down to the finally block
		} finally {
			bc.abort();
		}
			
		return vos;
	}
	
	
	/**
	 * Wrapper around {@link #dumpConfigToNewVersion(String)} with an empty message
	 */
	protected synchronized void dumpConfigToNewVersion() {
		this.dumpConfigToNewVersion(null);
	}
	
	/**
	 * This method takes the current configuration and dumps all relevant
	 * fields into the versioning-store.
	 * <p>
	 * Do note that versioning is stored separately from inheritance, but
	 * evaluated together. This means that, over time, parentage may change as
	 * well as compatibility markings. These <b>all</b> need to be saved 
	 * indefinitely.
	 */
	protected synchronized void dumpConfigToNewVersion(String message) {
		//Sanity checks
		if (this.isTransient) { return; }
		if (this.versionStore == null) {
			this.versionStore = this.loadVersionedObjectStore();
		}
		
		/* ATTENTION! Do NOT save the lists themselves, but rather copy them,
		 * unless you know that you already have a copy. Due to the nature
		 * of how Jenkins saves data, you don't need to copy the stored objects
		 * themselves.
		 * 
		 * Also never, ever save inherited or versioned data. Only save
		 * whatever the "super" class believes to be true for the project or
		 * whatever is directly saved as a field in this class.
		 */
		//Creating the next, clean version
		Version v = this.versionStore.createNextVersionAsEmpty();
		
		//Fetching the currently logged-on user and assigning that to the version
		String username = Jenkins.getAuthentication().getName();
		if (username != null && !username.isEmpty()) {
			v.setUsername(username);
		}
		
		//Attach the message (if any)
		if (message != null) {
			v.setDescription(message);
		}
		
		//Storing the list of parents
		this.versionStore.setObjectFor(
				v, "parentReferences",
				new LinkedList<AbstractProjectReference>(this.getRawParentReferences())
		);
		
		//Storing the list of compatibility matings -- also contains
		//the parameters defined on them.
		this.versionStore.setObjectFor(
				v, "compatibleProjects",
				new LinkedList<AbstractProjectReference>(this.compatibleProjects)
		);
		
		//Storing the properties of this job; this contains the project parameters
		this.versionStore.setObjectFor(
				v, "properties",
				new LinkedList<JobProperty<? super InheritanceProject>>(
						super.getAllProperties()
				)
		);
		
		//Storing build wrappers
		this.versionStore.setObjectFor(
				v, "buildWrappersList",
				new DescribableList<BuildWrapper, Descriptor<BuildWrapper>>(
						NOOP, super.getBuildWrappersList().toList()
				)
		);
		
		//Storing builders
		this.versionStore.setObjectFor(
				v, "buildersList",
				new DescribableList<Builder, Descriptor<Builder>>(
						NOOP, super.getBuildersList().toList()
				)
		);
		
		//Storing publishers
		this.versionStore.setObjectFor(
				v, "publishersList",
				new DescribableList<Publisher, Descriptor<Publisher>>(
						NOOP, super.getPublishersList().toList()
				)
		);
		
		//Storing actions
		this.versionStore.setObjectFor(
				v, "actions", new LinkedList<Action>(super.getActions())
		);
		
		
		//Storing the other, more simple properties
		this.versionStore.setObjectFor(v, "scm", super.getScm());
		this.versionStore.setObjectFor(v, "quietPeriod", this.getRawQuietPeriod());
		this.versionStore.setObjectFor(v, "scmCheckoutRetryCount", this.getRawScmCheckoutRetryCount());
		this.versionStore.setObjectFor(v, "scmCheckoutStrategy", super.getScmCheckoutStrategy());
		this.versionStore.setObjectFor(v, "blockBuildWhenDownstreamBuilding", super.blockBuildWhenDownstreamBuilding());
		this.versionStore.setObjectFor(v, "blockBuildWhenUpstreamBuilding", super.blockBuildWhenUpstreamBuilding());
		this.versionStore.setObjectFor(v, "logRotator", super.getBuildDiscarder());
		this.versionStore.setObjectFor(v, "customWorkspace", super.getCustomWorkspace());
		this.versionStore.setObjectFor(v, "parameterizedWorkspace", this.getRawParameterizedWorkspace());

		
		//Now, we check if this version is the same as the last one
		Version prev = this.versionStore.getVersion(v.id - 1);
		if (prev != null && this.versionStore.areIdentical(prev, v)) {
			//Drop the version, if possible
			this.versionStore.undoVersion(v);
		}
		//Save the file, to persist our changes
		try {
			this.versionStore.save(this.getVersionFile());
		} catch (IOException ex) {
			log.severe(String.format(
					"Failed to save version to: %s; Reason = %s",
					this.getVersionFile(), ex.getMessage()
			));
		}
	}
	
	
	private InheritanceProject getProjectFromRequest(StaplerRequest req) {
		//First, we check if an ancestor is defined
		InheritanceProject ip = req.findAncestorObject(InheritanceProject.class);
		if (ip != null) {
			return ip;
		}
		
		//Otherwise, we decode the URI and try to find the project that way
		String jobName = null;
		String uri = req.getRequestURI();
		if (uri != null && !uri.isEmpty()) {
			Matcher m = DescriptorImpl.urlJobPattern.matcher(uri);
			if (m.find()) {
				if (m.group(1) != null && !m.group(1).isEmpty()) {
					jobName = m.group(1);
				}
			}
		}
		ip = null;
		if (jobName != null) {
			ip = InheritanceProject.getProjectByName(jobName);
		}
		return ip;
	}
	
	
	/**
	 * This method tries to fetch the version number from the current
	 * Stapler request.
	 * <p>
	 * Do note that this only works in threads started by a Web-/GUI-request.
	 * It will not work if a call is triggered by the CLI, during the
	 * actual execution of a build or when Jenkins is querying values
	 * internally.
	 * 
	 * @return the version desired by the user; or null if not a request
	 */
	private Long getUserDesiredVersionFromRequest() {
		//Checking if we were invoked through an HTTP URL request
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) {
			return null;
		}
		
		//Checking if there's a specific "versions" attribute associated with
		//the current request, and is a Map of Strings to Long values
		Object verObj = req.getAttribute("versions");
		if (verObj != null && verObj instanceof Map) {
			Map verMap = (Map) verObj;
			try {
				Object ver = verMap.get(this.getName());
				if (ver != null && ver instanceof Number) {
					return ((Number)ver).longValue();
				}
			} catch (ClassCastException ex) {
				log.warning(
					"ClassCaseException when attempting to decode 'versions' attribute of HTTP-Request"
				);
			} catch (NullPointerException ex) {
				log.warning(
					"NullPointerException when attempting to decode 'versions' attribute of HTTP-Request"
				);
			}
		}
		
		//If that did not exist, we try for the broader "timestamp" attribute
		Object tsObj = req.getAttribute("vTimestamp");
		if (tsObj != null && tsObj instanceof Number) {
			Number ts = (Number) tsObj;
			Version v = this.versionStore.getNearestTo(ts.longValue());
			if (v != null) {
				return v.id;
			}
		}
		
		
		//Now that we've exhausted the attributes, we need to check the raw
		//URL parameters, which are of course MUCH more brittle
		
		String verParm = req.getParameter("versions");
		if (verParm != null && !verParm.isEmpty()) {
			Map<String, Long> verMap = 
					InheritanceParametersDefinitionProperty.
					decodeVersioningMap(verParm);
			if (!verMap.isEmpty()) {
				InheritanceProject.setVersioningMap(verMap);
			}
			//And checking if it contained a matching for the current project
			Long version = verMap.get(this.getName());
			if (version != null) {
				return version;
			}
		}
		
		//If that failed, we try to get the "timestamp" attribute
		String tsParm = req.getParameter("timestamp");
		if (tsParm != null && !tsParm.isEmpty()) {
			//Trying to parse as a number
			try {
				Long ts = Long.valueOf(tsParm, 10);
				if (ts != null && ts >= 0) {
					//Saving it, to not re-decode it again
					req.setAttribute("vTimestamp", ts);
					Version v = this.versionStore.getNearestTo(ts.longValue());
					if (v != null) {
						return v.id;
					}
				}
			} catch (NumberFormatException ex) {
				log.warning(
					"NumberFormatException when attempting to decode 'timestamp' attribute of HTTP-Request"
				);
			}
		}
		
		/* If that also failed, we try to decode the simple "version" parameter
		 * Since this is always just defined for ONE particular project, we
		 * need to grab the stable version closest to the timestamp of
		 * the specified version when dealing with other projects.
		 */
		
		verParm = req.getParameter("version");
		if (verParm != null) {
			//Fetching the project associated with that request; if any
			InheritanceProject ip = this.getProjectFromRequest(req);
			if (ip != null) {
				try {
					Long vNum = Long.valueOf(verParm, 10);
					if (this == ip) {
						return vNum;
					}
					//Fetching the timestamp of that version
					Version v = ip.versionStore.getVersion(vNum);
					if (v != null) {
						long ts = v.timestamp;
						//Fetching the best matching version to that ts
						Version near = this.versionStore.getNearestTo(ts);
						if (near != null) {
							return near.id;
						}
					}
				} catch (NumberFormatException ex) {
					log.warning(
						"NumberFormatException when attempting to decode 'version' attribute of HTTP-Request"
					);
				}
			}
		}
		//If we reach this spot; no version was defined anywhere
		return null;
		//return this.getStableVersion();
	}
	
	/**
	 * This method tries to use the {@link ThreadAssocStore} to fetch the
	 * special "versions" field.
	 * <p>
	 * The assumptions are that:
	 * <ol>
	 * <li>The {@link InheritanceBuild#run()} method has set this field up.</li>
	 * <li>No two builds share the same thread.</li>
	 * <li>If a thread does get re-used, that the previous builds do not make
	 * calls to Jenkins anymore that need versioning information.
	 * </li>
	 * </ol>
	 * @return
	 */
	private Long getUserDesiredVersionFromThread() {
		Object o = ThreadAssocStore.getInstance().getValue("versions");
		if (o != null) {
			if (o instanceof Number) {
				return ((Number)o).longValue();
			}
			if (o instanceof Map) {
				Map<String, Long> map = (Map<String,Long>) o;
				Long ret = map.get(this.getName());
				if (ret != null) {
					return ret;
				}
			}
		}
		return null;
	}
	
	/**
	 * This method tries to determine the actual version requested by the
	 * current call sequence. This may be due to a configuration page request or
	 * through a build started from the GUI / CLI.
	 * <p>
	 * Do note that the returned version is the one defined for <b>this</b>
	 * project. If you desire the version of a particular parent project, call
	 * the parent's implementation of this method. If you wish to get all
	 * the parent's versions, it is faster to call
	 * {@link #getUserDesiredParentVersions()}.
	 * 
	 * @see #getUserDesiredParentVersions()
	 * @see #getVersionIDs()
	 * 
	 * @return the desired version number; or null if no such version can be
	 * found and the latest version is to be used.
	 */
	public Long getUserDesiredVersion() {
		return this.getUserDesiredVersion(false);
	}
	
	public Long getUserDesiredVersion(boolean noInsertStableVersion) {
		//TODO: Buffer this result in some way
		
		//First, we check if a version was passed via an URL parameter
		Long version = this.getUserDesiredVersionFromRequest();
		
		//If that failed, we try to fetch it from the Thread
		if (version == null) {
			version = this.getUserDesiredVersionFromThread();
		}
		
		//If that failed, too, we just use the latest stable version
		if (version == null) {
			if (noInsertStableVersion) {
				return null;
			}
			return this.getStableVersion();
		}
		
		//We check whether the user-passed version is valid at all
		if (this.getVersionIDs().contains(version)) {
			return version;
		}
		//Otherwise, the version does not exist and we return the latest one
		return this.getLatestVersion();
	}
	
	/**
	 * This method returns the versions selected for this project and its
	 * parents.
	 * 
	 * @return
	 */
	public Map<String, Long> getAllVersionsFromCurrentState() {
		LinkedList<InheritanceProject> open = new LinkedList<InheritanceProject>();
		Set<String> closed = new HashSet<String>();
		Map<String, Long> out = new HashMap<String, Long>();
		
		//Adding ourselves as the first node
		open.add(this);
		
		while (!open.isEmpty()) {
			InheritanceProject ip = open.pop();
			//Fetching the user-requested version for the open node
			Long v = ip.getUserDesiredVersion();
			out.put(ip.getName(), v);
			//Then, adding this node to the closed set
			closed.add(ip.getName());
			//And adding the parent nodes to the open list
			for (AbstractProjectReference apr : ip.getParentReferences()) {
				if (closed.contains(apr.getName())) { continue; }
				InheritanceProject next = apr.getProject();
				if (next == null) { continue; }
				open.addLast(next);
			}
		}
		return out;
	}
	
	
	public Deque<Long> getVersionIDs() {
		Object obj = onSelfChangeBuffer.get(this, "getVersionIDs()");
		if (obj != null && obj instanceof Deque) {
			return (Deque) obj;
		}
		
		LinkedList<Long> lst = new LinkedList<Long>();
		for (Version v : this.getVersions()) {
			lst.add(v.id);
		}
		
		onSelfChangeBuffer.set(this, "getVersionIDs()", lst);
		return lst;
	}
	
	public Deque<Version> getVersions() {
		Object obj = onSelfChangeBuffer.get(this, "getVersions()");
		if (obj != null && obj instanceof Deque) {
			return (Deque) obj;
		}
		
		LinkedList<Version> lst = new LinkedList<Version>();
		if (this.versionStore == null) {
			return lst;
		}
		lst.addAll(
			this.versionStore.getAllVersions()
		);
		
		onSelfChangeBuffer.set(this, "getVersions()", lst);
		return lst;
	}
	
	public Deque<Version> getStableVersions() {
		Object obj = onSelfChangeBuffer.get(this, "getStableVersions()");
		if (obj != null && obj instanceof Deque) {
			return (Deque) obj;
		}

		LinkedList<Version> lst = new LinkedList<Version>();
		if (this.versionStore == null) {
			return lst;
		}
		for (Version version : this.versionStore.getAllVersions()) {
			if (version.getStability()) {
				lst.add(version);
			}
		}
		onSelfChangeBuffer.set(this, "getStableVersions()", lst);
		return lst;
	}

	public VersionedObjectStore getVersionedObjectStore() {
		return this.versionStore;
	}
	
	public Long getStableVersion() {
		if (this.versionStore == null) {
			return null;
		}
		Version v = this.versionStore.getLatestStable();
		return (v == null) ? null : v.id;
	}
	
	public Long getLatestVersion() {
		if (this.versionStore == null) {
			return null;
		}
		Version v = this.versionStore.getLatestVersion();
		return (v == null) ? null : v.id;
	}
	
	
	public class InheritedVersionInfo {
		public final InheritanceProject project;
		public final Long version;
		public final List<Long> versions;
		public final String description;
		
		public InheritedVersionInfo(
				InheritanceProject project, Long version, List<Long> versions,
				String description) {
			this.project = project;
			this.version = version;
			this.versions = versions;
			this.description = description;
		}
		
		public List<Long> getVersions() {
			return versions;
		}
	}
	
	public List<InheritedVersionInfo> getAllInheritedVersionsList() {
		return this.getAllInheritedVersionsList(null);
	}
	
	public List<InheritedVersionInfo> getAllInheritedVersionsList(InheritanceBuild build) {
		List<InheritedVersionInfo> out = new LinkedList<InheritedVersionInfo>();
		
		//Adding ourselves as the first entry
		LinkedList<Long> verLst = new LinkedList<Long>();
		for (Version v : this.versionStore.getAllVersions()) {
			verLst.add(v.id);
		}
		if (!verLst.isEmpty()) {
			Long verID = this.getUserDesiredVersion();
			Version verObj = this.versionStore.getVersion(verID);
			
			out.add(new InheritedVersionInfo(
					this, verID, verLst,
					(verObj != null) ? verObj.getDescription() : null
			));
		}
		
		Map<String, Long> buildVersions = null;
		if (build != null) {
			buildVersions = build.getProjectVersions();
		}
		
		//Fetching all parent references in order and adding them
		List<AbstractProjectReference> aprLst =
				this.getAllParentReferences(SELECTOR.MISC);
		for (AbstractProjectReference apr: aprLst) {
			InheritanceProject ip = apr.getProject();
			if (ip == null) { continue; }
			
			verLst = new LinkedList<Long>();
			for (Version v : ip.versionStore.getAllVersions()) {
				verLst.add(v.id);
			}
			if (verLst.isEmpty()) {
				// No versions available for that project; skipping it
				continue;
			}
			
			//Fetch the version; either from the passed build or URL params
			Long verID = ip.getUserDesiredVersion(true);
			if (verID == null) {
				if (buildVersions != null) {
					verID = buildVersions.get(ip.getName());
				} else {
					verID = ip.getUserDesiredVersion();
				}
			}
			
			if (verID != null) {
				//Fetch the version object associated with the given ID 
				Version verObj = ip.versionStore.getVersion(verID);
				if (verObj == null) { continue; }
				
				out.add(new InheritedVersionInfo(
						ip, verID, verLst, verObj.getDescription()
				));
			}
		}
		return out;
	}
	
	
	
	// === INHERITANCE-HELPER METHODS ===
	
	public List<InheritanceProject> getChildrenProjects() {
		Object obj = onChangeBuffer.get(this, "getChildrenProjects");
		if (obj != null && obj instanceof LinkedList) {
			return (LinkedList) obj;
		}
		
		LinkedList<InheritanceProject> lst =
				new LinkedList<InheritanceProject>();
		
		Map<String, InheritanceProject> map = getProjectsMap();
		for (InheritanceProject p : map.values()) {
			//Checking if that project inherits from us
			for (AbstractProjectReference ref : p.getParentReferences()) {
				if (this.name.equals(ref.getName())) {
					lst.add(p);
				}
			}
		}
		
		onChangeBuffer.set(this, "getChildrenProjects", lst);
		return lst;
	}
	
	public List<InheritanceProject> getParentProjects() {
		LinkedList<InheritanceProject> lst =
				new LinkedList<InheritanceProject>();
		
		for (AbstractProjectReference ref : this.getParentReferences()) {
			if (ref == null) { continue; }
			InheritanceProject ip = ref.getProject();
			if (ip == null) { continue; }
			lst.add(ip);
		}
		
		return lst;
	}
	
	public List<String> getProjectReferenceIssues() {
		LinkedList<String> lst = new LinkedList<String>();
				
		//Checking direct parents
		for (AbstractProjectReference apr : this.getParentReferences()) {
			InheritanceProject ip = apr.getProject();
			if (ip == null) {
				lst.add("Invalid parent reference to: " + apr.getName());
			}
		}
		
		//Checking matings
		for (AbstractProjectReference apr : this.compatibleProjects) {
			InheritanceProject ip = apr.getProject();
			if (ip == null) {
				lst.add("Invalid compatibility reference to: " + apr.getName());
			}
		}
		
		return lst;
	}
	
	/**
	 * This method re-parents a given trigger, to ensure that it belongs to the
	 * current project.
	 * <p>
	 * It does so by looping it through XStream to get a copy and then calling
	 * {@link Trigger#start(Item, boolean)} on it; just like if the project was
	 * just read from disk.
	 * <p>
	 * As such, this method should be highly robust, but is of course very much
	 * slower than if it had a reliable direct copying method available.
	 * 
	 * @param trigger
	 * @return
	 */
	private <T extends Trigger> T getReparentedTrigger(T trigger) {
		//Copy the trigger by looping it through XSTREAM and then calling start()
		//based on the CURRENT project
		//TODO: Find out somehow if "trigger" already belongs to the current project
		try {
			String xml = Jenkins.XSTREAM2.toXML(trigger);
			if (xml == null) { return trigger; }
			Object copy = Jenkins.XSTREAM2.fromXML(xml);
			if (copy == null || !(copy instanceof Trigger)) {
				return trigger;
			}
			//The copying loop was successful! Calling start() on the trigger
			trigger = (T) copy;
			trigger.start(this, false);
			return trigger;
		} catch (XStreamException ex) {
			//The loop-copy failed; returning the originally retrieved field
			return trigger;
		}
	}
	
	
	// === NON-INHERITANCE CONTROLLED PROPERTY SETTING METHODS ===
	
	/**
	 * This method is called by the configuration submission to set a new
	 * SCM. This does not need to care about inheritance or versioning, as
	 * this function should only be invoked from
	 * {@link #doConfigSubmit(StaplerRequest, StaplerResponse)}.
	 */
	@Override
	public void setScm(SCM scm) throws IOException {
		super.setScm(scm);
	}
	
	
	
	// === INHERITANCE-AWARE PROPERTY READING METHODS ===
	
	private InheritanceGovernor<List<AbstractProjectReference>> getParentReferencesGovernor(ProjectReference.PrioComparator.SELECTOR sortKey) {
		return new InheritanceGovernor<List<AbstractProjectReference>>(
				"parentReferences", sortKey, this) {
			
			@Override
			protected List<AbstractProjectReference> castToDestinationType(
					Object o) {
				return castToList(o);
			}
			
			@Override
			public List<AbstractProjectReference> getRawField(
					InheritanceProject ip) {
				return ip.getRawParentReferences();
			}
			
			@Override
			protected List<AbstractProjectReference> reduceFromFullInheritance(Deque<List<AbstractProjectReference>> list) {
				return InheritanceGovernor.reduceByMergeWithDuplicates(
						list, AbstractProjectReference.class, this.caller
				);
			}
		};
	}
		
	public List<AbstractProjectReference> getParentReferences() {
		return this.getParentReferences(SELECTOR.MISC);
	}
	
	public List<AbstractProjectReference> getParentReferences(
			ProjectReference.PrioComparator.SELECTOR sortKey) {
		InheritanceGovernor<List<AbstractProjectReference>> gov =
				getParentReferencesGovernor(sortKey);
		//We will ALWAYS just return the LOCAL parent references.
		//If you ever do anything else; this WILL cause an infinite loop!
		return gov.retrieveFullyDerivedField(this, IMode.LOCAL_ONLY);
	}
	
	public List<AbstractProjectReference> getRawParentReferences() {
		return this.parentReferences;
	}
	
	/**
	 * This method returns a list of all parent references.
	 * <p>
	 * <b><i>DO NOT</i></b> use this method inside any function from
	 * {@link InheritanceGovernor} or any method called by it, because that
	 * will almost always lead to an infinite recursion.
	 * 
	 * @param sortKey the key specifying the order in which projects are returned.
	 * @return a list of all parent references
	 */
	public List<AbstractProjectReference> getAllParentReferences(
			ProjectReference.PrioComparator.SELECTOR sortKey) {
		InheritanceGovernor<List<AbstractProjectReference>> gov =
				this.getParentReferencesGovernor(sortKey);
		return gov.retrieveFullyDerivedField(this, IMode.INHERIT_FORCED);
	}
	
	/**
	 * Wrapper for {@link #getAllParentReferences(SELECTOR)}, but will add
	 * a reference to this project too, if needed.
	 * 
	 * @param sortKey the key specifying the order in which projects are returned.
	 * @param addSelf if true, add a self-reference in the correct spot
	 * @return a list of all parent references, including a self-reference if
	 * addSelf is true.
	 */
	public List<AbstractProjectReference> getAllParentReferences(
			ProjectReference.PrioComparator.SELECTOR sortKey, boolean addSelf) {
		List<AbstractProjectReference> lst = this.getAllParentReferences(sortKey);
		
		if (addSelf) {
			boolean hasAddedSelf = false;
			ListIterator<AbstractProjectReference> iter = lst.listIterator();
			while (iter.hasNext()) {
				AbstractProjectReference ref = iter.next();
				int prio;
				if (ref instanceof ProjectReference) {
					prio = PrioComparator.getPriorityFor(ref, sortKey);
				} else {
					//An anonymous ref is always at priority 0
					prio = 0;
				}
				if (!hasAddedSelf && prio > 0) {
					hasAddedSelf = true;
					iter.add(new SimpleProjectReference(this.getFullName()));
				}
			}
			//Check if we were able to add a self-reference at all
			if (!hasAddedSelf) {
				lst.add(new SimpleProjectReference(this.getFullName()));
			}
		}
		
		return lst;
	}
	
	public List<AbstractProjectReference> getCompatibleProjects() {
		return this.getCompatibleProjects(SELECTOR.MISC);
	}
	
	public List<AbstractProjectReference> getCompatibleProjects(
			ProjectReference.PrioComparator.SELECTOR sortKey) {
		InheritanceGovernor<List<AbstractProjectReference>> gov =
				new InheritanceGovernor<List<AbstractProjectReference>>(
						"compatibleProjects", sortKey, this) {
			@Override
			protected List<AbstractProjectReference> castToDestinationType(
					Object o) {
				return castToList(o);
			}
			
			@Override
			public List<AbstractProjectReference> getRawField(
					InheritanceProject ip) {
				return ip.getRawCompatibleProjects();
			}
		};
		//No sense in returning anything but local compatibles
		List<AbstractProjectReference> refs = gov.retrieveFullyDerivedField(this, IMode.LOCAL_ONLY);
		if (refs == null) {
			return new LinkedList<AbstractProjectReference>();
		}
		return refs;
	}
	
	public List<AbstractProjectReference> getRawCompatibleProjects() {
		return this.compatibleProjects;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<Action> getActions() {
		return this.getActions(IMode.AUTO);
	}
	
	public synchronized List<Action> getActions(IMode mode) {
		InheritanceGovernor<List<Action>> gov =
				new InheritanceGovernor<List<Action>>(
						"actions", SELECTOR.MISC, this) {
			@Override
			protected List<Action> castToDestinationType(Object o) {
				return castToList(o);
			}
			
			@Override
			public List<Action> getRawField(
					InheritanceProject ip) {
				return ip.getRawActions();
			}
			
			@Override
			protected List<Action> reduceFromFullInheritance(Deque<List<Action>> list) {
				return InheritanceGovernor.reduceByMerge(
						list, Action.class, this.caller
				);
			}
		};
		//No sense in returning anything but local compatibles
		List<Action> nonTransients = gov.retrieveFullyDerivedField(
				this, IMode.LOCAL_ONLY
		);
		
		//TODO: Buffer the creation of transient actions somehow
		
		/* The above call will only return the non-transient actions. The actual
		 * transient actions have to the spliced in now
		 * 
		 * This can lead to a stack overflow (see the annotation in the comments
		 * for createTransientActions()), so we use the thread-store to register
		 * that the current thread is trying to create transient actions.
		 */
		List<Action> transients;
		ThreadAssocStore tas = ThreadAssocStore.getInstance();
		String key = String.format(
				"project-%s-creates-transients", this.getFullName()
		);
		Object o = tas.getValue(key);
		if (o == null) {
			try {
				//We're not fetching transients; so we fetch them
				tas.setValue(key, this);
				transients = this.createVersionAwareTransientActions();
			} finally {
				tas.clear(key);
			}
		} else {
			//We are already fetching transients and have entered a recursion
			transients = Collections.emptyList();
		}
		
		List<Action> merge = new LinkedList<Action>();
		merge.addAll(nonTransients);
		merge.addAll(transients);
		
		// return the read only list to cause a failure on plugins who try to add an action here
		return Collections.unmodifiableList(merge);
	}

	public synchronized List<Action> getRawActions() {
		/* Do notice that the function below will not actually return all
		 * actions; as the override of createTransientActions() causes the
		 * super's transientActions field to be always empty to ensure that
		 * they are not accidentally saved in the version store.
		 */
		return super.getActions();
	}
	
	/**
	 * Overrides the super-function to always return an empty list. This is
	 * vitally important so that the super class' transientActions member is
	 * always kept empty.
	 * <p>
	 * Otherwise, you get the nasty problems that temporary actions contaminate
	 * the versioning archive and generally cause troubles during build.
	 * <p>
	 * The downside with generating this on-the-fly is, that some plugins
	 * themselves call {@link #getActions()} (maybe indirectly), which recurses
	 * back into calling {@link #createVersionAwareTransientActions()};
	 * <p>
	 * This will cause a stack overflow. The only way to fix this is to
	 * return an empty list if a recursion is detected.
	 * 
	 * @see #getActions()
	 * @see #getActions(IMode)
	 */
	@Override
	protected List<Action> createTransientActions() {
		return new LinkedList<Action>();
	}
	
	/**
	 * Creates a list of temporary {@link Action}s as they are contributed
	 * by the various Builders, Publishers, etc. from the correct version and
	 * with the the correct inheritance.
	 */
	protected List<Action> createVersionAwareTransientActions() {
		Vector<Action> ta = new Vector<Action>();
		
		// START Implementation from AbstractProject
		for (JobProperty<? super InheritanceProject> p : this.getAllProperties()) {
			ta.addAll(p.getJobActions(this));
		}
		
		for (TransientProjectActionFactory tpaf : FilteredTransientActionFactoryHelper.all()) {
			ta.addAll(Util.fixNull(tpaf.createFor(this))); // be defensive against null
		}
		// END Implementation from AbstractProject
		
		// START Implementation from Project
		for (BuildStep step : this.getBuildersList())
			ta.addAll(step.getProjectActions(this));
		for (BuildStep step : this.getPublishersList())
			ta.addAll(step.getProjectActions(this));
		for (BuildWrapper step : this.getBuildWrappersList())
			ta.addAll(step.getProjectActions(this));
		
		//FIXME: Triggers are not yet versioned! Correct this!
		for (Trigger trigger : this.getTriggers().values())
			ta.addAll(trigger.getProjectActions());
		// END Implementation from Project
		
		return ta;
	}
	
	public DescribableList<Builder, Descriptor<Builder>> getBuildersListForVersion(Long versionId) {
		return (DescribableList<Builder, Descriptor<Builder>>)this.versionStore.getObject(versionId, "buildersList");
	}
	
	@Override
	public DescribableList<Builder, Descriptor<Builder>> getBuildersList() {
		return this.getBuildersList(IMode.AUTO);
	}
	
	public DescribableList<Builder, Descriptor<Builder>> getBuildersList(
			IMode mode) {
		InheritanceGovernor<DescribableList<Builder, Descriptor<Builder>>> gov =
				new InheritanceGovernor<DescribableList<Builder, Descriptor<Builder>>>(
						"buildersList", SELECTOR.BUILDER, this) {
			@Override
			protected DescribableList<Builder, Descriptor<Builder>> castToDestinationType(Object o) {
				return castToDescribableList(o);
			}
			
			@Override
			public DescribableList<Builder, Descriptor<Builder>> getRawField(
					InheritanceProject ip) {
				return ip.getRawBuildersList();
			}
			
			@Override
			protected DescribableList<Builder, Descriptor<Builder>> reduceFromFullInheritance(
					Deque<DescribableList<Builder, Descriptor<Builder>>> list) {
				return InheritanceGovernor.reduceDescribableByMerge(list);
			}
		};
		
		return gov.retrieveFullyDerivedField(this, mode);
	}
	
	public DescribableList<Builder, Descriptor<Builder>> getRawBuildersList() {
		return super.getBuildersList();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
		return this.getBuildWrappersList(IMode.AUTO);
	}
	
	public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList(
			IMode mode) {
		InheritanceGovernor<DescribableList<BuildWrapper, Descriptor<BuildWrapper>>> gov =
				new InheritanceGovernor<DescribableList<BuildWrapper, Descriptor<BuildWrapper>>>(
						"buildWrappersList", SELECTOR.BUILD_WRAPPER, this) {
			@Override
			protected DescribableList<BuildWrapper, Descriptor<BuildWrapper>> castToDestinationType(Object o) {
				return castToDescribableList(o);
			}
			
			@Override
			public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getRawField(
					InheritanceProject ip) {
				return ip.getRawBuildWrappersList();
			}
			
			@Override
			protected DescribableList<BuildWrapper, Descriptor<BuildWrapper>> reduceFromFullInheritance(
					Deque<DescribableList<BuildWrapper, Descriptor<BuildWrapper>>> list) {
				return InheritanceGovernor.reduceDescribableByMerge(list);
			}
		};
		
		return gov.retrieveFullyDerivedField(this, mode);
	}
	
	public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getRawBuildWrappersList() {
		return super.getBuildWrappersList();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
		//TODO: The marking of these functions as synchronized can easily lead
		// to a deadlock: doConfigSubmit() -> buildDependencyGraph() -> getPublishersList()
		return this.getPublishersList(IMode.AUTO);
	}
	
	public synchronized DescribableList<Publisher,Descriptor<Publisher>> getPublishersList(
			IMode mode) {
		InheritanceGovernor<DescribableList<Publisher, Descriptor<Publisher>>> gov =
				new InheritanceGovernor<DescribableList<Publisher, Descriptor<Publisher>>>(
						"publishersList", SELECTOR.PUBLISHER, this) {
			@Override
			protected DescribableList<Publisher, Descriptor<Publisher>> castToDestinationType(Object o) {
				return castToDescribableList(o);
			}
			
			@Override
			public DescribableList<Publisher, Descriptor<Publisher>> getRawField(
					InheritanceProject ip) {
				return ip.getRawPublishersList();
			}
			
			@Override
			protected DescribableList<Publisher, Descriptor<Publisher>> reduceFromFullInheritance(
					Deque<DescribableList<Publisher, Descriptor<Publisher>>> list) {
				return InheritanceGovernor.reduceDescribableByMerge(list);
			}
		};
		
		return gov.retrieveFullyDerivedField(this, mode);
	}
	
	public synchronized DescribableList<Publisher, Descriptor<Publisher>> getRawPublishersList() {
		return super.getPublishersList();
	}
	
	
	/**
	 * Returns all triggers defined on this project; or if detected to be
	 * necessary, also all parents.
	 * <p>
	 * @return a map of triggers, might be empty, but never null
	 */
	public synchronized Map<TriggerDescriptor,Trigger> getTriggers() {
		return this.getTriggers(IMode.AUTO);
	}
	
	public synchronized Map<TriggerDescriptor,Trigger> getTriggers(IMode mode) {
		if (ProjectCreationEngine.instance.getTriggersAreInherited() != TriggerInheritance.INHERIT) {
			return this.getRawTriggers();
		}
		
		InheritanceGovernor<Collection<Trigger>> gov =
				new InheritanceGovernor<Collection<Trigger>>(
						"triggers", SELECTOR.MISC, this) {
			@Override
			protected Collection<Trigger> castToDestinationType(Object o) {
				try {
					return (Collection<Trigger>) o;
				} catch (ClassCastException e) {
					return null;
				}
			}
			
			@Override
			public Collection<Trigger> getRawField(InheritanceProject ip) {
				Map<TriggerDescriptor, Trigger> raw = ip.getRawTriggers();
				return raw.values();
			}
			
			@Override
			protected Collection<Trigger> reduceFromFullInheritance(Deque<Collection<Trigger>> list) {
				Collection<Trigger> out = new LinkedList<Trigger>();
				for (Collection<Trigger> sub : list) {
					out.addAll(sub);
				}
				return out;
			}
		};
		
		Collection<Trigger> triggers = gov.retrieveFullyDerivedField(this, mode);
		Map<TriggerDescriptor,Trigger> out = new HashMap<TriggerDescriptor,Trigger>();
		for (Trigger t : triggers) {
			Trigger copied = this.getReparentedTrigger(t);
			out.put(copied.getDescriptor(), copied);
		}
		return out;
	}
	
	public synchronized Map<TriggerDescriptor,Trigger> getRawTriggers() {
		return super.getTriggers();
	}
	
	/**
	 * Gets the specific trigger, or null if the property is not configured for this job.
	 */
	public <T extends Trigger> T getTrigger(Class<T> clazz) {
		return this.getTrigger(clazz, IMode.AUTO);
	}
	
	public <T extends Trigger> T getTrigger(Class<T> clazz, IMode mode) {
		if (ProjectCreationEngine.instance.getTriggersAreInherited() != TriggerInheritance.INHERIT) {
			return this.getRawTrigger(clazz);
		}
		
		final Class<T> fClazz = clazz;
		InheritanceGovernor<T> gov =
				new InheritanceGovernor<T>(
						"triggers", SELECTOR.MISC, this) {
			@Override
			protected T castToDestinationType(Object o) {
				try {
					return (T) o;
				} catch (ClassCastException e) {
					return null;
				}
			}
			
			@Override
			public T getRawField(InheritanceProject ip) {
				return ip.getRawTrigger(fClazz);
			}
			
			/*
			@Override
			protected T reduceFromFullInheritance(Deque<T> list) {
				//Just select the last trigger; it will be of the correct class
				return list.getLast();
			}
			*/
		};
		
		//Return a trigger that is guaranteed to be owned by the current project
		T trigger = gov.retrieveFullyDerivedField(this, mode);
		return getReparentedTrigger(trigger);
	}
	
	public <T extends Trigger> T getRawTrigger(Class<T> clazz) {
		return super.getTrigger(clazz);
	}
	
	
	public Map<JobPropertyDescriptor, JobProperty<? super InheritanceProject>> getProperties() {
		return this.getProperties(IMode.AUTO);
	}
	
	public Map<JobPropertyDescriptor, JobProperty<? super InheritanceProject>> getProperties(IMode mode) {
		List<JobProperty<? super InheritanceProject>> lst = this.getAllProperties(mode);
		if (lst == null || lst.isEmpty()) {
			return Collections.emptyMap();
		}
		
		HashMap<JobPropertyDescriptor, JobProperty<? super InheritanceProject>> map =
				new HashMap<JobPropertyDescriptor, JobProperty<? super InheritanceProject>>();
		
		for (JobProperty<? super InheritanceProject> prop : lst) {
			map.put(prop.getDescriptor(), prop);
		}
		
		return map;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Exported(name="property",inline=true)
	public List<JobProperty<? super InheritanceProject>> getAllProperties() {
		return this.getAllProperties(IMode.AUTO);
	}
	
	public List<JobProperty<? super InheritanceProject>> getAllProperties(IMode mode) {
		//Fetching the variance of the current project; it is necessary
		//to access the correct compatibility setting in the correct parent
		final InheritanceProject rootProject = this;
		
		InheritanceGovernor<List<JobProperty<? super InheritanceProject>>> gov =
				new InheritanceGovernor<List<JobProperty<? super InheritanceProject>>>(
						"properties", SELECTOR.PARAMETER, this) {
			@Override
			protected List<JobProperty<? super InheritanceProject>> castToDestinationType(Object o) {
				return castToList(o);
			}
			
			@Override
			public List<JobProperty<? super InheritanceProject>> getRawField(
					InheritanceProject ip) {
				return ip.getRawAllProperties();
			}
			
			@Override
			protected List<JobProperty<? super InheritanceProject>> reduceFromFullInheritance(
					Deque<List<JobProperty<? super InheritanceProject>>> list) {
				//First, we add the variances for the root project
				InheritanceParametersDefinitionProperty variance = 
						rootProject.getVarianceParameters();
				if (variance != null) {
					List<JobProperty<? super InheritanceProject>> varLst =
							new LinkedList<JobProperty<? super InheritanceProject>>();
					varLst.add(variance);
					list.addLast(varLst);
				}
				return InheritanceGovernor.reduceByMerge(
						list, JobProperty.class, this.caller
				);
			}
		};
		
		return gov.retrieveFullyDerivedField(this, mode);
	}
	
	/**
	 * This method will fetch all properties defined for the current project
	 * and only those defined on it.
	 * <p>
	 * There are two complications though:
	 * <ol>
	 * <li>
	 * 		{@link ParametersDefinitionProperty} instances need to be replaced
	 * 		with {@link InheritanceParametersDefinitionProperty} instances,
	 * 		to make sure that versioning details are correctly stored.
	 * 		Also, we wish to make sure that the more advanced Jelly-files from
	 * 		the latter class are used for build-purposes.
	 * </li>
	 * <li>
	 * 		Variances define additional properties; so that we can make sure
	 * 		to splice-in those additional properties if a request for
	 * 		parameters comes from a direct child with the correct variance.
	 * </li>
	 * </ol>
	 * 
	 * @param rootProject the project from which the call for inherited
	 * properties originally came from.
	 * @param rootParents the parents of that project. Passed to prevent having
	 * to repeatedly access (versioning overhead!).
	 * @return
	 */
	public List<JobProperty<? super InheritanceProject>> getRawAllProperties() {
		LinkedList<JobProperty<? super InheritanceProject>> out =
				new LinkedList<JobProperty<? super InheritanceProject>>();
		
		//First, we add the global properties defined for this project
		List<JobProperty<? super InheritanceProject>> origProps =
				super.getAllProperties();
		
		//Filling the output list with the adjusted original properties
		for (JobProperty<? super InheritanceProject> prop : origProps) {
			if (!(prop instanceof ParametersDefinitionProperty)) {
				out.add(prop);
				continue;
			}
			//Converting a PDP to an IPDP
			ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) prop;
			InheritanceParametersDefinitionProperty ipdp = 
					new InheritanceParametersDefinitionProperty(
							pdp.getOwner(),
							pdp
					);
			out.add(ipdp);
		}
		return out;
	}
	
	public InheritanceParametersDefinitionProperty getVarianceParameters() {
		if (this.isTransient == false) {
			//No variance is or can possibly be defined
			return null;
		}
		
		//Fetch parents of this project; if any
		List<InheritanceProject> parLst = this.getParentProjects();
		if (parLst == null || parLst.size() < 2) {
			return null;
		}
		
		//Now, determine which parent carries our definition
		for (InheritanceProject ip : parLst) {
			if (ip == null) { continue; }
			
			//A project carrying a variance MUST be the prefix of our name
			if (this.name.startsWith(ip.name) == false) {
				continue;
			}
			
			List<AbstractProjectReference> compatLst =
					ip.getCompatibleProjects();
			if (compatLst == null) { continue; }
			
			for (AbstractProjectReference apr : compatLst) {
				if (!(apr instanceof ParameterizedProjectReference)) {
					continue;
				}
				ParameterizedProjectReference ppr =
						(ParameterizedProjectReference) apr;
				String projVar = ppr.getVariance();
				
				//Checking if the variance do not match up
				if (this.variance == null) {
					if (projVar != null) {
						continue;
					}
				} else {
					if (projVar == null ||
							!this.variance.equals(ppr.getVariance())) {
						continue;
					}
				}
				
				//Now, generating the full name and comparing
				String compatName = ProjectCreationEngine.generateNameFor(
						ppr.getVariance(), ip.name, ppr.getName()
				);
				if (!this.name.equals(compatName)) {
					continue;
				}
				
				//The correct variance description was found; adding its parameters
				InheritanceParametersDefinitionProperty ipdp =
						new InheritanceParametersDefinitionProperty(
								this, ppr.getParameters()
						);
				return ipdp;
			}
		}
		
		//No variance found
		return null;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public <T extends JobProperty> T getProperty(Class<T> clazz) {
		return this.getProperty(clazz, IMode.AUTO);
	}
	
	public <T extends JobProperty> T getProperty(Class<T> clazz, IMode mode) {
		/* Note: getAllProperties returns a list of properties in order of
		 * inheritance. Therefore, properties might be defined twice. In these
		 * cases, we need to return the last property.
		 */
		List<JobProperty<? super InheritanceProject>> props =
				this.getAllProperties(mode);
		
		//Checking if we can reverse-iterate the list for more efficiency
		if (props instanceof Deque) {
			Iterator<JobProperty<? super InheritanceProject>> rIter =
					((Deque) props).descendingIterator();
			while (rIter.hasNext()) {
				JobProperty p = rIter.next();
				if (clazz.isInstance(p))
					return clazz.cast(p);
			}
		} else {
			for (JobProperty p : props) {
				if (clazz.isInstance(p))
					return clazz.cast(p);
			}
		}
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public JobProperty getProperty(String className) {
		return this.getProperty(className, IMode.AUTO);
	}
	
	public JobProperty getProperty(String className, IMode mode) {
		for (JobProperty p : this.getAllProperties(mode)) {
			if (p.getClass().getName().equals(className)) {
				return p;
			}
		}
		return null;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public Collection<?> getOverrides() {
		return this.getOverrides(IMode.AUTO);
	}
	
	public Collection<?> getOverrides(IMode mode) {
		List<Object> r = new ArrayList<Object>();
		for (JobProperty<? super InheritanceProject> p : this.getAllProperties(mode)) {
			r.addAll(p.getJobOverrides());
		}
		return r;
	}
	
	/**
	 * This needs to be overridden, because {@link AbstractProject} reads the
	 * properties field directly; which circumvents inheritance.
	 */
	@Override
	public List<SubTask> getSubTasks() {
		List<SubTask> r = new ArrayList<SubTask>();
		r.add(this);
		for (SubTaskContributor euc : SubTaskContributor.all()) {
			r.addAll(euc.forProject(this));
		}
		for (JobProperty<?> p : this.getAllProperties()) {
			r.addAll(p.getSubTasks());
		}
		return r;
	}
	
	public synchronized List<ParameterDefinition> getParameters() {
		return this.getParameters(IMode.AUTO);
	}
	
	public synchronized List<ParameterDefinition> getParameters(IMode mode) {
		ParametersDefinitionProperty pdp =
				this.getProperty(ParametersDefinitionProperty.class, mode);
		if (pdp == null) {
			return new LinkedList<ParameterDefinition>();
		}
		return pdp.getParameterDefinitions();
	}
	
	
	@Override
	public SCM getScm() {
		return getScm(IMode.AUTO);
	}
	
	public SCM getScm(IMode mode) {
		InheritanceGovernor<SCM> gov = 
				new InheritanceGovernor<SCM>(
						"scm", SELECTOR.MISC, this) {
			@Override
			protected SCM castToDestinationType(
					Object o) {
				return (o instanceof SCM) ? (SCM) o : null;
			}
			
			@Override
			public SCM getRawField(
					InheritanceProject ip) {
				return ip.getRawScm();
			}
			
			@Override
			protected SCM reduceFromFullInheritance(Deque<SCM> list) {
				if (list == null || list.isEmpty()) {
					return new NullSCM();
				}
				//Return the SCM that was defined last and is not a NullSCM
				Iterator<SCM> iter = list.descendingIterator();
				while (iter.hasNext()) {
					SCM scm = iter.next();
					if (scm != null && !(scm instanceof NullSCM)) {
						return scm;
					}
				}
				//All SCMs are NullSCMs; so it does not matter which one to return
				return list.peekLast();
			}
		};
		
		SCM scm = gov.retrieveFullyDerivedField(this, mode);
		
		//We may not return null directly
		return (scm == null) ? new NullSCM() : scm;
	}
	
	public SCM getRawScm() {
		return super.getScm();
	}
	
	
	@Override
	public int getQuietPeriod() {
		Integer i = this.getQuietPeriodObject();
		return (i != null) ? i : super.getQuietPeriod();
	}
	
	public Integer getQuietPeriodObject() {
		InheritanceGovernor<Integer> gov =
				new InheritanceGovernor<Integer>(
						"quietPeriod", SELECTOR.MISC, this) {
			@Override
			protected Integer castToDestinationType(
					Object o) {
				return (o instanceof Integer) ? (Integer) o : null;
			}
			
			@Override
			public Integer getRawField(
					InheritanceProject ip) {
				return ip.getRawQuietPeriod();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public Integer getRawQuietPeriod() {
		if (super.getHasCustomQuietPeriod()) {
			return super.getQuietPeriod();
		} else {
			return null;
		}
	}
	
	@Override
	public boolean getHasCustomQuietPeriod() {
		Integer i = this.getQuietPeriodObject();
		return i != null;
	}
	
	
	@Override
	public int getScmCheckoutRetryCount() {
		Integer i = this.getScmCheckoutRetryCountObject();
		return (i != null) ? i : super.getScmCheckoutRetryCount();
	}
	
	public Integer getScmCheckoutRetryCountObject() {
		InheritanceGovernor<Integer> gov = 
				new InheritanceGovernor<Integer>(
						"scmCheckoutRetryCount", SELECTOR.MISC, this) {
			@Override
			protected Integer castToDestinationType(
					Object o) {
				return (o instanceof Integer) ? (Integer) o : null;
			}
			
			@Override
			public Integer getRawField(
					InheritanceProject ip) {
				return ip.getRawScmCheckoutRetryCount();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public Integer getRawScmCheckoutRetryCount() {
		if (super.hasCustomScmCheckoutRetryCount()) {
			return super.getScmCheckoutRetryCount();
		} else {
			return null;
		}
	}

	@Override
	public boolean hasCustomScmCheckoutRetryCount(){
		return this.getScmCheckoutRetryCountObject() != null;
	}

	
	public SCMCheckoutStrategy getScmCheckoutStrategy() {
		InheritanceGovernor<SCMCheckoutStrategy> gov = 
				new InheritanceGovernor<SCMCheckoutStrategy>(
						"scmCheckoutStrategy", SELECTOR.MISC, this) {
			@Override
			protected SCMCheckoutStrategy castToDestinationType(
					Object o) {
				return (o instanceof SCMCheckoutStrategy) ? (SCMCheckoutStrategy) o : null;
			}
			
			@Override
			public SCMCheckoutStrategy getRawField(
					InheritanceProject ip) {
				return ip.getRawScmCheckoutStrategy();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public SCMCheckoutStrategy getRawScmCheckoutStrategy() {
		return super.getScmCheckoutStrategy();
	}
	
	
	
	@Override
	public boolean blockBuildWhenDownstreamBuilding() {
		InheritanceGovernor<Boolean> gov = 
				new InheritanceGovernor<Boolean>(
						"blockBuildWhenDownstreamBuilding", SELECTOR.MISC, this) {
			@Override
			protected Boolean castToDestinationType(
					Object o) {
				return (o instanceof Boolean) ? (Boolean) o : null;
			}
			
			@Override
			public Boolean getRawField(
					InheritanceProject ip) {
				return ip.getRawBlockBuildWhenDownstreamBuilding();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public boolean getRawBlockBuildWhenDownstreamBuilding() {
		return super.blockBuildWhenDownstreamBuilding();
	}
	

	@Override
	public boolean blockBuildWhenUpstreamBuilding() {
		InheritanceGovernor<Boolean> gov = 
				new InheritanceGovernor<Boolean>(
						"blockBuildWhenUpstreamBuilding", SELECTOR.MISC, this) {
			@Override
			protected Boolean castToDestinationType(
					Object o) {
				return (o instanceof Boolean) ? (Boolean) o : null;
			}
			
			@Override
			public Boolean getRawField(
					InheritanceProject ip) {
				return ip.getRawBlockBuildWhenUpstreamBuilding();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public boolean getRawBlockBuildWhenUpstreamBuilding() {
		return super.blockBuildWhenUpstreamBuilding();
	}
	
	@Override
	public String getCustomWorkspace() {
		InheritanceGovernor<String> gov = 
				new InheritanceGovernor<String>(
						"customWorkspace", SELECTOR.MISC, this) {
			@Override
			protected String castToDestinationType(
					Object o) {
				return (o instanceof String) ? (String) o : null;
			}
			
			@Override
			public String getRawField(
					InheritanceProject ip) {
				return ip.getRawCustomWorkspace();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public String getRawCustomWorkspace() {
		return super.getCustomWorkspace();
	}
	
	
	public String getParameterizedWorkspace() {
		InheritanceGovernor<String> gov = 
				new InheritanceGovernor<String>(
						"parameterizedWorkspace", SELECTOR.MISC, this) {
			@Override
			protected String castToDestinationType(
					Object o) {
				return (o instanceof String) ? (String) o : null;
			}
			
			@Override
			public String getRawField(
					InheritanceProject ip) {
				return ip.getRawParameterizedWorkspace();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, IMode.AUTO);
	}
	
	public String getRawParameterizedWorkspace() {
		return this.parameterizedWorkspace;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * 
	 * @deprecated as of 1.503
	 *	  Use {@link #getBuildDiscarder()}.
	 */
	@Override
	public LogRotator getLogRotator() {
		BuildDiscarder d = this.getBuildDiscarder();
		if (d instanceof LogRotator) {
			return (LogRotator) d;
		}
		return null;
	}
	
	/**
	 * @deprecated as of 1.503
	 *	  Use {@link #getBuildDiscarder()}.
	 *  
	 * @see #getLogRotator()
	 */
	public LogRotator getLogRotator(IMode mode) {
		BuildDiscarder d = this.getBuildDiscarder(mode);
		if (d instanceof LogRotator) {
			return (LogRotator) d;
		}
		return null;
	}
	
	@Override
	public BuildDiscarder getBuildDiscarder() {
		return this.getBuildDiscarder(IMode.AUTO);
	}
	
	public BuildDiscarder getBuildDiscarder(IMode mode) {
		InheritanceGovernor<BuildDiscarder> gov =
				new InheritanceGovernor<BuildDiscarder>(
						"logRotator", SELECTOR.MISC, this) {
			@Override
			protected BuildDiscarder castToDestinationType(
					Object o) {
				if (o instanceof BuildDiscarder) {
					return (BuildDiscarder) o;
				} else if (o instanceof LogRotator) {
					// Old-style log-rotator object; LR is a subtype of BD
					return (LogRotator) o;
				}
				return null;
			}
			
			@Override
			public BuildDiscarder getRawField(
					InheritanceProject ip) {
				return ip.getRawBuildDiscarder();
			}
		};
		
		return gov.retrieveFullyDerivedField(this, mode);
	}
	
	public BuildDiscarder getRawBuildDiscarder() {
		return super.getBuildDiscarder();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Contrary to all the other properties methods, this will ALWAYS return
	 * the fully inherited version and will cache the result.
	 * <br/>
	 * This is done, because the only time when no inheritance is needed, is
	 * when the project is configured, and this will call
	 * {@link #getAssignedLabel(IMode)} instead with the {@link IMode#LOCAL_ONLY}
	 * set.
	 * <p>
	 * The reason for the caching is, that this method is called quite often
	 * by {@link Queue#maintain()}, a function that potentially blocks the
	 * entire server from progressing with builds.
	 * <br/>
	 * Thus, this method must take the minimum possible amount of time, which
	 * means that reflection is to expensive, as well as generating the label
	 * from scratch.
	 * <p>
	 * This has the downside of this method ignoring versioning completely,
	 * which might affect the result of this call through changing the
	 * inheritance.
	 * This is an accepted break, compared to the potential slowdown of
	 * {@link Queue#maintain()}.
	 */
	public Label getAssignedLabel() {
		//Check if there's a cached value
		Object cached = onChangeBuffer.get(this, "maintenanceAssignedLabel");
		if (cached != null && cached instanceof Label) {
			return (Label) cached;
		}
		
		//Generate a new label, forcing inheritance
		Label lbl = this.getAssignedLabel(IMode.INHERIT_FORCED);
		if (lbl == null) {
			lbl = super.getAssignedLabel();
		}
		
		//Caching the result
		if (lbl != null) {
			onChangeBuffer.set(this, "maintenanceAssignedLabel", lbl);
		}
		return lbl;
	}
	
	public Label getAssignedLabel(IMode mode) {
		InheritanceGovernor<Label> gov =
				new InheritanceGovernor<Label>(
						"assignedLabel", SELECTOR.MISC, this) {
			@Override
			protected Label castToDestinationType(
					Object o) {
				if (o instanceof Label) {
					return (Label) o;
				}
				return null;
			}
			
			@Override
			public Label getRawField(
					InheritanceProject ip) {
				return ip.getRawAssignedLabel();
			}
			
			@Override
			protected Label reduceFromFullInheritance(Deque<Label> list) {
				//We simply join the labels via the AND operator
				Label out = null;
				if (list == null || list.isEmpty()) { return out; }
				for (Label l : list) {
					if (l == null) { continue; }
					out = (out == null) ? l : out.and(l);
				}
				return out;
			}
		};
		
		//Generate the label on this node
		Label lbl = gov.retrieveFullyDerivedField(this, mode);
		
		//Checking if the node-label-for-testing needs to be added
		Label magic = ProjectCreationEngine.instance.getMagicNodeLabelForTestingValue();
		//Check if no magic label is set
		if (magic == null || magic.isEmpty()) {
			return lbl;
		}
		if (lbl != null) {
			String labelExpr = lbl.getExpression();
			String magicExpr = magic.getExpression();
			if (labelExpr.contains(magicExpr)) {
				//The label is already referencing the magic; no appending needed
				return lbl;
			}
		}
		//Otherwise, we need to add the magic label; but only when building
		if (InheritanceGovernor.inheritanceLookupRequired(this)) {
			return (lbl == null) ? magic.not() : lbl.and(magic.not());
		}
		
		//No appending of the magic value is necessary
		return lbl;
	}
	
	public Label getRawAssignedLabel() {
		if (this.isTransient) {
			//Transient projects do not have a label, they merely inherit
			return null;
		}
		return super.getAssignedLabel();
	}
	
	@Override
	public String getAssignedLabelString() {
		if (InheritanceGovernor.inheritanceLookupRequired(this) == false) {
			return super.getAssignedLabelString();
		}
		Label lbl = this.getAssignedLabel();
		if (lbl == null) {
			return super.getAssignedLabelString();
		}
		return lbl.getExpression();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Exported @Override
	public boolean isConcurrentBuild() {
		return this.isConcurrentBuild(false);
	}
	
	public boolean isConcurrentBuild(IMode mode) {
		InheritanceGovernor<Boolean> gov =
				new InheritanceGovernor<Boolean>(
						"concurrentBuild", SELECTOR.MISC, this) {
			@Override
			protected Boolean castToDestinationType(
					Object o) {
				if (o instanceof Boolean) {
					return (Boolean) o;
				}
				return null;
			}
			
			@Override
			public Boolean getRawField(
					InheritanceProject ip) {
				return ip.isRawConcurrentBuild();
			}
		};
		
		Boolean b = gov.retrieveFullyDerivedField(this, mode);
		return (b != null) ? b : false;
	}
	
	public boolean isRawConcurrentBuild() {
		return super.isConcurrentBuild();
	}
	
	
	/**
	 * In Vanilla-Jenkins, this method is really just a glorious wrapper around
	 * the following call:
	 * <p>
	 * <code>return getProperty(ParametersDefinitionProperty.class) != null;</code>
	 * <p>
	 * That means, its entire inheritance-based implementation would already
	 * be covered by {@link #getProperty(Class)}. But unfortunately, this is
	 * not something we can rely on, as this function &mdash; even though it's
	 * just a wrapper &mdash; fulfills a very specific role:
	 * <p>
	 * When Jenkins creates the sidepanel of a job, it queries this function
	 * to determine, whether the "Build Now" button should trigger a POST (if
	 * not parameterized) or a GET (if parameters need to be queried). Thus,
	 * this function <b>must always</b> return true, if <i>any</i> parent
	 * project is parameterized.
	 * <p>
	 * As such, contrary to the other functions, this function must
	 * <i>always</i> explore full inheritance. 
	 */
	@Override
	public boolean isParameterized() {
		ParametersDefinitionProperty pdp =
				this.getProperty(ParametersDefinitionProperty.class, IMode.INHERIT_FORCED);
		return pdp != null;
	}
	
	public boolean isRawParameterized() {
		return super.isParameterized();
	}
	
	
	@Override
	public boolean isBuildable() {
		if (!super.isBuildable()) {
			log.fine(String.format("%s not buildable; super.isBuildable() is false", this.getFullName()));
			return false;
		}
		//Then, we check if it's an abstract job
		if (this.isAbstract) {
			log.fine(String.format("%s not buildable; project is abstract", this.getFullName()));
			return false;
		}
		
		//Then, we check if there's a parameter inheritance issue with the
		//user selected version
		AbstractMap.SimpleEntry<Boolean, String> paramCheck =
				this.getParameterSanity();
		if (paramCheck.getKey() == false) {
			log.fine(String.format("%s not buildable; Parameter inconsistency: %s", this.getFullName(), paramCheck.getValue()));
			return false;
		}
		
		// Otherwise, we allow things to proceed
		return true;
	}
	
	public boolean isConcurrentBuild(boolean forcedInherit) {
		//Checking if we should not return an inherited value
		//FIXME: FIX THIS!
		//if (!inheritanceLookupRequired(forcedInherit)) {
		//	return super.isConcurrentBuild();
		//}
		boolean isConc = super.isConcurrentBuild();
		if (isConc) { return true; }
		//Otherwise, check the parents
		for (AbstractProjectReference apr: this.getParentReferences()) {
			if (apr == null || apr.getProject() == null) {
				continue;
			}
			if (apr.getProject().isConcurrentBuild(true)) {
				return true;
			}
		}
		return false;
	}
	
	
	
	// === INHERITANCE AND VERSIONING HELPER CLASSES AND MEMBERS ===
	
	public static void setVersioningMap(Map<String, Long> map) {
		StaplerRequest req = Stapler.getCurrentRequest();
		//Saving the versioning map into the current request
		if (req != null) {
			req.setAttribute("versions", map);
			//Removing the versioning buffer
			req.removeAttribute("versionedObjectBuffer");
		}
	}
	
	public static void setVersioningMapInThread(Map<String, Long> map) {
		//Setting the versioning for the current thread, in case the request is unavailable
		ThreadAssocStore.getInstance().setValue("versions", map);
	}
	
	public static void unsetVersioningMap() {
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req != null) {
			//Saving the versioning to the request
			req.removeAttribute("versions");
			//Removing the versioning buffer
			req.removeAttribute("versionedObjectBuffer");
		}
		//Unsetting the versioning in the thread (in case it was set)
		ThreadAssocStore.getInstance().clear("versions");
	}
	
	
	// === GUI ACCESS METHODS ===

	public boolean getIsTransient() {
		return this.isTransient;
	}
	
	
	public String getCreationClass() {
		return this.creationClass;
	}
	
	
	
	// === RELATIONSHIP ACCESS METHODS ===
	
	
	private static class ProjectGraphNode {
		public HashSet<String> parents = new HashSet<String>();
		public HashSet<String> mates = new HashSet<String>();
		public HashSet<String> children = new HashSet<String>();
	}
	
	public static Map<String, ProjectGraphNode> getConnectionGraph() {
		Object obj = onChangeBuffer.get(null, "getConnectionGraph");
		if (obj != null && obj instanceof Map) {
			return (Map) obj;
		}
		
		Map<String, ProjectGraphNode> map =
				new HashMap<String, ProjectGraphNode>();
		
		for (InheritanceProject ip : getProjectsMap().values()) {
			String currName = ip.getName();
			ProjectGraphNode currNode = (map.containsKey(currName))
					? map.get(currName)
					: new ProjectGraphNode();
			
			for (AbstractProjectReference apr : ip.getParentReferences()) {
				String parName = apr.getName();
				currNode.parents.add(parName);
				ProjectGraphNode parNode = (map.containsKey(parName))
						? map.get(parName)
						: new ProjectGraphNode();
				parNode.children.add(currName);
				map.put(parName, parNode);
			}
			for (AbstractProjectReference apr : ip.getCompatibleProjects()) {
				currNode.mates.add(apr.getName());
			}
			map.put(currName, currNode);
		}
		
		onChangeBuffer.set(null, "getConnectionGraph", map);
		return map;
	}

	public Collection<InheritanceProject> getRelationshipsOfType(Relationship.Type type) {
		Collection<InheritanceProject> relationshipsOfType = new LinkedList<InheritanceProject>();
		Map<InheritanceProject, Relationship> relationships = getRelationships();
		
		/*
		 * we are interested in getting the children ordered by last build time
		 * if last build time exists
		 */
		if (type == Relationship.Type.CHILD) {
			relationshipsOfType = getChildrenByBuildDate(relationships);
		} else if (type == Relationship.Type.PARENT) {
			LinkedList<InheritanceProject> parents = new LinkedList<InheritanceProject>();
			for (java.util.Map.Entry<InheritanceProject, Relationship> project : relationships.entrySet()) {
				if (Relationship.Type.PARENT == project.getValue().type) {
					parents.add(project.getKey());
				}
			}
			relationshipsOfType = parents;
		}
		
		return relationshipsOfType;
	}
	
	private class RunTimeComparator implements Comparator<InheritanceProject> {
		public int compare(InheritanceProject a, InheritanceProject b) {
			InheritanceBuild aBuild = a.getLastBuild();
			InheritanceBuild bBuild = b.getLastBuild();
			if (aBuild == null) {
				int retVal = (bBuild == null) ? a.getFullName().compareTo(b.getFullName()) : 1;
				return retVal;
			} else if (bBuild == null) {
				int retVal = (aBuild == null) ? a.getFullName().compareTo(b.getFullName()) : -1;
				return retVal;
			}
			return bBuild.getTime().compareTo(aBuild.getTime());
		}
	}
	
	/**
	 * we are interested in getting the children ordered by last build time
	 * if last build time exists
	 */
	public Collection<InheritanceProject> getChildrenByBuildDate(Map<InheritanceProject, Relationship> relationships) {
		//Using a TreeSet to do the sorting for last-build-time for us
		TreeSet<InheritanceProject> tree = new TreeSet<InheritanceProject>(
				new RunTimeComparator()
		);
		Map<InheritanceProject, Relationship> relations = this.getRelationships();
		if (relations.isEmpty()) { return tree; }
		//Filtering for buildable children
		for (Map.Entry<InheritanceProject, Relationship> pair : relations.entrySet()) {
			InheritanceProject child = pair.getKey();
			Relationship.Type type = pair.getValue().type;
			//Excluding non-childs
			if (type != Relationship.Type.CHILD) { continue; }
			//The child is buildable, so add it to the tree
			tree.add(child);
		}
		return tree;
	}
	
	public Map<InheritanceProject, Relationship> getRelationships() {
		Object obj = onInheritChangeBuffer.get(this, "getRelationships");
		if (obj != null && obj instanceof Map) {
			return (Map) obj;
		}
		
		//Creating the returned map and pre-filling it with empty lists
		Map<InheritanceProject, Relationship> map =
				new HashMap<InheritanceProject, Relationship>();
		
		//Preparing the set of projects that were already explored
		HashSet<String> seenProjects = new HashSet<String>();
		
		//Fetching the map of all projects and their connections
		Map<String, ProjectGraphNode> connGraph = getConnectionGraph();
		
		//Fetching the node for the current (this) project
		ProjectGraphNode node = connGraph.get(this.getName());
		if (node == null) { return map; }
		
		//Mates can be filled quite easily
		for (String mate : node.mates) {
			InheritanceProject p = InheritanceProject.getProjectByName(mate);
			ProjectGraphNode mateNode = connGraph.get(mate);
			boolean isLeaf = (mateNode == null) ? true : mateNode.children.isEmpty();
			if (p == null) { continue; }
			//Checking if we've seen this mate already
			if (!seenProjects.contains(p.getName())) {
				map.put(p, new Relationship(Relationship.Type.MATE, 0, isLeaf));
				seenProjects.add(p.getName());
			}
		}
		
		//Exploring parents
		int distance = 1;
		seenProjects.clear();
		LinkedList<InheritanceProject> cOpen =
				new LinkedList<InheritanceProject>();
		LinkedList<InheritanceProject> nOpen =
				new LinkedList<InheritanceProject>();
		cOpen.add(this);
		while (!cOpen.isEmpty()) {
			InheritanceProject ip = cOpen.pop();
			if (ip == null || seenProjects.contains(ip.getName())) {
				continue;
			}
			seenProjects.add(ip.getName());
			
			node = connGraph.get(ip.getName());
			if (ip == null || node == null) { continue; }
			//Adding all parents
			for (String parent : node.parents) {
				InheritanceProject par = InheritanceProject.getProjectByName(parent);
				if (par == null || seenProjects.contains(parent)) {
					continue;
				}
				map.put(par, new Relationship(Relationship.Type.PARENT, distance, false));
				nOpen.push(par);
			}
			if (cOpen.isEmpty() && !nOpen.isEmpty()) {
				cOpen = nOpen;
				nOpen = new LinkedList<InheritanceProject>();
				distance++;
			}
		}
		
		//Exploring children
		distance = 1; seenProjects.clear();
		cOpen.clear(); nOpen.clear();
		cOpen.add(this);
		while (!cOpen.isEmpty()) {
			InheritanceProject ip = cOpen.pop();
			if (ip == null || seenProjects.contains(ip.getName())) {
				continue;
			}
			seenProjects.add(ip.getName());
			
			node = connGraph.get(ip.getName());
			if (ip == null || node == null) { continue; }
			//Adding all parents
			for (String child : node.children) {
				InheritanceProject cProj = InheritanceProject.getProjectByName(child);
				if (cProj == null || seenProjects.contains(child)) {
					continue;
				}
				ProjectGraphNode childNode = connGraph.get(child);
				boolean isLeaf = (childNode == null) ? true : childNode.children.isEmpty();
				map.put(cProj, new Relationship(Relationship.Type.CHILD, distance, isLeaf));
				nOpen.push(cProj);
			}
			if (cOpen.isEmpty() && !nOpen.isEmpty()) {
				cOpen = nOpen;
				nOpen = new LinkedList<InheritanceProject>();
				distance++;
			}
		}
		
		onInheritChangeBuffer.set(this, "getRelationships", map);
		return map;
	}
	
	public List<Vector<String>> getRelatedProjects() {
		Object obj = onInheritChangeBuffer.get(this, "getRelatedProjects");
		if (obj != null && obj instanceof LinkedList) {
			return (LinkedList) obj;
		}
		
		LinkedList<Vector<String>> lst = new LinkedList<Vector<String>>();
		
		//Fetching the relationships of this project to others
		Map<InheritanceProject, Relationship> rels = this.getRelationships();
		for (Map.Entry<InheritanceProject, Relationship> entry : rels.entrySet()) {
			Relationship rel = entry.getValue();
			Vector<String> vec = new Vector<String>();
			vec.add(entry.getKey().getName());
			switch (rel.type) {
				case PARENT:
					vec.add(Messages.InheritanceProject_Relationship_Type_ParentDesc());
					break;
				case CHILD:
					vec.add(Messages.InheritanceProject_Relationship_Type_ChildDesc());
					break;
				case MATE:
					vec.add(Messages.InheritanceProject_Relationship_Type_MateDesc());
					break;
			}
			vec.add(Integer.toString(rel.distance));
			lst.add(vec);
		}
		
		onInheritChangeBuffer.set(this, "getRelatedProjects", lst);
		return lst;
	}
	
	private List<ScopeEntry> getFullParameterScope() {
		//Fetching the correct definition property
		ParametersDefinitionProperty pdp = this.getProperty(
				ParametersDefinitionProperty.class, IMode.INHERIT_FORCED
		);
		if (pdp == null) {
			//No parameters set, so we return an empty list
			return Collections.emptyList();
		}
		
		//Checking if it is a fully scoped inheritance-aware one; if yes, we
		//fetch the full scope of parameters.
		List<ScopeEntry> fullScope = null;
		if (pdp instanceof InheritanceParametersDefinitionProperty) {
			InheritanceParametersDefinitionProperty ipdp =
					(InheritanceParametersDefinitionProperty) pdp;
			fullScope = ipdp.getAllScopedParameterDefinitions();
		} else {
			String ownerName = (pdp.getOwner() != null)
					? pdp.getOwner().getName() : "";
			fullScope = new LinkedList<ScopeEntry>();
			for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
				fullScope.add(new ScopeEntry(ownerName, pd));
			}
		}
		
		if (fullScope != null) {
			return fullScope;
		} else {
			return Collections.emptyList();
		}
	}
	
	public List<ParameterDerivationDetails> getParameterDerivationList() {
		List<ParameterDerivationDetails> list =
				new LinkedList<ParameterDerivationDetails>();
		//Grab the full scope of all parameters
		List<ScopeEntry> fullScope = this.getFullParameterScope();
		
		int cnt = 0;
		for (ScopeEntry scope : fullScope) {
			String paramName = scope.param.getName();
			String projName = scope.owner;
			String detail = "";
			Object def = scope.param.getDefaultParameterValue().getShortDescription();
			
			
			if (scope.param instanceof InheritableStringParameterDefinition) {
				InheritableStringParameterDefinition ispd =
						(InheritableStringParameterDefinition) scope.param;
				StringBuilder b = new StringBuilder();
				b.append(ispd.getMustHaveDefaultValue());
				b.append("; ");
				b.append(ispd.getMustBeAssigned());
				detail = b.toString();
			}
			
			ParameterDerivationDetails pdd = new ParameterDerivationDetails(
				paramName, projName, detail, def
			);
			pdd.setOrder(cnt++);
			list.add(pdd);
		}
		
		return list;
	}
	

	// === SVGNode METHODS ===
	
	public String getSVGLabel() {
		return this.getName();
	}

	public String getSVGDetail() {
		List<ParameterDefinition> pLst = this.getParameters(IMode.LOCAL_ONLY);
		if (pLst == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		for (ParameterDefinition pd : pLst) {
			if (pd == null) { continue; }
			b.append(pd.getName());
			ParameterValue pv = pd.getDefaultParameterValue();
			if (pv != null && pv instanceof StringParameterValue) {
				b.append(": ");
				b.append(((StringParameterValue)pv).value);
			}
			b.append('\n');
		}
		
		if (b.length() > 0) {
			b.append("\r\n");
		}
		
		List<Builder> builders = this.getBuilders();
		String str = (builders == null || builders.size() != 1) ? "steps" : "step";
		int num = (builders == null) ? 0 : builders.size();
		b.append(String.format(
				"%d build %s\n", num, str
		));
		
		DescribableList<Publisher, Descriptor<Publisher>> pubs = this.getPublishersList();
		str = (pubs == null || pubs.size() != 1) ? "publishers" : "publisher";
		num = (pubs == null) ? 0 : pubs.size();
		b.append(String.format(
				"%d %s", num, str
		));
		
		
		
		return b.toString();
	}

	public URL getSVGLabelLink() {
		try {
			return new URL(this.getAbsoluteUrl());
		} catch (MalformedURLException ex) {
			return null;
		}
	}
	
	public Graph<SVGNode> getSVGRelationGraph() {
		Graph<SVGNode> out = new Graph<SVGNode>();
		
		LinkedList<InheritanceProject> open = new LinkedList<InheritanceProject>();
		HashSet<InheritanceProject> visited = new HashSet<InheritanceProject>();
		
		open.add(this);
		while (!open.isEmpty()) {
			InheritanceProject ip = open.pop();
			if (visited.contains(ip)) {
				continue;
			} else {
				visited.add(ip);
			}
			out.addNode(ip);
			
			for (InheritanceProject parent : ip.getParentProjects()) {
				open.add(parent);
				out.addNode(ip, parent);
			}
		}
		
		return out;
	}
	
	public String doRenderSVGRelationGraph() {
		return this.renderSVGRelationGraph(0, 0);
	}
	
	public String renderSVGRelationGraph(int width, int height) {
		SVGTreeRenderer tree = new SVGTreeRenderer(
				this.getSVGRelationGraph(), width, height
		);
		Document doc = tree.render();
		try {
			DOMSource source = new DOMSource(doc);
			StringWriter stringWriter = new StringWriter();
			StreamResult result = new StreamResult(stringWriter);
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			transformer.transform(source, result);
			return stringWriter.getBuffer().toString();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	
	
	// === MISC. HELPER METHODS ===
	
	
	/**
	 * Wrapper for {@link #hasCyclicDependency(String...)} with no new project
	 * references added on top of the existing ones.
	 */
	public final boolean hasCyclicDependency() {
		//TODO: Make this method version-aware
		
		//Checking if a result is buffered
		Object obj = onInheritChangeBuffer.get(this, "hasCyclicDependency");
		if (obj != null && obj instanceof Boolean) {
			return (Boolean) obj;
		}
		
		//Re-computing the result
		String[] arr = {};
		Boolean bufRes = this.hasCyclicDependency(arr);
		
		onInheritChangeBuffer.set(this, "hasCyclicDependency", bufRes);
		return bufRes;
	}
	
	/**
	 * Tests if this project's configuration leads to a cyclic, diamond or
	 * multiple dependency.<br/>
	 * <br/>
	 * See <a href="http://en.wikipedia.org/wiki/Cycle_detection">cycle detection</a> and
	 * <a href="http://en.wikipedia.org/wiki/Diamond_problem">diamond problem</a>.
	 * 
	 * @return true, if there is a cyclic, diamond or repeated dependency among
	 * this project's parents.
	 */
	public final boolean hasCyclicDependency(String... whenTheseProjectsAdded) {
		/* TODO: While this method runs reasonably fast, it is run very often
		 * As such, find a way to buffer the result across all projects and
		 * only rebuild if necessary.
		 */
		
		/* TODO: Further more, this method is not space-optimal
		 * See: http://en.wikipedia.org/wiki/Cycle_detection
		 * But do note that any replacement algorithm also, by contract, needs
		 * to detect multiple inheritance and its special case of diamond
		 * inheritance.
		 */
		
		//Preparing the set of project names that were seen at least once
		HashSet<String> closed = new HashSet<String>();
		
		//Creating the list of parent projects to still explore
		LinkedList<InheritanceProject> open = new LinkedList<InheritanceProject>();
		//And scheduling ourselves as the first to evaluate
		open.push(this);
		
		//And finally, creating a list of additional references the caller
		//wishes to eventually add to THIS project
		LinkedList<String> additionalRefs = new LinkedList<String>();
		for (String pName : whenTheseProjectsAdded) {
			//We need to ignore those, that we already refer to as parents
			//Do note that this makes direct multiple inheritance impossible
			//to detect in advance, but such errors should be obvious anyway
			boolean isAlreadyReferenced = false;
			for (AbstractProjectReference par : this.getParentReferences()) {
				if (par.getName().equals(pName)) {
					isAlreadyReferenced = true;
					break;
				}
			}
			if (!isAlreadyReferenced) {
				additionalRefs.add(pName);
			}
		}
		
		//Processing the open stack, checking if we're already met that parent
		//and if not, adding its parent to our open stack
		while(open.isEmpty() == false) {
			//Popping the first element
			InheritanceProject p = open.pop();
			//Checking if we've seen that parent already
			if (closed.contains(p.name)) {
				//Detected a cyclic dependency
				return true;
			}
			// Otherwise, we add all its parents to our open set
			for (AbstractProjectReference ref : p.getParentReferences()) {
				InheritanceProject refP = ref.getProject();
				if (refP != null) {
					open.push(refP);
				}
			}
			//And if the current object is active, we also need to check the
			//new future refs
			if (p == this && !additionalRefs.isEmpty()) {
				for (String ref : additionalRefs) {
					InheritanceProject ip = InheritanceProject.getProjectByName(ref);
					if (ip != null) {
						open.push(ip);
					}
				}
			}
			closed.add(p.name);
		}
		// If we reach this spot, there is no such dependency
		return false;
	}
	
	
	public final AbstractMap.SimpleEntry<Boolean, String> getParameterSanity() {
		//Creating a small local class to store sanity information
		final class SanityRestrictions {
			public Class<?> hasToBeOfThisClass;
			
			public boolean hasToHaveDefaultSet;
			public boolean hasToBeAssigned;
			
			public boolean hadDefaultSet;
			public IModes previousMode;
		}
		
		//Preparing a map of parameter name to restrictions
		HashMap<String, SanityRestrictions> resMap =
				new HashMap<String, SanityRestrictions>();
		
		//Fetch all parameters in the scope
		List<ScopeEntry> fullScope = this.getFullParameterScope();
		
		//Iterating through the parameters, and verifying their restrictions on-the-fly
		for (ScopeEntry scope : fullScope) {
			ParameterDefinition pd = scope.param;
			if (pd == null) { continue; }
			SanityRestrictions s = resMap.get(pd.getName());
			if (s == null) {
				//We've seen this PD for the first time
				s = new SanityRestrictions();
				s.hasToBeOfThisClass = pd.getClass();
				if (pd instanceof InheritableStringParameterDefinition) {
					InheritableStringParameterDefinition ispd =
							(InheritableStringParameterDefinition) pd;
					s.hasToHaveDefaultSet = ispd.getMustHaveDefaultValue();
					s.hasToBeAssigned = ispd.getMustBeAssigned();
					
					String defVal = ispd.getDefaultValue();
					s.hadDefaultSet = !(defVal == null || defVal.isEmpty());
					
					s.previousMode = ispd.getInheritanceModeAsVar();
				} else {
					s.hasToHaveDefaultSet = false;
					s.previousMode = IModes.OVERWRITABLE;
				}
				
				//No sense in checking this param instance further, as a
				//param can't make itself insane
				resMap.put(pd.getName(), s);
				continue;
			}
			
			/* Check if the scoped forms can be cast in at least one direction,
			 * which means that they share parenthood.
			 * This avoids an IntegerParamer becoming a StringParameter, but
			 * allows a PasswordParameter to merge with a StringParameter.
			 */
			boolean isScopeCastToCurrent = pd.getClass().isAssignableFrom(s.hasToBeOfThisClass);
			boolean isCurrentCastToScope = s.hasToBeOfThisClass.isAssignableFrom(pd.getClass());
			if (!(isScopeCastToCurrent || isCurrentCastToScope)) {
				return new AbstractMap.SimpleEntry<Boolean, String>(
						false, "Parameter '" + pd.getName() +
						"' redefined with different class name."
				);
			}
			
			if (s.previousMode == IModes.FIXED) {
				return new AbstractMap.SimpleEntry<Boolean, String>(
						false, "Fixed parameter '" + pd.getName() +
						"' may not be redefined at all."
				);
			}
			
			//Checking additional restrictions on ISPDs
			if (pd instanceof InheritableStringParameterDefinition) {
				InheritableStringParameterDefinition ispd =
						(InheritableStringParameterDefinition) pd;
				//We ignore references, as they can never invalidate flags
				//Otherwise, we check whether they unset values
				if (!(pd instanceof InheritableStringParameterReferenceDefinition)) {
					//Checking if the "force-default-value" flag is set by the new one
					if (ispd.getMustHaveDefaultValue()) {
						s.hasToHaveDefaultSet = true;
					}
					//Checking if the assignment flag was set by the new one
					if (ispd.getMustBeAssigned()) {
						s.hasToBeAssigned = true;
					}
					//Then, checking if the "force-default-value" flag was unset
					if (s.hasToHaveDefaultSet && !ispd.getMustHaveDefaultValue()) {
						return new AbstractMap.SimpleEntry<Boolean, String>(
								false, "Parameter '" + pd.getName() +
								"' may not unset the flag that ensures that a" +
								" default value is set."
								);
					}
					//Checking if the "must-be-assigned" flag was unset
					if (s.hasToBeAssigned && !ispd.getMustBeAssigned()) {
						return new AbstractMap.SimpleEntry<Boolean, String>(
								false, "Parameter '" + pd.getName() +
								"' may not unset the flag that ensures that a" +
								" final value is set before execution."
								);
					}
				}
				
				//Checking if overwriting causes a previous default to be lost
				String defVal = ispd.getDefaultValue();
				boolean defValNewlySet = !(defVal == null || defVal.isEmpty());
				
				try {
					switch(s.previousMode) {
						case OVERWRITABLE:
							//An overwrite always causes the default to be discarded
							s.hadDefaultSet = defValNewlySet;
							break;
						case EXTENSIBLE:
							//An extension does not overwrite an already set default
							if (!s.hadDefaultSet) {
								s.hadDefaultSet = defValNewlySet;
							}
							break;
						case FIXED:
							//FIXED parameters are ignored
							break;
						default:
							log.warning(
									"Detected invalid inheritance mode: " +
									s.previousMode.toString() + " on " +
									this.getName() + "->" + pd.getName()
							);
							break;
					}
				} finally {
					//In any case, we overwrite the sanity details with the new values
					s.previousMode = ispd.getInheritanceModeAsVar();
					s.hasToHaveDefaultSet = ispd.getMustHaveDefaultValue();
					s.hasToBeAssigned = ispd.getMustBeAssigned();
				}
			}
		}
		
		//Then, if the build is not abstract, we must check whether all values
		//that carry defaults actually had defaults defined at some point
		if (this.isAbstract == false) {
			for (Map.Entry<String, SanityRestrictions> e : resMap.entrySet()) {
				String name = e.getKey();
				SanityRestrictions s = e.getValue();
				
				if (s.hasToHaveDefaultSet && !s.hadDefaultSet) {
					return new AbstractMap.SimpleEntry<Boolean, String>(
							false, "Parameter '" + name + "' did not" +
							" have a default set. This is only allowed" +
							" if the project is marked as abstract."
					);
				}
			}
		}
		
		//If we reach this spot, everything checked out fine.
		return new AbstractMap.SimpleEntry<Boolean, String>(true, "");
	}
	
	public String getPronoun() {
		if (this.getIsTransient()) {
			return Messages.InheritanceProject_TransientPronounLabel();
		} else if (this.getCreationClass() != null) {
			return this.getCreationClass();
		} else {
			return super.getPronoun();
		}
	}


	/**
	 * {@inheritDoc}
	 * 
	 * The above is overridden in a way, that the Build-History widget is
	 * removed if the build is abstract and can't be run anyway.
	 * 
	 * This is ignored, in case there is a last build, though, to not
	 * hide any information.
	 */
	@Override
	public List<Widget> getWidgets() {
		List<Widget> widgets = super.getWidgets();
		if (!this.isBuildable() && this.getLastBuild() == null) {
			//Remove the history widgets
			List<Widget> strippedOffWidgets = new ArrayList<Widget>();
			for (Widget widget : widgets) {
				if (!(widget instanceof HistoryWidget<?, ?>)) {
					strippedOffWidgets.add(widget);
				}
			}
			return strippedOffWidgets;
		} else {
			return widgets;
		}
	}
	
	public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Class<? extends Job> clazz) {
		List<JobPropertyDescriptor> propertyDescriptors =
				JobPropertyDescriptor.getPropertyDescriptors(clazz);
		List<JobPropertyDescriptor> filteredPropertyDescriptors =
				new LinkedList<JobPropertyDescriptor>();
		for (JobPropertyDescriptor jobPropertyDescriptor : propertyDescriptors) {
			if (jobPropertyDescriptor.getClass().equals(ParametersDefinitionProperty.class) ||
					jobPropertyDescriptor.getClass().equals(ParametersDefinitionProperty.DescriptorImpl.class)) {
				filteredPropertyDescriptors.add(jobPropertyDescriptor);
			}
		}
		return filteredPropertyDescriptors;
	}
	
	
	
	// === HELPER METHODS FOR READONLY VIEW ===

	public Map<AbstractProjectReference, List<Builder>> getBuildersFor(
			Map<String, Long> verMap, Class<?> clazz) {
		//Set the current thread's versioning map
		if (verMap != null && !verMap.isEmpty()) {
			setVersioningMap(verMap);
		}
		//Loop over all parents and create a joined map of all builders
		Map<AbstractProjectReference, List<Builder>> out =
				new LinkedHashMap<AbstractProjectReference, List<Builder>>();
		
		List<AbstractProjectReference> refs =
				new LinkedList<AbstractProjectReference>(
						this.getAllParentReferences(SELECTOR.BUILDER)
				);
		
		//Add a reference to ourselves
		refs.add(new SimpleProjectReference(this.getFullName()));
		
		for (AbstractProjectReference apr : refs) {
			InheritanceProject ip = apr.getProject();
			if (ip == null) { continue; }
			List<Builder> bLst = ip.getBuildersList(IMode.LOCAL_ONLY).toList();
			if (clazz != null) {
				List<Builder> bSubLst = new LinkedList<Builder>();
				for (Builder b : bLst) {
					if (b != null && clazz.isAssignableFrom(b.getClass())) {
						bSubLst.add(b);
					}
				}
				out.put(apr, bSubLst);
			} else {
				out.put(apr, bLst);
			}
		}
		
		return out;
	}

	// === PROJECT DESCRIPTOR IMPLEMENTATION ===
	
	/**
	 * Returns the {@link Descriptor} for the parent object.<br/>
	 * <br/>
	 * The returned object should be a class-singleton that
	 * can be used to create an instance of its parent class and thereafter
	 * display a configuration dialog.<br/>
	 * As such, this class has the responsibility of creating a suitable
	 * instance, serving up the HTML/Jelly configuration fields, reading their
	 * values and modifying the created instance accordingly.<br/>
	 * <br/>
	 * Do note that the configuration-dialog for the object is displayed
	 * <i>after</i> the instance was created.<br/>
	 */
 	public DescriptorImpl getDescriptor() {
		return DESCRIPTOR;
	}

	@Extension(ordinal = 1000)
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final class DescriptorImpl extends AbstractProjectDescriptor {
		private final HashSet<String> projectsToBeCreatedTransient =
				new HashSet<String>();
		
		public final static Pattern urlJobPattern = Pattern.compile("/job/([^/]+)");
		
		public DescriptorImpl() {
			//Creating the static buffers of the IP class, if necessary
			InheritanceProject.createBuffers();
		}
		
		public String getDisplayName() {
			return Messages.InheritanceProject_DisplayName();
		}
		
		public String getDescription() {
			return "";
		}

		public InheritanceProject newInstance(ItemGroup parent, String name) {
			//Checking if the given name is on the list of transient jobs
			if (this.projectsToBeCreatedTransient.contains(name)) {
				this.projectsToBeCreatedTransient.remove(name);
				return new InheritanceProject(parent, name, true);
			} else {
				return new InheritanceProject(parent, name, false);
			}
		}
		
		public ListBoxModel doFillCreationClassItems() {
			ListBoxModel names = new ListBoxModel();
			for (CreationClass cl : ProjectCreationEngine.instance.getCreationClasses()) {
				names.add(cl.name);
			}
			//And also add an empty one, to select NO mating
			names.add("<None Specified>", "");
			return names;
		}
		
		/**
		 * Wrapper around {@link DescriptorImpl#doFillCreationClassItems()} to
		 * account for slightly different field names used by
		 * {@link InheritanceViewAction}'s groovy scripts.
		 */
		public ListBoxModel doFillProjectClassItems() {
			return this.doFillCreationClassItems();
		}
	
		public ListBoxModel doFillUserDesiredVersionItems() {
			ListBoxModel verBox = new ListBoxModel();
			
			InheritanceProject ip = this.getConfiguredProject();
			if (ip != null) {
				for (Long version : ip.getVersionIDs()) {
					verBox.add(version.toString());
				}
			} else {
				log.warning("Could not fetch or resolve project name");
			}
			
			return verBox;
		}
	
		/**
		 * This method identifies the project under configuration.
		 * 
		 * It first tries to do that by asking the request itself for its
		 * ancestor; but if that is unavailable it looks at the HTTP request
		 * to check for the name of the project under configuration.
		 * It then tries to retrieve the object associated with that name.
		 */
		public InheritanceProject getConfiguredProject(StaplerRequest req) {
			//Fetching the current request from the user
			if (req == null) { return null; }
			
			//Then, trying to fetch an ancestor
			InheritanceProject ip = req.findAncestorObject(
					InheritanceProject.class
			);
			if (ip != null) {
				return ip;
			}
			
			//If that failed; trying to decode the URL to get the project name
			String uri = req.getRequestURI();
			if (uri == null || uri.length() == 0) { return null; }
			Matcher m = urlJobPattern.matcher(uri);
			if (m == null || !m.find()) { return null; }
			String pName = m.group(1);
			if (pName == null || pName.length() == 0) { return null; }
			
			//Now that we have the name, we try to match it to a Project
			return InheritanceProject.getProjectByName(pName);
		}
		
		protected InheritanceProject getConfiguredProject() {
			return this.getConfiguredProject(Stapler.getCurrentRequest());
		}
		
		public synchronized void addProjectToBeCreatedTransient(String name) {
			//TODO: Do not allow this set to grow too long in case of error
			this.projectsToBeCreatedTransient.add(name);
		}
		
		public synchronized void dropProjectToBeCreatedTransient(String name) {
			this.projectsToBeCreatedTransient.remove(name);
		}
	}
}
