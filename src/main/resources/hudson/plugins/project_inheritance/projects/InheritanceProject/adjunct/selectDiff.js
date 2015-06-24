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
	
	//Now, relocate to the diff computation url
	window.location = "./showDiffOfVersions?left=" + versions[0] + "&right=" + versions[1];
}