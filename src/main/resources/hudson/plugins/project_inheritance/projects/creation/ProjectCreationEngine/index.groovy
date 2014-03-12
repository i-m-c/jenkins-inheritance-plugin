/**
 * Copyright (c) 2011-2014, Intel Mobile Communications GmbH
 * 
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

f = namespace(lib.FormTagLib);
l = namespace(lib.LayoutTagLib);


l.layout(title: my.displayName, norefresh: "true") {
	
	//Shorthands for various names
	helpRoot = "/plugin/project-inheritance/help"
	pce = "ProjectCreationEngine"
	
	// Dummy header-tag
	l.header()
	
	//Adding the default side-panel
	include(my, "sidepanel")
	
	//Adding the default main-panel
	include(my, "mainpanel")
}
