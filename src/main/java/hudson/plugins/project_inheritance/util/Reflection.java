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
import hudson.plugins.project_inheritance.projects.creation.ProjectCreationEngine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Reflection {
	
	protected static final int MAX_STACK_DEPTH = 30;
	
	/**
	 * This is a cache for loading classes by their name. It is supposed to
	 * reduce contention on the class-loader which otherwise might become a
	 * bottleneck.
	 * <p>
	 * You can (and should) give it a timeout after which the resolution will
	 * be considered "stale" and refreshed. You can omit this in case you
	 * believe that class resolution will remain stable over the entire
	 * runtime.
	 */
	protected static class CachedClassResolver {
		/**
		 * A single entry for the cache that can be checked for freshness
		 */
		protected final class CacheEntry {
			public final Class<?> clazz;
			private final long cacheTime;
			
			public CacheEntry(Class<?> clazz) {
				this.clazz = clazz;
				this.cacheTime = System.currentTimeMillis();
			}
			
			public boolean isOlderThan(long age) {
				return (this.cacheTime + age) < System.currentTimeMillis();
			}
		}
		
		protected final ConcurrentHashMap<String, CacheEntry> resolveMap;
		protected final Long maxAge;
		
		/**
		 * Creates a class resolver that does not clear its entries.
		 */
		public CachedClassResolver() {
			this(null);
		}
		
		public CachedClassResolver(Long maxAge) {
			this.resolveMap = new ConcurrentHashMap<String, CacheEntry>();
			this.maxAge = maxAge;
		}
		
		public Class<?> resolve(String className) {
			if (className == null || className.isEmpty()) {
				return null;
			}
			
			//Check if we have a cached result and it's not too old
			CacheEntry entry = this.resolveMap.get(className);
			if (entry != null) {
				if (maxAge == null || ! entry.isOlderThan(maxAge)) {
					return entry.clazz;
				}
			}
			
			//Fetching the class loader from Jenkins
			//Do note that it is synchronised and thus a possible bottleneck
			ClassLoader cl;
			try {
				cl = Hudson.getInstance().getPluginManager().uberClassLoader;
			} catch (NullPointerException ex) {
				//This can (or should) only happen if not on the server or during shutdown
				return null;
			}
			
			Class<?> clazz = null;
			try {
				//Try to resolve the class and test for assignability
				clazz = cl.loadClass(className);
			} catch (ClassNotFoundException e) {
				//No such class; clazz can stay == null
			}
			//Caching the resolution (there might already be an old one present)
			this.resolveMap.put(className, new CacheEntry(clazz));
			return clazz;
		}
	}
	
	protected static class AssignabilityChecker {
		/**
		 * This map stores for a class name, which other class names have been
		 * found to be assignable to it.
		 * <p>
		 * In more detail:
		 * <ul>
		 *   <li>For a given class name (outer), you get back a map.</li>
		 *   <li>Each entry is a tuple of another class name (inner) and a
		 *   boolean that states whether or not you can assign the inner class
		 *   to the outer class.</li>
		 * </ul>
		 * 
		 * TODO: This map can grow quite fast, theoretically up to n^2; n = # of classes
		 */
		protected final ConcurrentHashMap<Class<?>, ConcurrentHashMap<Class<?>, Boolean>> classAssignabilityMap;
		
		
		public AssignabilityChecker() {
			this.classAssignabilityMap =
					new ConcurrentHashMap<Class<?>, ConcurrentHashMap<Class<?>, Boolean>>();
		}
		
		/**
		 * Checks if the 'clazz' class is assignable from the 'other' class.
		 * <p>
		 * Identical to clazz.isAssignableFrom(other), but might use caching
		 * to speed up resolution. This is useful if Java 5 (or earlier) is
		 * used, as these use a much slower implementation than Java 6+.
		 * <p>
		 * TODO: Disable caching as soon as Java 5 has been deprecated for Jenkins
		 * 
		 * @param clazz the class to which 'other' must be assignable (left-hand value)
		 * @param other the class which must be assignable to 'clazz' (right-hand value)
		 * @return true, if 'other' is a subtype of 'clazz.
		 */
		public boolean isAssignableFrom(Class<?> clazz, Class<?> other) {
			if (clazz == null || other == null) { return false; }
			
			//Check if caching this operation is wanted or not
			if (!ProjectCreationEngine.instance.getEnableReflectionCaching()) {
				return clazz.isAssignableFrom(other);
			}
			
			//Check if the base class was already checked at least once
			ConcurrentHashMap<Class<?>, Boolean> checkMap = 
					this.classAssignabilityMap.get(clazz);
			if (checkMap == null) {
				boolean result = clazz.isAssignableFrom(other);
				checkMap = new ConcurrentHashMap<Class<?>, Boolean>();
				checkMap.put(other, result);
				classAssignabilityMap.putIfAbsent(clazz, checkMap);
				return result;
			} else {
				Boolean result = checkMap.get(other);
				if (result == null) {
					result = clazz.isAssignableFrom(other);
					//Not putIfAbsent() since something might've added a null
					checkMap.put(other, result);
					return result;
				} else {
					return result;
				}
			}
		}
	}
	
	
	/**
	 * A short-term (30s) cache to speed up resolving classes
	 */
	protected static final CachedClassResolver resolver =
			new CachedClassResolver(30*1000L);
	protected  static final AssignabilityChecker assigner =
			new AssignabilityChecker();
	
	
	public static boolean calledFromClassNames(String... classes) {
		return calledFromClassNames(MAX_STACK_DEPTH, classes);
	}
	
	public static boolean calledFromClassNames(int maxDepth, String... classes) {
		if (classes == null || classes.length == 0) {
			return false;
		}
		if (maxDepth <= 0) {
			maxDepth = Integer.MAX_VALUE;
		}
		//Fetch the call stack
		StackTraceElement[] stackTrace =
				new Throwable().getStackTrace(); 
		if (stackTrace == null || stackTrace.length == 0) {
			return false;
		}
		
		//A quick lookup table for the class name
		Set<String> clSet = new HashSet<String>(Arrays.asList(classes));
		
		//Then, checking for each stack element, whether a stack-class matches
		int cnt = 0;
		for (StackTraceElement ste : stackTrace) {
			if (cnt++ >= maxDepth) { break; }
			if (clSet.contains(ste.getClassName())) {
				return true;
			}
		}
		//No such class on the stack (or no class can be resolved)
		return false;
	}
	
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
				new Throwable().getStackTrace(); 
		if (stackTrace == null || stackTrace.length == 0) {
			return false;
		}
		
		//Fetching all class names currently present in the stack trace.
		Set<Class<?>> stackCls = getClasses(maxDepth, stackTrace);
		if (stackCls == null || stackCls.isEmpty()) {
			return false;
		}
		
		//Then, checking for each input class, whether a stack-class matches 
		for (Class<?> inClass : classes) {
			for (Class<?> outClass : stackCls) {
				if (assigner.isAssignableFrom(inClass, outClass)) {
					return true;
				}
			}
		}
		//No such class on the stack (or no class can be resolved)
		return false;
	}
	
	/**
	 * This method takes the given stack and returns the set of all class names
	 * contained therein.
	 * 
	 * @param maxDepth the maximum stack depth to explore
	 * @param stackTrace the stack trace
	 * @return a set containing the class names from the stack
	 */
	private static Set<Class<?>> getClasses(int maxDepth, StackTraceElement[] stackTrace) {
		HashSet<Class<?>> classSet = new HashSet<Class<?>>();
		int cnt = 0;
		for(StackTraceElement ste : stackTrace) {
			if (cnt++ >= maxDepth) { break; }
			Class<?> clazz = resolver.resolve(ste.getClassName());
			if (clazz != null) {
				classSet.add(clazz);
			}
		}
		return classSet;
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
	 * Determines whether or not at least one of the given methods from the
	 * given class are present in the call stack.
	 * <b>Both</b> class name and method name must be <b>exact</b> matches.
	 * 
	 * @param maxDepth the maximum stack depth to search in.
	 * @param clazz the class to search for. This must be an exact match.
	 * @param methodNames the method names inside the class to search for.
	 * @return true, if at least one of the methods was called from the given class.
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
		
		HashSet<String> methods = new HashSet<String>(
				Arrays.asList(methodNames)
		);
		
		//And iterating to a maximum fixed depth
		int cnt = 0;
		for (StackTraceElement ste : stackTrace) {
			//Checking if we've reached the maximum trace depth
			if (cnt++ >= maxDepth) { break; }
			//Checking if the class stack's class matches
			if (clazz != null) {
				Class<?> steClazz = resolver.resolve(ste.getClassName());
				if (clazz != steClazz) {
					//Mismatched method name
					continue;
				}
			}
			//Checking if the methodName matches one of the candidates
			if (methods.contains(ste.getMethodName())) {
				//Both method name and class assignment match
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
