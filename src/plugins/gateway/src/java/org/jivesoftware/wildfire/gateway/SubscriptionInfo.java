/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import java.io.Serializable;

import org.xmpp.packet.JID;

/**
 * <code>SubscriptionInfo</code> contains all the information that pertains to 
 * a legacy gateway that must be persisted across sessions.  For example, 
 * username and password are stored so the user does not have to enter the 
 * information repeatedly.
 * 
 * @author Noah Campbell
 */
public class SubscriptionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new <code>SubscriptionInfo</code>
     * @param username The username
     * @param password The password
     */
    public SubscriptionInfo(String username, String password, JID jid) {
        this.username = username;
        this.password = password;
        this.jid = jid;
    }

    /**
     * Has the session been registered on the client?     
     */
    public boolean clientRegistered;

    /**
     * Has the server registered with the client?
     */ 
    public boolean serverRegistered;

    /**
     * The username.
     */
    public String username;

    /**
     * The password.
     */
    public String password;

    /**
     * The jid.
     *
     * @see org.xmpp.packet.JID
     */
    //public transient JID jid;
    public JID jid;

}
