/**
 * Copyright (c) 2018-2019 Intel Corporation
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
package hudson.plugins.project_inheritance.projects.parameters;

import static javax.servlet.http.HttpServletResponse.SC_CREATED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

import hudson.AbortException;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.Queue.Task;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.queue.ScheduleResult;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector;
import hudson.plugins.project_inheritance.projects.inheritance.ParameterSelector.ScopeEntry;
import hudson.plugins.project_inheritance.projects.parameters.InheritableStringParameterDefinition.IModes;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.projects.view.BuildViewExtension;
import hudson.plugins.project_inheritance.util.Reflection;
import jenkins.model.Jenkins;
import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * This class is a wrapper around {@link ParametersDefinitionProperty}.
 * <p>
 * This is necessary, because {@link ParametersDefinitionProperty} does not
 * expose the setOwner() method, which is needed for the GUI to work correctly.
 * <p>
 * It also changes the parameter GUI to correctly handle the "isHidden" property
 * of parameters.
 * 
 * @author mhschroe
 */
public class InheritanceParametersDefinitionProperty extends
		ParametersDefinitionProperty {
	
	public static Comparator<ParameterDefinition> paramComp = new Comparator<ParameterDefinition>() {
		@Override
		public int compare(ParameterDefinition o1, ParameterDefinition o2) {
			return o1.getName().compareTo(o2.getName());
		}
	};
	
	// === CONSTRUCTORS AND CONSTRUCTOR HELPERS ===
	
	public InheritanceParametersDefinitionProperty(
			AbstractProject<?,?> owner,
			List<ParameterDefinition> parameterDefinitions) {
		super(copySortParameters(parameterDefinitions));
		
		//Save the final owner that created this IPDP
		this.owner = owner;
	}
	
	public InheritanceParametersDefinitionProperty(
			AbstractProject<?,?> owner,
			ParameterDefinition... parameterDefinitions) {
		this(owner, Arrays.asList(parameterDefinitions));
	}
	
	public InheritanceParametersDefinitionProperty(
			AbstractProject<?,?> owner,
			ParametersDefinitionProperty other) {
		this(owner, other.getParameterDefinitions());
	}
	
	public static final List<ParameterDefinition> copySortParameters(List<ParameterDefinition> in) {
		//Create a sorted copy of all PDs in that list
		ArrayList<ParameterDefinition> ret = new ArrayList<>(in);
		Collections.sort(ret, paramComp);
		return ret;
	}
	
	
	
	
	
	// === BUILD HANDLING METHODS ===
	
	@Override
	@Deprecated
	public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		_doBuild(req, rsp, this.getDelayFromRequest(req));
	}
	
	@Override
	public void _doBuild(
			StaplerRequest req, StaplerResponse rsp,
			@QueryParameter TimeDuration delay
	) throws IOException, ServletException {
		if(!req.getMethod().equals("POST")) {
			// show the parameter entry form.
			req.getView(this,"index.jelly").forward(req,rsp);
			return;
		}

		List<ParameterValue> values = new ArrayList<ParameterValue>();
		
		JSONObject formData = req.getSubmittedForm();
		JSONArray a = JSONArray.fromObject(formData.get("parameter"));

		for (Object o : a) {
			if (o instanceof JSONObject) {
				JSONObject jo = (JSONObject) o;
				String name = jo.getString("name");
				
				ParameterDefinition d = this.getParameterDefinition(name);
				ParameterValue parameterValue;
				if (d != null) {
					parameterValue = d.createValue(req, jo);
				} else if (jo.has("value")) {
					//Create an SPV
					parameterValue = new StringParameterValue(
							name, jo.getString("value")
					);
				} else {
					throw new IllegalArgumentException(
							"No such parameter definition and also not a string parameter: " + name);
				}
				if (parameterValue != null) {
					values.add(parameterValue);
				}
			}
		}
		
		if (delay == null) {
			delay = new TimeDuration(this.getJob().getQuietPeriod());
		}
		
		CauseAction ca;
		if (owner instanceof InheritanceProject) {
			ca = ((InheritanceProject) owner).getBuildCauseOverride(req);
		} else {
			ca = new CauseAction(new Cause.UserIdCause());
		}
		
		// Invoke the onBuild actions contributed by BuildViewExtension
		req.getAncestors();
		List<Action> actions;
		if (owner instanceof AbstractProject<?,?>) {
			actions = BuildViewExtension.callAll(
					(AbstractProject<?, ?>) owner,
					req
			);
		} else {
			actions = Collections.emptyList();
		}
		
		//Add the versioning Action
		actions.add(new VersioningAction(VersionHandler.getVersions()));
		//Add the cause action
		actions.add(ca);
		//Add the parameters action created from the values above
		actions.add(new ParametersAction(values));
		
		//Merge the ParametersActions, if any
		BuildViewExtension.mergeParameters(actions);
		
		ScheduleResult res = Jenkins.get().getQueue().schedule2(
				(Task) owner, delay.getTime(),
				actions
		);
		
		if (req.getAttribute("rebuildNoRedirect") != null) {
			//The user specified not to get a redirect
			return;
		}
		//Note: The following is taken (almost) verbatim from the super-class
		if (res.isAccepted()) {
			//Redirect to the project
			String url = formData.optString("redirectTo");
			if (StringUtils.isEmpty(url) || !Util.isSafeToRedirectTo(url)) {
				//No redirect specified, or an open redirect (e.g. to an absolute URL)
				//The former is useless, the latter must be avoided. 
				rsp.sendRedirect(".");
				
				//Note: Super-method redirects to the QueueItem -- which does not work
				//url = req.getContextPath() + '/' + res.getCreateItem().getUrl();
			}
			rsp.sendRedirect(formData.optInt("statusCode", SC_CREATED), url);
		} else {
			// send the user back to the job top page.
			rsp.sendRedirect(".");
		}
	}

	/** @deprecated use {@link #buildWithParameters(StaplerRequest, StaplerResponse, TimeDuration)} */
	@Deprecated
	@Override
	public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		this.buildWithParameters(req, rsp, this.getDelayFromRequest(req));
	}
	
	@Override
	public void buildWithParameters(
			StaplerRequest req,
			StaplerResponse rsp,
			@CheckForNull TimeDuration delay
	) throws IOException, ServletException {
		List<Action> actions = new LinkedList<Action>();
		
		//Create action for the parameters
		List<ParameterValue> values = new ArrayList<ParameterValue>();
		for (ParameterDefinition d: this.getParameterDefinitions()) {
			ParameterValue value = d.createValue(req);
			if (value != null) {
				values.add(value);
			}
		}
		actions.add(new ParametersAction(values));
		
		
		//Create an action to override the build cause
		CauseAction buildCause = null;
		if (owner instanceof InheritanceProject) {
			buildCause = ((InheritanceProject)owner).getBuildCauseOverride(req);
		} else {
			buildCause = new CauseAction(new Cause.UserIdCause());
		}
		actions.add(buildCause);
		
		
		//Add an action for the versioning
		if (this.owner instanceof AbstractProject<?,?>) {
			VersioningAction va = new VersioningAction((AbstractProject<?,?>)this.owner);
			actions.add(va);
		} else {
			//FIXME: What to do in this case?
		}
		
		
		//Decode the delay, before the job can be run
		if (delay == null) {
			delay = new TimeDuration(getJob().getQuietPeriod());
		}
		
		//Issue the job
		Jenkins.get().getQueue().schedule2(
				(Task) owner, delay.getTime(),
				actions
		);

		if (requestWantsJson(req)) {
			rsp.setContentType("application/json");
			rsp.serveExposedBean(req, owner, Flavor.JSON);
		} else {
			// send the user back to the job top page.
			rsp.sendRedirect(".");
		}
	}
	
	private boolean requestWantsJson(StaplerRequest req) {
		String a = req.getHeader("Accept");
		if (a==null)    return false;
		return !a.contains("text/html") && a.contains("application/json");
	}
	
	private TimeDuration getDelayFromRequest(StaplerRequest req) {
		return (req.hasParameter("delay"))
				? TimeDuration.fromString(req.getParameter("delay"))
				: new TimeDuration(0);
	}
	
	
	
	// === PARAMETER RETRIEVAL AND SUBSET GENERATION ===
	
	/**
	 * This checks if the assignment of all variables via Inheritance
	 * is sane and okay.
	 * <p>
	 * It checks if:
	 * <ul>
	 * <li>A fixed value was overwritten</li>
	 * <li>The mustHaveValue flag was set, but no value is present</li>
	 * <li>Parameters do not override incompatible classes</li>
	 * </ul>
	 * If any of these is the case, the process throws a suitable
	 * {@link AbortException}.
	 * <p>
	 * Do note that this check is different from and complementary to the
	 * check in {@link InheritanceProject#getParameterSanity()}.
	 * <p>
	 * Of special note here is that this method does <b>not</b> check whether
	 * the parameters have compatible classes during inheritance. That is
	 * done on the level of the project as a pre-build check.
	 * 
	 * @param build the build that uses a value
	 * @param listener the log emitter for that build
	 * 
	 * @throws RunnerAbortedException thrown in case of an invalid assignment.
	 */
	public static void checkParameterSanity(
			InheritanceBuild build, BuildListener listener
	) throws RunnerAbortedException {
		//Get the scope of parameters for the parent of that build
		List<ScopeEntry> scope = ParameterSelector
				.instance
				.getAllScopedParameterDefinitions(build.getParent());
		
		//Break the assigned parameters into a nice-to-use hashmap
		HashMap<String, StringParameterValue> valMap = new HashMap<>();
		for (ParameterValue pv : getParameterValues(build)) {
			if (!(pv instanceof StringParameterValue)) {
				continue;
			}
			valMap.put(pv.getName(), (StringParameterValue)pv);
		}
		
		//Looping over the scope -- it goes from close to further ancestors
		for (ScopeEntry sc : scope) {
			//Ignore non-inheritance parameters, as they guarantee nothing
			if (!(sc.param instanceof InheritableStringParameterDefinition)) {
				continue;
			}
			if (sc.param instanceof InheritableStringParameterReferenceDefinition) {
				//References only contribute values, no assurances
				continue;
			}
			
			InheritableStringParameterDefinition scDef =
					(InheritableStringParameterDefinition) sc.param;
			//Grab the assigned value from the build (if any -- can be null)
			StringParameterValue val = valMap.get(sc.param.getName());
			
			//Check if the "must have value" was violated
			if (scDef.getMustBeAssigned()) {
				if (StringUtils.isEmpty(val.getValue().toString())) {
					//Detected a violation
					listener.fatalError(String.format(
							"Parameter '%s' has no value, but was required to be set. Aborting!",
							val.getName()
					));
					throw new RunnerAbortedException();
				}
			}
			
			/* Check if the "FIXED" mode was violated
			 * Note: This is also check in the project, but a user might have
			 * added a custom parameter for only that one build.
			 */
			if (scDef.getInheritanceModeAsVar() == IModes.FIXED) {
				if (!StringUtils.equals(val.getValue().toString(), scDef.getDefaultValue())) {
					//Detected a violation
					listener.fatalError(String.format(
							"Parameter '%s' was fixed in '%s' to the value"
							+ " '%s', but it was overwritten to '%s'. Aborting!",
							val.getName(),
							sc.owner,
							scDef.getDefaultValue(),
							val.getValue().toString()
					));
					throw new RunnerAbortedException();
				}
			}
		}
	}
	
	public static Collection<ParameterValue> getParameterValues(Run<?,?> run) {
		Map<String, ParameterValue> map = new HashMap<String, ParameterValue>();
		List<ParametersAction> actions =
				run.getActions(ParametersAction.class);
		
		for (ParametersAction pa : actions) {
			for (ParameterValue pv : pa.getParameters()) {
				if (pv == null || pv.getName() == null) { continue; }
				map.put(pv.getName(), pv);
			}
		}
		
		return map.values();
	}
	
	public List<ParameterDefinition> getParameterDefinitionSubset(boolean showHidden) {
		LinkedList<ParameterDefinition> out =
				new LinkedList<ParameterDefinition>();
		//Iterate over all fields
		for (ParameterDefinition pd : this.getParameterDefinitions()) {
			//Checking if value has the getIsHidden field
			Object o = Reflection.invokeIfPossible(pd, "getIsHidden");
			if (o == null || ! (o instanceof Boolean)) {
				//These definitions are treated as non-hidden
				if (!showHidden) {
					out.add(pd);
				}
			} else {
				Boolean isHidden = (Boolean) o;
				if (isHidden && showHidden) {
					out.add(pd);
				} else if (!isHidden && !showHidden) {
					out.add(pd);
				}
			}
		}
		return out;
	}
	
	
	/**
	 * We need to override this method do prevent Jenkins from trying to
	 * register this class as a "real" property worthy of inclusion in the
	 * configuration view.
	 * <p>
	 * This is necessary, because this class is only a pure wrapper around
	 * {@link ParametersDefinitionProperty} and does not own any properties
	 * that need to be stored separately.
	 * <p>
	 * Unfortunately; not defining a Descriptor at all would lead to this
	 * class not being able to completely wrap the
	 * {@link ParametersDefinitionProperty} class.
	 */
	@Override
	public OptionalJobPropertyDescriptor getDescriptor() {
		//return super.getDescriptor();
		return (OptionalJobPropertyDescriptor) Jenkins.get().getDescriptorOrDie(
				ParametersDefinitionProperty.class
		);
	}
	
	public static class DescriptorImpl extends ParametersDefinitionProperty.DescriptorImpl {
		//Does nothing
	}
}
