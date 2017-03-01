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
package hudson.plugins.project_inheritance.projects.versioning;

import hudson.model.AbstractProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.InheritedVersionInfo;
import hudson.plugins.project_inheritance.util.LimitedHashMap;
import hudson.plugins.project_inheritance.util.ThreadAssocStore;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * This class offers utility functions to encode and decode versioning
 * information in various ways.
 * <p>
 * Since Jenkins lacks the concept of a persistent user-connection across
 * several HTTP interactions, the information about what versions a user
 * has requested needs to be stored in and retrieved from a number of different
 * communication paths.
 * <p>
 * Different situations need different kinds of storage:
 * <ul>
 * 	<li>In the StaplerRequest, when dealing with Web GUI interactions</li>
 * 	<li>In the URL parameters, when dealing with Web GUI redirects</li>
 * 	<li>In the thread, once execution of a build starts</li>
 * </ul>
 * 
 * @author mhschroe
 *
 */
public class VersionHandler {
	private static final Logger log = Logger.getLogger(
			VersionHandler.class.toString()
	);
	
	/**
	 * This field name is used as an URL parameter, to select the version of the
	 * job encoded in the lead-up to the URL.
	 */
	public static final String SINGLE_VERSION_KEY = "version";
	
	/**
	 * This field name is used as an URL parameter, so select versions of
	 * arbitrary subprojects.
	 */
	public static final String VERSIONING_KEY = "versions";
	
	private static final Pattern keyValP = Pattern.compile("([^=:]*)[=:](.*)");
	private static final Pattern leftTrimP = Pattern.compile("^[ \'\"]*");
	private static final Pattern rightTrimP = Pattern.compile("[ \'\"]*$");
	
	public static final Map<String, Map<String, Long>> decodedVersionMaps =
			new LimitedHashMap<String, Map<String,Long>>(100);
	
	
	
	// ==== PUBLIC HELPER METHODS ====
	
	/**
	 * Wrapper around {@link InheritanceProject#getProjectByName(String)}
	 * <p>
	 * @param name the full name of the prefix. Namespaces separated by '/'
	 * @return the project with that name, may be null if project or prefix
	 * 		namespaces do not exist.
	 */
	public static InheritanceProject resolve(String name) {
		return InheritanceProject.getProjectByName(name);
	}
	
	public static Map<InheritanceProject, Long> resolve(Map<String, Long> in) {
		if (in == null) { return null; }
		Map<InheritanceProject, Long> out = new HashMap<InheritanceProject, Long>();
		for (Entry<String, Long> entry : in.entrySet()) {
			InheritanceProject ip = resolve(entry.getKey());
			if (ip == null) { continue; }
			out.put(ip, entry.getValue());
		}
		return out;
	}
	
	
	
	// ==== Externally accessible complete-flow functions ===
	
	/**
	 * Tries to fetch pre-initialised versioning information.
	 * <p>
	 * If no values have been pre-initialised via
	 * {@link #initVersions(AbstractProject)} or {@link #setVersions(Map)},
	 * the map will be empty.
	 * 
	 * @return may be empty, but never null. If empty, the caller should use the
	 * most recent stable versions, but this function will not actually return
	 * those.
	 */
	public static Map<String, Long> getVersions() {
		Map<String, Long> map;
		//Try to fetch via the Request attribute (fast)
		map = getFromRequest();
		if (map != null) {
			return map;
		}
		
		//Try to use the fast thread storage
		map = getFromThread();
		if (map != null) {
			return map;
		}
		
		//At last, try to fetch from URL parameters (slow)
		map = getFromUrlParameter();
		if (map != null) {
			return map;
		}
		
		//If everything failed; return the empty map
		return Collections.emptyMap();
	}
	
	public static Long getVersion(InheritanceProject root) {
		Map<String, Long> map = getVersions();
		Long v = map.get(root.getFullName());
		if (v == null) {
			return root.getStableVersion();
		} else {
			return v;
		}
	}
	
	
	/**
	 * Will initialise the versioning information and distribute it across
	 * the various avenues of saving it.
	 * <p>
	 * It will first attempt to read the values from the environment via
	 * {@link #getVersions()}. It will then combine that map with the defaults
	 * from the given projects, with the former one overwriting values of the
	 * latter one.
	 * 
	 * @param root the project to initialize from
	 * @return
	 */
	public static Map<String, Long> initVersions(AbstractProject<?, ?> root) {
		//Fetch currently configured versions
		Map<String, Long> envMap = getVersions();
		
		//Make sure that they are saved everywhere
		setVersions(envMap);
		
		//Augment versions with those from the project, to complete missing versions
		Map<String, Long> jobMap = getFromProject(root);
		
		Map<String, Long> join = new HashMap<String, Long>();
		if (jobMap != null && !jobMap.isEmpty()) {
			join.putAll(jobMap);
		}
		if (envMap != null && !envMap.isEmpty()) {
			join.putAll(envMap);
		}
		
		//Register and return joined version
		setVersions(join);
		return join;
	}
	
	/**
	 * Will initialise the versioning information and distribute it across
	 * the various avenues of saving it.
	 * <p>
	 * It will take the information as given in the passed-in map.
	 * 
	 * @param root
	 * @return
	 */
	public static Map<String, Long> initVersions(Map<String, Long> map) {
		setVersions(map);
		return map;
	}
	
	/**
	 * Will merge the currently configured versions (if any) with the given map.
	 * 
	 * @param map the map to overwrite/add values with.
	 * 
	 * @return the joined map.
	 */
	public static Map<String, Long> addVersions(Map<String, Long> map) {
		Map<String, Long> join = new HashMap<String, Long>();
		join.putAll(getVersions());
		join.putAll(map);
		
		setVersions(join);
		return join;
	}
	
	
	private static void setVersions(Map<String, Long> map) {
		setInRequest(map);
		setInThread(map);
	}
	
	public static void clearVersions() {
		clearInRequest();
		clearInThread();
	}
	
	/**
	 * Same as {@link #clearVersions()}, but only clears "permanent" storage
	 * areas, that might leak into subsequent runs.
	 * <p>
	 * For example, it clears data saved inside the current Thread, but does
	 * not purge data from the {@link StaplerRequest}, which does not exist
	 * across invocations.
	 */
	public static void clearVersionsPartial() {
		clearInThread();
	}
	
	
	
	// ==== Project-based version retrieval ====
	
	public static Map<String, Long> getFromProject(AbstractProject<?, ?> root) {
		if (root == null || !(root instanceof InheritanceProject)) {
			return null;
		}
		InheritanceProject ip = (InheritanceProject) root;
		
		List<InheritedVersionInfo> versions = ip.getAllInheritedVersionsList();
		if (versions == null) { return null; }
		
		Map<String,Long> map = new HashMap<String, Long>();
		for (InheritedVersionInfo v : versions) {
			map.put(v.project.getFullName(), v.version);
		}
		return map;
	}
	
	
	
	// ==== URL-based version retrieval ====
	
	/**
	 * This method loads the map of versions from the parameters passed in
	 * via the current StaperRequest's URL.
	 * @return
	 */
	private static Map<String, Long> getFromUrlParameter() {
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) { return null; }
		
		Map<String, Long> out = new HashMap<String, Long>();
		
		//Check for "other jobs" version key
		String param = req.getParameter(VERSIONING_KEY);
		if (param != null && !(param.isEmpty())) {
			out.putAll(decodeUrlParameter(param));
		}
		
		//Check for "current job" version key
		param = req.getParameter(SINGLE_VERSION_KEY);
		if (param != null && !(param.isEmpty())) {
			InheritanceProject ip = InheritanceProject.getProjectFromRequest(req);
			if (ip != null) {
				try {
					out.put(ip.getFullName(), Long.valueOf(param));
				} catch (NumberFormatException ex) {
					//Invalid value for "versions" field
				}
			}
		}
		
		if (out.isEmpty()) {
			return null;
		} else {
			return out;
		}
	}
	
	public static String getFullUrlParameter(Map<String, Long> vMap) {
		String value = VersionHandler.encodeUrlParameter(vMap);
		return VersionHandler.getFullUrlParameter(value);
	}
	
	public static String getFullUrlParameter(String value) {
		String verUrlParm = String.format("%s=\"%s\"",
				VersionHandler.VERSIONING_KEY,
				value
		);
		return verUrlParm;
	}
	
	public static String encodeUrlParameter(InheritanceProject root) {
		Map<String, Long> map = getFromProject(root);
		return encodeUrlParameter(map);
	}
	
	public static String encodeUrlParameter(Map<String, Long> in) {
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
		String raw = out.toString();
		
		//Save the encoded map into the cache; as it should be used quite soon
		decodedVersionMaps.put(raw, in);
		return raw;
	}
	
	public static Map<String, Long> decodeUrlParameter(String in) {
		//Sanity check
		if (in == null || in.isEmpty()) {
			return null;
		}
		
		//Check if we already have decoded that string recently
		Map<String, Long> out = decodedVersionMaps.get(in);
		if (out != null) {
			//Making sure that the hashed entry is put to the front in LRU fashion
			decodedVersionMaps.put(in, out);
			return out;
		} else {
			out = new HashMap<String, Long>();
		}
		
		//The input might've been URL encoded; decode these until the string is stable
		String escaped = in;
		String unescaped = in;
		do {
			unescaped = escaped;
			try {
				escaped = URLDecoder.decode(unescaped, "utf8");
			} catch (UnsupportedEncodingException ex) {
				escaped = unescaped;
				break;
			}
		} while (!unescaped.equals(escaped));
		
		
		String inMod = escaped.trim();
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
		
		//Buffering that entry
		decodedVersionMaps.put(in, out);
		
		return out;
	}
	
	
	
	// ==== Request-based version retrieval ====
	
	private static Map<String, Long> getFromRequest() {
		//Checking if we were invoked through an HTTP URL request
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) {
			return null;
		}
		
		//FIXME: This method should allow using the ?version=<num> everywhere,
		//not just in structured form submissions.
		
		Object verObj = req.getAttribute("versions");
		if (verObj == null) {
			//Fallback, use explicit parameters in form content
			if (StringUtils.isEmpty(req.getParameter("project"))
					|| StringUtils.isEmpty(req.getParameter("version"))
			) {
				return null;
			}
			Map<String, Long> versionMap = getFromFormRequest(req);
			if (versionMap == null || versionMap.isEmpty()) {
				return null;
			}
			return versionMap;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Long> verMap =
					(Map<String, Long>) verObj;
			return verMap;
		} catch (ClassCastException ex) {
			log.warning(
				"ClassCaseException when attempting to decode 'versions' attribute of HTTP-Request"
			);
		}
		return null;
	}
	
	private static void setInRequest(Map<String, Long> map) {
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) {
			return;
		}
		req.setAttribute(VERSIONING_KEY, map);
	}
	
	private static void clearInRequest() {
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req == null) {
			return;
		}
		req.removeAttribute(VERSIONING_KEY);
	}
	
	
	
	// ==== Thread-based version retrieval ====
	
	private static Map<String, Long> getFromThread() {
		Object verObj = ThreadAssocStore.getInstance().getValue("versions");
		if (verObj == null) { return null; }
		try {
			@SuppressWarnings("unchecked")
			Map<String, Long> verMap =
					(Map<String, Long>) verObj;
			return verMap;
		} catch (ClassCastException ex) {
			log.warning(
				"ClassCaseException when attempting to decode 'versions' attribute of ThreadAssocStore"
			);
		}
		return null;
	}

	private static void setInThread(Map<String, Long> map) {
		ThreadAssocStore.getInstance().setValue(VERSIONING_KEY, map);
	}
	
	private static void clearInThread() {
		ThreadAssocStore.getInstance().clear(VERSIONING_KEY);
	}
	
	
	
	// ==== Form-based version retrieval ====
	
	public static Map<String, Long> getFromFormRequest(StaplerRequest req) {
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
