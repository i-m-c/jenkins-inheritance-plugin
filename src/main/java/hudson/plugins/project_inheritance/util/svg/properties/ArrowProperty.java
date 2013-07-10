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

package hudson.plugins.project_inheritance.util.svg.properties;


import java.awt.geom.Point2D;

public class ArrowProperty {
	public ColorProperty stroke;
	public boolean fillHead;
	public double headLen;
	public double headAngle;
	
	/**
	 * Constructor to set all arrow properties. Do note that start and end
	 * are the only mutable properties.
	 * 
	 * @param start the starting point.
	 * @param end the end point.
	 * @param stroke the colour and width of the stroke.
	 * @param fillHead whether or not to fill the head
	 * @param headLen how long the arrow head tips should be removed from the end point.
	 * @param headAngle the angle of the arrow head.
	 */
	public ArrowProperty(ColorProperty stroke, boolean fillHead,
			double headLen, double headAngle) {
		this.stroke = stroke;
		this.fillHead = fillHead;
		this.headLen = headLen;
		this.headAngle = Math.toRadians(headAngle);
	}
	
	//FIXME: The following function is returning invalid points for some angles
	public Point2D.Double[] calcArrowPoints(Point2D.Double start, Point2D.Double end) {
		// Calculate the length of the arrow body
		double bodyLen = start.distance(end);
		Point2D.Double diff = new Point2D.Double(
				start.x - end.x, start.y - end.y
		);
		
		// Calculate the angle of the arrow body
		int xs = (int) Math.signum(diff.x);
		int ys = (int) Math.signum(diff.y);
		int s = (xs == ys) ? 1 : -1;
		double c = (xs >= 0) 
				? (ys >= 0) ? 0 : Math.PI : (ys >= 0) ? Math.PI : 2*Math.PI;
		double bodyAngle = c + (s * (Math.asin(Math.abs(diff.y) / bodyLen)));
		
		//Increase the body angle to get one arrow point
		double sumAngle = bodyAngle + headAngle;
		Point2D.Double a = new Point2D.Double(
				end.x - Math.cos(sumAngle) * headLen,
				end.y - Math.sin(sumAngle) * headLen
		);
		
		// Decrease the angle to get the other arrow point
		sumAngle = bodyAngle - headAngle;
		Point2D.Double b = new Point2D.Double(
				end.x - Math.cos(sumAngle) * headLen,
				end.y - Math.sin(sumAngle) * headLen
		);
		
		Point2D.Double[] arr = {a,b};
		return arr;
	}
}