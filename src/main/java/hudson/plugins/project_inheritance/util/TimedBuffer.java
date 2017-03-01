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

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class TimedBuffer<O, K> {
	
	private class TimeCapsule {
		public final Object obj;
		public final long cTime;
		
		public TimeCapsule(Object obj) {
			this.obj = obj;
			this.cTime = System.currentTimeMillis();
		}
		
		public boolean agedPast(long timeout) {
			return ((System.currentTimeMillis() - cTime) > timeout);
		}
	}
	
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final long timeout;
	private final HashMap<O, HashMap<K,TimeCapsule>> map =
			new HashMap<O, HashMap<K,TimeCapsule>>();
	
	/**
	 * Creates a timed buffer that does not make use of a timeout
	 */
	public TimedBuffer() {
		this(-1);
	}
	
	/**
	 * Creates a timed buffer with the given timeout in milliseconds.
	 * If this value is zero or negative, no timeout will be used.
	 * 
	 * @param timeoutMillis timeout in milliseconds.
	 */
	public TimedBuffer(long timeoutMillis) {
		this.timeout = timeoutMillis;
	}
	
	public Object get(O obj, K key) {
		Entry<Object, Long> entry = this.getWithTimestamp(obj, key);
		return (entry != null) ? entry.getKey() : null;
	}
	
	public Entry<Object, Long> getWithTimestamp(O obj, K key) {
		lock.readLock().lock();
		try {
			//Checking if we have this object hashed
			HashMap<K,TimeCapsule> tMap = map.get(obj);
			if (tMap == null) {
				return null;
			}
			//Checking if that object has the given key
			TimeCapsule tc = tMap.get(key);
			if (tc == null) {
				return null;
			}
			//Checking if the entry has aged beyond its time
			if (timeout > 0 && tc.agedPast(timeout)) {
				return null;
			}
			return new AbstractMap.SimpleEntry<Object, Long>(tc.obj, tc.cTime);
		} finally {
			lock.readLock().unlock();
		}
	}
	
	public void set(O obj, K key, Object value) {
		lock.writeLock().lock();
		try {
			HashMap<K,TimeCapsule> tMap = map.get(obj);
			if (tMap == null) {
				//Creating a new mapping for that object
				tMap = new HashMap<K,TimeCapsule>();
				tMap.put(key, new TimeCapsule(value));
				map.put(obj, tMap);
				return;
			}
			//Adding the key/value pair as a timed capsule
			tMap.put(key, new TimeCapsule(value));
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void remove(O obj, K key) {
		lock.writeLock().lock();
		try {
			//Checking if we have this object hashed
			HashMap<K,TimeCapsule> tMap = map.get(obj);
			if (tMap == null) {
				return;
			}
			tMap.remove(key);
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * This method removes all keys and values associated with the given object.
	 */
	public void clear(O obj) {
		lock.writeLock().lock();
		this.map.remove(obj);
		lock.writeLock().unlock();
	}
	
	
	/**
	 * This method clears everything in this buffer.
	 */
	public void clearAll() {
		lock.writeLock().lock();
		this.map.clear();
		lock.writeLock().unlock();
	}
	
	/**
	 * This method clears all values associated with the given key across all
	 * objects.
	 */
	public void clearAll(K key) {
		lock.writeLock().lock();
		for (HashMap<K,TimeCapsule> tMap : map.values()) {
			if (tMap != null) {
				tMap.remove(key);
			}
		}
		lock.writeLock().unlock();
	}


	/**
	 * This method will remove all entries that aged beyond the assigned timeout.
	 * As this method has a linear complexity, don't call it <i>too</i> often.
	 * 
	 * This method is a O(1) no-op if no timeout is defined for this method.
	 */
	public void cull() {
		//Only do something if we actually have a timeout
		if (timeout <= 0) { return; }
		lock.writeLock().lock();
		for (HashMap<K,TimeCapsule> tMap : map.values()) {
			if (tMap == null) { continue; }
			Iterator<TimeCapsule> iter = tMap.values().iterator();
			while (iter.hasNext()) {
				TimeCapsule tc = iter.next();
				if (tc == null) { continue; }
				if (tc.agedPast(timeout)) {
					iter.remove();
				}
			}
		}
		lock.writeLock().unlock();
	}
}
