/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.xmpp.packet.JID;

/**
 * @author Noah Campbell
 */
public interface ForeignContact {

    /**
     * Get the JID, constructing it if necessary.
     * 
     * @return jid returns the jid.
     */
    public JID getJid();

    /**
     * Add a session to the associated sessions for this foreign contact.
     * 
     * @param session
     */
    public void addSession(GatewaySession session);

    /**
     * Remove a <code>GatewaySession</code> from the foreign contact.
     * 
     * @param session
     */
    public void removeSession(GatewaySession session);

    /**
     * Returns true if at least one associated session is connected.
     * 
     * @return connected 
     */
    public boolean isConnected();

    /**
     * Return the translated status for the contact.
     * 
     * @return Status
     */
    public Status getStatus();

    /**
     * Return the name of the contact.
     * 
     * @return name The name of the contact.
     */
    public String getName();

}
