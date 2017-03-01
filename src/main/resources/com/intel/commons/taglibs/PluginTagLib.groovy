/*
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
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

/**
 * Groovy tag library providing views and renderers used in different
 * plugins. A common tag library ensures a unified look&feel among
 * Intel plugins.
 *
 */
package com.intel.commons.taglibs;


/**
 * Renders a section with an icon, a title and contents. A "section"
 * is tipically used in job detail pages.
 *
 * <p>The following keys are expected in the <code>properties</code>
 * map:</p>
 *
 * * icon - The relative URL for the icon image to be used.
 *
 * * title - The string to be used as section title.
 *
 * @param properties - Map of named arguments. See above for
 * recognized keys.
 *
 * @param contentsBody - Closure that will be executed to render the
 * internal contents of the section.
 */
def section(
		final Map properties,
		final Closure contentsRenderer) {

	def jenkins = namespace(lib.JenkinsTagLib);

	def icon = properties["icon"];
	def title = properties["title"];

	table(style: "margin-top: 1em; margin-left:1em;") {
		jenkins.summary(icon: icon) {
			h2(title);
			contentsRenderer();
		}
	}
}


/**
 * Renders a regular table.
 *
 * @param headerLabels List of strings to be used as labels for the
 * table header.
 *
 * @param properties Map of attributes to be added to the "table"
 * element.
 *
 * @tbodyRenderer Closure that will be called for rendering the body
 * of the table.
 */
def bigTable(
		final List headerLabels,
		final Map properties,
		final Closure tbodyRenderer) {

	Map myProperties = [ class: "pane bigtable"];

	mergePropsAndExtendCssClass(properties, myProperties);

	table (myProperties) {
		thead() {
			headerLabels.each { label ->
				th() { text(label) };
			}
		}
		tbody() {
			tbodyRenderer.call();
		}
	}
}


/**
 * Renders a regular table.
 *
 * @param headerLabels List of strings to be used as labels for the
 * table header.
 *
 * @tbodyRenderer Closure that will be called for rendering the body
 * of the table.
 */
def bigTable(
		final List headerLabels,
		final Closure tbodyRenderer) {

	Map properties = [:];

	bigTable(headerLabels, properties, tbodyRenderer);
}


/**
 * Renders one row in a regular table.
 */
def bigTableRow(
		final Map properties,
		final Closure rowRenderer) {

	tr(properties) {
		rowRenderer.call();
	}
}


/**
 * Renders one row in a regular table, without any element properties.
 */
def bigTableRow(Closure rowRenderer) {

	bigTableRow([:], rowRenderer);
}


/**
 * Renders one table cell in a regular table.
 */
def bigTableCell(
		final Map properties,
		final Closure cellRenderer) {

	Map myProperties = [ class: "pane" ];

	mergePropsAndExtendCssClass(properties, myProperties);

	td(myProperties) {
		cellRenderer.call();
	}
}


/**
 * Renders one table cell in a regular table.
 */
def bigTableCell(final Closure cellRenderer) {

	bigTableCell([:], cellRenderer);
}


/**
 * Copies all the properties in the <code>sourceProps</code> map to
 * the <code>targetProps</code> map.
 *
 * <p>If a property with key "class" already exists in the
 * <code>targetPropsMap</code>, then its value will be replaced with
 * concatenating the value comming from <code>sourceProps</code> with
 * the value that already was in <code>targetProps</code>.</p>
 */
def mergePropsAndExtendCssClass(
		final Map sourceProps,
		final Map targetProps) {

	String sourcePropsClass = null;
	String originalTargetClass = targetProps.class;

	if ( sourceProps != null ) {
		sourcePropsClass = sourceProps.class;
		targetProps.putAll(sourceProps);
	}

	if ( (sourcePropsClass!=null) && (originalTargetClass!=null) ) {
		String newTargetClass = sourcePropsClass + " " + originalTargetClass;
		targetProps.class = newTargetClass;
	}
}
