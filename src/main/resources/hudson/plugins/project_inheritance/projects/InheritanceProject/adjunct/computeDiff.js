var computeDiff = function(job, mode) {
	
	//TODO: Replace this retrieval with a JS->Java method bridge
	
	// Fetch the two versions from the select boxes
	var lElem = document.getElementById("leftVersion")
	var lSel = lElem.value;
	
	var rElem = document.getElementById("rightVersion")
	var rSel = rElem.value;
	var e = document.getElementById("diffBox");
	
	var xhr = new XMLHttpRequest();
	if (!xhr) {
		alert("Could not open XMLHttpRequest. Consider not using IE5/6.");
		return;
	}
	var url = "computeVersionDiff?l=" + lSel + "&r=" + rSel;
	if (mode) {
		url += "&mode=" + mode;
	}
	xhr.open('GET', url, true);
	xhr.onreadystatechange = function () {
		if (xhr.readyState != 4) { return; }
		if (e) {
			e.innerHTML = xhr.responseText;
		}
	};
	switch(mode) {
		default:
		case "unified":
			e.setAttribute("style", "width:90%;white-space:pre-wrap;");
			break;
		
		case "raw":
		case "side":
			if (e) {
				e.setAttribute("style", "");
			}
			break;
	}
	xhr.send(null);
	
	//Fetch the current URL; adjust it and spawn an alert box
	ext = "?left=" + lSel + "&right=" + rSel;
	url = "./" + ext
	
	//Try modifying the URL history
	try {
		window.history.replaceState(null, "", ext);
	} catch (e) {
		//Do nothing
	}
}
