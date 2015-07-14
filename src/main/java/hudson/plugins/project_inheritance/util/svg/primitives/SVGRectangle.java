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

import hudson.plugins.project_inheritance.util.svg.properties.ColorProperty;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SVGRectangle implements SVGPrimitive {
	public enum AttachPoints {
		TOP_LEFT(1), TOP_MID(2), TOP_RIGHT(4),
		MID_LEFT(8), MID_RIGHT(16),
		BTM_LEFT(32), BTM_MID(64), BTM_RIGHT(128),
		
		TOP(TOP_LEFT, TOP_MID, TOP_RIGHT),
		BTM(BTM_LEFT, BTM_MID, BTM_RIGHT),
		LEFT(TOP_LEFT, MID_LEFT, BTM_LEFT),
		RIGHT(TOP_RIGHT, MID_RIGHT, BTM_RIGHT),
		
		HORIZ(TOP, BTM), VERT(LEFT,RIGHT),
		
		CORNERS(TOP_LEFT, TOP_RIGHT, BTM_LEFT, BTM_RIGHT),
		MIDPOINTS(TOP_MID, MID_LEFT, MID_RIGHT, BTM_MID),
		
		ALL(CORNERS, MIDPOINTS);
		
		public final int modulo;
		
		private AttachPoints(AttachPoints... others) {
			int mod = 0;
			for (int i = 0; i < others.length; i++) {
				mod += others[i].modulo;
			}
			modulo = mod;
		}
		
		private AttachPoints(int i) {
			modulo = i;
		}
		
		public boolean contains(AttachPoints other) {
			if ((modulo & other.modulo) > 0) {
				return true;
			}
			return false;
		}
	}
	
	protected final Rectangle2D.Double box;
	protected final ColorProperty stroke;
	protected final ColorProperty fill;
	protected final AttachPoints attach;
	
	
	
	public SVGRectangle(Rectangle2D.Double box, ColorProperty stroke, ColorProperty fill) {
		this.box = box;
		this.stroke = stroke;
		this.fill = fill;
		this.attach = AttachPoints.MIDPOINTS;
	}
	
	public SVGRectangle(Rectangle2D.Double box, ColorProperty stroke, ColorProperty fill, AttachPoints pts) {
		this.box = box;
		this.stroke = stroke;
		this.fill = fill;
		this.attach = (pts != null) ? pts : AttachPoints.MIDPOINTS;
	}
	
	public String toString() {
		return String.format(Locale.US,
				"box[%.2f, %.2f, %.2f, %.2f]",
				box.x,
				box.y,
				box.x + box.width,
				box.y + box.height
		);
	}

	public Element render(Document doc) {
		Element e = doc.createElement("rect");
		StringBuilder style = new StringBuilder(64);

		if (fill != null) {
			style.append(String.format(Locale.US,
					"fill:#%06x;fill-opacity:%f;",
					fill.getRGB24(), fill.opacity
			));
		} else {
			style.append(String.format(Locale.US,
					"fill:#%06x;fill-opacity:%f;",
					0, 0.0
			));
		}
		if (stroke != null) {
			style.append(String.format(Locale.US,
					"stroke:#%06x;stroke-opacity:%f;stroke-width=%f;",
					stroke.getRGB24(), stroke.opacity, stroke.width
			));
		} else {
			style.append(String.format(Locale.US,
					"stroke:#%06x;stroke-opacity:%f;",
					0, 0.0
			));
		}
		if (style.length() > 0) {
			e.setAttribute("style", style.toString());
		}
		
		e.setAttribute("x", Double.toString(box.x));
		e.setAttribute("y", Double.toString(box.y));
		e.setAttribute("width", Double.toString(box.width));
		e.setAttribute("height", Double.toString(box.height));
		
		return e;
	}

	public void translate(Point2D.Double offset) {
		if (offset == null) { return; }
		this.box.x += offset.x;
		this.box.y += offset.y;
		
	}

	public void moveTo(Point2D.Double pos) {
		if (pos == null) { return; }
		this.box.x = pos.x;
		this.box.y = pos.y;
	}
	
	public void rescale(double factor, boolean applyToStyles) {
		this.box.width *= factor;
		this.box.height *= factor;
	}

	public Rectangle2D.Double getBounds() {
		return box;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		//A rectangle has 8 connection points; the corners and the midpoints
		//of each edge
		LinkedList<Point2D.Double> lst = new LinkedList<Point2D.Double>();
		
		//Midpoints; they are first; because they should be preferred
		if (attach.contains(AttachPoints.TOP_MID)) {
			lst.add(new Point2D.Double(box.getCenterX(), box.getMinY()));
		}
		if (attach.contains(AttachPoints.BTM_MID)) {
			lst.add(new Point2D.Double(box.getCenterX(), box.getMaxY()));
		}
		if (attach.contains(AttachPoints.MID_LEFT)) {
			lst.add(new Point2D.Double(box.getMinX(), box.getCenterY()));
		}
		if (attach.contains(AttachPoints.MID_RIGHT)) {
			lst.add(new Point2D.Double(box.getMaxX(), box.getCenterY()));
		}
		
		//Corners
		if (attach.contains(AttachPoints.TOP_LEFT)) {
			lst.add(new Point2D.Double(box.getMinX(), box.getMinY()));
		}
		if (attach.contains(AttachPoints.BTM_LEFT)) {
			lst.add(new Point2D.Double(box.getMinX(), box.getMaxY()));
		}
		if (attach.contains(AttachPoints.TOP_RIGHT)) {
			lst.add(new Point2D.Double(box.getMaxX(), box.getMinY()));
		}
		if (attach.contains(AttachPoints.BTM_RIGHT)) {
			lst.add(new Point2D.Double(box.getMaxX(), box.getMaxY()));
		}
		
		return lst;
	}

}
