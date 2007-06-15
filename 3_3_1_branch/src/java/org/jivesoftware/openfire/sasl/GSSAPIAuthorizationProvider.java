/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;

/**
 * Provider for gsaapi authorization using one of the 3 policy states.
 *
 * @author Derek DeMoro
 */
public class GSSAPIAuthorizationProvider implements AuthorizationPolicy {

    private AuthPolicy currentPolicy;

    /**
     * AuthorizationPolicies defines different authorization types using GSAAPI.
     */
    private static enum AuthPolicy {
        /**
         * This policy will authorize any principal who's username matches exactly
         * the username of the JID. This means when cross realm authentication is
         * allowed, user@REALM_A.COM and user@REALM_B.COM could both authorize as
         * user@servername, so there is some risk here. But if usernames across the
         */
        loose,
        /**
         * This policy will authorize any principal who:
         * <p/>
         * <li> Username of principal matches exactly the username of the JID </li>
         * <li> The user principal's realm matches exactly the realm of the server.</li>
         * Note that the realm may not match the servername, and in fact for this
         * policy to be useful it will not match the servername. RFC3920 Section
         * 6.1, item 7 states that if the principal (authorization entity) is the
         * same as the JID (initiating entity), its MUST NOT provide an authorization
         * identity. In practice however, GSSAPI will provide both. (Note: Ive
         * not done extensive testing on this)
         */
        strict;
    }

    public GSSAPIAuthorizationProvider() {
    }


    /**
     * Authorize the authenticated used to the requested username.  This uses the
     * GSSAPIAuthProvider if it is the specified provider.
     *
     * @param username  the username.
     * @param principal the principal.
     * @return true if the user is authorized.
     */
    public boolean authorize(String username, String principal) {
        // Find set Authorization Policy
        // Find set Authorization Policy
        final String policy = JiveGlobals.getProperty("gssapi.auth.policy", "strict");
        if ("strict".equals(policy)) {
            currentPolicy = AuthPolicy.strict;
        }
        else if ("loose".equals(policy)) {
            currentPolicy = AuthPolicy.loose;
        }

        // Handle authorization policy
        if (currentPolicy == AuthPolicy.strict) {
            String serverName = XMPPServer.getInstance().getServerInfo().getName();
            return (principal.equals(username) || principal.equals(username + "@" + serverName.toUpperCase()));
        }
        else if (currentPolicy == AuthPolicy.loose) {
            return (principal.startsWith(username + "@"));
        }

        return false;
    }


    public String name() {
        return "GSSAPI Authorization Policy";
    }

    public String description() {
        return "This policy will authorize a principal based on the set policy for the server.";
    }
}
