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
package hudson.plugins.project_inheritance.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LimitedHashMap<K, V> extends LinkedHashMap<K, V> {
	private static final long serialVersionUID = -1075647431523853453L;
	
	private final int maxCapacity;
	
	public LimitedHashMap(int maxCapacity) {
		super();
		this.maxCapacity = (maxCapacity <= 0) ? 0 : maxCapacity;
	}
	
	public LimitedHashMap(int maxCapacity, int initialCapacity) {
		super(initialCapacity);
		this.maxCapacity = (maxCapacity <= 0) ? 0 : maxCapacity;
	}
	
	
	protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
		if (maxCapacity <= 0) { return false; }
		return (this.size() > maxCapacity);
	}
}
