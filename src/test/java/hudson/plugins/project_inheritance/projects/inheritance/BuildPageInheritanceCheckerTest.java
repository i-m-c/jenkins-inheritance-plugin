/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuildPageInheritanceCheckerTest {
    @Test
    public void isInheritanceRequired_buildPageURI_retunsTrue() {
        BuildPageInheritanceChecker checker = new BuildPageInheritanceChecker();
        String requestUri = "http://server_name/job/JobName/build";
        boolean result = checker.isInheritanceRequired(requestUri);
        assertTrue(result);
    }
}
