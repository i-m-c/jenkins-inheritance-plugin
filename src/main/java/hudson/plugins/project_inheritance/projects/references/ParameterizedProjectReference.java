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
package hudson.plugins.project_inheritance.projects.references;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Failure;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;


/**
 * This class extends {@link SimpleParameterizedProjectReference} to add
 * a variance to the reference, in case the same project needs to be referred
 * to multiple times.
 * 
 * @author Martin Schroeder
 */
public class ParameterizedProjectReference extends SimpleParameterizedProjectReference {
	
	protected String variance = null;
	
	public ParameterizedProjectReference(String targetJob, String variance,
			List<ParameterDefinition> parameters) {
		super(targetJob, parameters);
		
		if (StringUtils.isNotBlank(variance)) {
			this.variance = variance.trim();
		}
	}
	
	
	// === FIELD ACCESS FUNCTIONS ===
	
	public String getVariance() {
		if (StringUtils.isBlank(this.variance)) {
			return null;
		}
		return this.variance;
	}
	
	
	// === DESCRIPTOR DEFINITION ===
	
	@Extension
	public static class ParameterizedReferenceDescriptor extends SimpleParameterizedReferenceDescriptor {
		@Override
		public String getDisplayName() {
			return Messages.ParameterizedProjectReference_DisplayName();
		}
		
		@Override
		public AbstractProjectReference newInstance(
				StaplerRequest req, JSONObject formData) throws FormException {
			String targetJob = formData.getString("targetJob");
			
			String variance = formData.getString("variance");
			FormValidation formValidation = doCheckVariance(formData.getString("variance"));
			if (formValidation.kind != FormValidation.Kind.OK) {
				throw new FormException(
						formValidation.getMessage(),
						"variance"
				);
			}
			
			Object jParams = formData.get("parameters");
			List<ParameterDefinition> params = 
					ParameterDescriptor.newInstancesFromHeteroList(
							req, jParams, ParameterDefinition.all()
					);
			
			return new ParameterizedProjectReference(targetJob, variance, params);
		}
		
		public FormValidation doCheckVariance(@QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			return validGoodName(value);
		}
		
		private FormValidation validGoodName(String variance) {
			if (StringUtils.isBlank(variance)) {
				return FormValidation.ok();
			}
			try {
				Jenkins.checkGoodName(variance);
				return FormValidation.ok();
			} catch (Failure e) {
				return FormValidation.error(e.getMessage());
			}
		}
	}
}
