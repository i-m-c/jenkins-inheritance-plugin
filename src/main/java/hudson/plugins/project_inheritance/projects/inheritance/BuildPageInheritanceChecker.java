/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.Extension;

@Extension
public class BuildPageInheritanceChecker extends RequestInheritanceChecker {

    /**
     * 
     * Checks if given URI is job build page
     */
    @Override
    public boolean isInheritanceRequired(String requestURI) {
        boolean isBuildPageUri = requestURI.endsWith("/build");
        return isBuildPageUri;
    }

}
