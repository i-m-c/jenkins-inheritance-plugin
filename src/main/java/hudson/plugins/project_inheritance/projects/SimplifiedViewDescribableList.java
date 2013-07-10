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

package hudson.plugins.project_inheritance.projects;

import hudson.model.Describable;
import hudson.model.ReconfigurableDescribable;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import hudson.model.Descriptor.FormException;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

public class SimplifiedViewDescribableList<T extends Describable<T>, D extends Descriptor<T>>
		extends DescribableList<T, Descriptor<T>> {

	public SimplifiedViewDescribableList(
			Saveable owner, Collection<? extends T> initialList) {
		super(owner, initialList);
	}
	
	@SuppressWarnings("unchecked")
	public List<T> rebuildForSimplifiedView(
			StaplerRequest req, JSONObject json,
			List<? extends Descriptor<T>> descriptors
			) throws FormException, IOException {
		List<T> newList = new ArrayList<T>();

		for (Descriptor<T> d : descriptors) {
			T existing = this.get(d);
			String name = d.getJsonSafeClassName();
			JSONObject o = json.optJSONObject(name);
			
			T instance = null;
			if (o!=null) {
				if (existing instanceof ReconfigurableDescribable) {
					ReconfigurableDescribable<?> rd =
							(ReconfigurableDescribable<?>)existing;
					instance = (T) rd.reconfigure(req,o);
				} else {
					instance = d.newInstance(req, o);
				}
			} else {
				if (existing instanceof ReconfigurableDescribable) {
					ReconfigurableDescribable<?> rd =
							(ReconfigurableDescribable<?>)existing;
					instance = (T) rd.reconfigure(req, null);
				}
			}

			if (instance!=null)
				newList.add(instance);
		}

		return newList;
	}
}
