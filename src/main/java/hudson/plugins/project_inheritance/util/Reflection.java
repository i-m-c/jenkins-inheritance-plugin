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

import hudson.model.Hudson;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class Reflection {
	
	protected static final int MAX_STACK_DEPTH = 30;
	
	//TODO: This map can grow quite fast, theoretically up to n^2; n = # of classes
	public static final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> classAssignabilityMap =
			new ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>(10, 0.75f, 1);

	/**
	* Wrapper for {@link #calledFromClass(Class, int)}, with the maxDepth set
	* to {@value #MAX_STACK_DEPTH}.
	* 
	* @param clazz  the class to search for.
	* @return true, if the method was called from the given class.
	*/
	public static boolean calledFromClass(Class<?>... classes) {
		return calledFromClass(MAX_STACK_DEPTH, classes);
	}

	/**
	 * This method determines whether or not it was called by an object 
	 * derived from the {@link InheritanceBuild} class.
	 * <p>
	 * It does so by examining the call stack and checking whether one of the
	 * last 20 calls has a classname set, that can be resolved down to the
	 * {@link InheritanceBuild} class.
	 * </p>
	 * 
	 * @param clazz the class to search for
	 * @param maxDepth the maximum depth to search in the call stack.
	 * 		If 0 or negative, explore the full stack.
	 * @return true, if the method was called from the given class.
	 */
	public static boolean calledFromClass(int maxDepth, Class<?>... classes) {
		if (classes == null || classes.length == 0) {
			return false;
		}
		if (maxDepth <= 0) {
			maxDepth = Integer.MAX_VALUE;
		}
		//Fetch the call stack
		StackTraceElement[] stackTrace =
				//Thread.currentThread().getStackTrace();
				new Throwable().getStackTrace(); 
		if (stackTrace == null || stackTrace.length == 0) {
			return false;
		}
		
		String joinedStackTraceClasses = joinStacktraceClasses(maxDepth, stackTrace);
		
		//And fetching Jenkins' ClassLoader that is Plugin-aware
		ClassLoader cl;
		try {
			cl = Hudson.getInstance().getPluginManager().uberClassLoader;
		} catch (NullPointerException ex) {
			//This can (or should) only happen if not on the server or during shutdown
			return false;
		}
		
		for (Class<?> clazz : classes) {
			String clazzName = clazz.getName();
			ConcurrentHashMap<String, Boolean> assignMap = classAssignabilityMap.get(clazzName);
			if (assignMap == null) {
				classAssignabilityMap.putIfAbsent(clazzName, new ConcurrentHashMap<String, Boolean>(100, 0.75f, 2));
				assignMap = classAssignabilityMap.get(clazzName);
			}
			
			// see if we already checked that particular stack trace for this class
			Boolean hasKnownAssignable = assignMap.get(joinedStackTraceClasses);
			if (hasKnownAssignable != null) {
			    if (hasKnownAssignable) {
			        return true;
			    } else {
			        continue;
			    }
			}
			
			//And iterating to a maximum fixed depth
			int cnt = 0;
			for (StackTraceElement ste : stackTrace) {
				//Checking if we've reached the maximum trace depth
				if (cnt++ >= maxDepth) { break; }
				//Fetching the Class object from the stacktrace 
				String steClass = ste.getClassName();
				//Checking if we've already checked the assignability once
				Boolean isAssignable = assignMap.get(steClass);
				if (isAssignable != null) {
					if (isAssignable == true) {
						return isAssignable;
					} else {
						//Checking next stack element
						continue;
					}
				}
					
				try {
					//Otherwise, we try to resolve the class and test for assignability
					Class<?> steClazz = cl.loadClass(steClass);
					//And then, we compare the assignability of both classes
					if (clazz.isAssignableFrom(steClazz)) {
						assignMap.put(steClass, true);
						assignMap.put(joinedStackTraceClasses, true);
						return true;
					} else {
						assignMap.put(steClass, false);
					}
				} catch (ClassNotFoundException e) {
					continue;
				}
			}
			
			assignMap.put(joinedStackTraceClasses, false);
		}
		return false;
	}
	
	private static String joinStacktraceClasses(int maxDepth, StackTraceElement[] stackTrace) {
	    StringBuilder sb = new StringBuilder(512);
	    int cnt = 0;
	    for(StackTraceElement ste : stackTrace) {
            if (cnt++ >= maxDepth) { break; }
	        sb.append(ste.getClassName()).append('\n');
	    }
	    
	    return sb.toString();
	}
	
	
	/**
     * Wrapper for {@link #calledFromMethod(Class, String, int)}, with the
     * maxDepth set to {@value #MAX_STACK_DEPTH}.
     * 
     * @param clazz the class to search for. This must be an exact match.
     * @param methodNames the method names inside the class.
     * @return true, if the method was called from the given class.
     */
	public static boolean calledFromMethod(Class<?> clazz, String... methodNames) {
		return calledFromMethod(clazz, MAX_STACK_DEPTH, methodNames);
	}
	
	/**
	 * Determines whether or not the given class-method is present in the call
	 * stack. Both class name and method name must be exact fits.
	 * 
	 * @param maxDepth the maximum stack depth to search in.
	 * @param clazz the class to search for. This must be an exact match.
     * @param methodNames the method name inside the class.
     * @return true, if the method was called from the given class.
	 */
	public static boolean calledFromMethod(Class<?> clazz, int maxDepth, String... methodNames) {
		if (clazz == null || methodNames == null || methodNames.length == 0) {
			return false;
		}
		if (maxDepth <= 0) {
			maxDepth = Integer.MAX_VALUE;
		}
		//Fetch the call stack
		StackTraceElement[] stackTrace =
				Thread.currentThread().getStackTrace();
		
		Arrays.sort(methodNames);
		
		//And iterating to a maximum fixed depth
		int cnt = 0;
		for (StackTraceElement ste : stackTrace) {
			//Checking if we've reached the maximum trace depth
			if (cnt++ >= maxDepth) { break; }
			//Checking if the class name matches
			if (clazz != null && !ste.getClassName().equals(clazz.getCanonicalName())) {
			    //Mismatched method name
			    continue;
			}
			
			//Checking if the methodName matches
			if (Arrays.binarySearch(methodNames, ste.getMethodName()) >= 0) {
			    //Both method name and class assignation match
			    return true;
			}
		}
		return false;
	}
	
	
	public static Object invokeIfPossible(Object self, String methodName, Object... args) {
		Object out = null;
		try {
			Class<?>[] classes = new Class<?>[args.length];
			int i = 0;
			for (Object o : args) {
				classes[i++] = (o == null) ? Object.class : o.getClass();
			}
			Method m = self.getClass().getMethod("getIsHidden", classes);
			out = m.invoke(self, args);
		} catch (NoSuchMethodException ex) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		} catch (InvocationTargetException e) {
			return null;
		}
		return out;
	}
}
