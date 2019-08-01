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
package hudson.plugins.project_inheritance.projects.inheritance;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference.PrioComparator.SELECTOR;

public class ParameterSelector
		extends InheritanceSelector<JobProperty<?>> {
	private static final long serialVersionUID = 6765147898181407182L;
	
	public static final String VERSION_PARAM_NAME = "JENKINS_JOB_VERSIONS";

	@Extension
	public static final ParameterSelector instance = new ParameterSelector();
	
	public static class ScopeEntry {
		public final String owner;
		public final ParameterDefinition param;
		
		public ScopeEntry(String owner, ParameterDefinition param) {
			this.owner = owner;
			this.param = param;
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder();
			b.append('[');
			b.append(owner);
			b.append(", ");
			b.append(param.toString());
			b.append(']');
			return b.toString();
		}
	}
	
	
	@Override
	public boolean isApplicableFor(
			Class<?> clazz) {
		return JobProperty.class.isAssignableFrom(clazz);
	}
	
	@Override
	public MODE getModeFor(
			Class<?> clazz) {
		if (ParametersDefinitionProperty.class.isAssignableFrom(clazz)) {
			return MODE.MERGE;
		}
		return MODE.NOT_RESPONSIBLE;
	}

	@Override
	public String getObjectIdentifier(JobProperty<?> obj) {
		//All sorts of parameter definitions should be mashed together into one
		if (obj instanceof ParametersDefinitionProperty) {
			return "SINGLETON";
		} else {
			return null;
		}
	}

	@Override
	public JobProperty<?> merge(
			JobProperty<?> prior,
			JobProperty<?> latter,
			InheritanceProject caller) {
		if (!(prior instanceof ParametersDefinitionProperty) ||
				!(latter instanceof ParametersDefinitionProperty)) {
			return latter;
		}
		
		return this.createMerged(
				(ParametersDefinitionProperty) prior,
				(ParametersDefinitionProperty) latter
		);
	}
	
	/**
	 * This method is used to merge two PDPs in the order of their inheritance.
	 * <p>
	 * This is repeatedly called, until the final set of properties only
	 * contains a single parameter-property.
	 * <p>
	 * This method will merge ParameterDefinitions based on their name. If they
	 * are inheritance-aware (e.g. an {@link InheritableStringParameterDefinition}),
	 * it will merge the two parameters into one, based on the inheritance
	 * settings.
	 * <p>
	 * The result is a "frozen" parameter definition with the full inheritance
	 * applied to it. That property can then be used as-is, without having to
	 * be inheritance and versioning unaware.
	 * <p>
	 * If you need access to the precise nature of how everything was merged,
	 * use 
	 * <p>
	 * Related to this, do note that the sanity of this merge is checked 
	 * separately by:
	 * <ul>
	 *  <li>{@link InheritanceProject#getParameterSanity()} and</li>
	 *  <li>{@link InheritanceParametersDefinitionProperty#checkParameterSanity(hudson.plugins.project_inheritance.projects.InheritanceBuild, hudson.model.BuildListener)}</li>
	 * </ul>
	 * The former will emit a warning and disable the buildable flag.<br>
	 * The latter will abort builds if bad values are present.
	 * 
	 * @param prior the property from earlier in the inheritance
	 * @param latter the property from later in the inheritance
	 * 
	 * @return a merged property, according to the rules laid out above.
	 */
	private ParametersDefinitionProperty createMerged(
			ParametersDefinitionProperty prior,
			ParametersDefinitionProperty latter) {
		ParametersDefinitionProperty[] pdps = {latter, prior};
		
		/* Merge the ParameterDefinitions based on their name
		 * 
		 * Using a tree-map sorts the parameters alphabetically, which avoids
		 * the issue of the order being affected by the inheritance order.
		 */
		TreeMap<String, ParameterDefinition> unifyMap =
				new TreeMap<String, ParameterDefinition>();
		for (int i = pdps.length-1; i >= 0; i--) {
			ParametersDefinitionProperty pdp = pdps[i];
			for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
				ParameterDefinition past = unifyMap.get(pd.getName());
				//Check if there exists a predecessor
				if (past == null) {
					unifyMap.put(pd.getName(), pd);
					continue;
				}
				/* If a previous version exists, a merge might need to happen.
				 * 
				 * Do note that the sanity of this merge is checked separately
				 * by:
				 *  - InheritanceProject.getParameterSanity() and
				 *  - InheirtanceBuild.checkParameterSanity().
				 * 
				 * The former will emit a warning and disable the buildable flag.
				 * The latter will abort builds if bad values are present.
				 * 
				 */
				if (pd instanceof InheritableStringParameterDefinition) {
					InheritableStringParameterDefinition ispd =
							((InheritableStringParameterDefinition)pd);
					//Execute the merge
					unifyMap.put(
							pd.getName(),
							ispd.getMergeWithOther(past)
					);
				} else {
					//Straight overwrite in case of non-inheritance-aware PDs
					unifyMap.put(pd.getName(), pd);
				}
			}
		}
		
		//PDP expects a list -- use ArrayList, to avoid chain-link creation
		List<ParameterDefinition> unifyList =
				new ArrayList<ParameterDefinition>(unifyMap.values());
		
		//Return the merged PDP, as an IPDP, while using the "latter" owner
		InheritanceParametersDefinitionProperty ret =
				new InheritanceParametersDefinitionProperty(
						latter.getOwner(), unifyList
				);
		return ret;
	}
	
	/**
	 * This method is triggered, when a singleton is encountered -- either
	 * after everything has been merged or there is only a single value left.
	 */
	@Override
	public JobProperty<?> handleSingleton(
			JobProperty<?> jp,
			InheritanceProject caller) {
		//Only handle PDPs
		if (!(jp instanceof ParametersDefinitionProperty)) { return jp; }
		ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) jp;
		
		/* Make sure that the caller is the owner of the property and that
		 * an IPDP is returned.
		 * This is because the original PDP does not allow undefined parameters
		 * (those that have no ParameterDefinition) to be passed into the build,
		 * while an IPDP permits that.
		 */
		if (pdp.getOwner() == caller && pdp instanceof InheritanceParametersDefinitionProperty) {
			//No need to do any copying based on a new caller
			return jp;
		}
		return new InheritanceParametersDefinitionProperty(
				caller, pdp.getParameterDefinitions()
		);
	}
	

	// === PARAMETER SCOPE COMPUTATION ===
	
	/**
	 * Returns all parameter definitions that are involved in generating parameter values.
	 * <p>
	 * It walks the inheritance tree for the current request and
	 * generates a list of all parameter declarations that are involved in
	 * generating the final values of all parameters.
	 * <p>
	 * The returned list is sorted in order of when each definition is
	 * encountered, so it walks from the current project upwards.
	 * Do note that it is likely, that parameters with the same
	 * name are not stored sequentially.
	 * <p>
	 * <b>Beware:</b> The returned scope contains the original
	 * {@link ParameterDefinition}s, as they are stored in the project class
	 * instances. Each individual parameter has no idea about its position in
	 * the inheritance. If you need finalized values, you need to use
	 * {@link InheritableStringParameterDefinition#getMergeWithOther(ParameterDefinition)}
	 * or
	 * {@link #createMerged(ParametersDefinitionProperty, ParametersDefinitionProperty)}
	 * 
	 * @param root the project for which to retrieve the parameter scope
	 * 
	 * @return a list of {@link ScopeEntry} instances, sorted by order of
	 * derivation by inheritance. Never null, but may be empty.
	 */
	public List<ScopeEntry> getAllScopedParameterDefinitions(InheritanceProject root) {
		List<ScopeEntry> lst = new LinkedList<ScopeEntry>();
		
		//Now, we get the sorted list of *all* parents, not just the direct ones
		for (AbstractProjectReference ref : root.getAllParentReferences(SELECTOR.PARAMETER, true)) {
			InheritanceProject par = ref.getProject();
			if (par == null) { continue; }
			
			//Grab the LOCALLY defined parameters for the project
			ParametersDefinitionProperty parPDP = par.getProperty(
					ParametersDefinitionProperty.class,
					IMode.LOCAL_ONLY
			);
			if (parPDP == null) { continue; }
			
			for (ParameterDefinition pd : parPDP.getParameterDefinitions()) {
				lst.add(new ScopeEntry(par.getFullName(), pd));
			}
		}
		
		//At the end, we must also add the parameters from a possible variance
		ParametersDefinitionProperty variance = root.getVarianceParameters();
		if (variance != null) {
			for (ParameterDefinition pd : variance.getParameterDefinitions()) {
				lst.add(new ScopeEntry(root.getFullName(), pd));
			}
		}
		
		//TODO: This should be cached, if possible
		return lst;
	}
	
	
	public List<ScopeEntry> getScopedParameterDefinition(InheritanceProject root, String name) {
		List<ScopeEntry> all = getAllScopedParameterDefinitions(root);
		List<ScopeEntry> out = new LinkedList<ScopeEntry>();
		for (ScopeEntry se : all) {
			String sName = se.param.getName();
			if (StringUtils.equals(sName, name)) {
				out.add(se);
			}
		}
		return out;
	}
}
