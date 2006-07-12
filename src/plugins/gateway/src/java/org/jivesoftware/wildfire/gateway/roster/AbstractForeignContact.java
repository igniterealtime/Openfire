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

import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

import org.jivesoftware.wildfire.gateway.Gateway;
import org.jivesoftware.wildfire.gateway.GatewaySession;
import org.xmpp.packet.JID;

/**
 * All maintanence information pretaining to a gateway user.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public abstract class AbstractForeignContact implements Serializable, ForeignContact {
    /**
     * The serialVersionUID
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * The id for this contact.  This maps directly to the legacy userid.
     */
    public final String id;

    /**
     * The status of this contact.
     *
     * @see Status
     */
    public final Status status;
    
    /**
     * The jid associated with this foreign contact.
     *
     * @see JID
     */
    private transient JID jid;
    
    /**
     * The gatewayDomain.
     */
    private final String gatewayDomain;
    
    /**
     * The gatewayName.
     */
    private final String gatewayName;
    
    /**
     * The associatedSessions that are currently active for this <code>ForeignContact</code>.
     *
     * @see java.util.Set
     */
    private transient Set<GatewaySession> associatedSessions = new HashSet<GatewaySession>();
    
    /**
     * The format for a JID
     *
     * @see java.text.MessageFormat
     */
    private static final MessageFormat mf = new MessageFormat("{0}@{1}.{2}");
    
    /**
     * Create a ForeignContact relating to the gateway it originated from.
     * 
     * @param id the foreign contact id.
     * @param status the current status of the contact.
     * @param gateway the gateway the foreign contact is associated with.
     */
    public AbstractForeignContact(String id, Status status, Gateway gateway) {
        this.id = id;
        this.status = status;
        this.gatewayDomain = gateway.getDomain();
        this.gatewayName = gateway.getName();
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#getJid()
     */
    public JID getJid() {
        if(jid == null) {
            jid = new JID(mf.format(new Object[]{id, gatewayName, gatewayDomain}));
        }
        return jid;
    }
    
    /**
     * @param in
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unused")
    private void readObject(@SuppressWarnings("unused") java.io.ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        associatedSessions = new HashSet<GatewaySession>();
        getJid();
    }
    
    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#addSession(org.jivesoftware.wildfire.gateway.GatewaySession)
     */
    public void addSession(GatewaySession session) {
        associatedSessions.add(session);
    }
    
    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#removeSession(org.jivesoftware.wildfire.gateway.GatewaySession)
     */
    public void removeSession(GatewaySession session) {
        associatedSessions.remove(session);
    }
    
    /**
     * @see org.jivesoftware.wildfire.gateway.roster.ForeignContact#isConnected()
     */
    public boolean isConnected() {
        boolean connected = true;
        for(GatewaySession session : associatedSessions) {
            if(!session.isConnected()) {
                connected = false;
                break;
            }
        }
        return connected;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof AbstractForeignContact) {
            AbstractForeignContact fc = (AbstractForeignContact)obj;
            return fc.id.equalsIgnoreCase(this.id);
        } else {
            return super.equals(obj);
        }
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return this.getJid().toBareJID().hashCode();
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Foreign Contact: " + this.getJid() + "[Gateway: " + this.gatewayName + ", Connected: " + isConnected() + "]";  
    }
    
    
    
}
