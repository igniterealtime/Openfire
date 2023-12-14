/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.auth;

import java.util.*;

import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the AuthorizationProvider objects.
 *
 * Overall description of the authentication and authorization process:
 *
 * After a client connects, and indicates a desire to use SASL, the SASLAuthentication object decides which SASL
 * mechanisms to advertise, and then performs the authentication. If authentication is successful, the
 * XMPPCallbackHandler is asked to handle() an AuthorizeCallback.
 *
 * The XMPPCallbackHandler asks the AuthorizationManager to authorize the authentication identity whose password was
 * used (the 'principal) to the requested authorization identity (the username that the user wants to act as).
 *
 * The AuthorizationManager manages a list of AuthorizationProvider classes, and tries them one at a time and returns
 * true with the first AuthorizationProvider that authorizes the authentication identity to the authorization identity.
 *
 * If no classes authorize the authentication identity, false is returned, which traces all the way back to give the
 * client an unauthorized message. It's important to note that the message the client receives will give no indication
 * if the authentiation identity authenticated successfully. You will need to check the server logs for that information.
 *
 * @author Jay Kline
 */
public class AuthorizationManager {

    private static final Logger Log = LoggerFactory.getLogger(AuthorizationManager.class);

    private static final ArrayList<AuthorizationPolicy> authorizationPolicies = new ArrayList<>();
    private static final ArrayList<AuthorizationMapping> authorizationMapping = new ArrayList<>();

    static {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("provider.authorization.classList");
        JiveGlobals.migrateProperty("provider.authorizationMapping.classList");

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

    // static utility class; do not instantiate
    private AuthorizationManager() { }

    /**
     * Returns the currently-installed AuthorizationProvider. Warning: You
     * should not be calling the AuthorizationProvider directly to perform
     * authorizations, it will not take into account the policy selected in
     * the {@code openfire.xml}.  Use @see{authorize} in this class, instead.
     *
     * @return the current AuthorizationProvider.
     */
    public static Collection<AuthorizationPolicy> getAuthorizationPolicies() {
        return authorizationPolicies;
    }

    /**
     * Authorize the authenticated username (authcid, principal) to the requested username (authzid).
     *
     * This uses the selected AuthenticationProviders.
     *
     * @param authzid authorization identity (identity to act as).
     * @param authcid authentication identity (identity whose password will be used)
     * @return true if the user is authorized to act as the requested authorization identity.
     */
    public static boolean authorize(String authzid, String authcid) {
        for (AuthorizationPolicy ap : authorizationPolicies) {
            Log.trace("Trying if AuthorizationPolicy {} authorizes {} to act as {}", ap.name(), authcid, authzid);

            if (ap.authorize(authzid, authcid)) {
                // Authorized...  but do you exist?
                try {
                    UserManager.getUserProvider().loadUser(authzid);
                }
                catch (UserNotFoundException nfe) {
                    Log.debug("User {} not found ", authzid, nfe);

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
                            UserManager.getInstance().createUser(authzid, StringUtils.randomString(8), null, null);
                            Log.info("AuthorizationManager: User {} created.", authzid);
                            return true;
                        }
                        catch (UserAlreadyExistsException uaee) {
                            // Somehow the user got created in this very short timeframe.. 
                            // To be safe, lets fail here. The user can always try again.
                            Log.error("AuthorizationManager: User {} already exists while attempting to add user.", authzid, uaee);
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
     * Map the authenticated username (authcid, principal) to the username to act as (authzid).
     *
     * If the authenticated username did not supply a username to act as, determine the default to use.
     *
     * @param authcid authentication identity (identity whose password will be used), for which to determine the username to act as (authzid).
     * @return The username to act as (authzid) for the provided authentication identity.
     */
    public static String map(String authcid) {
        for (AuthorizationMapping am : authorizationMapping) {
            Log.trace("Trying if AuthorizationMapping {} provides an authzid that is different from the provided authcid {}", am.name(), authcid);
            String authzid = am.map(authcid);
            if(!authzid.equals(authcid)) {
                Log.trace("AuthorizationMapping {} provided an authzid {} for the provided authcid {}", am.name(), authzid, authcid);
                return authzid;
            }
        }
        return authcid;
    }
}
