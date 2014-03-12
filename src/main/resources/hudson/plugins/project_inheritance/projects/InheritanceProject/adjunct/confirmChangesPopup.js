function safeStringify(obj) {
	var out = null;
	if (Array.prototype.toJSON != undefined) {
		var oldToJson = Array.prototype.toJSON;
		delete Array.prototype.toJSON;
		out = JSON.stringify(obj);
		Array.prototype.toJSON = Array.prototype.toJSON;
	} else {
		out = JSON.stringify(json);
	}
	return out;
}

function confirmChanges(form, msg) {
	var ret = confirm(msg);
	return ret;
}

function confirmChangesAndEnterVersion(form, msg) {
	var ret = prompt(msg, "");
	if (ret == null) {
		//The user wished to abort
		return false;
	} else if (ret.length == 0) {
		//The user did not enter a message
		alert("ERROR: You did not enter a message describing what you changed!");
		return false;
	} else {
		/* Jenkins is nasty; it decodes the form into a JSON representation
		 * BEFORE handing it over to us. As such, changing the form is useless
		 * and we need to read-in and manipulate the JSON itself.
		 * 
		 * Do note though that Prototype.js badly interacts with JSON, so
		 * we need to protect ourselves by unsetting its Array.toJSON method
		 */
		if (form.elements.json != null && form.elements.json != undefined) {
			var json = JSON.parse(form.elements.json.value);
			//Check if there's a "versionMessageString" in there
			if (json.versionMessageString != null && json.versionMessageString != undefined) {
				json.versionMessageString = ret;
				//Now, copy back the JSON, but make sure that Prototype does not fuck up
				form.elements.json.value = safeStringify(json);
			}
		}
		return true;
	}
}

