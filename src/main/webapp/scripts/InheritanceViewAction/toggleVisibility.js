function changeAllBuilderVisibility(tag, prefix, projectClass) {
	//First, we hide fields matching the prefix
	_doForAll(tag, prefix, null, null, "hide");
	
	//Then, we selectively enable all sections that have that class
	//or make everything visible again; if no class is selected 
	if (projectClass) {
		_doForAll(tag, prefix, null, projectClass, "show");
	} else {
		_doForAll(tag, prefix, null, null, "show");
	}
}

function _doForAll(tag, prefix, intra, suffix, fName) {
	var blocks = document.getElementsByTagName(tag);
	for(var i = 0; i < blocks.length; i++) {
		var elem = blocks[i];
		var id = elem.id;
		if (!id) { continue; }
		//Check if the prefix matches
		if (prefix) {
			if (id.indexOf(prefix) != 0) {
				continue;
			}
		}
		//Check if the intra matches
		if (intra) {
			if (id.indexOf(intra) == -1) {
				continue;
			}
		}
		//Check if the suffix matches
		if (suffix) {
			idx = id.indexOf(suffix);
			if (idx + suffix.length != id.length) {
				continue;
			}
		}
		//All qualifiers matched; executing the method
		elem[fName]();
	}
}

function hideAll(tag, prefix, intra, suffix) {
	_doForAll(tag, prefix, intra, suffix, "hide");
}

function showAll(tag, prefix, intra, suffix) {
	_doForAll(tag, prefix, intra, suffix, "show");
}

function toggleAll(tag, prefix, intra, suffix) {
	_doForAll(tag, prefix, intra, suffix, "toggle");
}

function toggleElem(elemID) {
	var elem = document.getElementById(elemID);
	if (!elem) { return; }
	elem.toggle();
}