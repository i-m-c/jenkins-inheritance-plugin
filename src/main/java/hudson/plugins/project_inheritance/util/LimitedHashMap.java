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
