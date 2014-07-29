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

package hudson.plugins.project_inheritance.util.svg.renderers;

import hudson.plugins.project_inheritance.util.svg.Graph;
import hudson.plugins.project_inheritance.util.svg.SVGNode;
import hudson.plugins.project_inheritance.util.svg.prefabs.SVGClassBox;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGArrow;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGPrimitive;
import hudson.plugins.project_inheritance.util.svg.properties.ArrowProperty;
import hudson.plugins.project_inheritance.util.svg.properties.ColorProperty;
import hudson.plugins.project_inheritance.util.svg.properties.TextProperty;
import hudson.plugins.project_inheritance.util.svg.properties.TextProperty.STYLE;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import com.google.common.collect.HashBiMap;


/**
 * This class renders an SVG-Graph as if it were a simple tree.
 * <p>
 * That means it draws Nodes in layers. The first layer is filled with all
 * leaves of the graph (or if there are none; the elements with the least
 * amount of outbound connections).
 * <p>
 * The subsequent layers are filled with the direct children of each element.
 * As soon as all the layers are filled, the final graph is drawn with the
 * correct spacing between the elements in the layer, so that each parent
 * is centered above all its children in the layers below.
 * 
 * @author Martin Schroeder
 *
 */
public class SVGTreeRenderer extends SVGGraphRenderer {
	private static Color[] colors = {
		new Color(0x000000), //Black
		new Color(0xB05F3C), //Brown
		new Color(0xFF4848), //Pastel red
		new Color(0x1FCB4A), //Dark green
		new Color(0x9669FE), //Violet
		new Color(0x62A9FF), //Steel Blue
		new Color(0xFFB428), //Orange
		new Color(0xDFE32D)  //Dirty Yellow
	};
	private static Color getColor(int num) {
		return colors[Math.abs(num) % colors.length];
	}
	
	private final double deltaX = 20;
	private final double deltaY = 50;
	private final double marginX = 10;
	private final double marginY = 10;
	
	public SVGTreeRenderer(Graph<SVGNode> graph, int width, int height) {
		super(graph, width, height);
	}

	@Override
	public Collection<SVGPrimitive> getElements() {
		LinkedList<SVGPrimitive> out = new LinkedList<SVGPrimitive>();
		
		if (this.graph.getNumNodes() <= 0) {
			return out;
		}
		
		/* To generate a suitable tree-like graph; the following is done
		 * 
		 * 1.) Generate a forest of left-spanning trees (LST) from the graph.
		 * 2.) Each leaf (none or minimal inbound edges) is a root of a tree
		 * 3.) Order the tree nodes into layers depending on their distance to
		 *     their root element.
		 * 4.) Create SVG drawables for each node where the x-coord is
		 *     determined by their position in their layer and their y-coord
		 *     is determined by the sum of heights of the previous layers.
		 *     Leave space between each layer (y-diff) and each node (x-diff).
		 * 5.) Now repeatedly iterate through all nodes and ensure that each
		 *     parent's center on the x-asis is over the x-axis middle of all
		 *     its children (includes children's children).
		 *
		 *     If this is not the case do the following:
		 *
		 *     A) If the parent is to the left of the middle; move the parent
		 *        to that middle and move all its siblings on the right along
		 *        with it
		 *     B) If the parent is to the right of the middle; move its
		 *        children right by the detected difference.
		 *        Apply the same move to the children's children.
		 *        
		 * 6.) Add all edges from the original tree as SVGArrows between the
		 *     drawn nodes.
		 */
		
		//Fetch a suitable spanning tree
		Graph<SVGNode> span = this.graph.getSpanningTree();
		
		//Use it to generate the drawables and add them to a mirrored STree
		Graph<SVGPrimitive> spanDraw = new Graph<SVGPrimitive>();
		
		//A bimap for node/drawable lookup
		HashBiMap<SVGNode, SVGPrimitive> nodeLookup = HashBiMap.create();
		
		for (SVGNode node : span.getNodes()) {
			//Create a drawable for that node
			SVGPrimitive drawable = new SVGClassBox(
					new Point2D.Double(0,0), //Filled in later
					new TextProperty(
							node.getSVGLabel(), null, STYLE.BOLD, "Consolas", 16, 5
					),
					node.getSVGLabelLink(),
					new TextProperty(
							node.getSVGDetail(), null, STYLE.PLAIN, "Consolas", 16, 5
					),
					new ColorProperty(
							getColor(span.getEdgesFor(node).size()), this.width, 1.0, null
					),
					new Point2D.Double(10, -1), //Restrict min-width to 10px
					new Point2D.Double(384, -1)  //Restrict max-width to 512px
			);
			//Add the primitive to the tree; edges are filled in later
			spanDraw.addNode(drawable);
			nodeLookup.put(node, drawable);
		}
		//Transfer the egdes
		for (SVGNode node : span.getNodes()) {
			SVGPrimitive drawable = nodeLookup.get(node);
			for (SVGNode edge : span.getEdgesFor(node)) {
				spanDraw.addEdges(drawable, nodeLookup.get(edge));
			}
		}
		
		
		//Start from the minimum inbound nodes and add their childs in layers
		LinkedList<SVGPrimitive> open = new LinkedList<SVGPrimitive>(
				spanDraw.getMinimalInboundEdgeNodes(null)
		);
		LinkedList<SVGPrimitive> next = new LinkedList<SVGPrimitive>();
		
		//We need to remember the right-most siblings of each node
		HashMap<SVGPrimitive, LinkedList<SVGPrimitive>> siblings =
				new HashMap<SVGPrimitive, LinkedList<SVGPrimitive>>();
		
		double xOffset = marginX;
		double yOffset = marginY;
		double maxHeight = 0;
		while (!open.isEmpty() || !next.isEmpty()) {
			if (open.isEmpty()) {
				xOffset = marginX;
				yOffset += maxHeight + this.deltaY;
				maxHeight = 0;
				//Swap open and next lists
				LinkedList<SVGPrimitive> tmp = open;
				open = next;
				next = tmp;
			}
			SVGPrimitive node = open.pop();
			node.moveTo(new Point2D.Double(xOffset, yOffset));
			Rectangle2D.Double bounds = node.getBounds();
			if (bounds != null) {
				xOffset += bounds.width + this.deltaX;
				maxHeight = Math.max(bounds.height, maxHeight);
			}
			//Add the list of right-hand siblings to this node
			siblings.put(node, new LinkedList<SVGPrimitive>(open));
			
			//Add the children of the node to the next layer's todo-list
			next.addAll(spanDraw.getEdgesFor(node));
		}
		
		
		//Center all drawables above their children
		boolean hasMoved = true;
		while (hasMoved) {
			hasMoved = false;
			for (SVGPrimitive node : spanDraw.getNodes()) {
				//Fetch the bounds of the current node
				Rectangle2D.Double nodeBounds = node.getBounds();
				if (nodeBounds == null) {
					//Node is not visible
					continue;
				}
				//Calculate the union bound of all direct children
				Rectangle2D.Double childBounds = null;
				for (SVGPrimitive child : spanDraw.getEdgesFor(node)) {
					Rectangle2D.Double childBound = child.getBounds();
					if (childBound == null) { continue; }
					if (childBounds == null) {
						childBounds = (Rectangle2D.Double) childBound.clone();
					} else {
						Rectangle2D.Double.union(childBounds, childBound, childBounds);
					}
				}
				if (childBounds == null) {
					//No children or all of them are non-visible
					continue;
				}
				double xDiff = nodeBounds.getCenterX() - childBounds.getCenterX();
				if (Math.abs(xDiff) < 1) {
					//We don't care about a delta of less than one pixel
					continue;
				}
				Point2D.Double delta = new Point2D.Double(Math.abs(xDiff), 0);
				if (xDiff > 0) {
					//We move all children so that they're centered below the node
					//Do note that we also need to move the siblings of all children
					HashSet<SVGPrimitive> visitedNodes =
							new HashSet<SVGPrimitive>(); 
					HashSet<SVGPrimitive> openNodes =
							new HashSet<SVGPrimitive>(
									spanDraw.getEdgesFor(node)
							);
					while (!openNodes.isEmpty()) {
						SVGPrimitive subNode = openNodes.iterator().next();
						openNodes.remove(subNode);
						if (visitedNodes.contains(subNode)) {
							continue;
						}
						visitedNodes.add(subNode);
						subNode.translate(delta);
						//Adding all children of that subNode
						openNodes.addAll(spanDraw.getEdgesFor(subNode));
						//Adding all siblings of that subNode
						openNodes.addAll(siblings.get(subNode));
					}
				} else {
					//We move the node and all its siblings to the right
					node.translate(delta);
					for (SVGPrimitive sibling : siblings.get(node)) {
						sibling.translate(delta);
					}
				}
				hasMoved = true;
			}
		}
		
		
		//Add all generated boxes to the out-list
		out.addAll(spanDraw.getNodes());
		
		//Add ALL edges from the original graph as arrows; not just from the STree
		//They are prepended to be BEHIND the boxes
		for (SVGNode node : this.graph.getNodes()) {
			SVGPrimitive dNode = nodeLookup.get(node);
			
			for (SVGNode edge : this.graph.getEdgesFor(node)) {
				SVGPrimitive dEdge = nodeLookup.get(edge);
				
				SVGArrow arrow = SVGArrow.createConnection(
						dEdge, dNode,
						new ColorProperty(
								Color.BLACK, 2.0, 1.0, null
						),
						new ArrowProperty(
								new ColorProperty(
										Color.BLACK, 2.0, 1.0, null
								),
								true, 12, 35
						)
				);
				
				out.addFirst(arrow);
			}
		}
		
		return out;
	}

}
