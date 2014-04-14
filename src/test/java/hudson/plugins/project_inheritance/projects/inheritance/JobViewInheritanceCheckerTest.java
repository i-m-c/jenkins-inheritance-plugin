/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JobViewInheritanceCheckerTest {

    @Test
    public void isInheritanceRequired_specificBuildPageURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/1/";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }

    @Test
    public void isInheritanceRequired_notJobURI_retunsFalse() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/anything/";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertFalse(result);
    }
    
    @Test
    public void isInheritanceRequired_specificBuildSubpageURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/1/anyPage";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
    
    @Test
    public void isInheritanceRequired_jobMainPageURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
    
    @Test
    public void isInheritanceRequired_testTrendChartURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/test/trend";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
    
    @Test
    public void isInheritanceRequired_findbugsTrendChartURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/findbugs/trendGraph/png";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
    @Test
    public void isInheritanceRequired_jacocoTrendChartURI_retunsTrue() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/jacoco/graph";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
    
    @Test
    public void isInheritanceRequired_otherJobSubpageURI_retunsFalse() {
        JobViewInheritanceChecker checker = new JobViewInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/other";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertFalse(result);
    }
}
