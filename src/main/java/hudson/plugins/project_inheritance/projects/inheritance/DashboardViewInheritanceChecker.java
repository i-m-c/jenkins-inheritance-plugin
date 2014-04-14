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
public class DashboardViewInheritanceChecker extends RequestInheritanceChecker {

    public static final Pattern portletImageUriRegExp = Pattern
                                                              .compile(".*/view/[^/]+/portlet/dashboard_portlet_\\d+/.*");

    public static final Pattern viewUriRegExp         = Pattern.compile(".*/view/[^/]+/?");

    /**
     * Checks if given request comes from view main page or from dashboard portlet.
     */
    @Override
    public boolean isInheritanceRequired(String requestURI) {
        boolean isPortletImage = isPortletImageURI(requestURI);
        boolean isViewRoot = isViewRootURI(requestURI);
        return isViewRoot || isPortletImage;
    }

    private boolean isViewRootURI(String requestURI) {
        Matcher dashboardViewUriMatcher = viewUriRegExp.matcher(requestURI);
        boolean isDashboardView = dashboardViewUriMatcher.matches();
        return isDashboardView;
    }

    private boolean isPortletImageURI(String requestURI) {
        Matcher portletImageUriMatcher = portletImageUriRegExp.matcher(requestURI);
        boolean isPortletImage = portletImageUriMatcher.matches();
        return isPortletImage;
    }

}
