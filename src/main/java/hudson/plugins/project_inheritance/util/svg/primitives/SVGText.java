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


import hudson.plugins.project_inheritance.util.svg.properties.TextProperty;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jfree.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SVGText implements SVGPrimitive {
	private final Point2D.Double pos;
	private final TextProperty props;
	private double lineBreakPxls;
	
	private transient FontMetrics fm = null;
	private transient Graphics2D g = null;

	private transient Rectangle2D.Double bounds = null;

	public SVGText(Point2D.Double pos, TextProperty props, double lineBreakPxls) {
		if (pos == null || props == null) {
			throw new IllegalArgumentException(
					"Null values for position or text-property not allowed."
			);
		}
		this.pos = pos;
		this.props = props;
		this.lineBreakPxls = lineBreakPxls;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("\"");
		for (int i = 0; i < props.text.length; i++) {
			b.append(props.text[i]);
			if (i+1 < props.text.length) {
				b.append('\n');
			}
		}
		b.append('\"');
		return b.toString();
	}
	
	public Element render(Document doc) {
		Element txtElem = doc.createElement("text");
		txtElem.setAttribute("font-family", props.fontName);
		txtElem.setAttribute("font-size", String.format("%dpx", props.fontSzPx));
		
		//The y-position needs to be fudged; as text boxes are anchored at the
		//bottom of the first line; instead of the top left
		txtElem.setAttribute("x", Double.toString(pos.x));
		txtElem.setAttribute("y", Double.toString(pos.y + this.getHeightOfLine()));
		
		switch (props.style) {
			default:
			case PLAIN:
				break;
			
			case BOLD:
				txtElem.setAttribute("font-weight", "bold");
				break;
			
			case BOLDITALIC:
				txtElem.setAttribute("font-weight", "bold");
				txtElem.setAttribute("font-style", "italic");
				break;
				
			case ITALIC:
				txtElem.setAttribute("font-style", "italic");
				break;
		}
		
		Collection<String> lines = this.getBrokenLines();
		
		//Add tspan's for each line
		boolean isFirst = true;
		int lineOffsetY = this.getHeightOfLine() + props.horizLineDist;
		for (String line : lines) {
			Element e = doc.createElement("tspan");
			e.appendChild(doc.createTextNode(line));
			e.setAttribute("x", Double.toString(pos.x));
			if (!isFirst) {
				e.setAttribute("dy", Double.toString(lineOffsetY));
			} else {
				isFirst = false;
			}
			txtElem.appendChild(e);
		}
		
		return txtElem;
	}

	public void translate(Point2D.Double offset) {
		if (offset == null) { return; }
		pos.x += offset.x;
		pos.y += offset.y;
		this.bounds = null;
	}

	public void moveTo(Point2D.Double pos) {
		if (pos == null) { return; }
		this.pos.x = pos.x;
		this.pos.y = pos.y;
		this.bounds = null;
	}
	
	public void rescale(double factor, boolean applyToStyles) {
		if (this.lineBreakPxls > 0) {
			this.lineBreakPxls *= factor;
		}
		if (applyToStyles) {
			props.fontSzPx *= factor;
			props.horizLineDist *= factor;
		}
	}

	public Rectangle2D.Double getBounds() {
		if (bounds == null) {
			Collection<String> lines = this.getBrokenLines();
			int lineOffsetY = this.getHeightOfLine() + props.horizLineDist;
			int heightOfAllLines = lines.size() * lineOffsetY;
			
			int maxWidth = 0;
			for (String line : lines) {
				maxWidth = Math.max(maxWidth, this.getWidthOfLine(line));
			}
			bounds = new Rectangle2D.Double(
					pos.x, pos.y,
					maxWidth, heightOfAllLines
			);
		}
		return bounds;
	}

	public List<Point2D.Double> getAttachmentPoints() {
		return null;
	}

	
	
	private void initFontMetrics() {
		if (this.g == null) {
			this.g = new BufferedImage(
					1, 1, BufferedImage.TYPE_INT_RGB
			).createGraphics();
		}
		if (this.fm == null) {
			Font font = new Font(
					props.fontName, props.getAwtFontStyle(),
					props.getSizeAsPx()
			);
			g.setFont(font);
			this.fm = g.getFontMetrics();
		}
	}
	
	protected int getWidthOfLine(String line) {
		initFontMetrics();
		if (line == null || line.isEmpty()) {
			return 0;
		}
		
		TextLayout tl = new TextLayout(
				line, this.fm.getFont(), this.g.getFontRenderContext()
		);
		int advance = (int) Math.ceil(tl.getAdvance());
		return advance;
	}
	
	protected int getHeightOfLine() {
		initFontMetrics();
		return fm.getHeight();
	}

	protected Collection<String> getBrokenLine(String line) {
		initFontMetrics();
		
		LinkedList<String> out = new LinkedList<String>();
		if (line == null || line.isEmpty()) {
			return out;
		}
		
		//Assigning correct font
		Map<Attribute, Object> attributes = new HashMap<Attribute, Object>();
		attributes.put(TextAttribute.FONT, g.getFont());
		
		//Creating text with the above attributes
		AttributedString as = new AttributedString(line, attributes);
		AttributedCharacterIterator aci = as.getIterator();
		LineBreakMeasurer measurer = new LineBreakMeasurer(
				aci, this.g.getFontRenderContext()
		);
		
		
		int start = 0;
		while (true) {
			TextLayout tl = measurer.nextLayout((float)lineBreakPxls);
			if (start >= line.length() || tl == null) {
				if (start < line.length()) {
					out.add(line.substring(start, line.length()));
				}
				break;
			}
			int end = measurer.getPosition();
			String text = line.substring(start, end);
			
			float lenInPx = tl.getAdvance();
			if (lenInPx > lineBreakPxls) {
				Log.error(String.format(
						"Line breaker error: String '%s' is %fpx wide, but should be < %fpx",
						text, lenInPx, lineBreakPxls
				));
			}
			out.add(text);
			start = end;
		}
		
		return out;
	}

	protected Collection<String> getBrokenLines() {
		if (lineBreakPxls <= 0) {
			return Arrays.asList(props.text);
		} else {
			//Breaking lines at the desired pixel
			LinkedList<String> lines = new LinkedList<String>();
			for (String line : props.text) {
				lines.addAll(this.getBrokenLine(line));
			}
			return lines;
		}
	}
}
