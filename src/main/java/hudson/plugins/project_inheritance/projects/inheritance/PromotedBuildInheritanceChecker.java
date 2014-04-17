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