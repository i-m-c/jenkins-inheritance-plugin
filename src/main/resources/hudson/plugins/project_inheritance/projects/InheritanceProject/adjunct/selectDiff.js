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

var selectForDiff = function(event) {
	if (event == null) { return; }
	//Fetch the target the user has actually clicked on, to ignore clicking on certain elements
	var t = event.target;
	if (t == null) { return; }
	if (t.tagName == "INPUT" || t.tagName == "A") {
		return;
	}
	//Fetching the target the event was declared on
	var ct = event.currentTarget;
	if (ct == null) { return; }
	
	//Toggle the element's "highlight" class
	ct.classList.toggle("highlight");
}

var executeDiff = function(clName) {
	if (clName == null) { return; }
	//Try to fetch the element with that ID
	var elems = document.getElementsByClassName(clName);
	if (elems == null || elems.length == 0) { return; }
	
	var elem = elems[0];
	
	//For that element, retrieve all elements with the "highlight" class
	var selected = elem.getElementsByClassName("highlight")
	if (selected == null || selected.length != 2) {
		alert("You must select exactly 2 versions to compare. Just click on their row.")
		return;
	}
	
	//Try to find the "versionID" textbox
	var versions = [];
	for (var i = 0; i < 2; i++) {
		var vElem = selected[i].querySelectorAll('[name=versionID]');
		if (vElem == null || vElem.length == 0) { return; }
		var val = vElem[0].value
		if (val == undefined || val == null) { return; }
		versions.push(val)
	}
	
	//Sort the versions, so that low numbers come first
	versions.sort();
	
	//Now, relocate to the diff computation url
	window.location = "./showDiffOfVersions?left=" + versions[0] + "&right=" + versions[1];
}