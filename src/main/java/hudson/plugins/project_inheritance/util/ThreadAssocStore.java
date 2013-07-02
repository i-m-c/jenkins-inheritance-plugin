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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.PeriodicWork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import jenkins.model.Jenkins;


/**
 * This singleton class is used to associated arbitrary objects with the
 * current thread.
 * <p>
 * It is a singleton to ensure static access from all corners of the
 * application. It can be used as a crude way to communicate across methods in
 * the same call stack.
 * <p>
 * Simply think of it as a way to store thread-global object references. The
 * content will be removed after the thread ends. If you need earlier release,
 * just overwrite the values with a null.
 * <p>
 * Do note that this class extends {@link PeriodicWork} as a way to clean-up
 * after itself every few minutes. This is done to prevent the held
 * Threads from never being garbage-collected.
 * 
 * @author mhschroe
 *
 */
@Extension
public class ThreadAssocStore extends PeriodicWork {
	
	private static transient ThreadAssocStore instance = null;
	
	private static final Logger log = Logger.getLogger(
			ThreadAssocStore.class.toString()
	);
	
	
	private final HashMap<Thread, HashMap<String, Object>> map =
			new HashMap<Thread, HashMap<String,Object>>();
	
	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	
	/**
	 * Constructor used by the Extension annotation.
	 * <p>
	 * You should not need to spawn a {@link ThreadAssocStore} yourself.
	 * Instead, use {@link #getInstance()} to get the singleton.
	 */
	public ThreadAssocStore() {
		//Nothing to do; we can't set the "instance" field here, as Jenkins
		//might call this constructor multiple times.
	}

	public final static ThreadAssocStore getInstance() {
		if (instance != null) {
			return instance;
		}
		try {
			Jenkins j = Jenkins.getInstance();
			if (j == null) { return null; }
			ExtensionList<ThreadAssocStore> list = j.getExtensionList(
					ThreadAssocStore.class
			);
			if (list.isEmpty()) {
				return null;
			}
			instance = list.get(0);
			return instance;
		} catch (Exception ex) {
			return null;
		}
	}
	
	public void setValue(Thread t, String key, Object value) {
		// Fetching a write lock
		lock.writeLock().lock();
		try {
			log.finest("SET value for: " + key + " on " + t.toString());
			HashMap<String, Object> subMap = map.get(t);
			if (subMap == null) {
				subMap = new HashMap<String, Object>();
				map.put(t, subMap);
			}
			subMap.put(key, value);
		} finally {
			// Releasing the write lock
			lock.writeLock().unlock();
		}
	}
	
	public void setValue(String key, Object value) {
		this.setValue(Thread.currentThread(), key, value);
	}
	
	
	public Object getValue(Thread t, String key) {
		lock.readLock().lock();
		try {
			log.finest("GET value for: " + key + " on " + t.toString());
			HashMap<String, Object> subMap = map.get(t);
			if (subMap == null) { return null; }
			return subMap.get(key);
		} finally {
			lock.readLock().unlock();
		}
	}
	
	public Object getValue(String key) {
		return this.getValue(Thread.currentThread(), key);
	}
	
	public void cleanup() {
		lock.writeLock().lock();
		try {
			Set<Thread> keys = map.keySet();
			Iterator<Thread> iter = keys.iterator();
			while (iter.hasNext()) {
				Thread t = iter.next();
				if (!t.isAlive()) {
					iter.remove();
				}
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	public void clear(Thread t) {
		lock.writeLock().lock();
		try {
			map.remove(t);
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	public void clear(Thread t, String key) {
		lock.writeLock().lock();
		try {
			HashMap<String, Object> subMap = map.get(t);
			if (subMap == null) { return; }
			subMap.remove(key);
		} finally {
			lock.writeLock().unlock();
		}
	}


	/**
	 * Tells Jenkins to run {@link #doRun()} every 5 minutes.
	 * @return
	 */
	@Override
	public long getRecurrencePeriod() {
		return 5*60*1000;
	}


	/**
	 * Simply calls {@link #cleanup()}. Since the number of Threads should
	 * be low (<<100), this function should resume reasonably quickly.
	 * @throws Exception
	 */
	@Override
	protected void doRun() throws Exception {
		//ThreadAssocStore.getInstance().cleanup();
		this.cleanup();
	}
}
