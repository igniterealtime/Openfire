package org.jivesoftware.admin;

import javax.servlet.http.HttpServletRequest;

public interface ServletRequestAuthenticator {

    /**
     * Attempts to authenticate an HTTP request to a page on the admin console.
     * @param request the request to authenticate
     * @return {@code true} if the request was successfully authenticated, otherwise {@code false}
     */
    boolean authenticateRequest(final HttpServletRequest request);

}
