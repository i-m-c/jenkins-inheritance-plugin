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

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import hudson.DescriptorExtensionList;
import hudson.RelativePath;
import hudson.init.TermMilestone;
import hudson.init.Terminator;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.Project;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterReferenceDefinition;
import hudson.plugins.project_inheritance.projects.references.filters.IProjectReferenceFilter;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

//TODO: This reference class should be converted to AbstractProject
@ExportedBean(defaultVisibility=3)
public abstract class AbstractProjectReference implements Describable<AbstractProjectReference> {
	protected String name;
	
	/**
	 * Cache for the name<->project lookup. Useful, since many reference objects
	 * might look up the same project and the weak value references make it
	 * so a reference does not block cleanup of projects.
	 * <p>
	 * Also, the timeout avoids that rarely referenced objects pollute the
	 * map.
	 * <p>
	 * Important note: In Unit-tests, the values in here survive in between
	 * test cases, since the JVM and ClassLoader are not torn down. As such,
	 * this class needs to hook into the Jenkins shutdown and clean the lookup
	 * on shutdown.
	 */
	private static final Cache<String, InheritanceProject> nameLookup =
			CacheBuilder.newBuilder()
					.expireAfterWrite(5, TimeUnit.MINUTES)
					.weakValues()
					.build();
	
	/**
	 * This method makes sure the name lookup does not survive Jenkins tear-down
	 * in-between Unittests or Jenkins soft-restarts.
	 */
	@Terminator(before=TermMilestone.COMPLETED)
	public static void onJenkinsStop() {
		nameLookup.invalidateAll();
		nameLookup.cleanUp();
	}
	
	
	public AbstractProjectReference(String targetJob) {
		this.name = targetJob;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		//b.append(super.toString());
		b.append("[");
		b.append(this.name);
		b.append("]");
		return b.toString();
	}
	
	
	
	// === MEMBER ACCESS METHODS ===
	
	/**
	 * Returns the project associated with this reference. In contrast to the
	 * public field {@link AbstractProjectReference#getProject()}, this will
	 * try to make sure that the object is actually assigned.
	 * 
	 * @return the associated {@link InheritanceProject}, or null in case the
	 * name could not be resolved at this moment.
	 */
	public InheritanceProject getProject() {
		InheritanceProject ip = nameLookup.getIfPresent(this.name);
		if (ip == null) {
			ip = InheritanceProject.getProjectByName(this.name);
			if (ip != null) {
				nameLookup.put(this.name, ip);
			}
		}
		return ip;
	}
	
	
	
	// === GUI ACCESS METHODS ===
	
	public String getName() {
		return this.name;
	}
	
	public void switchProject(InheritanceProject project) {
		if (project != null) {
			this.name = project.getFullName();
		}
	}
	
	public void switchProject(String name) {
		this.name = name;
	}
	
	
	// === DESCRIPTOR MEMBERS AND CLASSES ===
	
	/**
	 * @return all the registered {@link ParameterDefinition} descriptors that
	 * construct classes derived from this abstract base class.
	 */
	public static DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all() {
		return Jenkins
				.getInstance()
				.<AbstractProjectReference,ProjectReferenceDescriptor>
				getDescriptorList(AbstractProjectReference.class);
	}
	
	/**
	 * @param clazz the class to filter for
	 * @return all the registered {@link ParameterDefinition} descriptors that
	 * construct classes derived from this abstract base class.
	 */
	public static DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all(
			Class<AbstractProjectReference> clazz
	) {
		if (clazz == null) { clazz = AbstractProjectReference.class; }
		Jenkins j = Jenkins.get();
		
		DescriptorExtensionList<AbstractProjectReference, ProjectReferenceDescriptor>
			dList, ret;
		
		//The following list might be empty, if clazz is not an ExtensionPoint
		ret = j.<AbstractProjectReference,ProjectReferenceDescriptor>
			getDescriptorList(clazz);
		
		if (!ret.isEmpty()) {
			return ret;
		}
		
		//The list was empty; we need to fill it manually
		//Do note, that this changes the list in-place; so the command above
		//will actually contain the modified data on the next invocation!
		dList = j.<AbstractProjectReference,ProjectReferenceDescriptor>
				getDescriptorList(AbstractProjectReference.class);
		
		Descriptor<?> clazzDesc = j.getDescriptor(clazz);
		if (clazzDesc == null) {
			return dList;
		}
		
		for (ProjectReferenceDescriptor prd : dList) {
			if (clazzDesc.getClass().isAssignableFrom(prd.getClass())) {
				ret.add(prd);
			}
		}
		return ret;
	}
	
	/**
	 * @return the same list as {@link #all()}, with all those classes removed
	 * that do <b>not</b> match the given regular expression
	 * on the class name.
	 * 
	 * @param classNameExp the regular expression for the class name
	 * 
	 */
	//public static DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all(String classNameExp) {
	public static List<ProjectReferenceDescriptor> all(String classNameExp) {
		Pattern p = Pattern.compile(classNameExp);
		DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all = all();
		LinkedList<ProjectReferenceDescriptor> reduced =
				new LinkedList<ProjectReferenceDescriptor>();
		
		ListIterator<ProjectReferenceDescriptor> iter = all.listIterator();
		while (iter.hasNext()) {
			ProjectReferenceDescriptor d = iter.next();
			Matcher mFull = p.matcher(d.clazz.getName());
			Matcher mPart = p.matcher(d.clazz.getSimpleName());
			/*
			if (!mFull.matches() && !mPart.matches()) {
				//This entry is not wanted at all
				iter.remove();
			}
			*/
			if (mFull.matches() || mPart.matches()) {
				reduced.add(d);
			}
		}
		return reduced;
	}
	
	public static ProjectReferenceDescriptor getDescriptor(
			Class<? extends AbstractProjectReference> clazz) {
		return (ProjectReferenceDescriptor)
				Jenkins.get()
				.getDescriptorOrDie(clazz);
	}
	
	public ProjectReferenceDescriptor getDescriptor() {
		return (ProjectReferenceDescriptor)
				Jenkins.get()
				.getDescriptorOrDie(getClass());
	}
	
	public abstract static class ProjectReferenceDescriptor extends
			Descriptor<AbstractProjectReference> {
		
		protected final Cache<String, IProjectReferenceFilter> filters = CacheBuilder.newBuilder()
				.concurrencyLevel(4)
				.expireAfterAccess(30, TimeUnit.SECONDS)
				.expireAfterWrite(1, TimeUnit.MINUTES)
				.build();
		
		protected ProjectReferenceDescriptor(Class<? extends AbstractProjectReference> klazz) {
			super(klazz);
		}
		
		/**
		 * Infers the type of the corresponding
		 * {@link ProjectReferenceDescriptor} from the outer class.
		 * This version works when you follow the common convention, where a
		 * descriptor is written as the static nested class of the describable
		 * class.
		 */
		protected ProjectReferenceDescriptor() {
			super();
		}
		
		
		public String getValuePage() {
			return getViewPage(clazz, "index.jelly");
		}
		
		/**
		 * Checks whether the "name" field is sensible.
		 * <p>
		 * Do note that the field is 'name', but the JSON/Form name is 'targetJob'.
		 * This is due to historical reasons, where the field was given the
		 * generic name, until this collided with the {@link QueryParameter}
		 * assignment in the {@link InheritableStringParameterReferenceDefinition}
		 * class, which can be added to {@link ParameterizedProjectReference}
		 * instances.
		 * <p>
		 * This verification is only valid, if the APR is used in the context
		 * of the definition of parents. Since project reference <i>can</i> be
		 * used elsewhere, care has to be taken for this verification to return
		 * {@link FormValidation#ok()} in these cases, and/or ensure that
		 * a subclass descriptor overrides this method.
		 *
		 * @param targetJob the name of the job that is referenced.
		 * @param parents a CSV list, containing all parents defined on the page.
		 * @param localJob the job in which this reference is created
		 * @return a valid, non-null {@link FormValidation} instance
		 */
		public FormValidation doCheckName(
				@QueryParameter String targetJob,
				@RelativePath(value="..") @QueryParameter String parents,
				@AncestorInPath InheritanceProject localJob) {
			//Sanity check
			if (targetJob == null || targetJob.isEmpty()) {
				return FormValidation.error(
					"You need to pass a valid project name"
				);
			} else if (localJob == null) {
				//In this case, the APR was not defined in a Project, so no
				//base-project name could be fetched to verify anything
				return FormValidation.ok();
			}
			
			//Check if the pointed-to job still exists
			InheritanceProject targetProject =
					InheritanceProject.getProjectByName(targetJob);
			if (targetProject == null) {
				return FormValidation.error(
					"Project name can't be resolved to project of correct type"
				);
			}
			if (targetProject == localJob) {
				return FormValidation.error(
						"Project should not reference itself"
					);
			}
			
			//Decode the list of parents -- if any
			String[] pNames = (parents != null) 
					? StringUtils.split(parents, ",")
					: new String[0];
			//Trim the split elements
			for (int i = 0; i < pNames.length; i++) {
				pNames[i] = pNames[i].trim();
			}
			
			//Check for whether the local job would be circular with the given
			//set of parents -- ignoring locally defined refs
			boolean hasCycle = localJob.hasCyclicDependency(false, pNames);
			
			//Check if adding the target to the parent would cause a cycle
			if (hasCycle) {
				//TODO: The cycle content should be printed here
				return FormValidation.error(
					"Adding this project would cause a cyclic/diamond dependency."
				);
			}
			//Otherwise, the name is good enough
			return FormValidation.ok();
		}
		
		/**
		 * Internal listing of permissible references.
		 * <p>
		 * Public, instead of protected/private, because the Unittests make
		 * use of this one.
		 * 
		 * @param targetJob the job under configuration
		 * @param filter the filter of the list of jobs
		 * @return a list of names of compatible jobs. May be empty, but never null.
		 */
		public ListBoxModel internalFillNameItems(String targetJob, IProjectReferenceFilter filter) {
			TreeSet<String> projNames = new TreeSet<String>();
			for (InheritanceProject ip : InheritanceProject.getProjectsMap().values()) {
				//We ensure that both are compatible
				if (!this.projectIsCompatible(ip)) {
					continue;
				}
				//And that the current set-up does not filter out that job
				if (filter != null && !filter.isApplicable(ip)) {
					continue;
				}
				projNames.add(ip.getFullName());
			}
			//Adding the previous definition; if any is already present
			if (targetJob != null) {
				projNames.add(targetJob);
			}
			
			ListBoxModel model = new ListBoxModel();
			for (String pName : projNames) {
				model.add(pName, pName);
			}
			return model;
		}
		
		public ListBoxModel doFillNameItems(
				@QueryParameter String targetJob,
				@QueryParameter String filterKey
		) {
			//Fetch the filter associated with the given filterKey
			IProjectReferenceFilter filter =
					(filterKey != null && !filterKey.isEmpty())
							? filters.getIfPresent(filterKey) : null;
			return this.internalFillNameItems(targetJob, filter);
		}
		
		@SuppressWarnings("rawtypes")
		public boolean projectIsCompatible(Project p) {
			if (!(p instanceof InheritanceProject)) {
				return false;
			}
			InheritanceProject ip = (InheritanceProject) p;
			if (ip.getIsTransient()) {
				return false;
			}
			
			/* Make sure, that only those projects are shown, whose class is
			 * identical to the caller class. This avoids mix & matching
			 * incompatible classes
			 */
			StaplerRequest req = Stapler.getCurrentRequest();
			if (req != null) {
				Job<?,?> currJob = req.findAncestorObject(Job.class);
				if (currJob != null) {
					if (currJob.getClass() != ip.getClass()) {
						//Incompatible classes
						return false;
					}
				}
			}
			
			
			return true;
		}
	
		public void addReferenceFilter(String key, IProjectReferenceFilter filter) {
			if (key == null || key.isEmpty() || filter == null) { return; }
			this.filters.put(key, filter);
		}
	}
}

