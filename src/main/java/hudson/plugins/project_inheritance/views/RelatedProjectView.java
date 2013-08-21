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

package hudson.plugins.project_inheritance.views;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModifiableItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.ViewGroup;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship;
import hudson.plugins.project_inheritance.projects.InheritanceProject.Relationship.Type;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine.CreationClass;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.util.DescribableList;
import hudson.util.ListBoxModel;
import hudson.views.ListViewColumn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class RelatedProjectView extends View {
	
	// === SUBCLASS/ENUM DEFINITIONS ===
	
	public enum ProjectTypes {
		ALL, TRANSIENTS, REGULARS;
		
		public boolean matches(String str) {
			return this.name().equals(str);
		}
	}
	
	public enum Relationships {
		PARENTS, CHILDREN, COMPATIBLES;
		
		public boolean matches(String str) {
			return this.name().equals(str);
		}
		
		public String toString() {
			switch(this) {
				case CHILDREN:
					return "Children of project";
				case COMPATIBLES:
					return "Projects marked as compatible";
				case PARENTS:
					return "Parents of project";
				default:
					return "N/A";
			}
		}
	}
	
	
	
	// === MEMBER FIELDS ===
	
	private transient Collection<TopLevelItem> lastItems;
	
	private ProjectTypes typeFilter = ProjectTypes.ALL;
	
	private String creationClassFilter = null;
	
	private Set<Relationships> selectedRelations =
			new HashSet<RelatedProjectView.Relationships>();
	
	private DescribableList<AbstractProjectReference, Descriptor<AbstractProjectReference>> references;
	
	private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;
	
	
	
	// === CONSTRUCTORS ===
	
	@DataBoundConstructor
	public RelatedProjectView(String name) {
		super(name);
		this.initColumns();
	}

	public RelatedProjectView(String name, ViewGroup owner) {
		super(name, owner);
		this.initColumns();
	}
	
	
	
	// === SIMPLE MEMBER FIELD GETTERS ===
	
	// === PROTECTED FIELD GETTERS ===
	
	public ProjectTypes getTypeFilter() {
		return this.typeFilter;
	}
	
	public Set<Relationships> getSelectedRelations() {
		return this.selectedRelations;
	}
	
	public List<Relationships> getAllRelations() {
		return Arrays.asList(Relationships.values());
	}
	
	public DescribableList<AbstractProjectReference, Descriptor<AbstractProjectReference>> getProjectReferences() {
		//Making sure the references list is never null
		if (this.references == null) {
			this.references = new DescribableList<
					AbstractProjectReference,Descriptor<AbstractProjectReference>
			>(this);
		}
		return this.references;
	}
	
	public String getCreationClassFilter() {
		return this.creationClassFilter;
	}
	
	// === GUI INITIALIZERS ===
	
	protected void initColumns() {
		if (this.columns == null) {
			this.columns = new DescribableList<
					ListViewColumn, Descriptor<ListViewColumn>
			>(
					this,ListViewColumn.createDefaultInitialColumnList()
			);
		}
	}
	
	
	
	// === HELPER METHODS ===
	protected boolean filterApplies(InheritanceProject ip) {
		//Checking if the transience/permanence filter matches
		boolean isTransient = ip.getIsTransient();
		if (typeFilter == ProjectTypes.REGULARS && isTransient) {
			return false;
		}
		if (typeFilter == ProjectTypes.TRANSIENTS && !isTransient) {
			return false;
		}
		
		//Checking if the creation class filter matches
		String ipClass = ip.getCreationClass();
		if (creationClassFilter != null && !creationClassFilter.isEmpty()) {
			//Checking if it is the special "All classes" value
			boolean isAllSel = creationClassFilter.equals(
					Messages.RelatedProjectView_AllClassesSelector()
			);
			if (!isAllSel && (ipClass == null || !ipClass.equals(creationClassFilter))) {
				return false;
			}
		}
		
		//If we survived until here; the project is okay
		return true;
	}
	
	
	// === INHERITED METHODS ===
	
	@Override
	public Collection<TopLevelItem> getItems() {
		List<TopLevelItem> items = new LinkedList<TopLevelItem>();
		
		if (this.getProjectReferences().isEmpty()) {
			//Enumerating all possible projects
			for (TopLevelItem item : getOwnerItemGroup().getItems()) {
				if (item instanceof InheritanceProject) {
					InheritanceProject ip = (InheritanceProject) item;
					if (filterApplies(ip)) {
						items.add(item);
					}
				}
			}
			this.lastItems = items;
			return items;
		}
		
		//Fetching the filter criteria for relationships
		boolean filterForChildren =  this.selectedRelations.contains(
				Relationships.CHILDREN
		);
		boolean filterForMates =  this.selectedRelations.contains(
				Relationships.PARENTS
		);
		boolean filterForParents =  this.selectedRelations.contains(
				Relationships.COMPATIBLES
		);
		
		//Otherwise, we return the selected projects and their relatives
		TreeSet<InheritanceProject> projs = new TreeSet<InheritanceProject>();
		
		Iterator<AbstractProjectReference> iter = this.getProjectReferences().iterator(); 
		while (iter != null && iter.hasNext()) {
			AbstractProjectReference apr = iter.next();
			InheritanceProject ip = apr.getProject();
			if (ip == null) { continue; }
			
			Map<InheritanceProject, Relationship> map = ip.getRelationships();
			
			//Adding the project under scrutiny
			if (filterApplies(ip)) { projs.add(ip); }
			
			//Adding its related projects
			for (Map.Entry<InheritanceProject, Relationship> entry : map.entrySet()) {
				boolean suitable =
					(filterForParents && entry.getValue().type == Type.PARENT) ||
					(filterForMates && entry.getValue().type == Type.MATE) ||
					(filterForChildren && entry.getValue().type == Type.CHILD);
				if (suitable) {
					projs.add(entry.getKey());
				}
			}
		}
		
		//Adding the projects to the output list; they are sorted and unique
		for (InheritanceProject p : projs) {
			if (filterApplies(p)) {
				items.add(p);
			}
		}
		
		this.lastItems = items;
		return items;
	}

	@Override
	public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
		return this.columns;
	}
	
	@Override
	public boolean contains(TopLevelItem item) {
		//TODO: "lastItems" should probably not be buffered for hours
		if (this.lastItems == null) {
			return this.getItems().contains(item);
		}
		return this.lastItems.contains(item);
	}

	@Override
	public void onJobRenamed(Item item, String oldName, String newName) {
		if (oldName == null) { return; }
		if (newName == null) {
			if (item == null) {
				return;
			}
			newName = item.getFullName();
		}
		//Searching whether one of our referenced jobs has changed
		for (AbstractProjectReference apr : this.getProjectReferences()) {
			if (apr == null) { continue; }
			if (apr.getName().equals(oldName)) {
				apr.switchProject(newName);
			}
		}
	}

	// === CONFIGURATION SUBMISSION
	
	@Override
	protected void submit(StaplerRequest req) throws IOException,
			ServletException, FormException {
		
		//Fetching the referenced project names
		if (references == null) {
			references = new DescribableList<
					AbstractProjectReference,Descriptor<AbstractProjectReference>
			>(this);
		}
		references.rebuildHetero(
				req, req.getSubmittedForm(),
				AbstractProjectReference.all(), "projects"
		);
		
		//Fetching the creation class filter
		if (req.hasParameter("creationClassFilter")) {
			String filter = req.getParameter("creationClassFilter");
			if (filter == null || filter.isEmpty()) {
				this.creationClassFilter = null;
			} else {
				this.creationClassFilter = filter;
			}
		}
		
		//Applying the type filter
		if (req.hasParameter("typeFilter")) {
			String type = req.getParameter("typeFilter");
			ProjectTypes pt = ProjectTypes.valueOf(type);
			if (pt != null) {
				this.typeFilter = pt;
			} else {
				this.typeFilter = ProjectTypes.ALL;
			}
		} else {
			this.typeFilter = ProjectTypes.ALL;
		}
		
		//Applying the relationship filter
		if (this.selectedRelations == null) {
			this.selectedRelations = new HashSet<RelatedProjectView.Relationships>();
		} else  {
			this.selectedRelations.clear();
		}
		for (Relationships rel : Relationships.values()) {
			if (req.hasParameter("relation_" + rel.name())) {
				this.selectedRelations.add(rel);
			}
		}
		
		//Regenerating the columns list
		if (columns == null) {
			columns = new DescribableList<ListViewColumn,Descriptor<ListViewColumn>>(this);
		}
		columns.rebuildHetero(req, req.getSubmittedForm(), ListViewColumn.all(), "columns");
		
		
		
	}

	@Override
	public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		//First, we just create this item
		Item item = null;
		ItemGroup<? extends TopLevelItem> ig = getOwnerItemGroup();
        if (ig instanceof ModifiableItemGroup)
            item = ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
        
        //Checking if we deal with an inheritable job
        if (item == null || !(item instanceof InheritanceProject)) {
        	return item;
        }
        //If we deal with an inheritable project, we assign it to the currently
        //viewed creation class, if any
        InheritanceProject ip = (InheritanceProject) item;
        //Checking if we define a CC
        if (!this.creationClassFilter.isEmpty()) {
        	ip.setCreationClass(this.creationClassFilter);
        }
        return item;
	}
	
	
	
	// === DESCRIPTOR IMPLEMENTATION ===
	
	@Extension
	public static final class DescriptorImpl extends ViewDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.RelatedProjectView_DisplayName();
		}
		
		public ListBoxModel doFillProjectNameItems() {
			ListBoxModel pNames = new ListBoxModel();
			for (InheritanceProject ip : InheritanceProject.getProjectsMap().values()) {
				pNames.add(ip.getName());
			}
			return pNames;
		}
	
		public ListBoxModel doFillCreationClassFilterItems() {
			ListBoxModel names = new ListBoxModel();
			//Add the "All fields" one
			names.add(Messages.RelatedProjectView_AllClassesSelector());
			//And add those actually defined
			for (CreationClass cl : ProjectCreationEngine.instance.getCreationClasses()) {
				names.add(cl.name);
			}
			return names;
		}
	
	
	}


}
