/*
 * Plik stworzony dnia 11 kwi 2014 przez krzysztofkos
 * 
 * Copyright ATREM S.A. ATREM 2014(C)
 */

package hudson.plugins.project_inheritance.projects.inheritance;

import hudson.ExtensionPoint;

/**
 * Allows to decide if inheritance is required during processing request.
 * 
 * @author krzysztofkos
 * 
 */
public abstract class RequestInheritanceChecker implements ExtensionPoint {

    /**
     * Decides if inheritance is required during processing request. This decision is
     * based on given request URI.
     * 
     * @param requestURI
     *            URI of the request being processed
     * @return <code>true</code> if inheritance is needed, <code>false</code> otherwise.
     */
    public abstract boolean isInheritanceRequired(String requestURI);

}
