/*
 * Copyright (c) 2019 Intel Corporation
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
function isVisible(tag) {
	//Taken from jQuery
	return !!( tag.offsetWidth || tag.offsetHeight || tag.getClientRects().length );
}

/**
 * Determines which element is responsible for emitting the error via a
 * 'checkurl' attribute. This element is always a child of the previous
 * sibling of the error area.
 * 
 * @param errorArea an element that is a validation-error-area.
 * @param patterns the patterns to scan for "is-contained-by" the checkUrl
 * @returns {Boolean} true, if the checkurl contains one of the patterns. False
 * 		in all other cases, including if the checkurl can't be found.
 */
function ignoreByCheckurl(errorArea, patterns) {
	if (!errorArea) { return false; }
	if (!patterns) { return false; }
	
	var sibling = errorArea.previousSibling;
	if (!sibling) { return false; }
	
	var checkUrlElem = sibling.querySelector("[checkurl]");
	if (!checkUrlElem) { return false; }
	
	var checkUrl = checkUrlElem.getAttribute("checkurl");
	if (!checkUrl) { return false; }
	
	//Loop over the patterns, and see if the checkUrl contains them
	for (var i = 0; i < patterns.length; i++) {
		if (checkUrl.indexOf(patterns[i]) >= 0) {
			//Match! The errorArea ought to be ignored
			return true;
		}
	}
	
	//Nothing matched, error shall not be ignored
	return false;
}

function detectValidationErrors() {
	//Fetch all error areas
	var validationErrorArea = document.querySelectorAll(".validation-error-area");
	if (validationErrorArea === null) {
		return false;
	}
	
	//Retrieve the 'checkUrl' ignore patterns
	var checkUrlPatterns = [];
	var checkUrlElement = document.querySelector(".acceptable-error-url-data")
	if (checkUrlElement) {
		var text = checkUrlElement.textContent || ''
		checkUrlPatterns = text.split("\n");
	}
	
	var errorMsg, focusElement;
	for (var i = 0; i < validationErrorArea.length; ++i) {
		var errorArea = validationErrorArea[i];
		//Only care for VISIBLE areas of the correct node type
		//nodetype - element - http://www.w3schools.com/jsref/prop_node_nodetype.asp
		if (errorArea === null
				|| errorArea.nodeType !== 1
				|| !isVisible(errorArea)
		) {
			continue;
		}
		
		//Check if the patterns allow the error message to be ignored
		var ignore = ignoreByCheckurl(errorArea, checkUrlPatterns);
		if (ignore) {
			//No need to report this error to the user
			continue;
		}
		
		// Find all DIV tags with the error class
		var errorTags = errorArea.querySelectorAll("div.error");
		var errorTag = null;
		for (var j = 0; j < errorTags.length; ++j) {
			errorTag = errorTags[j];
			//Grab error text either from innerText or (on Firefox < 47) textContent
			var errorText = errorTag.innerText || errorTag.textContent;
			//Trim the text, if present
			if (errorText) { errorText = errorText.trim(); }
			//Only save the message and tag, if it is non-empty
			if (errorText) {
				errorMsg = errorText;
				//focus to previous element
				focusElement = validationErrorArea[i].previousSibling;
				focusElement.scrollIntoView({block: "start", behavior: "smooth"});
				break;
			}
		}
		//Break, if at least one errorMessage has been found
		if (errorMsg) { break; }
	}
	//Show the error, if the message is non-null
	if (errorMsg) {
		alert(errorMsg);
		return true;
	}
	return false;
}

