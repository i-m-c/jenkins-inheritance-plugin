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

package hudson.plugins.project_inheritance.projects.parameters;

import hudson.model.Build;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterValue;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.actions.VersioningAction;
import hudson.plugins.project_inheritance.util.Reflection;

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
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

/**
 * This class is a wrapper around {@link ParametersDefinitionProperty}.
 * <p>
 * This is necessary, because the derivation of parameters is a lot more
 * complicated than the base class can deal with as soon as inheritance and
 * versioning are introduced.
 * <p>
 * The problem is that in Vanilla-Jenkins, a {@link Job} stores a single
 * instance of {@link ParametersDefinitionProperty} in its list of properties.
 * In turn, this instance stores single instances of the various
 * {@link ParameterDefinition}s that you can set-up via the GUI.
 * <br/>
 * This means that, no matter how many builds are started at the same time,
 * they all make use of the same {@link ParametersDefinitionProperty} and thus
 * the same {@link ParameterDefinition}. The differences between the builds are
 * exclusively stored inside the {@link ParameterValue}s created by the
 * {@link ParameterDefinition}s.
 * <br/>
 * If you reconfigure a project and click on "save", a new
 * {@link ParametersDefinitionProperty} is created with a new set of
 * {@link ParameterDefinition}s.
 * <br/>
 * This approach works, because the {@link ParameterDefinition}s can create
 * the values without needing to refer to the {@link Job} that desires and
 * needs them. Currently running builds will still make use of the old
 * instances and will thus not be silently corrupted.
 * <p>
 * Unfortunately, this causes extreme problems with inheritance and
 * versioning, as those not only need to refer to the one {@link Job} that 
 * they are stored in, but also to all of its parents in their correct
 * versions to generate the correct final values.
 * <p>
 * Thus, the following altered behaviour is necessary:
 * <ol>
 * 	<li>
 * 		Jobs keep storing the unaltered {@link ParametersDefinitionProperty}
 * 		objects. This is necessary to support legacy behaviour of Jenkins and
 * 		keep the saving/loading of Jobs intact and unaltered.
 * </li><li>
 * 		Whenever any part of Jenkins request this property, it is instead
 * 		presented with a new instance of this class. This instance contains
 * 		copies of all {@link ParameterDefinition}s.
 * </li><li>
 * 		Additionally, this class gets copies of <b>all</b> other
 * 		{@link ParameterDefinition}s from the correct versions of all parents,
 * 		hashed by their name and ordered by their priority.
 * </li><li>
 * 		The copies of the {@link InheritableStringParameterDefinition}s also
 * 		get a reference back to the instance of this class. This can then be
 * 		used by them to create the correctly derived {@link ParameterValue}s.
 * </li>
 * </ol>
 * The obvious downside to this approach is that the number of created objects
 * rises dramatically. Instead of only creating one {@link ParameterValue}
 * per {@link ParameterDefinition} for each {@link Build}, numerous
 * copies of {@link ParametersDefinitionProperty}s and {@link ParameterDefinition}
 * are created every time {@link Job#getProperties()} or a similar
 * method is called.
 * <p>
 * Finally, this class displays the interstitial, that asks the
 * user to input values for the project's parameters when the build is triggered
 * via the GUI. Here, a specific option needs to be displayed to allow the user
 * to alter the versions of each parent used for the next build.
 * 
 * @author mhschroe
 */

public class InheritanceParametersDefinitionProperty extends
		ParametersDefinitionProperty {
	
	private static final Pattern keyValP = Pattern.compile("([^=:]*)[=:](.*)");
	private static final Pattern leftTrimP = Pattern.compile("^[ \'\"]*");
	private static final Pattern rightTrimP = Pattern.compile("[ \'\"]*$");
	
	public static final String VERSION_PARAM_NAME = "JENKINS_JOB_VERSIONS";
	
	
	public static class ScopeEntry {
		public final String owner;
		public final ParameterDefinition param;
		
		public ScopeEntry(String owner, ParameterDefinition param) {
			this.owner = owner;
			this.param = param;
		}
		
		public String toString() {
			StringBuilder b = new StringBuilder();
			b.append('[');
			b.append(owner);
			b.append(", ");
			b.append(param.toString());
			b.append(']');
			return b.toString();
		}
	}
	
	/**
	 * This map contains <b>all</b> {@link ParameterDefinition}s and their
	 * original owners that are reachable through the inheritance tree. Each
	 * entry is be a list that is sorted by inheritance order.
	 * <p>
	 * This is contrary to the list you can get through
	 * {@link #getParameterDefinitions()} in that it lists all the
	 * parameters that have to be considered for value derivation without
	 * having to look up the full inheritance tree again.
	 */
	private transient Map<String, List<ScopeEntry>> fullScope =
			new HashMap<String, List<ScopeEntry>>();
	
	private transient ReentrantReadWriteLock scopeLock =
			new ReentrantReadWriteLock();
	
	
	public InheritanceParametersDefinitionProperty(
			AbstractProject<?,?> owner,
			List<ParameterDefinition> parameterDefinitions) {
		super(sortParametersByName(parameterDefinitions));
		this.owner = owner;
		//Applying the current owner and this object to the PDs, if necessary.
		this.applyOwnerToDefinitions();
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
		//Then, we copy the scope
		if (other instanceof InheritanceParametersDefinitionProperty) {
			InheritanceParametersDefinitionProperty ipdp =
					(InheritanceParametersDefinitionProperty) other;
			
			ipdp.scopeLock.readLock().lock();
			this.scopeLock.writeLock().lock();
			try {
				for (String pName : ipdp.fullScope.keySet()) {
					List<ScopeEntry> oLst = ipdp.fullScope.get(pName);
					if (oLst == null) { continue; }
					LinkedList<ScopeEntry> newLst = new LinkedList<ScopeEntry>();
					for (ScopeEntry entry : oLst) {
						newLst.add(new ScopeEntry(entry.owner, entry.param));
					}
					this.fullScope.put(pName, newLst);
				}
			} finally {
				this.scopeLock.writeLock().unlock();
				ipdp.scopeLock.readLock().unlock();
			}
		} else {
			this.addScopedParameterDefinitions(
					(owner != null) ? owner.getName() : "",
					other.getParameterDefinitions()
			);
		}
	}
	
	public static final List<ParameterDefinition> sortParametersByName(List<ParameterDefinition> in) {
		//We ensure that the list of PDs is sorted on its name
		Collections.sort(
			in,
			new Comparator<ParameterDefinition>() {
				public int compare(ParameterDefinition o1, ParameterDefinition o2) {
					return o1.getName().compareTo(o2.getName());
				}
			}
		);
		return in;
	}
	
	public static InheritanceParametersDefinitionProperty createMerged(
			ParametersDefinitionProperty prior,
			ParametersDefinitionProperty latter) {
		//Determining which owner to use for the new merge.
		//It needs to be an InheritanceProject!
		InheritanceProject newOwner = null;
		ParametersDefinitionProperty[] pdps = {latter, prior};
		
		for (ParametersDefinitionProperty pdp : pdps) {
			if (pdp.getOwner() != null && pdp.getOwner() instanceof InheritanceProject) {
				newOwner = (InheritanceProject) pdp.getOwner();
				break;
			}
		}
		
		//Then, we merge their ParameterDefinitions based on their name
		HashMap<String, ParameterDefinition> unifyMap =
				new HashMap<String, ParameterDefinition>();
		for (int i = pdps.length-1; i >= 0; i--) {
			ParametersDefinitionProperty pdp = pdps[i];
			for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
				unifyMap.put(pd.getName(), pd);
			}
		}
		List<ParameterDefinition> unifyList =
				new LinkedList<ParameterDefinition>(unifyMap.values());
		
		//With that, we create a new IPDP
		InheritanceParametersDefinitionProperty out =
				new InheritanceParametersDefinitionProperty(newOwner, unifyList);
		
		//At the end, we merge the scoping informations
		for (int i = pdps.length-1; i >= 0; i--) {
			ParametersDefinitionProperty pdp = pdps[i];
			
			if (pdp instanceof InheritanceParametersDefinitionProperty) {
				//We deal with an already inherited parameter; so we need to
				//copy the scope exactly.
				InheritanceParametersDefinitionProperty ipdp =
						(InheritanceParametersDefinitionProperty) pdp;
				for (ScopeEntry scope : ipdp.getAllScopedParameterDefinitions()) {
					out.addScopedParameterDefinitions(scope);
				}
			} else {
				//No inheritance means the scope is fully local
				String ownerName =
					(pdp.getOwner() != null) ? pdp.getOwner().getName() : "";
				out.addScopedParameterDefinitions(
						ownerName, pdp.getParameterDefinitions()
				);
			}
			
		}
		
		return out;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		if(!req.getMethod().equals("POST")) {
			// show the parameter entry form.
			req.getView(this,"index.jelly").forward(req,rsp);
			return;
		}

		List<ParameterValue> values = new ArrayList<ParameterValue>();
		
		JSONObject formData = req.getSubmittedForm();
		JSONArray a = JSONArray.fromObject(formData.get("parameter"));

		for (Object o : a) {
			JSONObject jo = (JSONObject) o;
			String name = jo.getString("name");

			ParameterDefinition d = getParameterDefinition(name);
			if(d==null)
				throw new IllegalArgumentException("No such parameter definition: " + name);
			ParameterValue parameterValue = d.createValue(req, jo);
			values.add(parameterValue);
		}

		TimeDuration delay = (req.hasParameter("delay"))
				? TimeDuration.fromString(req.getParameter("delay"))
				: new TimeDuration(0);
		
		Jenkins.getInstance().getQueue().schedule(
				owner, (int) delay.as(TimeUnit.SECONDS),
				new ParametersAction(values),
				new CauseAction(new Cause.UserIdCause()),
				new VersioningAction(this.getVersioningMap())
		);

		//Send the user back to the job page, except if "rebuildNoRedirect" is set
		if (req.getAttribute("rebuildNoRedirect") == null) {
			rsp.sendRedirect(".");
		}
	}
	
	public void buildWithParameters(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
		List<ParameterValue> values = new ArrayList<ParameterValue>();
		for (ParameterDefinition d: this.getParameterDefinitions()) {
			ParameterValue value = d.createValue(req);
			if (value != null) {
				values.add(value);
			}
		}
		
		CauseAction buildCause = null;
		if (owner instanceof InheritanceProject) {
			buildCause = ((InheritanceProject)owner).getBuildCauseOverride(req);
		} else {
			buildCause = new CauseAction(new Cause.UserIdCause());
		}
		
		TimeDuration delay = (req.hasParameter("delay"))
				? TimeDuration.fromString(req.getParameter("delay"))
				: new TimeDuration(0);
		
		Jenkins.getInstance().getQueue().schedule(
				owner, (int) delay.as(TimeUnit.SECONDS),
				new ParametersAction(values),
				buildCause,
				new VersioningAction(this.getVersioningMap())
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
	
	
	
	private void applyOwnerToDefinitions() {
		for (ParameterDefinition pd : super.getParameterDefinitions()) {
			if (!(pd instanceof InheritableStringParameterDefinition)) {
				continue;
			}
			InheritableStringParameterDefinition ispd =
					(InheritableStringParameterDefinition) pd;
			ispd.setRootProperty(this);
		}
	}
	
	private Map<String,Long> getVersioningMap() {
		if (this.owner == null || !(this.owner instanceof InheritanceProject)) {
			return null;
		}
		return ((InheritanceProject)owner).getAllVersionsFromCurrentState();
	}
	
	public static String encodeVersioningMap(Map<String, Long> in) {
		if (in == null || in.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Entry<String, Long> e : in.entrySet()) {
			String key = e.getKey();
			if (key == null || key.isEmpty()) {
				continue;
			}
			out.append(e.getKey());
			out.append("=");
			out.append(e.getValue());
			out.append(";");
		}
		if (out.length() > 0) {
			out.deleteCharAt(out.length()-1);
		}
		return out.toString();
	}
	
	public String encodeVersioningMap() {
		Map<String,Long> map = this.getVersioningMap();
		if (map != null) {
			return encodeVersioningMap(map);
		}
		return null;
	}
	
	public static Map<String, Long> decodeVersioningMap(String in) {
		//Cleaning up
		Map<String, Long> out = new HashMap<String, Long>();
		if (in == null || in.isEmpty()) {
			return null;
		}
		String inMod = in.trim();
		inMod = leftTrimP.matcher(inMod).replaceFirst("");
		inMod = rightTrimP.matcher(inMod).replaceFirst("");
		
		
		//The value should look like this: <proj>=<ver>;<proj>=ver;...
		for (String entry : inMod.split(";")) {
			Matcher m = keyValP.matcher(entry);
			while (m.find()) {
				String key = m.group(1);
				if (key == null || key.isEmpty()) { continue; }
				String value = m.group(2);
				if (value == null || value.isEmpty()) { continue; }
				try {
					Long lv = Long.parseLong(value);
					//Trying to add this to the version map
					out.put(key, lv);
				} catch (NumberFormatException ex) {
					continue;
				}
			}
		}
		return out;
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
	
	public List<ParameterDefinition> getParameterDefinitions() {
		return super.getParameterDefinitions();
	}
	
	public ParameterDefinition getParameterDefinition(String name) {
		return super.getParameterDefinition(name);
	}
	
	public List<String> getParameterDefinitionNames() {
		return super.getParameterDefinitionNames();
	}
	
	
	public List<ScopeEntry> getAllScopedParameterDefinitions() {
		List<ScopeEntry> lst = new LinkedList<ScopeEntry>();
		this.scopeLock.readLock().lock();
		try {
			for (List<ScopeEntry> subLst : fullScope.values()) {
				lst.addAll(subLst);
			}
		} finally {
			this.scopeLock.readLock().unlock();
		}
		return lst;
	}
	
	public List<ScopeEntry> getScopedParameterDefinition(String name) {
		this.scopeLock.readLock().lock();
		try {
			List<ScopeEntry> lst = fullScope.get(name);
			if (lst == null) { return null; }
			return Collections.unmodifiableList(lst);
		} finally {
			this.scopeLock.readLock().unlock();
		}
		
	}
	
	
	public void addScopedParameterDefinitions(ParametersDefinitionProperty pdp) {
		String owner = (pdp.getOwner() != null) ? pdp.getOwner().getName() : "";
		this.addScopedParameterDefinitions(owner, pdp.getParameterDefinitions());
	}
	
	public void addScopedParameterDefinitions(ScopeEntry entry) {
		if (entry == null || entry.param == null) {
			return;
		}
		this.scopeLock.writeLock().lock();
		try {
			List<ScopeEntry> sLst = fullScope.get(entry.param.getName());
			if (sLst == null) {
				sLst = new LinkedList<ScopeEntry>();
			}
			sLst.add(new ScopeEntry(entry.owner, entry.param));
			fullScope.put(entry.param.getName(), sLst);
		} finally {
			this.scopeLock.writeLock().unlock();
		}
	}
	
	public void addScopedParameterDefinitions(String owner, Collection<ParameterDefinition> pds) {
		for (ParameterDefinition pd : pds) {
			String pdName = pd.getName();
			this.scopeLock.writeLock().lock();
			try {
				List<ScopeEntry> lst = fullScope.get(pdName);
				if (lst == null) {
					lst = new LinkedList<ScopeEntry>();
					lst.add(new ScopeEntry(owner, pd));
					fullScope.put(pdName, lst);
				} else {
					lst.add(new ScopeEntry(owner, pd));
				}
			} finally {
				this.scopeLock.writeLock().unlock();
			}
		}
	}
	
	public void addScopedParameterDefinitions(String owner, ParameterDefinition... pds) {
		this.addScopedParameterDefinitions(owner, Arrays.asList(pds));
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
	public JobPropertyDescriptor getDescriptor() {
		//return super.getDescriptor();
		return (JobPropertyDescriptor) Jenkins.getInstance().getDescriptorOrDie(
				ParametersDefinitionProperty.class
		);
	}
	
	public static class DescriptorImpl extends ParametersDefinitionProperty.DescriptorImpl {
		//Does nothing
	}
}
