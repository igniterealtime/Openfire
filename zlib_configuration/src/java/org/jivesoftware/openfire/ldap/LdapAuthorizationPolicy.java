/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-07 09:28:54 -0500 (Fri, 07 Apr 2006) $
 *
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
import org.xmpp.packet.JID;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

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
 * <li><tt>ldap.authorizeField = k5login</tt></li>
 * </ul>
 *
 * This implementation requires that LDAP be configured, obviously.
 *
 * @author Jay Kline
 */
public class LdapAuthorizationPolicy implements AuthorizationPolicy {

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
     * Returns if the principal is explicity authorized to the JID, throws
     * an UnauthorizedException otherwise
     *
     * @param username  The username requested.import org.jivesoftware.openfire.ldap.*;
     * @param principal The principal requesting the username.
     */
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

        Collection<String> authorized = new ArrayList<String>();
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            // Load record.
            String[] attributes = new String[]{
                    usernameField,
                    authorizeField
            };
            ctx = manager.getContext();
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            Attribute authorizeField_a = attrs.get(manager.getNameField());
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
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return authorized;
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "LDAP Authorization Policy";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public String description() {
        return "Provider for authorization using LDAP. Checks if the authenticated principal is in the user's LDAP object using the authorizeField property.";
    }
}
