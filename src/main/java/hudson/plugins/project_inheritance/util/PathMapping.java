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

package hudson.plugins.project_inheritance.util;

import java.io.File;
import java.util.AbstractMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathMapping {
	private static final Pattern absPathPattern = Pattern.compile(
			"^(/|[a-zA-Z]:|\\\\\\\\)"
	);
	public final TreeMap<String, String> map = new TreeMap<String, String>();
	
	
	public PathMapping(String srcDir, Set<String> srcFiles, String target) {
		
		//Checking if the source is Windows or Unix
		String srcSep = (isUnixPath(srcDir)) ? "/" : "\\";
		String dstSep = (isUnixPath(target)) ? "/" : "\\";
		
		for (String src : srcFiles) {
			//Determine full source and destination path
			final String fullSrc = String.format(
					"%s%s%s", srcDir, srcSep, src
			);
			final String fullDst = String.format(
					"%s%s%s", target, dstSep, src
			);
			map.put(fullSrc, fullDst);
		}
	}
	
	public static boolean isUnixPath(String path) {
		if (path.contains("/")) {
			return true;
		} else if (path.contains("\\")) {
			return false;
		}
		return true;
	}
	
	public static boolean isPathSingleton(String path) {
		return !(path.contains("/") || path.contains("\\"));
	}
	
	public static boolean isAbsolute(String path) {
		if (path == null) { return false; }
		Matcher m = absPathPattern.matcher(path);
		return m.find();
	}
	
	public static Entry<String, String> splitPath(String path) {
		File f = new File(path);
		String base = f.getName();
		String dir = path.substring(0, path.length() - base.length() - 1);
		return new AbstractMap.SimpleEntry<String, String>(dir, base);
	}
	
	public static String join(String front, String end) {
		if (front == null || end == null) {
			throw new NullPointerException("May not pass null values for path parts");
		}
		if (isAbsolute(end)) {
			return end;
		}
		
		boolean isUnix;
		if (!isPathSingleton(front)) {
			isUnix = isUnixPath(front);
		} else if (!isPathSingleton(end)) {
			isUnix = isUnixPath(end);
		} else {
			//Both single; assuming unix
			isUnix = true;
		}
		
		if (isUnix) {
			return front + "/" + end;
		} else {
			return front + "\\" + end;
		}
	}
}
