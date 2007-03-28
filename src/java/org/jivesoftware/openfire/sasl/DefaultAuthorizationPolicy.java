/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-20 10:46:24 -0500 (Thu, 20 Apr 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.XMPPServer;

/**
 * This policy will authorize any principal that matches exactly the full
 * JID (REALM and server name must be the same if using GSSAPI) or any
 * principal that matches exactly the username (without REALM or server
 * name). This does exactly what users expect if not supplying a seperate
 * principal for authentication.
 *
 * @author Jay Kline
 */
public class DefaultAuthorizationPolicy extends AbstractAuthorizationPolicy
        implements AuthorizationProvider {

    private String serverName;

    public DefaultAuthorizationPolicy() {
        serverName = XMPPServer.getInstance().getServerInfo().getName();
    }

    /**
     * Returns true if the principal is explicity authorized to the JID
     *
     * @param username  The username requested.
     * @param principal The principal requesting the username.
     * @return true is the user is authorized to be principal
     */
    public boolean authorize(String username, String principal) {
        return (principal.equals(username) || principal.equals(username + "@" + serverName));
    }

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public String name() {
        return "Default Policy";
    }

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public String description() {
        return "This policy will authorize any principal that matches exactly the full " +
                "JID (REALM and server name must be the same if using GSSAPI) or any principal " +
                "that matches exactly the username (without REALM or server name). This does " +
                "exactly what users expect if not supplying a seperate principal for authentication.";
    }
}
    