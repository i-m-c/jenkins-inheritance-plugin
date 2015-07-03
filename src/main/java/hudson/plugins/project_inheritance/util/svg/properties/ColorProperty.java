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

import java.awt.Color;

import java.util.Locale;

public class ColorProperty {
	public Color color;
	public double width;
	public double opacity;
	public String style;
	
	public ColorProperty(Color col, double width, double opacity, String style) {
		this.color = col;
		this.width = width;
		this.opacity = Math.max(0, Math.min(opacity, 1));
		this.style = style;
	}
	
	public int getRGB24() {
		int code = (this.color.getRed()<<16) +
				(this.color.getGreen()<<8) +
				(this.color.getBlue());
		return code;
	}
	
	public int getRGB32() {
		return ((int)this.opacity*256) << 24 + this.getRGB24();
	}
	
	public String getRGB24Hex() {
		return String.format(Locale.US, "#%06x", this.getRGB24());
	}
}