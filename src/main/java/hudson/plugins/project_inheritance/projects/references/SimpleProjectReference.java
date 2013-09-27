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

package hudson.plugins.project_inheritance.projects.references;

import hudson.Extension;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


/**
 * This class is an implementation of {@link AbstractProjectReference} with
 * no new fields added.
 * 
 * Its only use is to prevent having to use {@link AbstractProjectReference}
 * in an untyped "raw" manner.
 * 
 * @author Martin Schröder
 */
public class SimpleProjectReference extends AbstractProjectReference {

	@DataBoundConstructor
	public SimpleProjectReference(String name) {
		super(name);
	}
	
	
	@Extension
	public static class SimpleProjectReferenceDescriptor extends ProjectReferenceDescriptor {
		@Override
		public String getDisplayName() {
			return "Simple Project Reference";
			//return Messages.StringParameterDefinition_DisplayName();
		}

		@Override
		public AbstractProjectReference newInstance(
				StaplerRequest req, JSONObject formData) throws FormException {
			return req.bindJSON(SimpleProjectReference.class, formData);
		}
		
		/*
		@Override
		public String getHelpFile() {
			return "/help/parameter/string.html";
		}
		*/
	}
}
