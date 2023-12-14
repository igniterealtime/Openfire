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
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation that defines the default authorization identity to be used, when none was selected by the
 * client.
 *
 * This class simply removes the realm (if any) from the authentication identity (or 'principal') if and only if
 * the realm matches the server's realm, the server's xmpp domain name, or any of the pre-approved realm names.
 * 
 * @author Jay Kline
 */
public class DefaultAuthorizationMapping implements AuthorizationMapping {

    private static final Logger Log = LoggerFactory.getLogger(DefaultAuthorizationMapping.class);

    /**
     * Returns the default authorization identity (the identity to act as) for a provided authentication identity
     * (or 'principal' - whose password is used).
     *
     * @param authcid authentication identity (or 'principal' whose password is used)
     * @return The name of the default authorization identity to use.
     */
    @Override
    public String map(String authcid) {
        if(authcid.contains("@")) {
            String realm = authcid.substring(authcid.lastIndexOf('@')+1);
            String authzid = authcid.substring(0,authcid.lastIndexOf('@'));

            if(realm.length() > 0) {
                if(realm.equals(XMPPServerInfo.XMPP_DOMAIN.getValue())) {
                    Log.debug("DefaultAuthorizationMapping: realm = " + XMPPServerInfo.XMPP_DOMAIN.getKey());
                    return authzid;
                } else if(realm.equals(SASLAuthentication.REALM.getValue())) {
                    Log.debug("DefaultAuthorizationMapping: ream = sasl.realm");
                    return authzid;
                } else {
                    for(String approvedRealm : SASLAuthentication.APPROVED_REALMS.getValue()) {
                        if(realm.equals(approvedRealm)) {
                            Log.debug("DefaultAuthorizationMapping: realm ("+realm+") = "+approvedRealm+" which is approved");
                            return authzid;
                        } else {
                            Log.debug("DefaultAuthorizationPolicy: realm ("+realm+") != "+approvedRealm+" which is approved");
                        }
                    }
                }
                Log.debug("DefaultAuthorizationMapping: No approved mappings found.");
                return authcid;
            } else {
                Log.debug("DefaultAuthorizationMapping: Realm has no length");
            }
        } else {
            Log.debug("DefaultAuthorizationMapping: No realm found");
        }
        return authcid;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    @Override
    public String name() {
        return "Default Mapping";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    @Override
    public String description() {
        return "Simply removes the realm of the requesting authentication identity ('principal') if and only if "+
               "the realm matches the server's realm, the server's XMPP domain name, or any of the pre-approved "+
               "realm names. Otherwise the input value (authentication identity) is returned as-is, causing it to " +
               "be used as the authorization identity (the identity to act as).";
    }
}
