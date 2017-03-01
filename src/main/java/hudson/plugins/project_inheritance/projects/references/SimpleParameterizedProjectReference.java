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
package hudson.plugins.project_inheritance.projects.references;

import java.util.LinkedList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import net.sf.json.JSONObject;


/**
 * This class is an implementation of {@link AbstractProjectReference} with
 * with the added option of specifying addition parameters to be passed to
 * the referenced Project.
 * 
 * @author Martin Schroeder
 */
public class SimpleParameterizedProjectReference extends SimpleProjectReference {

	protected List<ParameterDefinition> parameters;
	protected String variance = null;
	
	@DataBoundConstructor
	public SimpleParameterizedProjectReference(
			String targetJob,
			List<ParameterDefinition> parameters) {
		super(targetJob);
		InheritanceProject project = this.getProject();
		if (project != null && parameters != null) {
			for (ParameterDefinition pd : parameters) {
				if (pd instanceof InheritableStringParameterDefinition) {
					InheritableStringParameterDefinition ispd =
							(InheritableStringParameterDefinition) pd;
					ispd.setRootProperty(project.getProperty(
							InheritanceParametersDefinitionProperty.class
					));
				}
			}
		}
		if (parameters == null) {
			this.parameters = new LinkedList<ParameterDefinition>();
		} else {
			this.parameters = parameters;
		}
	}
	
	
	// === FIELD ACCESS FUNCTIONS ===
	
	public List<ParameterDefinition> getParameters() {
		if (this.parameters == null) {
			this.parameters = new LinkedList<ParameterDefinition>();
		}
		return this.parameters;
	}
	
	
	// === DESCRIPTOR DEFINITION ===
	
	@Extension
	public static class SimpleParameterizedReferenceDescriptor extends ProjectReferenceDescriptor {
		@Override
		public String getDisplayName() {
			return "Simple Parameterized Project Reference";
			//return Messages.StringParameterDefinition_DisplayName();
		}
		
		@Override
		public AbstractProjectReference newInstance(
				StaplerRequest req, JSONObject formData) throws FormException {
			String name = formData.getString("name");
			
			Object jParams = formData.get("parameters");
			List<ParameterDefinition> params = 
					ParameterDescriptor.newInstancesFromHeteroList(
							req, jParams, ParameterDefinition.all()
					);
			
			return new SimpleParameterizedProjectReference(name, params);
		}
	}
}
