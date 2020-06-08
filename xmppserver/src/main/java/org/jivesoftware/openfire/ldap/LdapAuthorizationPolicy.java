/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
 * Provider for authorization using LDAP. Checks if the authenticated
 * principal is in the user's LDAP object using the authorizeField
 * from the system properties. An entry in that file would
 * look like the following:
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
     * Returns if the principal is explicitly authorized to the JID, throws
     * an UnauthorizedException otherwise
     *
     * @param username  The username requested.import org.jivesoftware.openfire.ldap.*;
     * @param principal The principal requesting the username.
     */
    @Override
    public boolean authorize(String username, String principal) {
        return getAuthorized(username).contains(principal);
    }

    /**
     * Returns a String Collection of principals that are authorized to use
     * the named user.
     *
     * @param username the username.
     * @return A String Collection of principals that are authorized.
     */
    private Collection<String> getAuthorized(String username) {
        // Un-escape Node
        username = JID.unescapeNode(username);

        Collection<String> authorized = new ArrayList<>();
        DirContext ctx = null;
        try {
            Rdn[] userRDN = manager.findUserRDN(username);
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
        catch (Exception e) {
            // Ignore.
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug("An exception occurred while trying to close a LDAP context after trying to retrieve authorized principals for user {}.", username, ex);
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
        return "Provider for authorization using LDAP. Checks if the authenticated principal is in the user's LDAP object using the authorizeField property.";
    }
}
