/**
 * Copyright © 2014 Contributor.  All rights reserved.
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

package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.Extension;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extension
public class PromotedBuildInheritanceChecker extends RequestInheritanceChecker {

	public static final Pattern promotionPattern = Pattern.compile(".*/promotion/?");
	
    /**
     * 
     * Checks if given URI is the job-wide promotion page
     */
    @Override
    public boolean isInheritanceRequired(String requestURI) {
         Matcher promotionMatcher = promotionPattern.matcher(requestURI);
		 boolean isPromotion = promotionMatcher.matches();
		 return isPromotion;
    }

}