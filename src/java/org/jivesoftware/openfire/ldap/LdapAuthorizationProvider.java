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

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.sasl.AbstractAuthorizationProvider;
import org.jivesoftware.openfire.sasl.AuthorizationProvider;
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
 * from the <tt>openfire.xml</tt> file. An entry in that file would
 * look like the following:
 *
 * <pre>
 *   &lt;ldap&gt;
 *     &lt;authorizeField&gt; k5login &lt;/authorizeField&gt;
 *   &lt;/ldap&gt;</pre>
 *
 * This implementation requires that LDAP be configured, obviously.
 *
 * @author Jay Kline
 */
public class LdapAuthorizationProvider extends AbstractAuthorizationProvider implements AuthorizationProvider  {

    private LdapManager manager;
    private String usernameField;
    private String authorizeField;

    public LdapAuthorizationProvider() {
        manager = LdapManager.getInstance();
        usernameField = manager.getUsernameField();
        authorizeField = JiveGlobals.getXMLProperty("ldap.authorizeField", "k5login");
    }
    
    /**
     * Returns if the principal is explicity authorized to the JID, throws 
     * an UnauthorizedException otherwise
     *
     * @param username The username requested.import org.jivesoftware.openfire.ldap.*;
     * @param principal The principal requesting the username.
     *
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
    public Collection<String> getAuthorized(String username) {
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
                for(Enumeration e = authorizeField_a.getAll(); e.hasMoreElements();) {
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
     * Returns false, this implementation is not writeable.
     *
     * @return False.
     */
    public boolean isWritable() {
        return false;
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principal The principal authorized to use the named user.
     * @throws UnsupportedOperationException If this AuthorizationProvider cannot be updated.
     */
    public void addAuthorized(String username, String principal) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principals The Collection of principals authorized to use the named user.
     */
    public void addAuthorized(String username, Collection<String> principals) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Always throws UnsupportedOperationException.
     *
     * @param username The username.
     * @param principals The Collection of principals authorized to use the named user.
     * @throws UnsupportedOperationException If this AuthorizationProvider cannot be updated.
     */
    public void setAuthorized(String username, Collection<String> principals) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "LDAP Authorization Provider";
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