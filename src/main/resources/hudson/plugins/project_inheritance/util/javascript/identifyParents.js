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
function submitEventOnElement(elem, eventType) {
	if (!elem || !eventType) { return; }
	
	//Then, create a fake change event
	if ("createEvent" in document) {
		//Most browsers
		var evt = document.createEvent("HTMLEvents");
		evt.initEvent(eventType, false, true);
		elem.dispatchEvent(evt);
	} else {
		//IE compatibility
		elem.fireEvent("on" + eventType);
	}
}

/**
 * This method reacts to an event on an element (pointed to by "source"), to read
 * all parents of the job as currently defined on the page (siblings of source)
 * <p>
 * The parents are concatenated with "," and put into the "target" element
 * as its "value" attribute.
 * 
 * @param source the DOM element to read the value off of (and from its siblings)
 * @param event the event that was triggered.
 */
function fillParentJobs(source, event) {
	//Find all parentage select boxes on the current page
	var selects = document.querySelectorAll(
			"DIV[name='projects'] SELECT.setting-input[name='targetJob']"
	);
	
	//Create the string enumerating all parent names (if any)
	var parLst = "";
	if (selects) {
		for (var i = 0; i<selects.length; i++) {
			var name = selects[i].value;
			if (!name) { continue; }
			parLst += name;
			if (i+1 < selects.length) {
				parLst += ",";
			}
		}
	}
	
	//Find target elements that have the "parentageInfo" class
	var targets = document.getElementsByClassName("parentageInfo");
	if (!targets || targets.length == 0) { return; }
	for (var i = 0; i < targets.length; i++) {
		targets[i].value = parLst;
	}
	
	//Then, find those, that always need to be updated explicitly
	targets = document.getElementsByClassName("parentageInfo alwaysSubmitChange");
	if (!targets || targets.length == 0) { return; }
	for (var i = 0; i < targets.length; i++) {
		/* Create a fake 'change' event on the parentage field, to trigger a
		 * re-evaluation of all the other fields on that page, that reference
		 * it.
		 * This is not useful for doCheckXyz methods, but extremely useful
		 * for doFillXyz methods.
		 */
		submitEventOnElement(targets[i], "change");
	}
	
	
}

/**
 * This call adds an onload behaviour to all inheritance-parent SELECT boxes.
 * 
 * It does so with a low priority, to make sure that the event handler contributed
 * to the fields is called as early as possible in the change handling. Hopefully,
 * before any verification fields are attached to that field.
 */
Behaviour.specify(
		"DIV[name='projects'] SELECT.setting-input[name='targetJob']",
		"Parentage-Change-Scanner", -1000, function(e) {
	//Add the parentage analysis method to its on-change event
	e.addEventListener(
			"change",
			fillParentJobs.curry(e)
	)
});

Behaviour.specify(
		"INPUT.parentageInfo",
		"Initial-Parent-Field-Fill", 0, function(e) {
	//Ensure that the fill method is called at least once for initialization after a small wait
	fillParentJobs(e, null);
});
