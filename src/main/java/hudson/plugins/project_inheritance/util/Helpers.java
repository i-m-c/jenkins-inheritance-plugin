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

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.FieldKey;
import com.thoughtworks.xstream.converters.reflection.FieldKeySorter;
import com.thoughtworks.xstream.converters.reflection.Sun14ReflectionProvider;


public class Helpers {

	public static boolean bothNullOrEqual(Object a, Object b) {
		if (a == null && b == null) {
			return true;
		} else if ((a == null) ^ (b == null)) {
			return false;
		} else {
			return a.equals(b);
		}
	}
	
	private static XStream sortStream = null;
	
	public static XStream getSortedXSteam() {
		if (sortStream != null) { return sortStream; }
		FieldKeySorter sorter = new FieldKeySorter() {
			@SuppressWarnings("rawtypes")
			public Map sort(Class type, Map keyedByFieldKey) {
				Map<FieldKey, Field> out = new TreeMap<FieldKey, Field>(
					new Comparator<FieldKey>() {
						public int compare(FieldKey o1, FieldKey o2) {
							return o1.getFieldName().compareTo(o2.getFieldName());
						}
					}
				);
				for (Object key : keyedByFieldKey.keySet()) {
					out.put((FieldKey)key, (Field)keyedByFieldKey.get(key));
				}
				return out;
			}
		};
		sortStream = new XStream(new Sun14ReflectionProvider(
				new FieldDictionary(sorter)
		));
		return sortStream;
	}
}
