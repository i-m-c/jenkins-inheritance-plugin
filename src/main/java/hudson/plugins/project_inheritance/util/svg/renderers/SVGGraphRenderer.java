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
package hudson.plugins.project_inheritance.util.svg.renderers;

import java.awt.geom.Rectangle2D;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import hudson.plugins.project_inheritance.util.svg.Graph;
import hudson.plugins.project_inheritance.util.svg.SVGNode;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGPrimitive;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * This is the abstract base class for rendering an SVG graph out of the basic
 * {@link Graph} description.
 * 
 * @author mhschroe

 */
public abstract class SVGGraphRenderer {
	protected final Graph<SVGNode> graph;
	protected final int width;
	protected final int height;
	
	public SVGGraphRenderer(Graph<SVGNode> graph, int width, int height) {
		this.graph = graph;
		this.width = width;
		this.height = height;
	}
	
	
	/**
	 * This method renders the graph as a full SVG 1.1 document. 
	 * <p>
	 * Override this method if you don't want the default SVG 1.1 header
	 * 
	 * @return an SVG 1.1 document containing the graph.
	 */
	public Document render() {
		// Create the SVG document root
		Document doc = null;
		try {
			DocumentBuilderFactory factory =
					DocumentBuilderFactory.newInstance();
			DocumentBuilder builder =
					factory.newDocumentBuilder();
			doc = builder.newDocument();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}
		if (doc == null) {
			return null;
		}
		
		Element root = doc.createElement("svg");
		root.setAttribute("xmlns", "http://www.w3.org/2000/svg");
		root.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
		root.setAttribute("xmlns:ev", "http://www.w3.org/2001/xml-events");
		root.setAttribute("version", "1.1");
		root.setAttribute("baseProfile", "full");
		
		doc.appendChild(root);
		
		Rectangle2D.Double bounds = new Rectangle2D.Double(0, 0, 1, 1);
		
		Collection<SVGPrimitive> children = this.getElements();
		if (children != null) {
			for (SVGPrimitive e : children) {
				root.appendChild(e.render(doc));
				Rectangle2D.Double.union(e.getBounds(), bounds, bounds);
			}
		}
		
		root.setAttribute(
				"width",
				String.format("%dpx", (width > 0) ? width : (int)bounds.width+3)
		);
		root.setAttribute(
				"height",
				String.format("%dpx", (height > 0) ? height : (int)bounds.height+3)
		);
		
		return doc;
	}
	
	/**
	 * This method returns all drawables defined by the current graph.
	 * 
	 * @return the {@link SVGPrimitive}s that should go below the &lt;svg&gt; root.
	 */
	public abstract Collection<SVGPrimitive> getElements();
	
}
