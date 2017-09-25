package org.jivesoftware.admin;

import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger Log = LoggerFactory.getLogger(SiteMinderServletRequestAuthenticator.class);

    public static boolean isEnabled() {
        return SiteMinderServletRequestAuthenticator.class.getName().equals(AuthCheckFilter.getServletRequestAuthenticatorClassName());
    }

    @Override
    public boolean authenticateRequest(final HttpServletRequest request) {
        final AuthToken authToken = getSiteMinderBasedAuthToken(request);
        if (authToken != null) {
            // The user has been authenticated
            request.getSession().setAttribute("jive.admin.authToken", authToken);
            return true;
        } else {
            // We've not authenticated the user - do nothing
            return false;
        }
    }

    private AuthToken getSiteMinderBasedAuthToken(final HttpServletRequest request) {
        final String smUser = request.getHeader("SM_USER");
        if (smUser == null || smUser.trim().isEmpty()) {
            // SiteMinder has not authenticated the user
            return null;
        }

        if (!AdminManager.getInstance().isUserAdmin(smUser, true)) {
            // The SiteMinder user is not an admin user
            Log.warn("SiteMinder user '" + smUser + "' is not an Openfire administrator.");
            return null;
        }

        // We've got a valid admin user, so record the login attempt
        LoginLimitManager.getInstance().recordSuccessfulAttempt(smUser, request.getRemoteAddr());
        // And return the auth token
        return new AuthToken(smUser);
    }
}
