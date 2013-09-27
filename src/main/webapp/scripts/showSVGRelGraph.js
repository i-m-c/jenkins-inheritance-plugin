var inlineSVG = function() {
	// Fetch the two versions from the select boxes
	var graphDiv = document.getElementById("svgRelGraph")
	
	var xhr = new XMLHttpRequest();
	if (!xhr) {
		alert("Could not open XMLHttpRequest. Consider not using IE5/6.");
		return;
	}
	var url = "renderSVGRelationGraph";
	
	xhr.open('GET', url, true);
	xhr.onreadystatechange = function () {
		if (xhr.readyState != 4) { return; }
		if (graphDiv) {
			graphDiv.innerHTML = xhr.responseText;
		}
	};
	xhr.send(null);
}

Event.observe(window, "load", inlineSVG);