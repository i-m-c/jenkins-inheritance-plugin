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

package hudson.plugins.project_inheritance.projects.references;

import hudson.DescriptorExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.Project;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=3)
public abstract class AbstractProjectReference implements Describable<AbstractProjectReference> {
	
	private static final Logger log = Logger.getLogger(
			AbstractProjectReference.class.toString()
	);
	
	
	protected String name;
	protected transient InheritanceProject project = null;
	private transient long timeOfLastResolveError = 0;
	
	
	@DataBoundConstructor
	public AbstractProjectReference(String name) {
		this.name = name;
		//And attempting to load the associated object
		this.reloadProjectObject();
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
	
	/**
	 * Wrapper for {@link AbstractProjectReference}{@link #reloadProjectObject(HashMap)}
	 * where the HashMap is generated directly. Protected, because of its
	 * bad performance when you query multiple ProjectReferences.
	 */
	protected boolean reloadProjectObject() {
		InheritanceProject ip = InheritanceProject.getProjectByName(name);
		if (ip != null) {
			this.project = ip;
			return true;
		}
		return false;
	}
	
	
	// === MEMBER ACCESS METHODS ===
	/**
	 * Returns the project associated with this reference. In contrast to the
	 * public field {@link AbstractProjectReference#project}, this will try to make
	 * sure that the object is actually assigned.
	 * 
	 * @return the associated {@link InheritanceProject}, or null in case the
	 * name could not be resolved at this moment.
	 */
	public InheritanceProject getProject() {
		if (project == null) {
			this.reloadProjectObject();
		}
		if (project == null &&
				(System.currentTimeMillis() - timeOfLastResolveError) > 5*1000) {
			log.warning(
					"Found unresolveable reference to: " + this.getName()
			);
			timeOfLastResolveError = System.currentTimeMillis();
		}
		return this.project;
	}
	
	
	
	// === GUI ACCESS METHODS ===
	
	public String getName() {
		return this.name;
	}
	
	public void switchProject(InheritanceProject project) {
		//Do note that the project referenced may not change through this
		if (project != null) {
			this.name = project.getName();
			this.project = project;
		}
	}
	
	public void switchProject(String name) {
		//Do note that the project referenced may not change through this
		this.name = name;
		this.reloadProjectObject();
	}
	
	
	// === DESCRIPTOR MEMBERS AND CLASSES ===
	
	/**
	 * Returns all the registered {@link ParameterDefinition} descriptors that
	 * construct classes derived from this abstract base class.
	 */
	public static DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all() {
		return Jenkins
				.getInstance()
				.<AbstractProjectReference,ProjectReferenceDescriptor>
				getDescriptorList(AbstractProjectReference.class);
	}
	
	/**
	 * Returns all the registered {@link ParameterDefinition} descriptors that
	 * construct classes derived from this abstract base class.
	 */
	public static DescriptorExtensionList<AbstractProjectReference,ProjectReferenceDescriptor> all(
			Class<AbstractProjectReference> clazz
	) {
		if (clazz == null) { clazz = AbstractProjectReference.class; }
		Jenkins j = Jenkins.getInstance();
		
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
	 * This method returns the same list as {@link #all()}, with all those
	 * classes removed that do <b>not</b> match the given regular expression
	 * on the class name.
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
				Jenkins.getInstance()
				.getDescriptorOrDie(clazz);
	}
	
	public ProjectReferenceDescriptor getDescriptor() {
		return (ProjectReferenceDescriptor)
				Jenkins.getInstance()
				.getDescriptorOrDie(getClass());
	}
	
	public abstract static class ProjectReferenceDescriptor extends
			Descriptor<AbstractProjectReference> {
		
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
		protected ProjectReferenceDescriptor() { }
		
		public String getValuePage() {
			return getViewPage(clazz, "index.jelly");
		}
		
		public FormValidation doCheckName(
				@QueryParameter String name,
				@QueryParameter String projectName) {
			//Sanity check
			if (name == null || name.isEmpty()) {
				return FormValidation.error(
					"You need to pass a valid project name"
				);
			} else if (projectName == null || projectName.isEmpty()) {
				//In this case, the APR was not defined in a Project, so no
				//base-project name could be fetched to verify anything
				return FormValidation.ok();
			}
			
			//Fetching the current project and the new parent
			InheritanceProject currProj = 
					InheritanceProject.getProjectByName(projectName);
			InheritanceProject parentProj =
					InheritanceProject.getProjectByName(name);
			
			if (currProj == null || parentProj == null) {
				return FormValidation.error(
					"Project name can't be resolved to project of correct type"
				);
			}
			
			//Now, we can check if adding the parent name to the project would
			//cause a cyclic dependency
			if (currProj.hasCyclicDependency(parentProj.getName())) {
				return FormValidation.error(
					"Adding this project would cause a cyclic/diamond dependency."
				);
			}
			//Otherwise, the name is good enough
			return FormValidation.ok();
		}
		
		public ListBoxModel doFillNameItems(@QueryParameter String name) {
			TreeSet<String> projNames = new TreeSet<String>();
			for (InheritanceProject ip : InheritanceProject.getProjectsMap().values()) {
				//We ensure that both are compatible
				if (this.projectIsCompatible(ip)) {
					projNames.add(ip.getName());
				}
			}
			//Adding the previous definition; if any is already present
			if (name != null && !name.isEmpty()) {
				projNames.add(name);
			}
			
			ListBoxModel model = new ListBoxModel();
			for (String pName : projNames) {
				model.add(pName, pName);
			}
			return model;
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
			return true;
		}
	}
}

