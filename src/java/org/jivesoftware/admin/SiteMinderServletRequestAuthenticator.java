package org.jivesoftware.admin;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * Enables CA SiteMinder/Single Sign-On authentication to the admin console - https://www.ca.com/gb/products/ca-single-sign-on.html
 * </p>
 * <p>
 * To enable, set the system property {@code adminConsole.servlet-request-authenticator} =
 * {@code org.jivesoftware.admin.SiteMinderServletRequestAuthenticator} and restart Openfire.
 * </p>
 */
public class SiteMinderServletRequestAuthenticator implements ServletRequestAuthenticator {

    /**
     * Indicates if this ServletRequestAuthenticator is enabled or not
     *
     * @return {@code true} if enabled, otherwise {@code false}
     */
    public static boolean isEnabled() {
        return AuthCheckFilter.isServletRequestAuthenticatorInstanceOf(SiteMinderServletRequestAuthenticator.class);
    }

    @Override
    public String authenticateRequest(final HttpServletRequest request) {
        final String smUser = request.getHeader("SM_USER");
        if (smUser == null || smUser.trim().isEmpty()) {
            // SiteMinder has not authenticated the user
            return null;
        } else {
            return smUser;
        }
    }
}
