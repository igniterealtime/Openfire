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

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

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

    private static ArrayList<AuthorizationPolicy> authorizationPolicies = new ArrayList<AuthorizationPolicy>();
    private static ArrayList<AuthorizationMapping> authorizationMapping = new ArrayList<AuthorizationMapping>();
    private static AuthorizationManager instance = new AuthorizationManager();

    static {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("provider.authorization.classList");
        JiveGlobals.migrateProperty("provider.authorizationMapping.classList");
        JiveGlobals.migrateProperty("sasl.approvedRealms");
        JiveGlobals.migrateProperty("sasl.realm");

        String classList = JiveGlobals.getProperty("provider.authorization.classList");
        if (classList != null) {
            StringTokenizer st = new StringTokenizer(classList, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String s_provider = st.nextToken();
                try {
                    Class c_provider = ClassUtils.forName(s_provider);
                    AuthorizationPolicy provider =
                            (AuthorizationPolicy)(c_provider.newInstance());
                    Log.debug("AuthorizationManager: Loaded " + s_provider);
                    authorizationPolicies.add(provider);
                }
                catch (Exception e) {
                    Log.error("AuthorizationManager: Error loading AuthorizationProvider: " + s_provider + "\n" + e);
                }
            }
        }
        if (authorizationPolicies.isEmpty()) {
            Log.debug("AuthorizationManager: No AuthorizationProvider's found. Loading DefaultAuthorizationPolicy");
            authorizationPolicies.add(new DefaultAuthorizationPolicy());
        }

        classList = JiveGlobals.getProperty("provider.authorizationMapping.classList");
        if (classList != null) {
            StringTokenizer st = new StringTokenizer(classList, " ,\t\n\r\f");
            while (st.hasMoreTokens()) {
                String s_provider = st.nextToken();
                try {
                    Class c_provider = ClassUtils.forName(s_provider);
                    Object o_provider = c_provider.newInstance();
                    if(o_provider instanceof AuthorizationMapping) {
                        AuthorizationMapping provider = (AuthorizationMapping)(o_provider);
                        Log.debug("AuthorizationManager: Loaded " + s_provider);
                        authorizationMapping.add(provider);
                    } else {
                        Log.debug("AuthorizationManager: Unknown class type.");
                    }
                } catch (Exception e) {
                    Log.error("AuthorizationManager: Error loading AuthorizationMapping: " + s_provider + "\n" + e);
                }
            }
        }
        if (authorizationMapping.isEmpty()) {
            Log.debug("AuthorizationManager: No AuthorizationMapping's found. Loading DefaultAuthorizationMapping");
            authorizationMapping.add(new DefaultAuthorizationMapping());
        }
    }

    private AuthorizationManager() {

    }

    /**
     * Returns the currently-installed AuthorizationProvider. Warning: You
     * should not be calling the AuthorizationProvider directly to perform
     * authorizations, it will not take into account the policy selected in
     * the <tt>openfire.xml</tt>.  Use @see{authorize} in this class, instead.
     *
     * @return the current AuthorizationProvider.
     */
    public static Collection<AuthorizationPolicy> getAuthorizationPolicies() {
        return authorizationPolicies;
    }

    /**
     * Returns a singleton AuthorizationManager instance.
     *
     * @return a AuthorizationManager instance.
     */
    public static AuthorizationManager getInstance() {
        return instance;
    }

    /**
     * Authorize the authenticated used to the requested username.  This uses the
     * selected the selected AuthenticationProviders.
     *
     * @param username The requested username.
     * @param principal The authenticated principal.
     * @return true if the user is authorized.
     */

    public static boolean authorize(String username, String principal) {
        for (AuthorizationPolicy ap : authorizationPolicies) {
            if (Log.isDebugEnabled()) {
                Log.debug("AuthorizationManager: Trying "+ap.name()+".authorize("+username+" , "+principal+")");
            }

            if (ap.authorize(username, principal)) {
                // Authorized..  but do you exist?
                try {
                    UserManager.getUserProvider().loadUser(username);
                }
                catch (UserNotFoundException nfe) {
                    if (Log.isDebugEnabled()) {
                        Log.debug("AuthorizationManager: User " + username + " not found " + nfe.toString());
                    }
                    // Should we add the user?
                    if(JiveGlobals.getBooleanProperty("xmpp.auth.autoadd",false)) {
                        if (UserManager.getUserProvider().isReadOnly()) {
                            return false;
                        }
                        if (UserManager.getUserProvider().isNameRequired() || UserManager.getUserProvider().isEmailRequired()) {
                            // If these are required, there's no way we can arbitrarily auto-create this account.
                            return false;
                        }
                        try {
                            UserManager.getUserProvider().createUser(username, StringUtils.randomString(8), null, null);
                            if (Log.isDebugEnabled()) {
                                Log.info("AuthorizationManager: User "+username+" created.");
                            }
                            return true;
                        }
                        catch (UserAlreadyExistsException uaee) {
                            // Somehow the user got created in this very short timeframe.. 
                            // To be safe, lets fail here. The user can always try again.
                            if (Log.isDebugEnabled()) {
                                Log.error("AuthorizationManager: User " + username +
                                        " already exists while attempting to add user.");
                            }
                            return false;
                        }
                    }
                    return false;
                }
                // User exists
                return true;
            }
        }
        // Not authorized.
        return false;
    }

    /**
     * Map the authenticated principal to the default username.  If the authenticated 
     * principal did not supply a username, determine the default to use.
     *
     * @param principal The authentiated principal to determine the default username.
     * @return The default username for the authentiated principal.
     */

    public static String map(String principal) {
        for (AuthorizationMapping am : authorizationMapping) {
            if (Log.isDebugEnabled()) {
                Log.debug("AuthorizationManager: Trying " + am.name() + ".map(" + principal + ")");
            }
            String username = am.map(principal);
            if( ! username.equals(principal) ) {
                return username;
            }
        }
        return principal;
    }
}
