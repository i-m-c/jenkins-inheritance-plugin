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

package hudson.plugins.project_inheritance.util.svg;

import java.net.URL;

public interface SVGNode {
	
	/**
	 * Returns the unique, one-line label for the node.
	 * @return a unique string for this node 
	 */
	public String getSVGLabel();
	
	/**
	 * Returns a multi-line string containing a detailed description of the node.
	 * Line breaks are preserved when writing out the detail.
	 * 
	 * @return a string describing the important properties of the node.
	 */
	public String getSVGDetail();
	
	/**
	 * The link to the node through the browser. May be null.
	 * 
	 * @return a {@link URL} that specifies the link anchor that leads users to
	 * the object represented by this node.
	 */
	public URL getSVGLabelLink();
}
