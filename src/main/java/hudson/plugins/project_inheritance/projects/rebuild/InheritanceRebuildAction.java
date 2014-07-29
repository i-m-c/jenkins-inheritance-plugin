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

import hudson.model.Action;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.parameters.InheritanceParametersDefinitionProperty;
import hudson.plugins.project_inheritance.util.Reflection;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * This class implements the actions that are necessary to rebuild a
 * parameterised, inheritable job.
 * <p>
 * Do note that this method could also be made much more generic to suit all
 * possible {@link AbstractBuild} types. This would make it a suitable,
 * complete and improved replacement for the old 2010 "Rebuilder" plugin, as
 * that one can only rebuild jobs with a limited set of parameter-types.
 * 
 * 
 * TODO: Explore if such a generality is possible. It should be.
 * 
 * @author mhschroe
 *
 */
public class InheritanceRebuildAction implements Action {
	private transient InheritanceBuild build; 
	

	/**
	 * Returns the {@link InheritanceProject} associated with the current
	 * {@link StaplerRequest}.
	 * 
	 * @return null, if no {@link InheritanceProject} is associated with the
	 * given request.
	 */
	public InheritanceProject getProject() {
		return this.getProject(Stapler.getCurrentRequest());
	}
	
	/**
	 * This method returns the {@link InheritanceProject} associated with the
	 * given request; if any are.
	 * 
	 * Otherwise, returns null
	 * 
	 * @param request the request to check for an {@link InheritanceProject}
	 * @return null, if no such project is associated with the request
	 */
	public InheritanceProject getProject(StaplerRequest request) {
		return InheritanceProject.DESCRIPTOR.getConfiguredProject(request);
	}
	
	public InheritanceBuild getBuild() {
		if (this.build == null) {
			StaplerRequest req = Stapler.getCurrentRequest();
			return this.getBuild(req);
		}
		return build;
	}
	
	public InheritanceBuild getBuild(StaplerRequest req) {
		if (build == null && req != null) {
			this.build = req.findAncestorObject(InheritanceBuild.class);
		}
		return build;
	}
	
	private boolean isApplicableFor(InheritanceProject ip) {
		return (
				ip != null &&
				ip.hasPermission(AbstractProject.BUILD) &&
				ip.isBuildable() &&
				!(ip.isDisabled())
		);
	}
	
	public String getIconFileName() {
		//Checking if the project associated with the current request is valid
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
			//The rebuild action is allowed to be displayed and gets the
			//default icon for rebuilds
			return "clock.gif";
		} else {
			//Not assigning an icon automagically causes the build action to
			//not be displayed.
			return null;
		}
	}

	public String getDisplayName() {
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
			return "Rebuild";
		} else {
			return null;
		}
	}

	public String getUrlName() {
		InheritanceProject ip = this.getProject();
		if (this.isApplicableFor(ip)) {
			return "rebuild";
		} else {
			return null;
		}
	}

	public List<ParameterDefinition> getParametersFor(StaplerRequest request) {
		return this.getParametersFor(request, null);
		
	}
	
	public List<ParameterDefinition> getParametersFor(StaplerRequest request, Boolean showHidden) {
		//First, we fetch the project associated with that request
		InheritanceProject ip = this.getProject(request);
		//Then, we fetch the build associated with that request
		this.build = request.findAncestorObject(InheritanceBuild.class);
		
		//Sanity check
		if (ip == null || this.build == null) {
			//TODO: Return an error value here
			return new LinkedList<ParameterDefinition>();
		}
		
		//Reloading the versions used by the build
		Map<String, Long> versions = this.build.getProjectVersions();
		if (versions != null) {
			InheritanceProject.setVersioningMap(versions);
		}
		
		
		//Building a map of parameter names to parameter values
		HashMap<String, ParameterValue> map =
				new HashMap<String, ParameterValue>();
		
		//Retrieving the build actions for that build
		ParametersAction action =
				this.build.getAction(hudson.model.ParametersAction.class);
		
		for (ParameterValue value : action.getParameters()) {
			map.put(value.getName(), value);
		}
		
		//We fetch the definitions set by the project
		List<ParameterDefinition> pdLst = 
				ip.getParameters(IMode.INHERIT_FORCED);
		
		List<ParameterDefinition> modLst =
				new LinkedList<ParameterDefinition>();
		
		//Adjust their default values, to reflect those actually set
		//for the previous job
		Set<String> handledVars = new HashSet<String>();
		for (ParameterDefinition pd : pdLst) {
			//Check if that definition has the "hidden" property set
			if (showHidden != null) {
				Object o = Reflection.invokeIfPossible(pd, "getIsHidden");
				boolean isHidden = (o != null && o instanceof Boolean && (Boolean)o);
				if (showHidden && ! isHidden || !showHidden && isHidden) {
					//Ignore this entry
					handledVars.add(pd.getName());
					continue;
				}
			}
			// Checking if we have an suitable action defined for that
			ParameterValue pv = map.get(pd.getName());
			if (pv == null) {
				//Using regular default valued definition
				modLst.add(pd);
				handledVars.add(pd.getName());
				continue;
			}
			//Otherwise, we fetch&add a copy with a new default value
			modLst.add(pd.copyWithDefaultValue(pv));
			handledVars.add(pv.getName());
		}
		
		//Now, at last, we create StringParameterValues for all variables not
		//covered by ParameterDefinitions from the job (i.e. dynamically contributed vars)
		//NOTE: Dynamically allocated variables are ALWAYS hidden
		if (showHidden != null && showHidden) {
			for (ParameterValue pv : map.values()) {
				//Ignore params that were already added above
				if (handledVars.contains(pv.getName())) { continue; }
				//Ignore those, that are not strings
				if (!(pv instanceof StringParameterValue)) { continue; }
				StringParameterValue spv = (StringParameterValue) pv;
				
				modLst.add(new StringParameterDefinition(spv.getName(), spv.value));
			}
		}
		
		//Sort the modified list by name
		Collections.sort(modLst, new Comparator<ParameterDefinition>() {
			@Override
			public int compare(ParameterDefinition o1, ParameterDefinition o2) {
				if (o1 == null && o2 == null) { return 0; }
				if (o1 == null) { return -1; }
				if (o2 == null) { return 1; }
				return o1.getName().compareTo(o2.getName());
			}
		});
		
		//And finally, we return the tweaked list of entries
		return modLst;
	}
	
	
	/**
	 * This method contains the necessary steps to re-build a Project based
	 * on the altered parameters present in the {@link StaplerRequest}. 
	 * 
	 * It is called by the form submission dialog defined in the
	 * "index.jelly" file for this class.
	 */
	public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp)
			throws ServletException, IOException,
			InterruptedException {
		
		//Decoding the version map
		Map<String, Long> verMap = InheritanceRebuildAction.decodeVersionMap(
				req
		);
		if (verMap == null) {
			//TODO: Redirect to error page
			rsp.sendRedirect(".");
		}
		
		//Checking if a refresh is desired
		if (req.hasParameter("doRefresh")) {
			//Attaching the selected versions as a URL parameter
			String verMapStr = 
					InheritanceParametersDefinitionProperty
					.encodeVersioningMap(verMap);
			if (verMapStr == null || verMapStr.isEmpty()) {
				//TODO: Redirect to error page
				rsp.sendRedirect(".");
			}
			String redirURL = String.format(
					"?versions=\"%s\"",
					verMapStr
			);
			rsp.sendRedirect(redirURL);
			return;
		} else if (!req.hasParameter("doRebuild")) {
			//Just do nothing and send the user back to the origin
			rsp.sendRedirect(".");
		}
		
		//The user instructed us to do a rebuild
		
		//Determine the project this request is associated with
		InheritanceProject ip = this.getProject(req);
		if (ip == null) {
			rsp.sendRedirect(req.getContextPath());
			return;
		} else if (this.isApplicableFor(ip) == false) {
			//Redirecting back to the job's page
			rsp.sendRedirect(req.getContextPath() + "/job/" + ip.getName());
			return;
		}
		//Check if we were passed sensible data
		if (!req.getMethod().equals("POST")) {
			// show the parameter entry form again
			req.getView(this, "index.jelly").forward(req, rsp);
			return;
		}
		
		//Setting the versioning information
		InheritanceProject.setVersioningMap(verMap);
		
		//Triggering the build; do note that this will trigger a forward
		//on the request; unless the "rebuildNoRedirect" attribute is set
		//Also do note that that is an Inheritance-Project specific action
		req.setAttribute("rebuildNoRedirect", true);
		TimeDuration delay = (build != null)
				? new TimeDuration(build.getProject().getQuietPeriod())
				: new TimeDuration(0);
		ip.doBuild(
				req, rsp, delay
		);
		
		//Sending the user to the project's root page
		rsp.sendRedirect(req.getContextPath() + "/job/" + ip.getName());
	}

	
	public static Map<String, Long> decodeVersionMap(StaplerRequest req) {
		JSONObject jForm;
		try {
			jForm = req.getSubmittedForm();
		} catch (ServletException e) {
			return null;
		}
		
		
		String[] projects = null;
		try {
			Object obj = jForm.get("project");
			if (obj instanceof JSONArray) {
				JSONArray a = (JSONArray)obj;
				projects = new String[a.size()];
				for (int i = 0; i < a.size(); i++) {
					projects[i] = ((JSONArray)obj).getString(i);
				}
			} else if (obj instanceof String) {
				projects = new String[1];
				projects[0] = obj.toString();
			}
		} catch (JSONException ex) {
			projects = null;
		}

		Long[] versions = null;
		try {
			Object obj = jForm.get("version");
			if (obj instanceof JSONArray) {
				JSONArray a = (JSONArray)obj;
				versions = new Long[a.size()];
				for (int i = 0; i < a.size(); i++) {
					versions[i] = ((JSONArray)obj).getLong(i);
				}
			} else if (obj instanceof String) {
				versions = new Long[1];
				versions[0] = Long.valueOf(obj.toString());
			}
		} catch (JSONException ex) {
			versions = null;
		} catch (NumberFormatException ex) {
			versions = null;
		}
		
		if (projects == null || versions == null ||
				versions.length != projects.length) {
			return null;
		}

		//Decoding the version map from the submission
		Map<String, Long> verMap = new HashMap<String, Long>();
		
		for (int i = 0; i < projects.length; i++ ) {
			verMap.put(projects[i], versions[i]);
		}
		
		return verMap;
	}
}
