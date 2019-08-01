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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.AbstractProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * This class servers as an extension point to render additional configuration
 * entries for a build action.
 * <p>
 * It is useful to add additional properties to the build; regardless of
 * whether or not this happens on a build or re-build.
 * <p>
 * The extension needs to implement a "value.groovy" or "value.jelly" which
 * gets added into the form tag of the build submission. The name of this
 * file can be overridden via {@link #getValuePage()}.
 * <p>
 * The fields will be put into the JSON form data inside the
 * {@link StaplerRequest} when the build is executed. For simplicity's sake,
 * the method {@link #onBuild(AbstractProject, StaplerRequest)} will be called
 * whenever a build is configured.
 * 
 * 
 * @author mhschroe
 *
 */
public abstract class BuildViewExtension implements ExtensionPoint {

	public BuildViewExtension() {
		//Nothing to do
	}

	public String getValuePage() {
		return "value";
	}
	
	/**
	 * This function is being called, shortly before a build is being
	 * scheduled.
	 * <p>
	 * It allows you to inspect and alter the project and StaplerRequest and
	 * return additional Actions to be contributed to the build.
	 * <p>
	 * If a {@link ParametersAction} is returned, its definitions will be
	 * merged with the ParametersAction created from the build parameter page.
	 * 
	 * @param project the project for which a build will be started
	 * @param req the WebUI request that triggered the build.
	 * 
	 * @return a list of actions to contribute. May be empty, but never null.
	 * 
	 * @throws ServletException in case the build should be prevented entirely.
	 */
	public List<Action> onBuild(
			AbstractProject<?, ?> project, StaplerRequest req
	) throws ServletException {
		return Collections.emptyList();
	}
	
	
	public static final List<Action> callAll(
			AbstractProject<?, ?> project, StaplerRequest req
	) throws ServletException {
		List<Action> actions = new LinkedList<Action>();
		for (BuildViewExtension ext : Jenkins.get().getExtensionList(BuildViewExtension.class)) {
			actions.addAll(ext.onBuild(project, req));
		}
		//Merge ParametersActions together
		mergeParameters(actions);
		return actions;
	}
	
	public static final void mergeParameters(List<Action> actions) {
		List<ParameterValue> pv = new LinkedList<ParameterValue>();
		Iterator<Action> iter = actions.iterator();
		while (iter.hasNext()) {
			Action a = iter.next();
			if (!(a instanceof ParametersAction)) {
				continue;
			}
			ParametersAction pa = (ParametersAction) a;
			pv.addAll(pa.getParameters());
			iter.remove();
		}
		if (!pv.isEmpty()) {
			actions.add(new ParametersAction(pv));
		}
	}
	
	/**
	 * Calling getSubmittedForm, if no form is present in a {@link StaplerRequest}
	 * will throw an ugly exception. Call this to avoid the exception.
	 * <p>
	 * This can happen when triggering jobs that do not have parameters, and
	 * thus trigger a build purely via an HTTP GET without a form.
	 * 
	 * @param req the user-request
	 * 
	 * @return the JSON form, or null if none is present (or the request was not a POST)
	 */
	public static final JSONObject getSubmittedFormSafely(StaplerRequest req) {
		if (!req.getMethod().equals("POST")) {
			return null;
		}
		//Check if it looks like a form submission
		Map<?,?> pMap = req.getParameterMap();
		String cType = req.getContentType();
		boolean hasFormContent = (
				(pMap != null && pMap.containsKey("json")) ||
				(cType != null && cType.startsWith("multipart/"))
		);
		if (!hasFormContent) {
			return null;
		}
		try {
			return req.getSubmittedForm();
		} catch (ServletException e) {
			return null;
		}
	}
}
