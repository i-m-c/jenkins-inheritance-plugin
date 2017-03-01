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
package hudson.plugins.project_inheritance.projects.references.filters;

import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;

/**
 * This interface defines methods needed to filter a list of Jenkins jobs.
 * <p>
 * {@link AbstractProjectReference}s are used in several parts of the GUI.
 * Sometimes they reference parent jobs, sometimes siblings, sometimes jobs
 * of specific classes.
 * <p>
 * To allow the same descriptors and Groovy/Jelly files to be used, an
 * additional filter must be run, which selects which job names go into
 * the "select" combobox to specify the reference.
 * <p>
 * Just override this interface, and pass the object into
 * {@link hudson.plugins.project_inheritance.projects.references.AbstractProjectReference.ProjectReferenceDescriptor#doFillNameItems(String)}
 * 
 * @author mhschroe
 *
 */
public interface IProjectReferenceFilter {
	public boolean isApplicable(InheritanceProject project);
}
