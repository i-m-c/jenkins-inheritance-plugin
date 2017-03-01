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
package hudson.plugins.project_inheritance.util;

import java.io.File;
import java.util.AbstractMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

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

	/**
	 * Get the relative path from one file to another, specifying the directory separator. 
	 * If one of the provided resources does not exist, it is assumed to be a file unless it ends with '/' or
	 * '\'.
	 * 
	 * @param targetPath targetPath is calculated to this file
	 * @param basePath basePath is calculated from this file
	 * @param pathSeparator directory separator. The platform default is not assumed so that we can test Unix behaviour when running on Windows (for example)
	 * @return
	 */
	public static String getRelativePath(String targetPath, String basePath, String pathSeparator) {
		// Normalize the paths
		String normalizedTargetPath = FilenameUtils.normalizeNoEndSeparator(targetPath);
		String normalizedBasePath = FilenameUtils.normalizeNoEndSeparator(basePath);

		// Undo the changes to the separators made by normalization
		if (pathSeparator.equals("/")) {
			normalizedTargetPath = FilenameUtils.separatorsToUnix(normalizedTargetPath);
			normalizedBasePath = FilenameUtils.separatorsToUnix(normalizedBasePath);

		} else if (pathSeparator.equals("\\")) {
			normalizedTargetPath = FilenameUtils.separatorsToWindows(normalizedTargetPath);
			normalizedBasePath = FilenameUtils.separatorsToWindows(normalizedBasePath);

		} else {
			throw new IllegalArgumentException("Unrecognised dir separator '" + pathSeparator + "'");
		}

		String[] base = normalizedBasePath.split(Pattern.quote(pathSeparator));
		String[] target = normalizedTargetPath.split(Pattern.quote(pathSeparator));

		// First get all the common elements. Store them as a string,
		// and also count how many of them there are.
		StringBuilder common = new StringBuilder();

		int commonIndex = 0;
		while (commonIndex < target.length && commonIndex < base.length
				&& target[commonIndex].equals(base[commonIndex])) {
			common.append(target[commonIndex] + pathSeparator);
			commonIndex++;
		}

		if (commonIndex == 0) {
			// No single common path element. This most
			// likely indicates differing drive letters, like C: and D:.
			// These paths cannot be relativized.
			throw new PathResolutionException(
					"No common path element found for '" + normalizedTargetPath + "' and '" + normalizedBasePath
					+ "'"
			);
		}

		// The number of directories we have to backtrack depends on whether the base is a file or a dir
		// For example, the relative path from
		//
		// /foo/bar/baz/gg/ff to /foo/bar/baz
		// 
		// ".." if ff is a file
		// "../.." if ff is a directory
		//
		// The following is a heuristic to figure out if the base refers to a file or dir. It's not perfect, because
		// the resource referred to by this path may not actually exist, but it's the best I can do
		boolean baseIsFile = true;

		File baseResource = new File(normalizedBasePath);

		if (baseResource.exists()) {
			baseIsFile = baseResource.isFile();

		} else if (basePath.endsWith(pathSeparator)) {
			baseIsFile = false;
		}

		StringBuilder relative = new StringBuilder();

		if (base.length != commonIndex) {
			int numDirsUp = baseIsFile ? base.length - commonIndex - 1 : base.length - commonIndex;

			for (int i = 0; i < numDirsUp; i++) {
				relative.append(".." + pathSeparator);
			}
		}
		relative.append(normalizedTargetPath.substring(common.length()));
		return relative.toString();
	}


	static class PathResolutionException extends RuntimeException {
		private static final long serialVersionUID = -1260762639510818413L;

		PathResolutionException(String msg) {
			super(msg);
		}
	}
}
