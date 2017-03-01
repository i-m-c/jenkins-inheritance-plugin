/* The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Tom Huybrechts 
 * Copyright (c) 2011-2015, Intel Mobile Communications GmbH
 * Copyright (c) 2015-2017, Intel Deutschland GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);
ct = namespace(lib.CustomTagLib);
pt = taglib(com.intel.commons.taglibs.PluginTagLib)
st = namespace("jelly:stapler");

f.entry(title: _("Name"), field: "name", help: "/help/parameter/name.html") {
	f.select();
}

f.entry(title: _("Default Value"), field: "defaultValue", help: "/help/parameter/string-default.html") {
	f.textbox();
}

/* Add the invisible field, which will receive the value of all inherited
 * parents on the current page as a CSV.
 * 
 * This is then used in doFillNameItems() in Java, to determine the contents of
 * that select box.
 * 
 * This instrumentation is provided by the configuration page of InheritanceProject
 * and can be found in the following package:
 * hudson.plugins.project_inheritance.util.javascript.identifyParents.js
 * 
 * It also marks itself as to be always update, to receive changes to parents
 * immediately and re-evaluate the doFill method.
 */
f.invisibleEntry() { //Use "f.entry" in case of debugging
	f.textbox(name: "parents", clazz: "parentageInfo alwaysSubmitChange");
}
