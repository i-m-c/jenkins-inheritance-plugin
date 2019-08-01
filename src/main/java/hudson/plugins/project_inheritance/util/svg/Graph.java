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
package hudson.plugins.project_inheritance.util.svg;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

/**
 * This class implements a <i>very</i> simple directed graph.
 * 
 * @author mhschroe
 * @param <T> the type of values in each graph node
 *
 */
public class Graph<T> {
	private final HashSet<T> nodes = new LinkedHashSet<T>();
	
	private final HashMap<T, HashSet<T>> edges =
			new LinkedHashMap<T, HashSet<T>>();
	
	public Graph() {
		//Nothing else to do
	}
	
	
	public void addNode(T node, T... neighbours) {
		if (node == null) { return; }
		nodes.add(node);
		if (neighbours == null) { return; }
		
		for (T n : neighbours) {
			if (n == null) { continue; }
			nodes.add(n);
			//Adding the directed edge from node to n
			HashSet<T> eSet = edges.get(node);
			if (eSet == null) {
				eSet = new LinkedHashSet<T>();
			}
			eSet.add(n);
			edges.put(node, eSet);
		}
	}
	
	protected void addSingleNode(T node) {
		if (node == null) { return; }
		this.nodes.add(node);
	}
	
	protected void addSingleEdge(T start, T end) {
		if (start == null || end == null) { return; }
		this.nodes.add(start);
		
		HashSet<T> eSet = edges.get(start);
		if (eSet == null) {
			eSet = new LinkedHashSet<T>();
		}
		eSet.add(end);
		edges.put(start, eSet);
	}
	
	public Set<T> getNodes() {
		return Collections.unmodifiableSet(nodes);
	}
	
	
	/**
	 * Simple name-wrapper for {@link #addNode(Object, Object...)}, as the
	 * code for adding a node or adding edges is identical.
	 * 
	 * @param node the node
	 * @param neighbours its children
	 */
	public void addEdges(T node, T... neighbours) {
		this.addNode(node, neighbours);
	}
	
	public Set<T> getEdgesFor(T node) {
		if (node == null) {
			return new LinkedHashSet<T>();
		}
		if (!nodes.contains(node)) {
			return new LinkedHashSet<T>();
		}
		HashSet<T> eSet = edges.get(node);
		if (eSet == null) {
			return new HashSet<T>();
		} else {
			return eSet;
		}
	}
	
	public void removeNode(T node) {
		nodes.remove(node);
		
		edges.remove(node);
		for (Entry<T, HashSet<T>> entry : edges.entrySet()) {
			HashSet<T> eSet = entry.getValue();
			if (eSet != null) {
				eSet.remove(node);
			}
		}
	}
	
	public void removeEdge(T start, T end) {
		HashSet<T> eSet = edges.get(start);
		if (eSet != null) {
			eSet.remove(end);
		}
	}

	public int getNumNodes() {
		return nodes.size();
	}
	

	/**
	 * This function returns terminal leaf nodes. That is, it returns all those
	 * nodes that do not have any outgoing edges.
	 * 
	 * @param ignored a set of nodes to ignore for the purpose of identifying
	 * leaves. That means those nodes will not be returned, even if they are
	 * leaves and nodes that only have edges to them are considered leaves.
	 * 
	 * @return a set of leaf nodes.
	 */
	public Set<T> getLeaves(Set<T> ignored) {
		HashSet<T> set = new HashSet<T>();
		for (Entry<T, HashSet<T>> entry : edges.entrySet()) {
			T start = entry.getKey();
			HashSet<T> ends = entry.getValue();
			if (ignored.contains(start)) {
				continue;
			}
			if (ends == null || ends.isEmpty()) {
				set.add(start);
			}
			boolean hasValidEdges = false;
			for (T end : ends) {
				if (!ignored.contains(end)) {
					hasValidEdges = true;
					break;
				}
			}
			if (!hasValidEdges) {
				set.add(start);
			}
		}
		return set;
	}
	
	/**
	 * This function returns all those nodes that have the current minimum
	 * of outbound edges.
	 * 
	 * @param ignored a set of nodes to ignore for the purpose of counting edges.
	 * 
	 * @return a set of nodes with minimal number of outbound edges. Will not
	 * contain any node from <code>ignored</code>.
	 */
	public Set<T> getMinimalOutboundEdgeNodes(Set<T> ignored) {
		HashSet<T> out = new LinkedHashSet<T>();
		
		int minEdges = Integer.MAX_VALUE;
		for (T node : this.nodes) {
			if (ignored != null && ignored.contains(node)) {
				continue;
			}
			Set<T> edges = this.getEdgesFor(node);
			int edgeCnt = 0;
			for (T edge : edges) {
				if (ignored == null || !ignored.contains(edge)) {
					edgeCnt += 1;
				}
			}
			if (edgeCnt < minEdges) {
				minEdges = edgeCnt;
				out.clear();
				out.add(node);
			} else if (edgeCnt == minEdges) {
				out.add(node);
			}
		}
		
		return out;
	}

	/**
	 * This function returns all those nodes that have the current minimum
	 * number of inbound edges.
	 * 
	 * @param ignored a set of nodes to ignore for the purpose of counting edges.
	 * 
	 * @return a set of nodes with minimal number of inbound edges. Will not
	 * contain any node from <code>ignored</code>.
	 */
	public Set<T> getMinimalInboundEdgeNodes(Set<T> ignored) {
		HashMap<T, Integer> bucketLookup =
				new HashMap<T, Integer>();
		Vector<Set<T>> buckets =
				new Vector<Set<T>>();
		
		buckets.add(new LinkedHashSet<T>());
		for (T node : nodes) {
			if (ignored == null || !ignored.contains(node)) {
				bucketLookup.put(node, 0);
				buckets.get(0).add(node);
			}
		}
		
		int minBucket = 0;
		for (T node : nodes) {
			if (ignored != null && ignored.contains(node)) {
				continue;
			}
			HashSet<T> edgePartners = edges.get(node);
			if (edgePartners == null) {
				continue;
			}
			for (T edge : edgePartners) {
				if (ignored != null && ignored.contains(edge)) {
					continue;
				}
				Integer bucket = bucketLookup.get(edge);
				buckets.get(bucket).remove(edge);
				if (bucket+1 >= buckets.size()) {
					buckets.add(new LinkedHashSet<T>());
				}
				buckets.get(bucket+1).add(edge);
				bucketLookup.put(edge, bucket+1);
				
				if (bucket == minBucket && buckets.get(bucket).isEmpty()) {
					minBucket++;
				}
			}
		}
		
		return buckets.get(minBucket);
	}
	
	public Graph<T> getSpanningTree() {
		Graph<T> out = new Graph<T>();
		
		LinkedList<T> open = new LinkedList<T>(
				this.getMinimalInboundEdgeNodes(null)
		);
		HashSet<T> visited = new LinkedHashSet<T>();
		
		while (!open.isEmpty()) {
			T node = open.pop();
			if (visited.contains(node)) {
				continue;
			} else {
				visited.add(node);
			}
			out.addSingleNode(node);
			for (T child : this.getEdgesFor(node)) {
				if (child == null || visited.contains(child)) {
					continue;
				}
				out.addSingleEdge(node, child);
				open.push(child);
			}
		}
		
		return out;
	}
}
