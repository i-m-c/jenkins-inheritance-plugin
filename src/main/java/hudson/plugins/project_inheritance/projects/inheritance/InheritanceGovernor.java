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
package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.ExtensionList;
import hudson.cli.BuildCommand;
import hudson.model.Build;
import hudson.model.Describable;
import hudson.model.Queue;
import hudson.model.Saveable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.plugins.project_inheritance.projects.InheritanceProject.IMode;
import hudson.plugins.project_inheritance.projects.references.AbstractProjectReference;
import hudson.plugins.project_inheritance.projects.references.ProjectReference.PrioComparator;
import hudson.plugins.project_inheritance.projects.references.ProjectReference.PrioComparator.SELECTOR;
import hudson.plugins.project_inheritance.projects.versioning.VersionHandler;
import hudson.plugins.project_inheritance.util.Reflection;
import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildTrigger;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import jenkins.model.ParameterizedJobMixIn;

import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;


/**
 * This class offers a few helper functions to facilitate correct
 * inheritance and versioning of the various types of fields of Jenkins
 * projects.
 * 
 * @author Martin Schroeder
 *
 * @param <T> the target type of the field this helper is written for.
 */
public abstract class InheritanceGovernor<T> {
	public final String fieldName;
	public final SELECTOR orderMode;
	public final InheritanceProject caller;
	
	/**
	 * {@link Saveable} that doesn't save anything.
	 * @since 1.301.
	 */
	static Saveable NOOP = new Saveable() {
		public void save() throws IOException {}
	};
	
	
	
	public InheritanceGovernor(String field, SELECTOR order, InheritanceProject caller) {
		this.fieldName = field;
		this.orderMode = order;
		this.caller = caller;
	}
	
	/**
	 * This function should take an arbitrary Object o and cast it directly
	 * to T if possible; or return null if o is incompatible.
	 * <p>
	 * It <b>must not</b> return a copy. It must cast directly or return null.
	 * This property will be checked by this class and a nasty exception
	 * will be thrown by this class if you violate it.
	 * 
	 * @param o the object to be cast directly without copying.
	 * @return o recast as the type T, if possible.
	 */
	protected abstract T castToDestinationType(Object o);
	
	public abstract T getRawField(InheritanceProject ip);
	
	
	private final T noCopyCast(Object o) {
		T cast = castToDestinationType(o);
		if (cast != null && cast != o) {
			throw new ClassCastException("You MUST NOT cast by copying!");
		}
		return cast;
	}
	
	public T getVersionedField(InheritanceProject ip, Long version) {
		//Check sanity of version store for ip
		if (ip.getVersionedObjectStore().size() == 0) {
			//No versions; returning raw element if base project
			return this.getRawField(ip);
		}
		//Fetch values for given version
		Map<String, Object> map = ip.getVersionedObjectStore().getValueMapFor(version);
		if (map == null || map.isEmpty()) {
			return this.getRawField(ip);
		}
		
		//Fetch field from map; do note that even saved fields can be null!
		if (map.containsKey(fieldName)) {
			//Field exists, so if it is set to null; null is a valid value
			Object obj = map.get(fieldName);
			if (obj == null) {
				return null;
			}
			//Cast the found field by using the user-defined cast
			return this.noCopyCast(obj);
		} else {
			//The field was not saved, so we need to return the raw field
			return this.getRawField(ip);
		}
	}

	/**
	 * This method moves through as much of the inheritance tree as is necessary
	 * to retrieve the desired field as defined by this class.
	 * 
	 * @param root the project from which to start derivation.
	 * @param mode the mode of inheritance to use.
	 * @return the final element. Will be a copy if multiple inherited values
	 * are combined or a specific version is requested. Will only be the actual
	 * field reference if in "AUTO" mode and under very specific circumstances
	 * where this plugin is <b>SURE</b> that the actual field is needed.
	 */
	public final T retrieveFullyDerivedField(InheritanceProject root, IMode mode) {
		/* Figuring out which of the three cases we need:
		 * 1.) Full inheritance with versioning (will return a copied list)
		 * 2.) Local-only data with versioning (will also return a copied list)
		 * 3.) Local-only data without versioning (will return the currently active lists) 
		 */
		boolean needsInheritance = false;
		boolean needsVersioning = false;
		switch (mode) {
			case INHERIT_FORCED:
				needsInheritance = true;
				needsVersioning = true;
				break;
				
			case LOCAL_ONLY:
				needsInheritance = false;
				needsVersioning = true;
				break;
				
			case AUTO:
				needsInheritance = inheritanceLookupRequired(root);
				needsVersioning = versioningRequired();
				break;
		}
		
		//Checking if we should abort early by returning the raw fields
		if (!needsInheritance && !needsVersioning) {
			return this.getRawField(root);
		}
		
		if (!needsInheritance) {
			return this.getVersionedField(
					root, VersionHandler.getVersion(root)
			);
		}
		
		//Retrieving the full scope of all parents and ourselves in order
		List<InheritanceProject> scope =
				getFullScopeOrdered(root, new HashSet<String>());
		LinkedList<T> allFields = new LinkedList<T>();
		
		for (InheritanceProject ip : scope) {
			//Fetch the version desired for this project
			Long version = VersionHandler.getVersion(ip);
			//Fetch the field for that tuple
			T field = this.getVersionedField(ip, version);
			if (field != null) {
				allFields.add(field);
			}
		}
		
		//Now, at the end, reduce the list to a single element
		return reduceFromFullInheritance(allFields);
	}
	
	private final List<InheritanceProject> getFullScopeOrdered(
			InheritanceProject root, Set<String> seen) {
		return getFullScopeOrdered(root, orderMode, seen);
	}
	
	/**
	 * Returns the list of parents of a given job (including the given root),
	 * ordered according to the given selector.
	 * 
	 * @param root the job to get parents for.
	 * @param order the order to use.
	 * @param seen a set of names of projects which have already been included.
	 *  If null, an empty set is used.
	 * 
	 * @return the list of parents, including the given root. As such, is never
	 * empty or null.
	 */
	public static final List<InheritanceProject> getFullScopeOrdered(
			InheritanceProject root, SELECTOR order, Set<String> seen
	) {
		if (seen == null) { seen = new HashSet<String>(); }
		List<InheritanceProject> all = new LinkedList<InheritanceProject>();
		if (root == null) { return all; }
		
		String name = root.getFullName();
		if (seen.contains(name)) {
			return all;
		}
		seen.add(name);
		
		List<InheritanceProject> priors = new LinkedList<InheritanceProject>();
		List<InheritanceProject> latters = new LinkedList<InheritanceProject>();
		
		for (AbstractProjectReference apr : root.getParentReferences(order)) {
			if (apr == null) { continue; }
			InheritanceProject ip = apr.getProject();
			if (ip == null) { continue; }
			int prio = PrioComparator.getPriorityFor(apr, order);
			if (prio <= 0) {
				priors.addAll(getFullScopeOrdered(ip, order, seen));
			} else {
				latters.addAll(getFullScopeOrdered(ip, order, seen));
			}
		}
		
		all.addAll(0, priors);
		all.add(root);
		all.addAll(latters);
		
		return all;
	}
	
	
	
	// === STATIC HELPER METHODS ===
	
	/**
	 * This is a reduction function that is tasked to reduce the List&lt;T&gt;
	 * that is generated through inheritance down to a single T.
	 * <p>
	 * This might mean merging the elements of the list or just selecting
	 * one entry. <i>The default implementation simply selects the last
	 * non-null element. If none are present; it returns null.</i>
	 * <p>
	 * Do note that there's also the {@link InheritanceSelector} extension point
	 * which should be applied for certain fields of a Job class.
	 * <p>
	 * For example, the "properties" element has a list as the type T here.
	 * This means, the reduction helper will first merge the list and then
	 * try to apply one of the InheritanceSelector classes.
	 * The same is true for build-wrappers, build steps, publishers and more.
	 * <p>
	 * Some  implementations may also skip the first part since they are tasked
	 * not to return a List but a singleton value. As such, their reduction
	 * helper will apply the InheritanceSelector classes FIRST before stripping
	 * down the list to the single element they are tasked to build. An example
	 * would be the {@link SCM} property of a project.
	 * <p>
	 * <b>Do note:</b> This function is only applied if inheritance actually
	 * happened. It will neither be called when the local properties of a project
	 * are queried, nor if the project does not have any parents. Do note that
	 * the list may be empty or contain only a single element.
	 *
	 * @param list the list of fields from all the parents, sorted by their
	 * selected inheritance order.
	 * @return a single element T. Can be an element from the list or a merge
	 * of the list or even a newly generated element. The default
	 * implementation will simply return the last entry from the input list.
	 */
	protected T reduceFromFullInheritance(Deque<T> list) {
		if (list == null || list.isEmpty()) { return null; }
		Iterator<T> iter = list.descendingIterator();
		while (iter.hasNext()) {
			T obj = iter.next();
			if (obj != null) {
				return obj;
			}
		}
		return list.peekLast();
	}
	
	
	/**
	 * Simple helper function to use a merge as the default reduction.
	 * <p>
	 * Do note that the list is returned in-order of inheritance, with merges
	 * being put back into the list at the location of the last definition.
	 * <p>
	 * It will not de-duplicated the resulting list beyond doing the merges 
	 * based on the {@link InheritanceSelector} extensions.
	 * 
	 * @see #reduceFromFullInheritance(Deque)
	 * @see #reduceByMerge(Deque, Class, InheritanceProject)
	 * 
	 * @param <R> the type of the values in the given list.
	 * @param list the raw list to scan
	 * @param listType the type of elements in the list
	 * @param caller the project for which the merge is done
	 * @return a list of items resulting from the merge
	 */
	protected static <R> List<R> reduceByMergeWithDuplicates(Deque<List<R>> list, Class<?> listType, InheritanceProject caller) {
		List<R> merge = new LinkedList<R>();
		if (list == null) { return merge; }
		
		for (Collection<R> sub : list) {
			merge.addAll(sub);
		}
		
		if (merge.isEmpty()) {
			return merge;
		}
		
		@SuppressWarnings("rawtypes")
		ExtensionList<InheritanceSelector> isLst = InheritanceSelector.all();
		for (InheritanceSelector<?> is : isLst) {
			if (is.isApplicableFor(listType) == false) {
				continue;
			}
			@SuppressWarnings("unchecked")
			InheritanceSelector<R> isr = (InheritanceSelector<R>) is;
			merge = isr.applyAgainstList(merge, caller);
		}
		return merge;
	}
	
	/**
	 * Simple helper function to use a merge as the default reduction.
	 * <p>
	 * Do note that the list is returned in-order of inheritance, with merges
	 * being put back into the list at the location of the last definition.
	 * <p>
	 * In contrast to {@link #reduceByMergeWithDuplicates(Deque, Class, InheritanceProject)},
	 * it will remove duplicate entries based on the class-objects.
	 * 
	 * @param <R> the type of the values in the given list.
	 * @param list the list to reduce
	 * @param listType the type of the list elements
	 * @param caller the project for which the list is being generated
	 * @return a reduced list. May be empty but never null.
	 * @see #reduceFromFullInheritance(Deque)
	 */
	protected static <R> List<R> reduceByMerge(Deque<List<R>> list, Class<?> listType, InheritanceProject caller) {
		List<R> merge = reduceByMergeWithDuplicates(list, listType, caller);
		
		//Remove duplicated entries and select the LAST one of each
		LinkedList<R> out = new LinkedList<R>(merge);
		Set<Class<?>> seen = new HashSet<Class<?>>();
		Iterator<R> rIter = out.descendingIterator();
		while (rIter.hasNext()) {
			R entry = rIter.next();
			Class<?> clazz = entry.getClass();
			if (seen.contains(clazz)) {
				rIter.remove();
			} else {
				seen.add(clazz);
			}
		}
		return out;
	}
	
	protected static <R extends Describable<R>> DescribableList<R, Descriptor<R>> reduceDescribableByMerge(
			Deque<DescribableList<R, Descriptor<R>>> list) {
		if (list == null) {
			return new DescribableList<R, Descriptor<R>>(NOOP);
		}
		
		List<R> merge = new LinkedList<R>();
		for (DescribableList<R, Descriptor<R>> sub : list) {
			for (R item : sub) {
				merge.add(item);
			}
		}
		
		return new DescribableList<R, Descriptor<R>>(NOOP, merge);
	}
	
	protected static <R extends Describable<R>> DescribableList<R, Descriptor<R>> reduceDescribableByMergeWithoutDuplicates(
			Deque<DescribableList<R, Descriptor<R>>> list) {
		
			// set for keeping track of found classes
			Set<Class<?>> seen = new HashSet<Class<?>>();
			
			if (list == null) {
				return new DescribableList<R, Descriptor<R>>(NOOP);
			}
			
			// result list
			List<R> merge = new LinkedList<R>();
			
			// descending order for keeping the last buildwrapper that was set = lower in the inheritance tree
			Iterator<DescribableList<R, Descriptor<R>>> rIter = list.descendingIterator();
			while (rIter.hasNext()) {
				DescribableList<R, Descriptor<R>> sub = rIter.next();
				
				for (R item : sub) {
					Class<?> clazz = item.getClass();
					
					if (!seen.contains(clazz)) {
						merge.add(item);
						seen.add(clazz);
					}
				}
			}
			
			return new DescribableList<R, Descriptor<R>>(NOOP, merge);
	}
	
	@SuppressWarnings("unchecked")
	protected static <R> List<R> castToList(Object o) {
		try {
			if (o instanceof List) {
				return (List<R>) o;
			}
			return null;
		} catch (ClassCastException ex) {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	protected static <R extends Describable<R>> DescribableList<R, Descriptor<R>> castToDescribableList(Object o) {
		try {
			if (o instanceof DescribableList) {
				return ((DescribableList<R, Descriptor<R>>) o);
			}
			return null;
		} catch (ClassCastException ex) {
			return null;
		}
	}


	public static boolean inheritanceLookupRequired(InheritanceProject root) {
		return inheritanceLookupRequired(root, false);
	}
	
	public static boolean inheritanceLookupRequired(InheritanceProject root, boolean forcedInherit) {
		//In a cyclic dependency, any form of inheritance would be ill-advised
		try {
			if (root.hasCyclicDependency()) {
				return false;
			}
		} catch (NullPointerException ex) {
			//The project might not be loaded completely yet; this will cause
			//an NPE which means no inheritance should be queried
			return false;
		}
		/* Otherwise, an exploration is only required when one of the following
		 * holds:
		 * 1.) The user wants to force inheritance
		 * 2.) The project is transient and has no real own configuration
		 * 3.) The project is called in the context of a build
		 * 4.) The queue queries properties of the project 
		 */
		
		//Check forced inheritance or transience
		if (forcedInherit || root.getIsTransient()) {
			return true;
		}
		
		//Checking the Stapler Request, because it is fast
		StaplerRequest req = Stapler.getCurrentRequest();
		if (req != null) {
			String uri = req.getRequestURI();
			if (inheritanceRequiredByRequestURI(uri)) {
                return true;
            }
		}
		
		//Check via expensive stack reflection
		if (Reflection.calledFromClass(
				Build.class, BuildCommand.class,
				Queue.class, BuildTrigger.class,
				Trigger.class, BuildStep.class,
				ParameterizedJobMixIn.class
			) ||
			Reflection.calledFromMethod(
					InheritanceProject.class,
					"doBuild", "scheduleBuild2", "doBuildWithParameters"
			)
		) {
			return true;
		}
		
		//In all other cases, we don't require (or want) inheritance
		return false;
	}
	
    private static boolean inheritanceRequiredByRequestURI(String uri) {
        List<RequestInheritanceChecker> list = Hudson.getInstance().getExtensionList(
                RequestInheritanceChecker.class);
        for (RequestInheritanceChecker requestInheritanceChecker : list) {
            if (requestInheritanceChecker.isInheritanceRequired(uri)) {
                return true;
            }
        }
        return false;
    }
	
	/**
	 * This method uses reflection to tell whether the current state means
	 * that versioning is needed or not.
	 * <p>
	 * There is only one circumstance in which versioning should NOT be needed,
	 * and that is when Jenkins expects to access the original, raw fields
	 * of this class to directly manipulate them during reconfiguration.
	 * <p>
	 * Do note that both {@link #inheritanceLookupRequired(InheritanceProject)}
	 * and this function need to return false for the raw lists to be returned
	 * by {@link #getVersionedField(InheritanceProject, Long)}.
	 * 
	 * @return true if versioning for the various fields is needed.
	 */
	protected static boolean versioningRequired() {
		if (Reflection.calledFromMethod(Project.class, "submit")) {
			return false;
		}
		
		//In all other cases, always return true
		return true;
	}

}
