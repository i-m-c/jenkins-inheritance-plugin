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
package hudson.plugins.project_inheritance.util;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Slave;

/**
 * This class implements the ability to efficiently look up the file separator
 * used for each Node.
 * <p>
 * This is relevant for determining the correct path to the workspace and
 * avoids using "/" on Windows or "\" on Unix.
 * <p>
 * It is a singleton, because there's no point to individual instances.
 * 
 * @author mhschroe
 */
public class NodeFileSeparator {
	public static final NodeFileSeparator instance = 
			new NodeFileSeparator();
	
	/**
	 * Caches the separators for each node. This makes sure that separator
	 * computation is not too time-intense and that offlining nodes will not
	 * impact separator computation too much.
	 * <p>
	 * Just make sure not to change operating systems for one node instance,
	 * after it has been computed for the first time.
	 */
	private Cache<Node, String> nodeLookup = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES)
			.weakKeys()
			.build();
	
	/**
	 * Private constructor for singleton usage.
	 */
	private NodeFileSeparator() {
		//Nothing to do
	}
	
	/**
	 * Ensures that the given path uses the right file separator for the
	 * given node.
	 * <p>
	 * If the separator can't be determined, is invalid, or the node is null,
	 * the path is returned unmodified.
	 * 
	 * @param n the node
	 * @param path the path to ensure correctness for
	 * @return a path with correct separators, or the original, if the separator
	 * 		could not be determined.
	 */
	public String ensurePathCorrect(Node n, String path) {
		if (n == null) { return path; }
		//Determine & cache the file separator for that node
		String sep = this.getSepFor(n);
		//quote-replacement for '\' is needed. See replaceAll() javadoc
		String winSep = Matcher.quoteReplacement("\\");
		switch (sep) {
			case "/":
				return path.replaceAll(winSep, "/");
			case "\\":
				return path.replaceAll("/", winSep);
			default:
				//Not a known-good separator, so keep as-is
				return path;
		}
	}
	
	/**
	 * Returns the separator for that node -- if possible to be retrieved.
	 * <p>
	 * Guaranteed to always be either "/", "\\" or null.
	 * <p>
	 * The result is cached, so that repeated lookups are sped-up and
	 * random node offlining does not affect the separator calculation as much.
	 * 
	 * @param n the node to check.
	 * @return the separator, one of "/", "\\" or null.
	 */
	public String getSepFor(Node n) {
		if (n == null) { return null; }
		//Check if a value is cached
		String c = nodeLookup.getIfPresent(n);
		if (c != null) { return c; }
		
		try {
			//Check if the node is online and we can determine the separator via
			//a system property
			c = this.getRawSepFor(n.toComputer());
			if (c != null) { return c; }
			
			//Check if the remote root path can tell us something
			if (n instanceof Slave) {
				c = this.getRawSepFor((Slave)n);
				if (c != null) { return c; }
			}
			
			//Last fallback, try to determine based on full file path
			c = this.getRawSepFor(n.getRootPath());
			if (c != null) { return c; }
		} finally {
			if (c != null) {
				nodeLookup.put(n, c);
			}
		}
		//If this point is reached, no path could be found
		return null;
	}
	
	private String getRawSepFor(Computer c) {
		if (c == null) { return null; }
		String sep = null;
		//Fetch via System properties
		try {
			Map<Object,Object> map = c.getSystemProperties();
			if (map != null && map.containsKey("file.separator")) {
				Object o = map.get("file.separator");
				if (o instanceof String) {
					sep = (String)o;
					if (sep.equals("\\") || sep.equals("/")) {
						return sep;
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			return null;
		}
		return sep;
	}
	
	private String getRawSepFor(Slave sl) {
		if (sl == null) { return null; }
		return getRawSepFor(sl.getRemoteFS());
	}
	
	private String getRawSepFor(FilePath f) {
		if (f == null) { return null; }
		return getRawSepFor(f.getRemote());
	}
	
	private String getRawSepFor(String s) {
		if (s == null) { return null; }
		if (s.contains("/")) {
			return "/";
		} else if (s.contains("\\")) {
			return "\\";
		}
		return null;
	}
}
