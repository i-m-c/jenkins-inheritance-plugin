/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.Extension;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Extension
public class JobViewInheritanceChecker extends RequestInheritanceChecker {

    private static final String JOB_MAIN_VIEW_REGEX        = ".*/job/[^/]+/";

    private static final String JOB_RUN_PAGE_REGEX         = "[0-9]+/.*";

    private static final String TEST_TREND_GRAPH_REGEX     = "test/trend";

    private static final String FINDBUGS_TREND_GRAPH_REGEX = "findbugs/trendGraph/png";

    private static final String JACOCO_TREND_GRAPH_REGEX   = "jacoco/graph";

    public static final Pattern jobUriRegExp;

    static {
        StringBuilder regExpBuilder = new StringBuilder();
        regExpBuilder.append(JOB_MAIN_VIEW_REGEX);
        regExpBuilder.append("(");
        regExpBuilder.append(JOB_RUN_PAGE_REGEX);
        regExpBuilder.append("|");
        regExpBuilder.append(TEST_TREND_GRAPH_REGEX);
        regExpBuilder.append("|");
        regExpBuilder.append(FINDBUGS_TREND_GRAPH_REGEX);
        regExpBuilder.append("|");
        regExpBuilder.append(JACOCO_TREND_GRAPH_REGEX);
        regExpBuilder.append(")?");
        jobUriRegExp = Pattern.compile(regExpBuilder.toString());
    }

    /**
     * Checks if given URI is job main page or one of its specified subpages.
     */
    @Override
    public boolean isInheritanceRequired(String requestURI) {
        Matcher matcher = jobUriRegExp.matcher(requestURI);
        return matcher.matches();
    }

}
