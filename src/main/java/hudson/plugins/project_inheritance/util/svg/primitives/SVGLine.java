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
package hudson.plugins.project_inheritance.util.svg.primitives;

import hudson.plugins.project_inheritance.util.svg.properties.ColorProperty;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SVGLine implements SVGPrimitive {
	protected final Point2D.Double start;
	protected final Point2D.Double end;
	protected final ColorProperty stroke;
	
	private volatile transient Rectangle2D.Double bounds = null;
	
	public SVGLine(Point2D.Double start, Point2D.Double end, ColorProperty stroke) {
		this.start = start;
		this.end = end;
		this.stroke = stroke;
	}
	
	public String toString() {
		return String.format(
				"lin[%.2f, %.2f, %.2f, %.2f]",
				start.x, start.y,
				end.x, end.y
		);
	}
	
	public Element render(Document doc) {
		Element e = doc.createElement("line");
		
		if (stroke != null) {
			String style = String.format(
					"stroke:#%06x;stroke-opacity:%f;stroke-width:%f",
					stroke.getRGB24(), stroke.opacity, stroke.width
			);
			e.setAttribute("style", style);
		}
		
		e.setAttribute("x1", Double.toString(start.x));
		e.setAttribute("y1", Double.toString(start.y));
		e.setAttribute("x2", Double.toString(end.x));
		e.setAttribute("y2", Double.toString(end.y));
		
		return e;
	}

	public void translate(Point2D.Double offset) {
		if (offset == null) { return; }
		
		start.setLocation(start.x + offset.x, start.y + offset.y);
		end.setLocation(end.x + offset.x, end.y + offset.y);
		this.bounds = null;
	}
	
	public void moveTo(Point2D.Double pos) {
		if (pos == null) { return; }
		
		double xDiff = end.x - start.x;
		double yDiff = end.y - start.y;
		start.setLocation(pos.x, pos.y);
		start.setLocation(pos.x + xDiff, pos.y + yDiff);
		this.bounds = null;
	}

	public void rescale(double factor, boolean applyToStyles) {
		double[] newDist = {
				(end.x - start.x) * factor,
				(end.y - start.y)  * factor
		};
		end.x = start.x + newDist[0];
		end.y = start.y + newDist[1];
		
		if (applyToStyles) {
			stroke.width *= factor;
		}
		
		this.bounds = null;
	}

	public Rectangle2D.Double getBounds() {
		if (bounds == null) {
			bounds = new Rectangle2D.Double(
					Math.min(start.x, end.x),
					Math.min(start.y, end.y),
					Math.abs(start.x - end.x),
					Math.abs(start.y -end.y)
			);
		}
		return bounds;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		LinkedList<Point2D.Double> lst = new LinkedList<Point2D.Double>();
		lst.add(start);
		lst.add(end);
		return lst;
	}

	
	// === STATIC HELPER FUNCTIONS ===
	
	public static SVGLine createConnection(
			SVGPrimitive start, SVGPrimitive end, ColorProperty stroke) {
		//Fetching the attachment points of both primitives
		List<Point2D.Double> startPts = start.getAttachmentPoints();
		List<Point2D.Double> endPts = end.getAttachmentPoints();
		
		if (startPts == null || startPts.isEmpty() || endPts == null || endPts.isEmpty()) {
			return null;
		}
		
		/* Find the closest bichromatic pair
		 * 
		 * TODO: Use a better algorithm than naive O(m*n) here!
		 * 
		 * Reason: This currently uses the naive algorithm that runs in O(m*n)
		 * which is capable of quickly slowing everything to a crawls. As such,
		 * beyond a certain size; a faster algorithm like the heuristic
		 * "Randomized Sieve Algorithm" that runs in O(m+n) should be used.
		 */
		
		Point2D.Double minS = null;
		Point2D.Double minE = null;
		//Use naive approach
		double minDist = Double.POSITIVE_INFINITY;
		for (Point2D.Double s : startPts) {
			for (Point2D.Double e : endPts) {
				double dist = Point2D.Double.distance(s.x, s.y, e.x, e.y);
				if (dist < minDist) {
					minDist = dist;
					minS = s;
					minE = e;
				}
			}
		}
		
		return new SVGLine(minS, minE, stroke);
	}
}
