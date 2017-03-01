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
package hudson.plugins.project_inheritance.widgets;

import hudson.model.Queue.Task;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.widgets.BuildHistoryWidget;

public class ExtendedBuildHistoryWidget extends BuildHistoryWidget<InheritanceProject> {

	public ExtendedBuildHistoryWidget(Task owner, Iterable<InheritanceProject> baseList,
			hudson.widgets.HistoryWidget.Adapter<? super InheritanceProject> adapter) {
		super(owner, baseList, adapter);
	}
}
