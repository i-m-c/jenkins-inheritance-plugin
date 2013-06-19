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

import java.awt.Font;

public class TextProperty {
	public static enum STYLE {
		PLAIN, BOLD, ITALIC, BOLDITALIC
	};
	
	public String[] text;
	public int fontSzPx;
	public int horizLineDist;
	public String fontName;
	public ColorProperty color;
	public STYLE style;
	
	
	
	public TextProperty(String text, ColorProperty color, STYLE style,
			String fontName, int fontSizePx, int horizLineDist) {
		//Sanitizing the data
		if (text == null || text.length() == 0) {
			this.text = new String[1];
			this.text[0] = "";
		} else {
			this.text = text
					.replace("&", "&amp;")
					.replace("<", "&gt;")
					.split("\n");
		}
		
		this.color = color;
		this.fontName = (fontName == null) ? "" : fontName;
		this.fontSzPx = (fontSizePx <= 0) ? 1 : fontSizePx;
		this.horizLineDist = horizLineDist;
		this.style = (style == null) ? STYLE.PLAIN : style;
	}
	
	
	public int getAwtFontStyle() {
		switch(this.style) {
			default:
			case PLAIN:
				return Font.PLAIN;
			case ITALIC:
				return Font.ITALIC;
			case BOLD:
				return Font.BOLD;
			case BOLDITALIC:
				return Font.BOLD + Font.ITALIC;
		}
	}
	
	public int getSizeAsPx() {
		return fontSzPx;
	}
	
	public int getSizeAsPts() {
		return sizePxToPts(fontSzPx);
	}
	
	public String getText() {
		if (text == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		
		for (int i = 0; i < text.length; i++) {
			b.append(text[0]);
			if (i+1 < text.length) {
				b.append('\n');
			}
		}
		
		return b.toString();
	}
	
	public static int sizePxToPts(int px) {
		return (int) Math.ceil(px * (3.0/4.0));
	}
	
	public static int sizePtsToPx(int pts) {
		return (int) Math.round(pts * (4.0/3.0));
	}
}
