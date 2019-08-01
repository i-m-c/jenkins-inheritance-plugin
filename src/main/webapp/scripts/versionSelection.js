/**
 * Copyright (c) 2019 Intel Corporation
 */
var alterUrlToVersion = function(url, newVersion) {
	var baseURL = url.replace(/\?.*/, "");
	var paramStr = url.replace(/^[^\?]*/, "");
	
	//Trying to replace a "?version=xyz" or "&amp;version=xyz" field
	var re = /([?&amp;]version=)\d+/
	var newParamStr = paramStr;
	if (paramStr.search(re) >= 0) {
		newParamStr = paramStr.replace(re, "$1" + newVersion);
	} else {
		//No version field; adding one
		qPos = paramStr.search(/\?/);
		if (qPos >= 0) {
			newParamStr = paramStr.substr(0,qPos) +
				"version=" + newVersion +
				paramStr.substr(qPos);
		} else {
			newParamStr = paramStr + "?version=" + newVersion; 
		}
	}
	return baseURL + newParamStr
}