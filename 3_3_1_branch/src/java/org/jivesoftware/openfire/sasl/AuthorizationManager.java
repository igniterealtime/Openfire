/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-07 09:28:54 -0500 (Fri, 07 Apr 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserProvider;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

/**
 * Manages the AuthorizationProvider objects.
 * <p/>
 * Overall description of the authentication and authorization process:
 * <p/>
 * After a client connects, and idicates a desire to use SASL, the
 * SASLAuthentication object decides which SASL mechanisms to advertise,
 * and then performs the authentication. If authentication is successful,
 * the XMPPCallbackHandler is asked to handle() an AuthorizeCallback.  The
 * XMPPCallbackHandler asks the AuthorizationManager to authorize the
 * principal to the requested username.  The AuthorizationManager manages
 * a list of AuthorizationProvider classes, and tries them one at a time
 * and returns true with the first AuthorizationProvider that authorizes
 * the principal to the username.  If no classes authorize the principal,
 * false is returned, which traces all the way back to give the client an
 * unauthorized message. Its important to note that the message the client
 * recieves will give no indication if the principal authentiated successfully,
 * you will need to check the server logs for that information.
 *
 * @author Jay Kline
 */
public class AuthorizationManager {

    private static AuthorizationManager instance = new AuthorizationManager();
    private static AuthorizationPolicy authorizationProvider;

    static {
        String className = JiveGlobals.getXMLProperty("provider.authorization.className");
        if (className != null) {
            try {
                Class c_provider = ClassUtils.forName(className);
                AuthorizationPolicy provider = (AuthorizationPolicy)(c_provider.newInstance());
                Log.debug("AuthorizationManager: Loaded " + provider);
                authorizationProvider = provider;
            }
            catch (Exception e) {
                Log.error("Error loading AuthorizationProvider: " + className + "\n" + e);
            }
        }

        if (authorizationProvider == null) {
            Log.debug("No AuthorizationProvider's found. Loading DefaultAuthorizationPolicy");
        }
    }


    /**
     * Returns a singleton AuthorizationManager instance.
     *
     * @return a AuthorizationManager instance.
     */
    public static AuthorizationManager getInstance() {
        return instance;
    }

    private AuthorizationManager() {

    }


    /**
     * Authorize the authenticated used to the requested username.  This uses the
     * GSSAPIAuthProvider if it is the specified provider.
     *
     * @param username  the username.
     * @param principal the principal.
     * @return true if the user is authorized.
     */
    public static boolean authorize(String username, String principal) {
        boolean authorized = false;
        if (authorizationProvider != null) {
            authorized = authorizationProvider.authorize(username, principal);
            if (!authorized) {
                return false;
            }
        }

        // If the user is authorized, we want to check if the user is listed as a
        // member of the <code>UserProvider</code>, and if not, add the user (if writable).
        // See if the user exists in the database. If not, automatically create them.
        UserManager userManager = UserManager.getInstance();
        try {
            userManager.getUser(username);
        }
        catch (UserNotFoundException userNotFound) {
            try {
                Log.debug("Automatically creating new user account for " + username);
                // Create user; use a random password for better safety in the future.
                // Note that we have to go to the user provider directly -- because the
                // provider is read-only, UserManager will usually deny access to createUser.
                UserProvider userProvider = UserManager.getUserProvider();
                if (userProvider.isReadOnly()) {
                    Log.error("Error: Unable to add the user. The UserProvider is not writable.");
                }
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, null);
                return true;
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
        return true;
    }
}
