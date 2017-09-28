package org.jivesoftware.admin;

import javax.servlet.http.HttpServletRequest;

public interface ServletRequestAuthenticator {

    /**
     * Attempts to authenticate an HTTP request to a page on the admin console.
     * @param request the request to authenticate
     * @return the username if it was possible to determine from the request, otherwise {@code null}
     */
    String authenticateRequest(final HttpServletRequest request);

}
