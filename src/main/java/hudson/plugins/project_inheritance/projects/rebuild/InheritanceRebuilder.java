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

package hudson.plugins.project_inheritance.projects.rebuild;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.ParametersAction;
import hudson.model.listeners.RunListener;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;

/**
 * This class implements a RunListener specifically geared towards the
 * InheritanceBuild class.
 * <p>
 * Its sole purpose is to append the {@link InheritanceRebuildAction} to
 * newly completed {@link InheritanceBuild} runs
 * <p>
 * Do note that this clashes with the more general "Rebuilder" plugin, which is
 * unfortunately completely unsuitable for InheritanceBuilds, due to not
 * supporting the GUI files for the {@link InheritableStringParameterDefinition}
 * parameter type introduced by this plugin.
 * 
 * @see RebuildValidatorSuppressor
 */
@Extension
public class InheritanceRebuilder extends RunListener<InheritanceBuild> {

	public InheritanceRebuilder() {
		super();
	}

	@Override
	public void onCompleted(InheritanceBuild build, TaskListener listener) {
		//Asking if the build had parameters set
		ParametersAction p = build.getAction(ParametersAction.class);
		if (p != null) {
			//It had, so we add an action to rebuild everything to it
			InheritanceRebuildAction rebuildAction = new InheritanceRebuildAction();
			build.getActions().add(rebuildAction);
		}
	}
}