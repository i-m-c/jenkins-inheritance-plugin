package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.Extension;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extension
public class ScmPollingInheritanceChecker extends RequestInheritanceChecker {

	public static final Pattern scmPollLogPattern = Pattern.compile(".*/scmPollLog/?");
	
    /**
     * 
     * Checks if given URI is the scm polling log page
     */
    @Override
    public boolean isInheritanceRequired(String requestURI) {
         Matcher scmPollLogMatcher = scmPollLogPattern.matcher(requestURI);
		 boolean isScmPollLog = scmPollLogMatcher.matches();
		 return isScmPollLog;
    }

}