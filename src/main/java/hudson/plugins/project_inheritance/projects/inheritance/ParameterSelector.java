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

package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;

@Extension
public class ParameterSelector
		extends InheritanceSelector<JobProperty<?>> {
	private static final long serialVersionUID = 6765147898181407182L;

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
		//All regular and inheritable parameter definitions are the same, as
		//there should be only one!
		if (obj.getClass() == ParametersDefinitionProperty.class ||
				obj.getClass() == InheritanceParametersDefinitionProperty.class) {
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
		
		InheritanceParametersDefinitionProperty ipdp =
				InheritanceParametersDefinitionProperty
				.createMerged(
						(ParametersDefinitionProperty) prior,
						(ParametersDefinitionProperty) latter
				);
		return ipdp;
	}
	
	/**
	 * This method makes sure that only
	 * {@link InheritanceParametersDefinitionProperty} objects are stored in
	 * the list returned to Jenkins and that they are all owned by the caller.
	 * This means that it will convert+copy objects that do not match this
	 * and will make sure that the correct scoping is set for each new
	 * {@link InheritanceParametersDefinitionProperty} object.
	 */
	@Override
	public JobProperty<?> handleSingleton(
			JobProperty<?> jp,
			InheritanceProject caller) {
		boolean needsCopy = false;
		
		if (!(jp instanceof ParametersDefinitionProperty)) {
			return jp;
		}
		ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) jp;
		
		//Checking if we already deal with an IPDP
		if (pdp instanceof InheritanceParametersDefinitionProperty) {
			//Checking if the caller already owns the IPDP; if not we copy
			if (pdp.getOwner() != caller) {
				needsCopy = true;
			}
		} else {
			needsCopy = true;
		}
		
		if (needsCopy) {
			InheritanceParametersDefinitionProperty ipdp =
					new InheritanceParametersDefinitionProperty(
							(caller != null) ? caller : pdp.getOwner(),
							pdp
					);
			return ipdp;
		} else {
			return pdp;
		}
	}
	

}
