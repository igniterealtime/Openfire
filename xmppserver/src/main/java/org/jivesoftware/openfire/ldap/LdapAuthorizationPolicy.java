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

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.openfire.auth.AuthorizationPolicy;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

/**
 * Provider for authorization using LDAP. Checks if the XMPP authentication identity, or 'principal' (identity whose
 * password will be used) is in the user's LDAP object using the authorizeField from the system properties. An entry in
 * that file would look like the following:
 *
 * <ul>
 * <li>{@code ldap.authorizeField = k5login}</li>
 * </ul>
 *
 * This implementation requires that LDAP be configured, obviously.
 *
 * @author Jay Kline
 */
public class LdapAuthorizationPolicy implements AuthorizationPolicy {

    private static final Logger Log = LoggerFactory.getLogger(LdapAuthorizationPolicy.class) ;

    private LdapManager manager;
    private String usernameField;
    private String authorizeField;

    public LdapAuthorizationPolicy() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("ldap.authorizeField");

        manager = LdapManager.getInstance();
        usernameField = manager.getUsernameField();
        authorizeField = JiveGlobals.getProperty("ldap.authorizeField", "k5login");
    }

    /**
     * Returns true if the provided XMPP authentication identity (identity whose password will be used) is explicitly
     * allowed to the provided XMPP authorization identity (identity to act as).
     *
     * @param authzid XMPP authorization identity (identity to act as).
     * @param authcid XMPP authentication identity, or 'principal' (identity whose password will be used)
     * @return true if the authzid is explicitly allowed to be used by the user authenticated with the authcid.
     */
    @Override
    public boolean authorize(String authzid, String authcid) {
        return getAuthorized(authzid).contains(authcid);
    }

    /**
     * Returns a collection of XMPP authentication identities (or 'principals') that are authorized to use the XMPP
     * authorization identity (identity to act as) as provided in the argument of this method.
     *
     * @param authzid XMPP authorization identity (identity to act as).
     * @return A collection of XMPP authentication identities (or 'principals') that are authorized to use the authzid
     */
    private Collection<String> getAuthorized(String authzid) {
        // Un-escape Node
        authzid = JID.unescapeNode(authzid);

        Collection<String> authorized = new ArrayList<>();
        DirContext ctx = null;
        try {
            Rdn[] userRDN = manager.findUserRDN(authzid);
            // Load record.
            String[] attributes = new String[]{
                    usernameField,
                    authorizeField
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(LdapManager.escapeForJNDI(userRDN), attributes);
            Attribute authorizeField_a = attrs.get(authorizeField);
            if (authorizeField_a != null) {
                for (Enumeration e = authorizeField_a.getAll(); e.hasMoreElements();) {
                    authorized.add((String)e.nextElement());
                }
            }

            return authorized;
        }
        catch (Exception ex) {
            Log.debug("An exception occurred while trying to retrieve authorized principals for user {}.", authzid, ex);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to retrieve authorized principals for user {}.", authzid, ex);
            }
        }
        return authorized;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    @Override
    public String name() {
        return "LDAP Authorization Policy";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    @Override
    public String description() {
        return "Provider for authorization using LDAP. Checks if the authentication identity, or 'principal' (identity" +
            " whose password will be used) is in the user's LDAP object using the authorizeField property.";
    }
}
