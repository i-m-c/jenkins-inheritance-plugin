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

import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Resolver {
	public static String [] resolve(VariableResolver<String> resolver, String... in) {
		String[] mods = new String[in.length];
		for (int i = 0; i < in.length; i++) {
			mods[i] = resolveSingle(resolver, in[i]);
		}
		return mods;
	}
	
	public static String [] resolve(Map<String, String> map, String... in) {
		String[] mods = new String[in.length];
		for (int i = 0; i < in.length; i++) {
			mods[i] = resolveSingle(map, in[i]);
		}
		return mods;
	}
	
	public static String resolveSingle(VariableResolver<String> resolver, String in) {
		String curr = in;
		String out = null;
		for (int i = 0; i < 10; i++) {
			out = Util.replaceMacro(curr, resolver);
			if (out == curr) {
				//Done!
				return out;
			}
			curr = out;
		}
		return out;
	}
	
	public static String resolveSingle(Map<String, String> map, String in) {
		String curr = in;
		String out = null;
		for (int i = 0; i < 10; i++) {
			out = Util.replaceMacro(curr, map);
			if (out == curr) {
				//Done!
				return out;
			}
			curr = out;
		}
		return out;
	}
	
	
	public static Map<String, String> getEnvFor(AbstractBuild<?, ?> build, TaskListener log) {
		Map<String, String> evMap = new HashMap<String, String>();
		evMap.putAll(build.getBuildVariables());
		try {
			evMap.putAll(build.getEnvironment(log));
		} catch (IOException e) {
			//Do nothing
		} catch (InterruptedException e) {
			//Do nothing
		}
		return evMap;
	}

	
	/**
	 * This takes a comma-separated string and splits it into discrete Strings.
	 * You can protect commas as '\,' to prevent splitting it there.
	 * 
	 * @param in the string to split apart
	 * @return an array of split strings.
	 */
	public static String[] splitCommas(String in) {
		if (in == null) {
			return new String[0];
		}
		//Split along all non-escaped commas
		String[] split = in.split("(?<!\\\\),");
		Vector<String> vec = new Vector<String>();
		for (String str : split) {
			String trim = str.trim();
			if (trim.isEmpty() == false) {
				vec.add(trim.replaceAll("\\,", ","));
			}
		}
		String[] ret = new String[vec.size()];
		return vec.toArray(ret);
	}
	
	/**
	 * Takes multiple strings, protects commas as '\,' and then joins them,
	 * separated with ', '.
	 * 
	 * @param in the array of strings to join with a comma+space.
	 * @return the joined and escaped string.
	 */
	public static String joinWithCommas(String... in) {
		if (in == null) { return ""; }
		StringBuilder b = new StringBuilder();
		
		for (int i = 0; i < in.length; i++) {
			String glob = in[i].replaceAll(",", "\\,");
			b.append(glob);
			if (i < in.length - 1) {
				b.append(", ");
			}
		}
		
		return b.toString();
	}
}
