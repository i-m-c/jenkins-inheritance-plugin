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

package hudson.plugins.project_inheritance.util.svg.prefabs;

import hudson.plugins.project_inheritance.util.svg.primitives.SVGLink;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGRectangle;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGText;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGUnion;
import hudson.plugins.project_inheritance.util.svg.primitives.SVGRectangle.AttachPoints;
import hudson.plugins.project_inheritance.util.svg.properties.ColorProperty;
import hudson.plugins.project_inheritance.util.svg.properties.TextProperty;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URL;


/**
 * This class shows a simple UML-like class box.
 * <p>
 * It consists of the className encapsulated by a box and the body text
 * encapsulated by a box. The size of the boxes are determined by the text,
 * unless specific sizes are given for the main box.
 * <p>
 * Do note that both className, bodyText and the custom sizes may be null; in
 * which case this box will not actually render anything.
 * 
 * @author mhschroe
 */
public class SVGClassBox extends SVGUnion {

	public SVGClassBox(Point2D.Double pos,
			TextProperty className, URL classURL, TextProperty bodyText,
			ColorProperty borders) {
		this(pos, className, classURL, bodyText, borders, null, null);
	}
	
	public SVGClassBox(Point2D.Double pos,
			TextProperty className, URL classURL,
			TextProperty bodyText,
			ColorProperty borders,
			Point2D.Double minSize, Point2D.Double maxSize) {
		super();
		
		double yOffset = 0;
		
		int txtMarginX = 2;
		
		boolean hasClassText = (className != null && !(className.getText().isEmpty()));
		boolean hasBodyText = (bodyText != null && !(bodyText.getText().isEmpty()));
		
		SVGRectangle svgClassBox = null;
		SVGRectangle svgBodyBox = null;
		
		//Add the class name text, if present
		if (hasClassText) {
			Point2D.Double txtPos = new Point2D.Double(
					pos.x + txtMarginX,
					pos.y
			);
			SVGText svgClassText = new SVGText(
					txtPos, className,
					(maxSize != null && maxSize.x > 0) ? maxSize.x : 0
			);
			Rectangle2D.Double bounds = svgClassText.getBounds();
			bounds.x -= txtMarginX;
			bounds.width += 2 * txtMarginX;
			
			svgClassBox = new SVGRectangle(
					bounds, borders, null,
					(hasBodyText) ? AttachPoints.TOP : AttachPoints.HORIZ
			);
			if (classURL != null) {
				this.addElements(
						new SVGLink(
								classURL,
								new SVGUnion(svgClassText, svgClassBox)
						)
				);
			} else {
				this.addElements(svgClassText);
				this.addElements(svgClassBox);
			}
			
			yOffset = svgClassBox.getBounds().getHeight();
		}
		
		Point2D.Double newPos = new Point2D.Double(
				pos.x + txtMarginX,
				pos.y + yOffset
		);
		
		//Add the body; if present
		if (hasBodyText) {
			SVGText svgBodyText = new SVGText(
					newPos, bodyText,
					(maxSize != null && maxSize.x > 0) ? maxSize.x : 0
			);
			this.addElements(svgBodyText);
			
			//Computing the maximum bounds of class-body and this body
			Rectangle2D.Double bounds = svgBodyText.getBounds();
			//Increase by 5% to fix overlapping text
			bounds.width *= 1.05;
			//Check if we need to grow to cover the class box text
			if (svgClassBox != null) {
				bounds.width = Math.max(bounds.width, svgClassBox.getBounds().width);
			}
			
			if (minSize != null) {
				if (minSize.x > 0 && bounds.getWidth() < minSize.x) {
					bounds.width = minSize.x;
				}
				if (minSize.y > 0 && bounds.getHeight() < minSize.y) {
					bounds.height = minSize.y;
				}
			}
			
			bounds.x -= txtMarginX;
			bounds.width += 2 * txtMarginX;
			
			svgBodyBox = new SVGRectangle(
					bounds, borders, null, AttachPoints.HORIZ
			);
			this.addElements(svgBodyBox);
		} else if (minSize != null && minSize.x > 0 && minSize.y > 0) {
			Rectangle2D.Double bounds = new Rectangle2D.Double(
					newPos.x, newPos.y, minSize.x, minSize.y
			);
			if (svgClassBox != null) {
				bounds.width = Math.max(bounds.width, svgClassBox.getBounds().width);
			}
			svgBodyBox = new SVGRectangle(
					bounds, borders, null
			);
			this.addElements(svgBodyBox);
		}
	}
	
}
