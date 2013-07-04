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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import hudson.model.Hudson;
import hudson.plugins.project_inheritance.projects.InheritanceBuild;

public class Reflection {
	
	protected static final int MAX_STACK_DEPTH = 30;
	
	//TODO: This map can grow quite fast, theoretically up to n^2; n = # of classes
	public static final HashMap<String, HashMap<String, Boolean>> classAssignabilityMap =
			new HashMap<String, HashMap<String, Boolean>>();

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
		
		//And fetching Jenkins' ClassLoader that is Plugin-aware
		ClassLoader cl = Hudson.getInstance().getPluginManager().uberClassLoader;
		
		for (Class<?> clazz : classes) {
			String clazzName = clazz.getName();
			HashMap<String, Boolean> assignMap = classAssignabilityMap.get(clazzName);
			if (assignMap == null) {
				assignMap = new HashMap<String, Boolean>();
				classAssignabilityMap.put(clazzName, assignMap);
			}
			
			//And iterating to a maximum fixed depth
			int cnt = 0;
			for (StackTraceElement ste : stackTrace) {
				//Checking if we've reached the maximum trace depth
				if (cnt++ >= maxDepth) { break; }
				try {
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
					//Otherwise, we try to resolve the class and test for assignability
					Class<?> steClazz = cl.loadClass(steClass);
					//And then, we compare the assignability of both classes
					if (clazz.isAssignableFrom(steClazz)) {
						assignMap.put(steClass, true);
						return true;
					} else {
						assignMap.put(steClass, false);
					}
				} catch (ClassNotFoundException e) {
					continue;
				}
			}
		}
		return false;
	}
	
	
	/**
     * Wrapper for {@link #calledFromMethod(Class, String, int)}, with the
     * maxDepth set to {@value #MAX_STACK_DEPTH}.
     * 
     * @param clazz the class to search for. This must be an exact match.
     * @param methodName the method name inside the class.
     * @return true, if the method was called from the given class.
     */
	public static boolean calledFromMethod(Class<?> clazz, String methodName) {
		return calledFromMethod(clazz, methodName, MAX_STACK_DEPTH);
	}
	
	/**
	 * Determines whether or not the given class-method is present in the call
	 * stack. Both class name and method name must be exact fits.
	 * 
	 * @param maxDepth the maximum stack depth to search in.
	 * @param clazz the class to search for. This must be an exact match.
     * @param methodName the method name inside the class.
     * @return true, if the method was called from the given class.
	 */
	public static boolean calledFromMethod(Class<?> clazz, String methodName,
			int maxDepth) {
		if (clazz == null || methodName == null || methodName.isEmpty()) {
			return false;
		}
		if (maxDepth <= 0) {
			maxDepth = Integer.MAX_VALUE;
		}
		//Fetch the call stack
		StackTraceElement[] stackTrace =
				Thread.currentThread().getStackTrace();
		
		//And iterating to a maximum fixed depth
		int cnt = 0;
		for (StackTraceElement ste : stackTrace) {
			//Checking if we've reached the maximum trace depth
			if (cnt++ >= maxDepth) { break; }
			
			//Checking if the methodName matches
			if (methodName != null && !methodName.isEmpty() &&
					!ste.getMethodName().equals(methodName)) {
				//Mismatched method name
				continue;
			}
			//Checking if the class name matches
			if (clazz == null || ste.getClassName().equals(clazz.getCanonicalName())) {
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
