package org.jivesoftware.messenger.net;

import org.xmpp.packet.Packet;
import org.jivesoftware.messenger.Session;

/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

/**
 * The Composite Packet class defines the holder of both a <code>Packet</code> and it's associated
 * <code>Session</code> object.
 */
public class CompositePacket {
    private Session session;
    private Packet packet;

    public CompositePacket(){

    }

    public CompositePacket(Packet packet, Session session){
        setPacket(packet);
        setSession(session);
    }

    /**
     * Returns the Session.
     * @return the Session to return.
     */
    public Session getSession() {
        return session;
    }

    /**
     * Sets the Session for this packet.
     * @param session the Session to set.
     */
    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * Returns the Packet.
     * @return the Packet to return.
     */
    public Packet getPacket() {
        return packet;
    }

    /**
     * Sets the Packet.
     * @param packet the Packet to set.
     */
    public void setPacket(Packet packet) {
        this.packet = packet;
    }
}
