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
package hudson.plugins.project_inheritance.projects.parameters;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.RelativePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector.ScopeEntry;
import hudson.plugins.project_inheritance.projects.references.ParameterizedProjectReference;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

/**
 * This class extends {@link InheritableStringParameterDefinition} by
 * enabling the user to make a reference to an actual, full, parameter definition.
 * <p>
 * This avoids the user having to constantly redefine the additional flags
 * of these parameters, as well as offering a nice drop-down box for 
 * parameter names.
 * <p>
 * Do note that this means, that instances of this class do not make use of the
 * extended fields offered by the definition. As such, these fields are hidden
 * entirely from the serialised XML by making use of an
 * {@link InheritableParameterReferenceConverter}.
 * 
 * @author Martin Schroeder
 */
public class InheritableStringParameterReferenceDefinition extends
		InheritableStringParameterDefinition {
	private static final long serialVersionUID = 6758820021906716839L;
	
	// === CONSTRUCTORS ===
	
	@DataBoundConstructor
	public InheritableStringParameterReferenceDefinition(
			String name, String defaultValue) {
		super(name, defaultValue);
	}
	
	public InheritableStringParameterReferenceDefinition(
			InheritableStringParameterDefinition other) {
		super(other);
	}
	
	@Initializer(before=InitMilestone.PLUGINS_STARTED)
	public static void initializeXStream() {
		InheritableParameterReferenceConverter conv =
				new InheritableParameterReferenceConverter();
		
		final XStream2[] xs = {
				Jenkins.XSTREAM2, Run.XSTREAM2, Items.XSTREAM2
		};
		for (XStream2 x : xs) {
			//Add the custom converter to hide some fields
			x.registerConverter(conv);
		}
	}
	
	
	// === MEMBER METHODS ===
	
	/**
	 * This method returns the {@link InheritableStringParameterDefinition}
	 * that is the parent of this reference, but is not a reference itself.
	 * <p>
	 * As such, this method is useless if you want to compute the final value
	 * of the variable, but it is essential to find the value of the flags that
	 * can only be defined on a true parameter, like {@link #getMustBeAssigned()}.
	 * 
	 * @param root the project that uses the current
	 * 
	 * @return the actual {@link InheritableStringParameterDefinition} that this
	 * reference ultimately points to. Skips all other
	 * {@link InheritableStringParameterReferenceDefinition} in between them.
	 */
	public InheritableStringParameterDefinition getParent(InheritanceProject root) {
		ParameterSelector pSel = ParameterSelector.instance;
		List<ScopeEntry> scope = pSel.getScopedParameterDefinition(root, this.getName());
		ListIterator<ScopeEntry> iter = scope.listIterator(scope.size());
		while (iter.hasPrevious()) {
			ScopeEntry entry = iter.previous();
			//Refuse to return ourselves or siblings
			if (entry.param == null || entry.param == this) {
				continue;
			}
			if (!(entry.param instanceof InheritableStringParameterDefinition)) {
				continue;
			}
			if (entry.param instanceof InheritableStringParameterReferenceDefinition) {
				continue;
			}
			if (entry.param.getName().equals(this.getName())) {
				return (InheritableStringParameterDefinition) entry.param;
			}
		}
		return null;
	}
	
	/**
	 * This works like {@link #getParent(InheritanceProject)}, only that
	 * it tries to grab the current Project from the current stapler request.
	 * <p>
	 * If it can't, null is returned. This means that this method will only
	 * work when called from a UI call and at no other time.
	 * 
	 * @return the definition this reference points to, or null in case of
	 * the project or reference not being found.
	 */
	public InheritableStringParameterDefinition getParent() {
		//Check for a Stapler Request ancestor
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) { return null; }
		for (Ancestor a : req.getAncestors()) {
			Object obj = a.getObject();
			if (obj instanceof InheritanceProject) {
				return this.getParent((InheritanceProject) obj);
			}
		}
		//No ancestor found.
		return null;
	}
	
	
	@Override
	public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
		if (!(defaultValue instanceof StringParameterValue)) {
			//This should never happen
			return super.copyWithDefaultValue(defaultValue);
		}
		
		StringParameterValue spv = ((StringParameterValue) defaultValue);
		String value = spv.getValue().toString();
		InheritableStringParameterReferenceDefinition isprd =
				new InheritableStringParameterReferenceDefinition(
						this.getName(), value
				);
		return isprd;
	}
	
	
	/**
	 * Will fetch that field from its nearest parent; using the rootProperty
	 */
	@Override
	public boolean getMustHaveDefaultValue() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getMustHaveDefaultValue();
		}
		return parent.getMustHaveDefaultValue();
	}
	
	@Override
	public boolean getMustBeAssigned() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getMustBeAssigned();
		}
		return parent.getMustBeAssigned();
	}
	
	@Override
	public IModes getInheritanceModeAsVar() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getInheritanceModeAsVar();
		}
		return parent.getInheritanceModeAsVar();
	}

	@Override
	public String getDescription() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getDescription();
		}
		return parent.getDescription();
	}
	
	@Override
	public boolean getIsHidden() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return super.getIsHidden();
		}
		return parent.getIsHidden();
	}
	
	@Override
	public WhitespaceMode getWhitespaceModeAsVar() {
		InheritableStringParameterDefinition parent = this.getParent();
		if (parent == null) {
			//Return a dummy-value
			return WhitespaceMode.KEEP;
		}
		return parent.getWhitespaceModeAsVar();
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
		
		@Override
		public FormValidation doCheckDefaultValue(@QueryParameter String name) {
			return FormValidation.ok();
		}
		
		/**
		 * Fills the name select box, with all parameters from a pool of referenced jobs.
		 * <p>
		 * The pool is determined dynamically as containing:
		 * <ul>
		 * 	<li>The parent job in which the parameter is defined (plus all its
		 * 		parents in inheritance)
		 * 	</li>
		 * 	<li>The job targeted by the outer container, if present
		 * 		(usually a {@link ParameterizedProjectReference})
		 * 	</li>
		 * 	<li>TODO: Any new jobs, that the user added to the inheritance in
		 * 		the current HTML form
		 * 	</li>
		 * </ul>
		 * 
		 * @param name the currently pointed-to task. If null or blank, it is ignored.
		 * @param project the project on whose config page the parameter is generated. If null, it is ignored.
		 * @param targetJob another job reference, if the parent container has a 'targetJob' field.
		 * @param parents the parents from the current form data
		 * 
		 * @return a list of parameters. Will always contain at least the passed
		 * in name, but may be empty, if that name is blank/null.
		 */
		public ListBoxModel doFillNameItems(
				@QueryParameter String name,
				@AncestorInPath InheritanceProject project,
				@RelativePath(value="..") @QueryParameter String targetJob,
				@QueryParameter String parents
		) {
			ListBoxModel m = new ListBoxModel();
			
			//Create the set of projects, whose parameters might be referenced
			Set<InheritanceProject> references = new HashSet<>();
			
			//Add the job the parameter is defined in itself
			if (project != null) {
				references.add(project);
			}
			
			//Then, check if the container class (usually a ProjectReference)
			//made use of a 'targetJob' field, and if so, add it to the candidates
			if (targetJob != null) {
				TopLevelItem item = Jenkins.get().getItem(targetJob);
				if (item instanceof InheritanceProject) {
					references.add((InheritanceProject)item);
				}
			}
			
			//Add jobs from dynamically changed inheritance settings (parent jobs)
			if (StringUtils.isNotBlank(parents)) {
				String[] jobs = parents.split(",");
				for (String job : jobs) {
					if (StringUtils.isBlank(job)) { continue; }
					TopLevelItem item = Jenkins.get().getItem(job.trim());
					if (item instanceof InheritanceProject) {
						references.add((InheritanceProject)item);
					}
				}
			}
			
			//Read in all parameters from all referenced jobs (if any)
			TreeSet<String> nameSet = new TreeSet<String>();
			for (InheritanceProject proj : references) {
				//Use full inheritance for parents, local values for current job
				IMode mode = (proj == project)
						? IMode.LOCAL_ONLY
						: IMode.INHERIT_FORCED;
				List<ParameterDefinition> pDefs = proj.getParameters(mode);
				for (ParameterDefinition pd : pDefs) {
					if (pd == null) { continue; }
					String paramName = pd.getName();
					if (paramName != null) {
						nameSet.add(paramName);
					}
				}
			}
			
			//Make sure, that the previously selected setting is always present
			if (StringUtils.isNotBlank(name)) {
				nameSet.add(name);
			}
			
			//Fill the list box model with the final, sorted list of names
			for (String n : nameSet) {
				m.add(n, n);
			}
			return m;
		}
	}
}
