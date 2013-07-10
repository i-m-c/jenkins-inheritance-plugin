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

import hudson.plugins.project_inheritance.util.svg.properties.ArrowProperty;
import hudson.plugins.project_inheritance.util.svg.properties.ColorProperty;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SVGArrow implements SVGPrimitive {
	protected final SVGLine body;
	protected ArrowProperty head; 
	
	private transient Rectangle2D.Double bounds = null;
	
	/**
	 * Creates an SVG arrow with an optional single head at the end point of
	 * the given line.
	 * 
	 * @param body the line the arrow is based on; may not be null.
	 * @param head the properties of the arrow head; may be null in which case
	 * only a regular line will be drawn.
	 */
	public SVGArrow(SVGLine body, ArrowProperty head) {
		if (body == null) {
			throw new IllegalArgumentException(
					"You may not construct an arrow without a body"
			);
		}
		this.body = body;
		this.head = head;
	}

	public String toString() {
		return String.format(
				"arr[%.2f, %.2f, %.2f, %.2f]",
				body.start.x, body.start.y,
				body.end.x, body.end.y
		);
	}
	
	
	public Element render(Document doc) {
		//Create a group
		Element group = doc.createElement("g");
		//Add the arrow body
		group.appendChild(body.render(doc));
		//Add the head; if necessary
		if (head != null) {
			Point2D.Double[] pts = head.calcArrowPoints(body.start, body.end);
			Element hElem = doc.createElement("path");
			hElem.setAttribute(
					"d",
					String.format(
							"M %f %f L %f %f L %f %f %s %f %f Z",
							pts[0].x, pts[0].y,
							body.end.x, body.end.y,
							pts[1].x, pts[1].y,
							(head.fillHead) ? "L" : "M",
							pts[0].x, pts[0].y
					)
			);
			hElem.setAttribute(
					"fill",
					(head.fillHead) ? head.stroke.getRGB24Hex() : "none"
			);
			hElem.setAttribute(
					"stroke", head.stroke.getRGB24Hex()
			);
			hElem.setAttribute(
					"stroke-width", Double.toString(head.stroke.width)
			);
			
			group.appendChild(hElem);
		}
		
		return group;
	}

	public void translate(Point2D.Double offset) {
		body.translate(offset);
		bounds = null;
	}
	
	public void moveTo(Point2D.Double pos) {
		body.moveTo(pos);
	}
	

	public void rescale(double factor, boolean applyToStyles) {
		body.rescale(factor, applyToStyles);
		
		if (applyToStyles) {
			head.stroke.width *= factor;
		}
		head.headLen *= factor;
		
		bounds = null;
	}

	public Rectangle2D.Double getBounds() {
		if (bounds == null) {
			bounds = body.getBounds();
			Point2D.Double[] pts = head.calcArrowPoints(body.start, body.end);
			for (Point2D.Double pt : pts) {
				if (bounds == null) {
					bounds = new Rectangle2D.Double(pt.x, pt.y, 0, 0);
				} else {
					bounds.add(pt);
				}
			}
		}
		return bounds;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		return body.getAttachmentPoints();
	}
	
	
	
	// === STATIC HELPER FUNCTIONS ===
	
	public static SVGArrow createConnection(
			SVGPrimitive start, SVGPrimitive end,
			ColorProperty stroke, ArrowProperty head) {
		SVGLine connectorBody = SVGLine.createConnection(start, end, stroke);
		if (connectorBody == null) {
			return null;
		}
		return new SVGArrow(connectorBody, head);
	}
}
