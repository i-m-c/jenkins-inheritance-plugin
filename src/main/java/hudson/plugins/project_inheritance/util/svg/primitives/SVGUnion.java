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

package hudson.plugins.project_inheritance.util.svg.primitives;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This class corresponds to a union of SVGPrimitives.
 *  
 * @author Martin Schröder
 *
 */
public class SVGUnion implements SVGPrimitive {
	//TODO: This should really be a Z-Order respecting QuadTree
	private final LinkedList<SVGPrimitive> elements;
	
	private transient volatile Rectangle2D.Double bounds = null;
	
	public SVGUnion(SVGPrimitive... elements) {
		this.elements = new LinkedList<SVGPrimitive>();
		if (elements != null && elements.length > 0) {
			this.elements.addAll(Arrays.asList(elements));
		}
	}
	
	public SVGUnion(Collection<SVGPrimitive> elements) {
		this.elements = new LinkedList<SVGPrimitive>();
		if (elements != null && !elements.isEmpty()) {
			this.elements.addAll(elements);
		}
	}
	
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("u[");
		
		Iterator<SVGPrimitive> iter = elements.iterator();
		while (iter.hasNext()) {
			b.append(iter.next().toString());
			if (iter.hasNext()) {
				b.append(", ");
			}
		}
		b.append(']');
		return b.toString();
	}
	
	
	public void addElements(SVGPrimitive... elements) {
		if (elements == null || elements.length == 0) {
			return;
		}
		for (SVGPrimitive p : elements) {
			this.elements.add(p);
		}
	}
	
	public void removeElements(SVGPrimitive... elements) {
		if (elements == null || elements.length == 0) {
			return;
		}
		HashSet<SVGPrimitive> set = new HashSet<SVGPrimitive>(Arrays.asList(elements));
		Iterator<SVGPrimitive> iter = this.elements.iterator();
		while (iter.hasNext()) {
			SVGPrimitive p = iter.next();
			if (set.contains(p)) {
				iter.remove();
			}
		}
	}
	
	/**
	 * Returns an unmodifiable collection of the underlying set of elements.
	 */
	public Collection<SVGPrimitive> getElements() {
		return Collections.unmodifiableCollection(elements);
	}
	
	public Element render(Document doc) {
		//Creating a grouping element
		Element g = doc.createElement("g");
		//Now, we render the elements according to their z-order into that element
		for (SVGPrimitive p : elements) {
			g.appendChild(p.render(doc));
		}
		return g;
	}

	public void translate(Double offset) {
		for (SVGPrimitive p : elements) {
			p.translate(offset);
		}
		this.bounds = null;
	}

	public void moveTo(Double pos) {
		if (pos == null) { return; }
		//Get the bounds of the entire union
		Rectangle2D.Double bounds = this.getBounds();
		//Calculate the translation that the entire union needs to undergo
		Point2D.Double offset = new Point2D.Double(
				pos.x - bounds.getMinX(),
				pos.y - bounds.getMinY()
		);
		
		//Translating all subelements by that delta
		for (SVGPrimitive p : elements) {
			p.translate(offset);
		}
		this.bounds = null;
	}
	
	public void rescale(double factor, boolean applyToStyles) {
		for (SVGPrimitive p : elements) {
			p.rescale(factor, applyToStyles);
		}
		this.bounds = null;
	}

	public Rectangle2D.Double getBounds() {
		if (this.bounds == null) {
			Rectangle2D.Double rect = null;
			for (SVGPrimitive p : elements) {
				if (rect == null) {
					rect = p.getBounds();
				} else {
					Rectangle2D.Double newRect = new Rectangle2D.Double();
					Rectangle2D.Double.union(rect, p.getBounds(), newRect);
					rect = newRect;
				}
			}
			this.bounds = rect;
		}
		return this.bounds;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		LinkedList<Point2D.Double> lst = new LinkedList<Point2D.Double>();
		
		for (SVGPrimitive p : elements) {
			List<Point2D.Double> plst = p.getAttachmentPoints();
			if (plst != null) {
				lst.addAll(p.getAttachmentPoints());
			}
		}
		
		return lst;
	}
}
