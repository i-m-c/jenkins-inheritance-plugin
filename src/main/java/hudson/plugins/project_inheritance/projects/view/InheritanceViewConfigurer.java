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
package hudson.plugins.project_inheritance.projects.view;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.rebuild.RebuildValidatorSuppressor;

/**
 * This class implements a RunListener specifically geared towards the
 * InheritanceBuild class.
 * <p>
 * Its sole purpose is to append the {@link BuildFlowScriptAction} to
 * newly completed {@link InheritanceBuild} runs
 * 
 * @see RebuildValidatorSuppressor
 */
@Extension
public class InheritanceViewConfigurer extends RunListener<InheritanceBuild> {

	public InheritanceViewConfigurer() {
		super();
	}

	@Override
	public void onCompleted(InheritanceBuild build, TaskListener listener) {
		BuildFlowScriptAction view = new BuildFlowScriptAction();
		build.addAction(view);
	}
}
