/**
 * Copyright © 2014 Contributor.  All rights reserved.
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

package hudson.plugins.project_inheritance.projects.actions;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import hudson.ExtensionList;
import hudson.model.TransientProjectActionFactory;

public class FilteredTransientActionFactoryHelper {
		
	public static Iterable<TransientProjectActionFactory> all() {
		final ExtensionList<TransientProjectActionFactory> underlying =  TransientProjectActionFactory.all();
		return new Iterable<TransientProjectActionFactory>() {
			
			public Iterator<TransientProjectActionFactory> iterator() {
				return new FilteredIterator(underlying, Collections.singletonList((Filter)new JobConfigHistoryFilter()));
			}
		};
	}
	
	private static interface Filter {
		public boolean excludeItem(TransientProjectActionFactory factory);
	}
	
	private static class JobConfigHistoryFilter implements Filter {
		private static boolean jobConfigHistoryPluginEnabled = detectJobConfigHistoryPlugin();
		private static Class<?> factoryClass;
		private static boolean detectJobConfigHistoryPlugin() {
			boolean found = true;
			try {
				factoryClass = Class.forName("hudson.plugins.jobConfigHistory.JobConfigHistoryActionFactory");
			} catch (ClassNotFoundException e) {
				found = false;
			}
			return found;
		}
		
		public boolean excludeItem(TransientProjectActionFactory factory) {
			if(!jobConfigHistoryPluginEnabled)
				return false;
			
			return factoryClass.isInstance(factory);
		}
	}
	
	private static class FilteredIterator implements Iterator<TransientProjectActionFactory>{
		TransientProjectActionFactory cached;
		Iterator<TransientProjectActionFactory> underlying;
		List<Filter> filterList;
		
		
		public FilteredIterator(
				ExtensionList<TransientProjectActionFactory> underlyingIterable, List<Filter> filters) {
			this.underlying = underlyingIterable.iterator();
			this.filterList = filters;
		}

		/**
		 * delegate to list of filters for exclusion decision.  If any filter in the list
		 * votes to exclude the item, it is excluded, otherwise it is included.
		 * @param factory
		 * @return filter decision, true to exclude from result, false to include
		 */
		private boolean excludeItem(TransientProjectActionFactory factory) {
			for(Filter f : filterList) {
				if(f.excludeItem(factory))
					return true;
			}
			return false;
		}
		
		private boolean hasCached() {
			return cached != null;
		}
		
		private TransientProjectActionFactory popCached() {
			TransientProjectActionFactory c = cached;
			cached = null;
			return c;
		}
		
		private TransientProjectActionFactory advance() {
			while(underlying.hasNext()) {
				TransientProjectActionFactory toCache = underlying.next();
				if(!excludeItem(toCache))
					return toCache;				
			}
			return null;
		}
		
		public boolean hasNext() {
			if(hasCached())
				return true;
			
			cached = advance();
			return cached != null;
		}

		public TransientProjectActionFactory next() {
			if(hasCached()) {
				return popCached();
			}
			return advance();
		}

		public void remove() {
			throw new UnsupportedOperationException("Cannot remove() on FilteredIterator");			
		}
		
	}
}
