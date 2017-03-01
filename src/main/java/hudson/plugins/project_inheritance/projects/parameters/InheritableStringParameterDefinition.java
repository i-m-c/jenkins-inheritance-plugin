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
package hudson.plugins.project_inheritance.projects.parameters;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty.ScopeEntry;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

public class InheritableStringParameterDefinition extends StringParameterDefinition {
	private static final long serialVersionUID = 5458085487475338803L;

	private static final Logger log = Logger.getLogger(
			InheritableStringParameterDefinition.class.toString()
	);
	
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
		 * @return
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
	 * 
	 * Should be made final, once {@link #autoAddSpaces} is removed.
	 */
	private WhitespaceMode whitespaceMode;
	
	/**
	 * Whether or not to automatically add spaces when mode is "Overwritable"
	 * <p>
	 * Has been deprecated in favour of the more flexible {@link #whitespaceMode}.
	 * <p>
	 * Remove in next release.
	 */
	@Deprecated
	private boolean autoAddSpaces;
	
	/**
	 * This field stores a reference to the property that created this
	 * definition. This is necessary, because the property also contains the
	 * necessary full scope of parameter definitions to properly derive
	 * a final value in terms of inheritance. 
	 */
	private transient InheritanceParametersDefinitionProperty rootProperty = null;
	
	/**
	 * This field stores the project variance name that this parameter comes
	 * from. If this is null, it does not come from a variance but rather
	 * from the project itself. If is set to an actual string (even empty), it
	 * comes from the variance with that name.
	 * <p>
	 * This field is meant to be purely informational, as the actual values
	 * for the parameters are fetched using
	 * {@link InheritanceParametersDefinitionProperty#getScopedParameterDefinition(String)}.
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
		this.rootProperty = other.rootProperty;
		this.variance = other.variance;
	}
	
	public Object readResolve() {
		//Check if a whitespace mode is present
		if (whitespaceMode == null) {
			if (autoAddSpaces) {
				whitespaceMode = WhitespaceMode.ADD_IF_EXTENSION;
			} else {
				whitespaceMode = WhitespaceMode.KEEP;
			}
		}
		return this;
	}
	
	/**
	 * This function creates a new {@link ParameterDefinition} that carries
	 * the given value as the default value.
	 * <p>
	 * Do note that due to inheritance, the final default value may be derived
	 * from multiple projects/parents. Such a parameter may be derived either
	 * as: Fixed, Overwritable or Extensible.
	 * <p>
	 * The first and second one are trivial cases where the default value is
	 * used as is. The problem lies in dealing with an extensible parameter, as
	 * in that case a prefix of the given default value may need to be stripped.
	 * <p>
	 * <b>Do note</b> that this is automatically done when you call
	 * {@link #getDerivedValue(String, boolean)}, but <b>not</b> when you call
	 * {@link #produceDerivedValue(String)}. If you need to access this
	 * behaviour from some place else, call {@link #stripInheritedPrefixFromValue()}
	 */
	@Override
	public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
		if (!(defaultValue instanceof StringParameterValue)) {
			//This should never happen
			return super.copyWithDefaultValue(defaultValue);
		}
		
		StringParameterValue spv = ((StringParameterValue) defaultValue);
		String value = spv.value;
		
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
		ispd.setRootProperty(this.rootProperty);
		return ispd;
	}
	
	
	
	public void setRootProperty(InheritanceParametersDefinitionProperty root) {
		this.rootProperty = root;
	}
	
	public InheritanceParametersDefinitionProperty getRootProperty() {
		return this.rootProperty;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		//b.append(super.toString());
		b.append("[");
		b.append(this.getName());
		
		String loc = this.getLocationString();
		if (loc != null) {
			b.append("; from: ");
			b.append(loc);
		}
		
		b.append("]");
		return b.toString();
	}
	
	public String getLocationString() {
		StringBuilder b = new StringBuilder();
		if (this.rootProperty != null) {
			//Fetching the full scope
			List<ScopeEntry> fullScope = 
					rootProperty.getScopedParameterDefinition(this.getName());
			String owner = null;
			for (ScopeEntry scope : fullScope) {
				if (scope.param == this) {
					owner = scope.owner;
					break;
				}
			}
			if (owner != null) {
				b.append(owner);
			} else if (this.rootProperty.getOwner() != null) {
				b.append("?->");
				b.append(this.rootProperty.getOwner().getFullName());
			} else {
				b.append("!->BROKEN");
				
			}
		} else {
			b.append("!->UNKNOWN");
		}
		if (this.variance != null) {
			b.append("; variance: '");
			b.append(this.variance);
			b.append("'");
		}
		return b.toString();
	}
	
	public String getDefinitionLocationDescription() {
		StringBuilder b = new StringBuilder();
		//b.append(super.toString());
		b.append("[from: ");
		
		String loc = this.getLocationString();
		if (loc != null) {
			b.append(loc);
		}
		
		b.append("] ");
		b.append(this.getDescription());
		return b.toString();
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
	
	/**
	 * This function is invoked, when the user creates the value from an HTML
	 * form request. This happens, for example, when a build is triggered
	 * through the GUI instead of through the CLI.
	 * <p>
	 * This function is what makes an inheritable parameter inheritable at all.
	 * Here, the value entered by the user is combined with the values of its
	 * parents. This may mean:
	 * <ul>
	 * <li>overwriting the parent's value,</li>
	 * <li>ensuring that a particular value is correctly set (if mandatory),
	 * <li>that it does not overwrite a fixed value or</li>
	 * <li>appending the parameter to its parent.</li>
	 * </ul>
	 * A complicating fact is that by default, a {@link ParameterDefinition}
	 * is not informed for what kind of job is is being used, as that is not
	 * a necessary piece of information in Vanilla-Jenkins.
	 * 
	 * @see {@link #baseProject}.
	 */
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		//First, we produce a temporary SPV from the JSON request
		StringParameterValue value = req.bindJSON(
				StringParameterValue.class, jo
		);
		if (value == null) {
			//No such parameter name at all
			return null;
		}
		//Then, we check if we can get a properly derived value
		try {
			StringParameterValue dValue =
					this.produceDerivedValue(value.value);
			//Checking if that worked
			if (dValue == null) {
				return value;
			} else {
				return dValue;
			}
		} catch (IllegalArgumentException ex) {
			log.warning("Could not create inheritable string parameter: " + ex.toString());
			return value;
		}
	}

	public ParameterValue createValue(String value) {
		try {
			StringParameterValue spv = this.produceDerivedValue(value);
			if (spv == null) {
				return super.createValue(value);
			} else {
				return spv;
			}
		} catch (IllegalArgumentException ex) {
			log.warning("Could not create inheritable string parameter: " + ex.toString());
			return super.createValue(value);
		}
	}
	
	private StringParameterValue produceDerivedValue(String userEnteredValue)
			throws IllegalArgumentException {
		if (this.rootProperty == null) {
			//No inheritance-compatible parent defined; no point in inheritance
			log.warning(
				"No root property defined for the param: " + this.getName()
			);
			return null;
		}
		
		//Then, we enumerate all the other PDs from the root property and
		//derive the proper value
		List<ScopeEntry> fullScope =
				this.rootProperty.getScopedParameterDefinition(this.getName());
		
		if (fullScope == null) {
			return new InheritableStringParameterValue(
					this.getName(), userEnteredValue, this.getDescription()
			);
		}
		
		
		String value = "";
		IModes currMode = IModes.OVERWRITABLE;
		boolean mustHaveValueSet = false;
		
		Iterator<ScopeEntry> iter = fullScope.iterator();
		while(iter.hasNext()) {
			ScopeEntry scope = iter.next();
			if (!(scope.param instanceof InheritableStringParameterDefinition)) {
				continue;
			}
			//Copy the ISPD and assign "our" root property as its parent, to
			//make sure that it can properly discover its parents
			InheritableStringParameterDefinition ispd =
					(InheritableStringParameterDefinition)
					scope.param.copyWithDefaultValue(
							scope.param.getDefaultParameterValue()
					);
			ispd.setRootProperty(this.rootProperty);
			
			//The last value from the scope gets overwritten by the user-entered value
			String ispdVal = (!iter.hasNext())
					? userEnteredValue
					: ispd.getDefaultValue();
			
			if (ispdVal == null) {
				continue;
			}
			
			mustHaveValueSet |= ispd.getMustBeAssigned();
			
			//Adjust for whitespaces
			WhitespaceMode wsMode = ispd.getWhitespaceModeAsVar();
			if (wsMode == WhitespaceMode.TRIM) {
				ispdVal = StringUtils.trim(ispdVal);
			}
			
			switch(currMode) {
				case OVERWRITABLE:
					value = ispdVal;
					break;
					
				case EXTENSIBLE:
					if (wsMode == WhitespaceMode.ADD_IF_EXTENSION) {
						value += " ";
					}
					value += ispdVal;
					break;
					
				case FIXED:
					//This should NOT have happened. A value that was fixed
					//may not be overridden later on
					String msg =
							"Not allowed to alter fixed parameter " +
							scope.param.getName();
					log.warning(msg);
					throw new IllegalArgumentException(msg);
				
				default:
					log.severe(String.format(
							"Detected unknown inheritance mode: %s",
							currMode.toString()
					));
					continue;
			}
			currMode = ispd.getInheritanceModeAsVar();
		}
		
		//Now, we can create the new value
		InheritableStringParameterValue ispv = new InheritableStringParameterValue(
				this.getName(), value, this.getDescription()
		);
		ispv.setMustHaveValueSet(mustHaveValueSet);
		
		return ispv;
	}
	
	public String getDerivedValue(String userEnteredValue, boolean noThrow)
			throws IllegalArgumentException {
		try {
			//Remove any prefix from parents, in case of a rebuild
			String val = this.stripInheritedPrefixFromValue(userEnteredValue);
			//Generate a full value based on that
			StringParameterValue spv = this.produceDerivedValue(val);
			if (spv == null) {
				return null;
			}
			return spv.value;
		} catch (IllegalArgumentException ex) {
			if (noThrow) { return null; }
			throw ex;
		}
		
	}
	
	public String getLocalValue(String userEnteredValue) {
		return this.stripInheritedPrefixFromValue(userEnteredValue);
	}
	
	public String getDefaultValue() {
		return super.getDefaultValue();
	}
	
	public String stripInheritedPrefixFromValue(String value) {
		if (value == null || value.isEmpty() || this.rootProperty == null) {
			return value;
		}
		
		//Fetching the ordered list of all PDs in the inheritance scope
		//Avoiding copying, as we might be called from within copyWithDefaultValue
		List<ScopeEntry> fullScope =
				this.rootProperty.getScopedParameterDefinition(this.getName());
		if (fullScope == null || fullScope.isEmpty()) {
			//No inherited entries to care for
			return value;
		}
		
		String newVal = value;
		
		//Itering over all parent PDs to drop prefixes
		Iterator<ScopeEntry> sIter = fullScope.iterator();
		while (sIter.hasNext()) {
			ScopeEntry scope = sIter.next();
			if (scope.param == null || !(scope.param instanceof InheritableStringParameterDefinition)) {
				continue;
			} else if (scope.param == this) {
				//Reached the current node, aborting prefix stripping
				break;
			}
			InheritableStringParameterDefinition ispd =
					(InheritableStringParameterDefinition) scope.param;
			
			//We ignore the last entry, as it the one the user actually overwrote
			if (!sIter.hasNext()) { break; }
			
			//Checking the mode of inheritance
			switch (ispd.getInheritanceModeAsVar()) {
				case EXTENSIBLE:
					break;
				default:
					//Reverting to the initial value, as no extension took place
					newVal = value;
					continue;
			}
			
			//Stripping the front part of the string
			String defVal = ispd.getDefaultValue().trim();
			if (newVal.startsWith(defVal)) {
				newVal = newVal.substring(defVal.length()).trim();
			}
		}
		
		//Returning the newly discovered value
		return newVal;
	}
	
	public void setDefaultValue(String defaultValue) {
		super.setDefaultValue(defaultValue);
	}
	
	/**
	 * Returns the default value for this parameter.
	 * <p>
	 * It will always return the locally defined value, and not a derived one
	 * you'd get via {@link #createValue(String)} or
	 * {@link #createValue(StaplerRequest)}, to be compatible with how the
	 * configure page is retrieving the locally defined value.
	 * <p>
	 * Unfortunately, this conflicts with the way jobs are being scheduled.
	 * There are at least 3 different ways for this:
	 * <ol>
	 * 	<li>Spawned via an HTTP/CLI request with parameters assigned</li>
	 * 	<li>Spawned via a parameterised trigger</li>
	 * 	<li>Spawned via direct call to
	 * 		{@link AbstractProject#scheduleBuild(hudson.model.Cause)} or other
	 * 		methods.</li>.
	 * </ol>
	 * The first two pass through {@link #createValue(String)} or
	 * {@link #createValue(StaplerRequest)}. Unfortunately, the last one
	 * calls this method here directly, because there only is the default value
	 * of the parameter to consider.<br/>
	 * As such, Jenkins code simply calls this method, instead of createValue.
	 * <p>
	 * This causes a significant complication, in that this function would
	 * need to figure out via slow reflection, through which code path it was
	 * called.<br/>
	 * Due to these performance reasons, we do not use reflection here and
	 * return the locally defined version.
	 * <p>
	 * <b>Always be aware of this locality of the value when using this method here.</b>
	 * <br/>
	 * Please bear in mind, that the returned value will <b>not</b> be an
	 * {@link InheritableStringParameterValue}, but a regular {@link StringParameterValue},
	 * to further denote the locality.
	 * 
	 * @see InheritanceProject#scheduleBuild2(int, hudson.model.Cause, java.util.Collection)
	 * 
	 * @return the local default value for this parameter definition as an
	 * {@link InheritableStringParameterValue}
	 */
	@Override
	public StringParameterValue getDefaultParameterValue() {
		return super.getDefaultParameterValue();
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
				//Fetch all parameters from the given project 
				ParametersDefinitionProperty pdp =
						proj.getProperty(ParametersDefinitionProperty.class, IMode.INHERIT_FORCED);
				if (!(pdp instanceof InheritanceParametersDefinitionProperty)) {
					continue;
				}
				InheritanceParametersDefinitionProperty ipdp =
						(InheritanceParametersDefinitionProperty) pdp;
				List<ScopeEntry> scope = ipdp.getScopedParameterDefinition(name);
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
