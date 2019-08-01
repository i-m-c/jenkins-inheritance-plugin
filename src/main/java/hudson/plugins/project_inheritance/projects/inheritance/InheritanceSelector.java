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


import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.JobProperty;
import hudson.plugins.project_inheritance.projects.InheritanceProject;
import hudson.scm.SCM;
import jenkins.model.Jenkins;

/**
 * Extension point that allows other plugins to decide how a particular type
 * of object should be treated during inheritance.
 * <p>
 * During inheritance, the settings of multiple projects need to be merged into
 * one object, since the leaf project needs to respond to Jenkins as if it did
 * not do any inheritance.
 * <p>
 * For example, a project might inherit multiple {@link SCM} objects, but it
 * has to be ensured that only one is returned to Jenkins. The default
 * behaviour in that case is to use {@link SCM} that was defined "last". If you
 * want to override that behaviour, just register a new subclass of
 * {@link InheritanceSelector} that has {@link SCM} as the template type and
 * returns a mode other than {@link MODE#NOT_RESPONSIBLE} fpr {@link SCM} in
 * {@link #getModeFor(Class)}.
 * <p>
 *  
 * @author Martin Schroeder
 * 
 * @param <T> the type of the objects handled by this selector
 */
public abstract class InheritanceSelector<T> implements Serializable, ExtensionPoint {
	private static final long serialVersionUID = 6297336734737162857L;

	/**
	 * This enumeration lists the various modes of inheritance that can be
	 * specified. The modes mean:
	 * <table summary="" border="1" style="border-collapse:collapse">
	 * 	<tr>
	 * 		<td>Mode</td><td>Meaning</td>
	 * 	</tr>
	 * 	<tr>
	 * 		<td>USE_FIRST</td><td>Only the first definition is kept.</td>
	 * </tr>
	 * 	<tr>
	 * 		<td>MULTIPLE</td><td>Allow multiple values if Jenkins expects iterable.</td>
	 * 	</tr>
	 * 	<tr>
	 * 		<td>USE_LAST</td><td>Only the last definition is kept.</td>
	 * 	</tr>
	 * 	<tr>
	 * 		<td>MERGE</td>
	 * 		<td>
	 * 			This will call
	 * 			{@link InheritanceSelector#merge(Object, Object, InheritanceProject)}
	 * 			to merge identical definitions and put them at the position of
	 * 			the last definition.
	 * 		</td>
	 * 	</tr>
	 * 	<tr>
	 * 		<td>NOT_RESPONSIBLE</td>
	 * 		<td>
	 * 			This mode signifies, that this {@link InheritanceSelector}
	 * 			is not responsible for the given class of Objects. If all
	 * 			registered selectors return this mode for a particular type,
	 * 			the default behaviour is presumed. This means
	 * 			"USE_LAST" in case the return value expected by Jenkins is not 
	 * 			an iterable or "MULTIPLE" if it is an iterable.
	 * 		</td>
	 * 	</tr>
	 * </table>
	 * <p>
	 * Do note that it is for obvious reasons not possible to apply two
	 * {@link InheritanceSelector}s that handle the same 
	 * 
	 * @author Martin Schroeder
	 */
	public enum MODE {
		USE_FIRST, MULTIPLE, USE_LAST, MERGE, NOT_RESPONSIBLE;
	}
	
	/**
	 * This method must return true, if the given Class is a valid supertype
	 * of the generic class used by your type.
	 * <p>
	 * For example, if your selector handles a specific {@link JobProperty},
	 * this method must return true if you encounter a {@link JobProperty}.
	 * <p>
	 * This is used to do a fast pre-selection of which selectors to consider
	 * for the basic types offered by Jenkins (Properties, BuildSteps,
	 * Publishers, etc. pp.).
	 * <p>
	 * Expect to see lots of {@link ClassCastException}s or your code not
	 * even being called, if you violate this.
	 * 
	 * @param clazz the type of objects to check
	 * @return true, if this selector applies to the given type of objects.
	 */
	public abstract boolean isApplicableFor(Class<?> clazz);
	
	/**
	 * This method decides which mode should be used for the given class,
	 * <b>if your selector supports that type at all!</b>
	 * <p>
	 * The latter part is essential, since returning anything other than
	 * {@link MODE#NOT_RESPONSIBLE} for a type that your selector can't
	 * handle will result in {@link ClassCastException} or broken inheritance.
	 * <p>
	 * The default mode if no selector is matches is {@link MODE#USE_LAST},
	 * meaning that the last object overwrites and hides all the others defined
	 * earlier.
	 * 
	 * @param clazz the class of elements under inspection
	 * @return how to handle lists containing these elements
	 */
	public abstract MODE getModeFor(Class<?> clazz);
	
	
	
	/**
	 * This method returns an identifier for the given object. The identifier
	 * should be unique, as long as the objects are not the same in terms of
	 * inheritance. If they are the same their ID must also be the same.
	 * <p>
	 * One example is parameters. If you have two objects for the parameter
	 * with the name "foo", they should get the same ID, because they logically
	 * define the same element in terms of inheritance and thus should either
	 * be merged or override each other.
	 * <p>
	 * The purpose of this function is that candidates for merges can be
	 * identified by looping once through the list and then merging them
	 * in pairs or simply selection the last occurring one.
	 * 
	 * @param obj the object that should be identified.
	 * @return an ID that is the same for multiple objects, if they define the
	 * same logical entity in terms of inheritance. Otherwise, the ID must be
	 * unique - or null, if this selector is not responsible for this object type.
	 */
	public abstract String getObjectIdentifier(T obj);
	
	/**
	 * This function is called when {@link #isApplicableFor(Class)} returned the
	 * {@link MODE#MERGE} mode and the two objects have the same ID as returned
	 * by {@link #getObjectIdentifier(Object)}.
	 * <p>
	 * It is expected of this function to merge the two objects together. This
	 * may or may not create a new object, but should not modify the two given
	 * objects.
	 * <p>
	 * Do note that this function is only called, if there are at least 2
	 * elements to merge. If any post-processing or singleton-handling is
	 * necessary, use {@link #handleSingleton(Object, InheritanceProject)}.
	 * 
	 * @param prior the earlier defined object
	 * @param latter the later defined object
	 * @param caller the job for which the merge is done
	 * 
	 * @return a merged instance. Maybe be new, or one of the input elements
	 */
	public abstract T merge(T prior, T latter, InheritanceProject caller);
	
	/**
	 * This function will be called with the final object that was selected 
	 * to be returned to Jenkins during application of this selector.
	 * Thus, its chief purpose is post-processing after all merges have been
	 * done.
	 * <p>
	 * Please do note that if the {@link MODE#MULTIPLE} was used; this function
	 * will be called for each occurrence.
	 * 
	 * @param object the object selected to be returned to Jenkins.
	 * @param caller the project for which the inheritance is determined.
	 * 
	 * @return the object that should <i>actually</i> be returned to Jenkins. Do
	 * note that it may be part of a list, in which case the returned value
	 * replaces the original value.
	 */
	public abstract T handleSingleton(T object, InheritanceProject caller);
	
	
	/**
	 * This method scans through the given {@link List} and filters/merges
	 * the elements based on the instructions that the subclass delivers.
	 * <p>
	 * It expects that the entries in the list are sorted according to their
	 * inheritance-order; with later entries overwriting earlier settings.
	 * <p>
	 * Based on the {@link MODE} returned by the superclass, entries might be
	 * merged. In that case, the newly generated, merged entry is put at the
	 * location of the last occurrence.
	 * 
	 * @see #merge(Object, Object, InheritanceProject)
	 * 
	 * @param lst the list to scan for entries
	 * @param caller the project that called the modification; can be necessary
	 * 		to correctly process certain operations.
	 * 
	 * @return a new Deque that contains the filtered elements in
	 * inheritance-order.
	 */
	public final List<T> applyAgainstList(List<T> lst, InheritanceProject caller) {
		//Identify the elements this selector is responsible for and determine
		//their connections
		HashMap<String, LinkedList<T>> connections = new HashMap<String, LinkedList<T>>();
		for (T entry : lst) {
			MODE mode = this.getModeFor(entry.getClass());
			switch(mode) {
				case NOT_RESPONSIBLE:
					continue;
				default:
					String id = this.getObjectIdentifier(entry);
					LinkedList<T> connLst = connections.get(id);
					if (connLst == null) {
						connLst = new LinkedList<T>();
					}
					connLst.add(entry);
					connections.put(id, connLst);
			}
		}
		
		if (connections.isEmpty()) {
			//No sense in doing anything further
			return new LinkedList<T>(lst);
		}
		
		//Then, we iterate through the original list to merge or select
		//the correct connected elements
		List<T> out = new LinkedList<T>();
		for (T entry : lst) {
			MODE mode = this.getModeFor(entry.getClass());
			if (mode == MODE.NOT_RESPONSIBLE) {
				out.add(entry);
			} else {
				LinkedList<T> conn = connections.get(
						this.getObjectIdentifier(entry)
				);
				if (conn == null || conn.isEmpty()) {
					//We've already processed the connections of that entry
					continue;
				}
				switch(mode) {
					case USE_FIRST:
						out.add(this.handleSingleton(conn.peekFirst(), caller));
						//Mark this elements connections as processed
						conn.clear();
						break;
						
					case USE_LAST:
						//Wait until we have reached the last entry
						if (entry != conn.peekLast()) { continue; }
						out.add(this.handleSingleton(conn.peekLast(), caller));
						//Mark this elements connections as processed
						conn.clear();
						break;
						
					case MULTIPLE:
						out.add(this.handleSingleton(entry, caller));
						//We must not mark the connections as processed!
						break;
						
					case MERGE:
						//Wait until we have reached the last entry
						if (entry != conn.peekLast()) { continue; }
						// Merge all entries and put them into the list
						T merge = conn.pollFirst();
						while (conn.isEmpty() == false) {
							merge = this.merge(merge, conn.pollFirst(), caller);
						}
						out.add(this.handleSingleton(merge, caller));
						break;
						
					case NOT_RESPONSIBLE:
						//Already handled; should not happen here
						break;
				}
			}
		}
		return out;
	}
	
	
	@SuppressWarnings("rawtypes")
	public static ExtensionList<InheritanceSelector> all() {
		ExtensionList<InheritanceSelector> isLst =
				Jenkins
				.getInstance()
				.getExtensionList(InheritanceSelector.class);
		return isLst;
	}
}

