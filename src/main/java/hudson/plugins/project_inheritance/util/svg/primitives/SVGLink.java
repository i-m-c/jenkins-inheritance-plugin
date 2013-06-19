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
import java.awt.geom.Rectangle2D;
import java.net.URL;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SVGLink implements SVGPrimitive {
	private final URL link;
	private final SVGPrimitive body;
	
	public SVGLink(URL link, SVGPrimitive body) {
		this.link = link;
		this.body = body;
	}
	
	public String toString() {
		String str = link.toString();
		if (str.length() <= 16) {
			return String.format(
					"lnk[%s]",
					str
			);
		} else {
			return String.format(
					"lnk[...%s]",
					str.substring(str.length()-13)
			);
		}
	}
	
	public Element render(Document doc) {
		Element lnk = doc.createElement("a");
		lnk.setAttribute("xlink:href", link.toString());
		
		if (body != null) {
			lnk.appendChild(body.render(doc));
		}
		
		return lnk;
	}

	public void translate(Point2D.Double offset) {
		if (body != null) {
			body.translate(offset);
		}
	}
	public void moveTo(Point2D.Double pos) {
		if (body != null) {
			body.moveTo(pos);
		}
	}

	public void rescale(double factor, boolean applyToStyles) {
		if (body != null) {
			body.rescale(factor, applyToStyles);
		}
	}

	public Rectangle2D.Double getBounds() {
		if (body != null) {
			return body.getBounds();
		}
		return null;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		if (body != null) {
			return body.getAttachmentPoints();
		}
		return null;
	}
}
