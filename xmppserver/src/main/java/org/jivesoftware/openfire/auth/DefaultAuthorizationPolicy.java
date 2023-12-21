/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Different clients perform authentication differently, so this policy will authorize any authentication identity, or
 * 'principal' (identity whose password will be used) to a requested authorization identity (identity to act as) that
 * match specific conditions that are considered secure defaults for most installations.
 *
 * Keep in mind if a client does not request any authorization identity, the authentication identity will be used as the
 * authorization identity.
 *
 * <ul>
 * <li>If the authentication identity is in the form of a plain username, and the requested authorization identity is in
 *     the form of a plain username, then the two must be exactly the same.
 * <li>If the authentication identity contains an '@', then the portion before the '@' must match exactly the requested
 *     authorization identity and the portion after the '@' must match at least one of the following:
 *     <ul>
 *     <li>The XMPP domain of the server
 *     <li>The SASL realm of the server
 *     <li>Be in the list of acceptable realms
 *     </ul>
 * <li>If the requested authorization identity contains an '@' then the portion before the '@' will be considered the
 *     requested authorization identity only if the portion after the '@' matches the XMPP domain of the server or the
 *     portion after the '@' in the authentication identity, if any.
 * </ul>
 * 
 * @see AuthorizationManager
 * @author Jay Kline
 */
public class DefaultAuthorizationPolicy implements AuthorizationPolicy {

    private static final Logger Log = LoggerFactory.getLogger(DefaultAuthorizationPolicy.class);

    public static final SystemProperty<Boolean> IGNORE_CASE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.ignorecase")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    /**
     * Returns true if the provided authentication identity (identity whose password will be used) is explicitly allowed
     * to the provided authorization identity (identity to act as).
     *
     * @param authzid authorization identity (identity to act as).
     * @param authcid authentication identity, or 'principal' (identity whose password will be used)
     * @return true if the authzid is explicitly allowed to be used by the user authenticated with the authcid.
     */
    @Override
    public boolean authorize(String authzid, String authcid) {
        boolean authorized = false;

        String authzUser = authzid;
        String authzRealm = null;
 
        String authcUser = authcid;
        String authcRealm = null;

        if (authzid.contains("@")) {
            authzUser = authzid.substring(0, authzid.lastIndexOf("@"));
            authzRealm = authzid.substring((authzid.lastIndexOf("@")+1));
        }
        if (authcid.contains("@")){
            authcUser = authcid.substring(0,(authcid.lastIndexOf("@")));
            authcRealm = authcid.substring((authcid.lastIndexOf("@")+1));
        }

        if (!SASLAuthentication.PROXY_AUTH.getValue() || !AdminManager.getInstance().isUserAdmin(authcUser, true)) {
            if(!authzUser.equals(authcUser)) {
                // For this policy the user portion of both must match, so lets short circuit here if we can.
                if(IGNORE_CASE.getValue()) {
                    if(!authzUser.equalsIgnoreCase(authcUser)){
                        Log.debug("Authorization username {} doesn't match authentication username {}", authzUser, authcUser);
                        return false;
                    }
                } else {
                    Log.debug("Authorization username {} doesn't match authentication username {}", authzUser, authcUser);
                    return false;
                }
            }
        }
        Log.debug("Checking authcRealm");
        if(authcRealm != null) {
            if(authcRealm.equals(XMPPServerInfo.XMPP_DOMAIN.getValue()))  {
                Log.trace("authcRealm = {}", XMPPServerInfo.XMPP_DOMAIN.getKey());
                authorized = true;
            } else if(authcRealm.equals(SASLAuthentication.REALM.getValue()))  {
                Log.trace("authcRealm = sasl.realm");
                authorized = true;
            } else { 
                for(String realm : SASLAuthentication.APPROVED_REALMS.getValue()) {
                    if(authcRealm.equals(realm)) {
                        Log.trace("authcRealm = {} which is approved", realm);
                        authorized = true;
                    } else {
                        Log.trace("authcRealm != {} which is approved", realm);
                    }
                }
            }
        } else {
            // no realm in the authcRealm
            authorized = true;
        }

        if(!authorized) {
            return false;
        }  else {
            // reset for next round of tests
            authorized = false;
        }
        Log.debug("Checking authzRealm");
        if(authzRealm != null) {
            if(authzRealm.equals(XMPPServerInfo.XMPP_DOMAIN.getValue())) {
                Log.trace("authcRealm = {}", XMPPServerInfo.XMPP_DOMAIN.getKey());
                authorized = true;
            } else {
                if(authcRealm != null && authcRealm.equals(authzRealm)) {
                    Log.trace("DefaultAuthorizationPolicy: authcRealm = {} which is approved", authcRealm);
                    authorized = true;
                }
            }
        } else {
            authorized = true;
        }

        // no more checks
        return authorized;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    @Override
    public String name() {
        return "Default Policy";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    @Override
    public String description() {
        return "Different clients perform authentication differently, so this policy will authorize any authentication " +
            "identity, or 'principal' (identity whose password will be used) to a requested authorization identity " +
            "(identity to act as) that match specific conditions that are considered secure defaults for most installations.";
    }
}
    
