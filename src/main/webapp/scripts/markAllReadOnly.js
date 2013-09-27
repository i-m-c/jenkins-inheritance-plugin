// Add observer to window load event
Event.observe(window,"load",function(){
	// Remove action from form tag in order to prevent submitting the form accidentelly
	document.forms.config.action = "";

	// Hide all buttons that do not expand or advance read-only options
	document.forms.config.select(".first-child button").each(function(s) {
		t = s.innerHTML
		if (t.match(/Expand|Advanced/) || t.match(/\.\.\./) ) {
			//Found a button that needs to be preserved
		} else {
			//s.style.border = "3px solid red";
			s.hide();
		}
	});

	// Make all input fields readonly/disabled
	document.forms.config.select("input, textarea").each (function(s) {
		s.setAttribute("readonly", "readonly"); s.setAttribute("disabled", "disabled")
	});
});