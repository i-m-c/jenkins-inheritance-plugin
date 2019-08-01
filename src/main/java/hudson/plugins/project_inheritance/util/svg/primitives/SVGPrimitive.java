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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This interface is the root of all drawable SVG primitives. It includes
 * functions to render, rescaling and translate them.
 * 
 * @author Martin Schroeder
 */
public interface SVGPrimitive {
	
	/**
	 * This function should return an element containing the primitive in
	 * question at its current location that can be inserted into the given
	 * {@link Document}.
	 *  
	 * @param doc the document to render into
	 * 
	 * @return an XML {@link Element} that contains the necessary attributes
	 * to draw the primitive into an SVG document. Must return a new copy of the
	 * element on every invocation to allow for one primitive to generate
	 * multiple copies.
	 */
	public Element render(Document doc);
	
	/**
	 * Translate the current position by the given (x,y) tuple.
	 * <p>
	 * It is strictly necessary that the translation is applied identically
	 * to all sub-elements as long as it makes sense to translate them. Links
	 * or rotations for example do not need to be moved; as they only alter
	 * their child primitives and do not have a "location" to speak of.
	 * 
	 * @param offset the amount to move the primitive in (x,y) direction.
	 */
	public void translate(Point2D.Double offset);
	
	/**
	 * Move the object to the given (x,y) position.
	 * <p>
	 * It is strictly necessary that the move is applied identically
	 * to all sub-elements as long as it makes sense to move them. Links
	 * or rotations for example do not need to be moved; as they only alter
	 * their child primitives and do not have a "location" to speak of.
	 * 
	 * @param pos the new position in the (x,y) plane.
	 */
	public void moveTo(Point2D.Double pos);
	
	/**
	 * Rescales the primitive by the given positive factor. Rescaling must keep
	 * the aspect ratio and must not alter the (x,y) position of the top left
	 * corner.
	 * <p>
	 * That means; resizing does not move the primitive; but only expands or
	 * shrinks it in the positive x/y direction.
	 * 
	 * @param factor the multiplier to rescale the primitive by. Must be
	 * positive.
	 * @param applyToStyles if set to true; rescaling also affects styling, for
	 * example line widths.
	 */
	public void rescale(double factor, boolean applyToStyles);
	
	
	/**
	 * Returns the outer, rectangular bounds of this primitive.
	 * <p>
	 * It is assumed that this value is being cached and not recomputed
	 * until the element is translated. After recomputation, the returned
	 * element can, but does not have to be a new object.
	 * <p>
	 * If you want to get the center of this element, call this function
	 * followed by {@link java.awt.geom.Rectangle2D.Double#getCenterX()} and
	 * {@link java.awt.geom.Rectangle2D.Double#getCenterY()}.
	 * <p>
	 * 
	 * @return the outer bounds of this object; or null if not a graphical
	 * primitive.
	 */
	public Rectangle2D.Double getBounds();
	
	/**
	 * Returns the list of points to which other elements (especially arrows)
	 * can be attached. The points should ideally be on the exterior surface
	 * of the element; but that is not a strict demand.
	 * <p>
	 * This function may return null or an empty list if the element is not
	 * graphical.
	 * 
	 * @return a list of points.
	 */
	public List<Point2D.Double> getAttachmentPoints();
}
