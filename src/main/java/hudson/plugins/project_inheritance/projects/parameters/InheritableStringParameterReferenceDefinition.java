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

package hudson.plugins.project_inheritance.projects.parameters;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty.ScopeEntry;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;



public class InheritableStringParameterReferenceDefinition extends
		InheritableStringParameterDefinition {
	private static final long serialVersionUID = 6758820021906716839L;
	
	@DataBoundConstructor
	public InheritableStringParameterReferenceDefinition(
			String name, String defaultValue) {
		super(name, defaultValue);
	}
	
	public InheritableStringParameterReferenceDefinition(
			InheritableStringParameterDefinition other) {
		super(other);
	}
	
	public InheritableStringParameterDefinition getParent() {
		InheritanceParametersDefinitionProperty ipdp = this.getRootProperty();
		if (ipdp == null) {
			return null;
		}
		
		//Fetch the owner of this reference
		String selfOwner = ipdp.getOwner().getDisplayName();
		
		List<ScopeEntry> scope = ipdp.getAllScopedParameterDefinitions();
		ListIterator<ScopeEntry> iter = scope.listIterator(scope.size());
		while (iter.hasPrevious()) {
			ScopeEntry entry = iter.previous();
			//Refuse to return ourselves or siblings
			if (entry.param == null || entry.owner == selfOwner ||entry.param == this) {
				continue;
			}
			if (!(entry.param instanceof InheritableStringParameterDefinition)) {
				continue;
			}
			if (entry.param.getName().equals(this.getName())) {
				return (InheritableStringParameterDefinition) entry.param;
			}
		}
		return null;
	}
	
	
	/**
	 * Will fetch that field from its nearest parent; using the rootProperty
	 */
	public boolean getMustHaveDefaultValue() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getMustHaveDefaultValue();
		}
		return parent.getMustHaveDefaultValue();
	}
	
	public boolean getMustBeAssigned() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getMustBeAssigned();
		}
		return parent.getMustBeAssigned();
	}
	
	public boolean getAutoAddSpaces() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getAutoAddSpaces();
		}
		return parent.getAutoAddSpaces();
	}
	
	public String getInheritanceMode() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getInheritanceMode();
		}
		return parent.getInheritanceMode();
	}
	
	public IModes getInheritanceModeAsVar() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getInheritanceModeAsVar();
		}
		return parent.getInheritanceModeAsVar();
	}

	public String getDescription() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getDescription();
		}
		return parent.getDescription();
	}
	

	@Extension
	public static class DescriptorImpl extends InheritableStringParameterDefinition.DescriptorImpl {
		@Override
		public String getDisplayName() {
			return Messages.InheritableStringParameterReferencesDefinition_DisplayName();
		}
		
		@Override
		public String getHelpFile() {
			return "/plugin/project-inheritance/help/parameter/inheritableString.html";
		}
		
		public FormValidation doCheckDefaultValue(
				@QueryParameter String name,
				@QueryParameter String projectName) {
			return FormValidation.ok();
		}
		
		public ListBoxModel doFillNameItems(@QueryParameter String projectName) {
			ListBoxModel m = new ListBoxModel();
			
			//Fetch all parameters from all parents
			InheritanceProject ip = InheritanceProject.getProjectByName(projectName);
			if (ip == null) {
				return m;
			}
			
			//Create list of reference jobs; the current one and all compatibles
			LinkedList<InheritanceProject> projLst =
					new LinkedList<InheritanceProject>();
			projLst.add(ip);
			for (AbstractProjectReference apr : ip.getCompatibleProjects()) {
				InheritanceProject compat = apr.getProject();
				if (compat == null) { continue; }
				projLst.add(compat);
			}
			
			//Read in all parameters from all jobs
			TreeSet<String> nameSet = new TreeSet<String>();
			for (InheritanceProject proj : projLst) {
				List<ParameterDefinition> pDefs = proj.getParameters(
						IMode.INHERIT_FORCED
				);	
				for (ParameterDefinition pd : pDefs) {
					if (pd == null) { continue; }
					String name = pd.getName();
					if (name != null) {
						nameSet.add(pd.getName());
					}
				}
			}
			
			for (String n : nameSet) {
				m.add(n, n);
			}
			return m;
		}
	}


}
