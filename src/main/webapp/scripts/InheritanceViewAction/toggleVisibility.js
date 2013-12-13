function changeAllBuilderVisibility(projectClass) {
	//Loop over all elements; and dis-/enable the 'buildStepsBlock-*' fields
	var blocks = document.getElementsByTagName("tr");
	for(var i = 0; i < blocks.length; i++) {
		var elem = blocks[i];
		var id = elem.id;
		if (!id) { continue; }
		if (id.indexOf("buildStepsBlock-" + projectClass) != -1) {
			elem.show();
		} else if (id.indexOf("buildStepsBlock-") != -1) {
			elem.hide();
		} else {
			//Skip; as it's not a buildSTepsBlock
		}
	}
}

function toggleVisibility(elemID) {
	//Loop over all elements; and dis-/enable the 'buildStepsBlock-*' fields
	var elem = document.getElementById(elemID);
	if (!elem) { return; }
	elem.toggle();
}