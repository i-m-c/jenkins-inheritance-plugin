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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector.ScopeEntry;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

public class InheritableStringParameterDefinition extends StringParameterDefinition {
	private static final long serialVersionUID = 5458085487475338803L;

	
	public static enum IModes {
		OVERWRITABLE, EXTENSIBLE, FIXED;
		
		private static final String[] names = getNames();
		private static String[] getNames() {
			String[] n = new String[IModes.values().length];
			n[FIXED.ordinal()] = "Fixed";
			n[EXTENSIBLE.ordinal()] = "Extensible";
			n[OVERWRITABLE.ordinal()] = "Overwritable";
			return n;
		}
		
		public static IModes getFromName(String name) {
			for (int i = 0; i < names.length; i++) {
				if (name.equals(names[i])) {
					return values()[i];
				}
			}
			return null;
		}
		
		public String toString() {
			return names[this.ordinal()];
		}
		
		/**
		 * Necessary because stupid Jelly can't easily compare enum constants
		 * TODO: Find a way to fix that for file:
		 * src\main\resources\hudson\plugins\project_inheritance\projects\...
		 * ...parameters\InheritableStringParameterDefinition\index.jelly
		 * 
		 * @return true, if the mode is equal to {@link #FIXED}
		 */
		public boolean isFixed() {
			return this == IModes.FIXED;
		}
	}
	
	public static enum WhitespaceMode {
		/** Trim whitespace from front and end*/
		TRIM,
		/** Keep whitespace chars as-is */
		KEEP,
		/** Keep whitespace, but also automatically add one, if extending others */
		ADD_IF_EXTENSION;
		
		@Override
		public String toString() {
			switch (this) {
				case KEEP:
					return Messages.WhitespaceMode_KEEP();
				case TRIM:
					return Messages.WhitespaceMode_TRIM();
				case ADD_IF_EXTENSION:
					return Messages.WhitespaceMode_ADD();
				
				default:
					return this.name();
			}
		}
	}
	
	private final IModes inheritanceMode;
	
	private final boolean mustHaveDefaultValue;
	private final boolean mustBeAssigned;
	private final boolean isHidden;
	
	/**
	 * The way how this parameter handles whitespace characters.
	 */
	private final WhitespaceMode whitespaceMode;
	
	/**
	 * This field stores the project variance name that this parameter comes
	 * from. If this is null, it does not come from a variance but rather
	 * from the project itself. If is set to an actual string (even empty), it
	 * comes from the variance with that name.
	 * <p>
	 * This field is meant to be purely informational, as the actual values
	 * for the parameters are fetched using
	 * {@link ParameterSelector#getScopedParameterDefinition(InheritanceProject, String)}.
	 */
	public transient String variance = null;
	
	
	public InheritableStringParameterDefinition(
			String name, String defaultValue) {
		this(
				name, defaultValue, null, IModes.OVERWRITABLE,
				false, false, WhitespaceMode.ADD_IF_EXTENSION, false
		);
	}
	
	public InheritableStringParameterDefinition(
			String name, String defaultValue, IModes iMode,
			boolean mustHaveDefaultValue, boolean mustBeAssigned,
			WhitespaceMode whitespaceMode, boolean isHidden) {
		this(
				name, defaultValue, null, iMode,
				mustHaveDefaultValue, mustBeAssigned, whitespaceMode, isHidden
		);
	}

	public InheritableStringParameterDefinition(String name, String defaultValue,
			String description, IModes inheritanceMode,
			boolean mustHaveDefaultValue, boolean mustBeAssigned,
			WhitespaceMode whitespaceMode, boolean isHidden) {
		super(StringUtils.trim(name), defaultValue, description);
		this.mustHaveDefaultValue = mustHaveDefaultValue;
		this.mustBeAssigned = mustBeAssigned;
		this.isHidden = isHidden;
		
		this.whitespaceMode = (whitespaceMode == null)
				? WhitespaceMode.KEEP
				: whitespaceMode;
		
		this.inheritanceMode = (inheritanceMode == null)
				? IModes.OVERWRITABLE
				: inheritanceMode;
	}
	
	@DataBoundConstructor
	public InheritableStringParameterDefinition(String name, String defaultValue,
			String description, String inheritanceMode,
			boolean mustHaveDefaultValue, boolean mustBeAssigned,
			String whitespaceMode, boolean isHidden) {
		this(
				StringUtils.trim(name), defaultValue, description,
				StringUtils.isBlank(inheritanceMode)
						? IModes.OVERWRITABLE
						: IModes.valueOf(inheritanceMode),
				mustHaveDefaultValue, mustBeAssigned,
				StringUtils.isBlank(whitespaceMode)
						? WhitespaceMode.KEEP
						: WhitespaceMode.valueOf(whitespaceMode),
				isHidden
		);
	}

	public InheritableStringParameterDefinition(InheritableStringParameterDefinition other) {
		this(
				other.getName(),
				other.getDefaultValue(),
				other.getDescription(),
				other.getInheritanceModeAsVar(),
				other.getMustHaveDefaultValue(),
				other.getMustBeAssigned(),
				other.getWhitespaceModeAsVar(),
				other.getIsHidden()
		);
		this.variance = other.variance;
	}
	
	
	/**
	 * This function creates a new {@link ParameterDefinition} that carries
	 * the given value as the default value.
	 * <p>
	 * Do note that due to inheritance, the final default value may be derived
	 * from multiple projects/parents. Such a parameter may be derived either
	 * as: Fixed, Overwritable or Extensible.
	 * <p>
	 * This function does not care for this at all. If you need a fully
	 * materialised version of this parameter, use:
	 * {@link #getMergeWithOther(ParameterDefinition)}.
	 * 
	 * @param defaultValue the value to be assigned to the copy as a default.
	 * Must be a {@link StringParameterValue}.
	 */
	@Override
	public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
		if (!(defaultValue instanceof StringParameterValue)) {
			//This should never happen
			return super.copyWithDefaultValue(defaultValue);
		}
		
		StringParameterValue spv = ((StringParameterValue) defaultValue);
		String value = spv.getValue().toString();
		
		//Creating the PD to return
		InheritableStringParameterDefinition ispd =
			new InheritableStringParameterDefinition(
				getName(),
				value,
				getDescription(),
				this.getInheritanceModeAsVar(),
				this.getMustHaveDefaultValue(),
				this.getMustBeAssigned(),
				this.getWhitespaceModeAsVar(),
				this.getIsHidden()
			);
		ispd.variance = this.variance;
		return ispd;
	}
	
	@Override
	public String toString() {
		return String.format("[%s]", this.getName());
	}
	
	/**
	 * This method returns the chain of project names that contribute to the
	 * final value of the current parameter.
	 * 
	 * @param root the project to start looking from.
	 * @return a string. May be empty, but never null.
	 */
	public String getLocationString(InheritanceProject root) {
		StringBuilder b = new StringBuilder();
		
		//Fetching the full scope
		ParameterSelector pSel = ParameterSelector.instance;
		List<ScopeEntry> fullScope = pSel.getScopedParameterDefinition(
				root, this.getName()
		);
		
		boolean first = true;
		for (ScopeEntry scope : fullScope) {
			if (StringUtils.equals(scope.param.getName(), this.getName())) {
				if (!first) {
					b.append('\u2192'); //Right-arrow
				} else {
					first = false;
				}
				b.append(scope.owner);
			}
		}
		
		if (this.variance != null) {
			b.append("; variance: '");
			b.append(this.variance);
			b.append("'");
		}
		return b.toString();
	}
	
	public String getLocationStringViaStapler() {
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) { return ""; }
		InheritanceProject root = req.findAncestorObject(InheritanceProject.class);
		if (root == null) { return ""; }
		
		return this.getLocationString(root);
	}
	
	// === GENERAL FIELD ACCESS ===
	
	public boolean getMustHaveDefaultValue() {
		return this.mustHaveDefaultValue;
	}
	
	public boolean getMustBeAssigned() {
		return this.mustBeAssigned;
	}
	
	public String getInheritanceMode() {
		return this.getInheritanceModeAsVar().name();
	}
	
	public IModes getInheritanceModeAsVar() {
		if (this.inheritanceMode == null) {
			return IModes.OVERWRITABLE;
		}
		return this.inheritanceMode;
	}
	
	public boolean getIsHidden() {
		return this.isHidden;
	}
	
	public String getWhitespaceMode() {
		WhitespaceMode m  = this.getWhitespaceModeAsVar();
		return m.name();
	}
	
	public WhitespaceMode getWhitespaceModeAsVar() {
		if (whitespaceMode == null) {
			return WhitespaceMode.KEEP;
		} else {
			return whitespaceMode;
		}
	}
	
	
	// === VALUE DERIVATION ===
	
	public InheritableStringParameterDefinition getMergeWithOther(ParameterDefinition past) {
		if (!(past instanceof InheritableStringParameterDefinition)) {
			//Non-inheritable values are always overwritten
			return this;
		}
		InheritableStringParameterDefinition iPast =
				(InheritableStringParameterDefinition) past;
		
		/* NOTE: Due to the way merging works, iPast can never be an
		 * InheritableStringParameterReferenceDefinition.
		 * As such, the "reference lookup" can only be from the current
		 * to the past instance.
		 */
		
		//The past version defines the way the value is generated
		final String newVal;
		switch (iPast.getInheritanceModeAsVar()) {
			case OVERWRITABLE:
				newVal = this.getDefaultValue();
				break;
			
			case FIXED:
				//Use the past value (note: The job will error out later anyway)
				newVal = iPast.getDefaultValue();
				break;
			
			case EXTENSIBLE:
				StringBuilder v = new StringBuilder(iPast.getDefaultValue());
				if (iPast.getWhitespaceModeAsVar() == WhitespaceMode.ADD_IF_EXTENSION) {
					v.append(" ");
				}
				v.append(this.getDefaultValue());
				newVal = v.toString();
				break;
				
			default:
				throw new IllegalArgumentException(
						"Unknown mode: " + iPast.getInheritanceModeAsVar()
				);
		}
		
		//Determining the other flags based on whether a reference is used or not
		String newDesc;
		boolean newMustHaveDefaultValue, newMustBeAssigned, newIsHidden;
		IModes newInheritanceMode;
		WhitespaceMode newWhitespaceMode;
		if (this instanceof InheritableStringParameterReferenceDefinition) {
			newMustHaveDefaultValue = iPast.getMustHaveDefaultValue();
			newMustBeAssigned = iPast.getMustBeAssigned();
			newIsHidden = iPast.getIsHidden();
			newInheritanceMode = iPast.getInheritanceModeAsVar();
			newWhitespaceMode = iPast.getWhitespaceModeAsVar();
			newDesc = iPast.getDescription();
		} else {
			newMustHaveDefaultValue = this.getMustHaveDefaultValue() || iPast.getMustHaveDefaultValue();
			newMustBeAssigned = this.getMustBeAssigned() || iPast.getMustBeAssigned();
			newIsHidden = this.getIsHidden() || iPast.getIsHidden();
			newInheritanceMode = this.getInheritanceModeAsVar();
			newWhitespaceMode = this.getWhitespaceModeAsVar();
			newDesc = (StringUtils.isNotBlank(this.getDescription()))
					? this.getDescription()
					: iPast.getDescription();
		}
		
		//Return the new ISPD
		return new InheritableStringParameterDefinition(
				this.getName(),
				newVal,
				newDesc,
				newInheritanceMode,
				newMustHaveDefaultValue,
				newMustBeAssigned,
				newWhitespaceMode,
				newIsHidden
		);
	}
	
	/**
	 * This function is invoked, when the user creates the value from an HTML
	 * form request. This happens, for example, when a build is triggered
	 * through the GUI instead of through the CLI.
	 * <p>
	 * Note that this function does not actually ensure the inheritance
	 * of the values. It expects, that the {@link ParameterSelector} class
	 * has already handled this via {@link #getMergeWithOther(ParameterDefinition)}
	 * during the creation of the {@link ParametersDefinitionProperty} instance.
	 * <p>
	 * Also, the check if the derivation was successful is not done here either.
	 * It is assumed that the {@link InheritanceBuild} checks this when the
	 * job starts, by calling
	 * {@link InheritanceParametersDefinitionProperty#checkParameterSanity(InheritanceBuild, hudson.model.BuildListener)}.
	 */
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		ParameterValue pv = super.createValue(req, jo);
		if (pv instanceof StringParameterValue) {
			//Strip the value (if needed) and return either the original or the mod
			String val = ((StringParameterValue)pv).getValue().toString();
			String nextVal = this.trimValue(val);
			if (val != nextVal) {
				return new StringParameterValue(pv.getName(), nextVal);
			}
		}
		return pv;
	}
	
	/**
	 * Same as {@link #createValue(StaplerRequest, JSONObject)}, only with
	 * a simple assignment of the value, which happens when the user uses
	 * the API directly, instead of going through the WebUI.
	 * <p>
	 * {@inheritDoc}
	 */
	public ParameterValue createValue(String value) {
		return new StringParameterValue(
				this.getName(), this.trimValue(value)
		);
	}
	
	/**
	 * Helper to trim the value of a parameter -- if activated in this def.
	 * 
	 * @param value the value to trim -- if null, this will return the empty string.
	 * @return the trimmed value -- or the value itself if not trimmed.
	 */
	private String trimValue(String value) {
		if (value == null) { return ""; }
		if (this.whitespaceMode == WhitespaceMode.TRIM) {
			//Remove whitespace (use strip, as trim is too aggressive)
			String nextVal = StringUtils.stripToEmpty(value);
			if (value.length() != nextVal.length()) {
				//Trimming was done
				return nextVal;
			}
		}
		//No trimming was done
		return value;
	}
	
	/**
	 * Returns the default value for this parameter.
	 * <p>
	 * The only difference to the super function is that it makes sure the
	 * value is properly trimmed.
	 * 
	 * @return the local default value for this parameter definition.
	 */
	@Override
	public StringParameterValue getDefaultParameterValue() {
		return new StringParameterValue(
				this.getName(),
				this.trimValue(this.getDefaultValue()),
				this.getDescription()
		);
	}
	

	
	// === DESCRIPTOR IMPLEMENTATION ===
	
	@Extension
	public static class DescriptorImpl extends StringParameterDefinition.DescriptorImpl {
		@Override
		public String getDisplayName() {
			return Messages.InheritableStringParameterDefinition_DisplayName();
		}

		@Override
		public String getHelpFile() {
			return "/plugin/project-inheritance/help/parameter/inheritableString.html";
		}
	
		public FormValidation doCheckName(
				@QueryParameter String name,
				@AncestorInPath InheritanceProject project
		) {
			//Check if the config page has a reference to the current project
			if (project == null) {
				//The currently configured project is not an IP project;
				//so there is no inheritance, and as such no check to be done
				return FormValidation.ok();
			}
			
			/* The buffer for the FormValidation result. As the same
			 * parameter may be defined multiple times in the hierarchy,
			 * we must search through all of them on a match and only
			 * react to the last matching one
			 */
			FormValidation fv = FormValidation.ok();
			
			
			Set<InheritanceProject> references = 
					this.getReferencedProjects(project);
			
			//Then, we check the parameters of each such linked project
			for (InheritanceProject proj : references) {
				//Fetch all parameters from the given project in their correct scope
				ParameterSelector pSel = ParameterSelector.instance;
				List<ScopeEntry> scope = pSel.getScopedParameterDefinition(project, name);
				if (scope == null) { continue; }
				for (ScopeEntry entry : scope) {
					//Searching for the parameter that applies
					if (entry.param == null ||
							!(entry.param instanceof InheritableStringParameterDefinition) ||
							entry.param.getName().equals(name) == false ||
							entry.owner.equals(project.getFullName())) {
						continue;
					}
					InheritableStringParameterDefinition ispd =
							(InheritableStringParameterDefinition) entry.param;
					
					//Fetching the IMode for this definition
					IModes mode = ispd.getInheritanceModeAsVar();
					if (mode == IModes.FIXED) {
						if (proj != project) {
							String msg = String.format(
								"Be careful! This variable is marked as fixed" +
								" in the compatible job: %s",
								proj.getFullName()
							);
							fv = FormValidation.warning(msg);
						} else {
							String msg = String.format(
									"You may not override a parameter marked" +
									" as fixed in the parent: %s.",
									entry.owner
							);
							fv = FormValidation.error( msg);
						}
						//Return immediately with this issue
						return fv;
					} else {
						String msg = String.format(
								"FYI: You are redefining a parameter that is" +
								" marked as '%s'%s%s and was defined in the %s: %s.",
								mode.toString(),
								(ispd.getMustBeAssigned()) ? ", must be assigned before run" : "",
								(ispd.getMustHaveDefaultValue()) ? ",  must have a default set" : "",
								(proj == project) ? "parent" : "compatible job",
								entry.owner
						);
						fv = FormValidation.ok(msg);
						//No return here, as a later entry may overwrite that
					}
				}
			}
			return fv;
		}
		
		public FormValidation doCheckDefaultValue(@QueryParameter String name) {
			return FormValidation.ok();
		}
		
		public ListBoxModel doFillInheritanceModeItems() {
			ListBoxModel m = new ListBoxModel();
			for (IModes im : IModes.values()) {
				m.add(im.toString(), im.name());
			}
			return m;
		}
		
		public ListBoxModel doFillWhitespaceModeItems() {
			ListBoxModel m = new ListBoxModel();
			for (WhitespaceMode mode : WhitespaceMode.values()) {
				m.add(mode.toString(), mode.name());
			}
			return m;
		}
		
		/**
		 * Determines the set of projects referenced from the given project
		 * (including the current project).
		 * <p>
		 * The default implementation returns all references:
		 * <ul>
		 *	<li>
		 *		Via inheritance from the local job
		 * 		(TODO: the user might've changed inheritance on the current config page!)
		 * 	</li>
		 *	<li>
		 *		via the 'compatible' projects used for automatic job creation
		 *	</li>
		 * </ul>
		 * 
		 * @param root the project to begin from
		 * @return a set, may be empty, but never null. If root != null will
		 * contain at least root.
		 */
		protected Set<InheritanceProject> getReferencedProjects(InheritanceProject root) {
			if (root == null) { return Collections.emptySet(); }
			
			Set<InheritanceProject> refs =
					new HashSet<InheritanceProject>();
			refs.add(root);
			for (AbstractProjectReference apr : root.getCompatibleProjects()) {
				InheritanceProject mate = apr.getProject();
				if (mate == null) { continue; }
				refs.add(mate);
			}
			
			return refs;
		}
	}
}
