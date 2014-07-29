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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jenkins.model.Jenkins;

import com.google.common.collect.Sets;
import com.thoughtworks.xstream.XStreamException;

/**
 * This class allows you to version-control almost arbitrary objects.
 * 
 * TODO: This class is in heavy need of a read/write lock
 * TODO: Improve the JavaDoc of this class.
 * 
 * @author mhschroe
 *
 */
public class VersionedObjectStore implements Serializable {
	private static final long serialVersionUID = 4783406234088486875L;

	/**
	 * This integer denotes the current version of the save formatting.
	 * It is used to convert versions from older revisions to newer ones.
	 */
	private static final int currentFormatVersion = 1;
	private static final String formatVersionTag = "INTERNAL_FORMAT_VERSION";
	
	private static final Logger log = Logger.getLogger(
			VersionedObjectStore.class.toString()
	);
	
	
	public static class Version implements Serializable, Comparable<Object> {
		private static final long serialVersionUID = -5953602045057843995L;
		
		/**
		 * This field is the unique ID of this class. It is the only field
		 * that is considered for hashing, comparing and equality checks.
		 */
		public final Long id;
		/**
		 * This flag denotes whether this version is considered stable or not.
		 * This can have varying meanings, depending on the actual
		 * use of this class.
		 */
		private boolean stable;
		/**
		 * This field may contain an arbitrary description of this version.
		 */
		private String description;
		/**
		 * This field stores the creation date of this particular version.
		 * Do note that two Versions with the same ID but differing timestamps
		 * are still treated as equal!
		 */
		public final long timestamp;
		
		/**
		 * The name of the user that created this version. Might be null or empty.
		 * Do note that {@link #getUsername()} will never return null; only
		 * empty values.
		 * 
		 * @since 1.4.7
		 */
		private String username;
		
		
		public Version(Long id) {
			if (id == null || id < 0) {
				throw new IllegalArgumentException(
						"You may not assign null or negative version ids"
				);
			}
			this.id = id;
			this.stable = false;
			this.timestamp = new Date().getTime();
		}
		
		
		public boolean getStability() {
			return this.stable;
		}
		
		public void setStability(boolean stable) {
			this.stable = stable;
		}
		
		public String getDescription() {
			return this.description;
		}
		
		public void setDescription(String description) {
			this.description = description;
		}
		
		public String getLocalTimestamp() {
			Date d = new Date(this.timestamp);
			return DateFormat.getInstance().format(d);
		}
		
		public String getStabilityString() {
			return ((Boolean)this.stable).toString();
		}
		
		/**
		 * Returns the name of the user that created this version.
		 * 
		 * @return a string containing a username. May be empty, but never null.
		 */
		public String getUsername() {
			if (this.username == null) {
				return "";
			} else {
				return this.username;
			}
		}
		
		public void setUsername(String user) {
			this.username = user;
		}
		
		
		@Override
		public int hashCode() {
			return id.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Version) {
				return this.id.equals(((Version)obj).id);
			} else if (obj instanceof Long) {
				return id.equals((Long)obj);
			}
			return false;
		}
		
		public int compareTo(Object obj) {
			if (obj instanceof Version) {
				return id.compareTo(((Version)obj).id);
			} else if (obj instanceof Long) {
				return id.compareTo((Long)obj);
			}
			return 0;
		}
	}
	
	/**
	 * This field stores the mapping of versions to its associated key/value map.
	 * <p>
	 * Do note that the outer field is a TreeMap, because sorted order is more
	 * important than O(1) access time on version numbers. For the key/value
	 * pairs of object labels and object values, sorting is unimportant but
	 * best access speed is.
	 * <p>
	 * Thus, read/writes to a single object value are O(log(n)) instead of O(1)
	 * in the general case, but the last version can always be accessed in O(1).
	 *  
	 */
	private final TreeMap<Version, HashMap<String, Object>> store;
	
	
	public VersionedObjectStore() {
		this.store = new TreeMap<Version, HashMap<String,Object>>();
	}
	
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(super.toString());
		b.append(" [");
		Iterator<Version> iter = this.store.keySet().iterator();
		while (iter.hasNext()) {
			Version v = iter.next();
			b.append(v.id);
			if (iter.hasNext()) {
				b.append(", ");
			}
		}
		
		b.append(" ]");
		return b.toString();
	}
	
	public String toXML() {
		return Jenkins.XSTREAM2.toXML(this);
	}
	
	public int size() {
		return this.store.size();
	}
	
	
	/**
	 * Calling this function causes the store to be saved to disk.
	 * 
	 * Not making the save automatic allows you to make bulk-changes and only
	 * dump them to disk once finished.
	 * 
	 * Do note that while the function itself is synchronized, at the moment
	 * nothing prevents others to change the underlying data fields during save.
	 * 
	 * TODO: Fix this, so that saves are truly atomic.
	 * 
	 * Do note that this function fails silently in case the output file is not
	 * writable.  It will log an error, but do nothing beyond that
	 * 
	 * @param file the file to save the data to. The data is dumped
	 * as GZIP-compressed XML.
	 * @throws IOException 
	 */
	public synchronized void save(File file) throws IOException {
		if (file == null) {
			//Return silently, as the user explicitly wanted a null-save
			return;
		}
		//Create a temporary file for the output file
		File tmpFile = File.createTempFile("atomic", null, file.getParentFile());
		if (tmpFile == null) {
			throw new IOException("Could not create atomic file for saving versions");
		}
		GZIPOutputStream gzos = null;
		try {
			//Preparing the nested streams
			gzos = new GZIPOutputStream(new FileOutputStream(tmpFile));
			//Dumping the XML stream
			Jenkins.XSTREAM2.toXMLUTF8(this, gzos);
			//Closing the file stream; no need to flush as that happened above
			gzos.close();
			if (file.exists() && !file.delete()) {
				tmpFile.delete();
				throw new IOException("Unable to delete " + file);
			}
			tmpFile.renameTo(file);
		} catch (Exception ex) {
			log.warning(
					"Saving versioned object store failed due to exception: " +
					ex.toString()
			);
		} finally {
			if (gzos != null) { gzos.close(); }
			tmpFile.delete();
		}
	}
	
	/**
	 * Loads a {@link VersionedObjectStore} from the given file.
	 * @param file the file to load data from. Must be XML -- either raw or
	 * GZIP compressed.
	 * 
	 * @return a newly created VersionedObjectStore.
	 * @throws IOException in case the file can't be read.
	 * @throws IllegalArgumentException in case the file contains invalid data.
	 */
	public static VersionedObjectStore load(File file)
			throws IllegalArgumentException, IOException, XStreamException {
		if (!file.exists()) {
			throw new IOException("No such file: " + file.toString());
		}
		
		//Checking if the file is GZ compressed
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		boolean isGZ = false;
		try {
			if (raf.length() <= 2) {
				throw new IOException("File too short: " + file.toString());
			}
			int magic = GZIPInputStream.GZIP_MAGIC;
			byte[] bArr = new byte[2]; raf.read(bArr);
			byte m1 = (byte) (magic >> 8);
			byte m0 = (byte) magic;
			isGZ = (bArr[0] == m0 && bArr[1] == m1);
		} finally {
			if (raf != null) { raf.close(); }
		}
		
		InputStream is = new FileInputStream(file);
		try {
			if (isGZ) {
				is = new GZIPInputStream(is);
			}
			Object obj = Jenkins.XSTREAM2.fromXML(is);
			//Verifying that the object is a VOS
			if (obj instanceof VersionedObjectStore) {
				return (VersionedObjectStore) obj;
			} else {
				throw new IllegalArgumentException(
						"File does not describe a VersionedObjectStore"
				);
			}
		} finally {
			if (is != null) { is.close(); }
		}
	}
	
	
	public boolean areIdentical(Version v1, Version v2) {
		Map<String,Object> map1 = this.getValueMapFor(v1.id);
		Map<String,Object> map2 = this.getValueMapFor(v2.id);
		
		ByteArrayOutputStream os1, os2;
		try {
			//First, we check if the number of keys matches
			if (map1.size() != map2.size()) {
				return false;
			}
			//Then, we verify if all keys are present in both
			Set<String> union = Sets.union(map1.keySet(), map2.keySet());
			if (union.size() != map1.size()) {
				return false;
			}
			//Then, we verify that the values in them is identical
			for (String key : union) {
				os1 = new ByteArrayOutputStream();
				os2 = new ByteArrayOutputStream();
				
				Object o1 = map1.get(key);
				Object o2 = map2.get(key);
				
				if (o1 == null && o2 == null) {
					continue;
				} else if (o1 == null && o2 != null) {
					return false;
				} else if (o2 == null && o1 != null) {
					return false;
				}
				try {
					Jenkins.XSTREAM2.toXMLUTF8(o1, os1);
					Jenkins.XSTREAM2.toXMLUTF8(o2, os2);
				} catch (IOException ex) {
					return false;
				}
				if (os1.toString().equals(os2.toString()) == false) {
					return false;
				}
			}
		} catch (XStreamException ex) {
			return false;
		}
		return true;
	}
	
	/**
	 * This retrieves the actual last versioned object.
	 * Use this in favour of {@link #getLatestVersionID()} if you actually
	 * want to alter the internal fields of that Version object.
	 */
	public Version getLatestVersion() {
		if (this.store.isEmpty()) {
			return null;
		} else {
			return this.store.lastKey();
		}
	}
	
	public Version getVersion(Long id) {
		if (id == null) { return null; }
		Version v = this.store.ceilingKey(new Version(id));
		if (v != null && v.id.equals(id)) {
			return v;
		} else {
			return null;
		}
	}

	public Version getLatestStable() {
		//TODO: Buffer the latest stable version in some way
		if (this.store == null || this.store.isEmpty()) {
			return null;
		}
		//Trying to find the latest version marked as stable
		NavigableSet<Version> descSet = this.store.descendingKeySet();
		for (Version v : descSet) {
			if (v.stable) {
				return v;
			}
		}
		//If none is found, just return the latest one
		return this.store.lastKey();
	}
	
	/**
	 * gets all the more recent version ids since the sinceVersionId
	 * @param sinceVersionId
	 * @return
	 */
	public LinkedList<Version> getAllVersionsSince(Long sinceVersionId) {
		LinkedList<Version> allVersionsSince = new LinkedList<Version>();
		//TODO: Buffer the latest versions in some way
		if (this.store == null || this.store.isEmpty()) {
			return null;
		}
		//Trying to find the latest version marked as stable
		NavigableSet<Version> descSet = this.store.descendingKeySet();
		for (Version v : descSet) {
			if (v.id <= sinceVersionId) {
				break;
			} else {
				allVersionsSince.add(v);
			}
		}
		//If none is found, just return the latest one
		return allVersionsSince;
	}

	public Version getNearestTo(Long timestamp) {
		Version best = null;
		for (Version v : this.store.keySet()) {
			long diff = timestamp - v.timestamp;
			if (diff >= 0) {
				best = v;
			} else {
				break;
			}
		}
		return best;
	}
	
	/**
	 * Returns a new set of all versions, sorted by their ID in ascending order.
	 * 
	 * @return a sorted set of versions in ascending order.
	 */
	public SortedSet<Version> getAllVersions() {
		if (this.store.isEmpty()) {
			return new TreeSet<Version>();
		} else {
			return new TreeSet<Version>(this.store.keySet());
		}
	}
	
	public Collection<HashMap<String, Object>> getAllValueMaps() {
		return this.store.values();
	}
	
	/**
	 * This method creates the next version and copies all key/value object
	 * entries from the previous one.
	 * @return
	 */
	public Version createNextVersion() {
		if (this.store.isEmpty()) {
			return this.createNextVersionAsEmpty();
		}
		Version oldVer = this.getLatestVersion();
		HashMap<String, Object> oldMap = store.get(oldVer);
		
		Version newVer = new Version(oldVer.id + 1);
		HashMap<String, Object> newMap = new HashMap<String, Object>(oldMap);
		
		this.store.put(newVer, newMap);
		
		//Saving the current metadata version
		this.setObjectFor(newVer, formatVersionTag, currentFormatVersion);
				
		return newVer;
	}
	
	
	/**
	 * This function removes the given version from the internal storage, if
	 * it is the last version that is present.
	 * <p>
	 * You should <b>not</b> expose this function to the outside. <b>Ever!</b>
	 * 
	 * @param v the version to commit
	 */
	public void undoVersion(Version v) {
		Version latest = this.getLatestVersion();
		if (latest == null || v == null) {
			return;
		}
		if (v.id == latest.id) {
			this.store.remove(latest);
		}
	}
	
	
	/**
	 * Creates the next version with an empty key/value object mapping.
	 * @return
	 */
	public Version createNextVersionAsEmpty() {
		Version v = this.getLatestVersion();
		if (v == null) {
			v = new Version(1L);
		} else {
			v = new Version(v.id + 1);
		}
		this.store.put(v, new HashMap<String, Object>());
		
		//Saving the current metadata version
		this.setObjectFor(v, formatVersionTag, currentFormatVersion);
		
		return v;
	}

	
	public Version createNextVersionWithMapping(Map<String, Object> map) {
		Version v = this.getLatestVersion();
		if (v == null) {
			v = new Version(1L);
		} else {
			v = new Version(v.id + 1);
		}
		this.store.put(v, new HashMap<String, Object>(map));
		
		//Saving the current metadata version
		this.setObjectFor(v, formatVersionTag, currentFormatVersion);
		
		return v;
	}

	public Object getObject(Long id, String key) {
		if (id == null || key == null) { return null; }
		Version v = new Version(id);
		HashMap<String, Object> map = this.store.get(v);
		if (map == null) { return null; }
		return map.get(key);
	}
	
	public Object getObject(Version version, String key) {
		if (version == null || key == null) { return null; }
		return this.getObject(version.id, key);
	}
	
	/**
	 * This returns an immutable map that wraps around the key/value object map
	 * associated with the given version.
	 * <p>
	 * Use this method in favour of {@link #getObject(Long, String)} to avoid
	 * the O(log(n)) cost of retrieving the given version if you access more
	 * than one stored object.
	 * 
	 * @param id the numerical ID of the version to retrieve
	 * @return an immutable map of key/value object pairs
	 */
	public Map<String, Object> getValueMapFor(Long id) {
		if (id == null) { return null; }
		Version v = this.getVersion(id);
		if (v == null) { return null; }
		Map<String, Object> map = this.store.get(v);
		if (map == null) {
			return null;
		}
		return Collections.unmodifiableMap(map);
	}


	public boolean setObjectFor(Version v, String key, Object value) {
		if (key == null || v == null) {
			return false;
		}
		HashMap<String, Object> map = this.store.get(v);
		if (map == null) {
			//Something horribly went wrong
			throw new IllegalStateException(
					"Found a version that is not associated with a map"
			);
		}
		map.put(key, value);
		return true;
	}
}
