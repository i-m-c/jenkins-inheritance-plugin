/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DashboardViewInheritanceSelectorTest {

    @Test
    public void isInheritanceRequired_dashboardViewURI_retunsTrue() {
        DashboardViewInheritanceChecker checker = new DashboardViewInheritanceChecker();
        String requestUri = "http://server_name/view/ViewName/";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }

    @Test
    public void isInheritanceRequired_dashboardViewURIWithoutTrailingSlash_retunsTrue() {
        DashboardViewInheritanceChecker checker = new DashboardViewInheritanceChecker();
        String requestUri = "http://server_name/view/ViewName";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }

    @Test
    public void isInheritanceRequired_protletImageURI_retunsTrue() {
        DashboardViewInheritanceChecker checker = new DashboardViewInheritanceChecker();
        String requestUri = "http://server_name/view/ViewName/portlet/dashboard_portlet_6911/summaryGraph/png";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }

}
