/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.XMPPError;
import org.jivesoftware.messenger.XMPPPacket;

/**
 * <p>Implementation of the common packet functionality to ease implementation of packet
 * descendants.</p>
 * <p>The abstract packet tracks the packet's known routing information but has no
 * fragment representation.</p>
 *
 * @author Iain Shigeoka
 */
abstract public class XMPPAbstractPacket implements XMPPPacket {

    /**
     * <p>The packet's sender.</p>
     */
    protected XMPPAddress sender;

    /**
     * <p>The packet's recipient.</p>
     */
    protected XMPPAddress recipient;

    /**
     * <p>The packet's originating session.</p>
     */
    protected Session session;

    /**
     * <p>The packet's type.</p>
     */
    protected int packetType;

    /**
     * <p>The name of the server this packet originated on.</p>
     */
    protected String serverName;

    /**
     * The error of the packet or null if no error set.
     */
    protected XMPPError error;

    /**
     * <p>Create a packet with appropriate routing information.</p>
     *
     * @param sender     The packet's sender
     * @param recipient  The packet's recipient
     * @param session    The packet's originating session
     * @param packetType The type of packet
     */
    public XMPPAbstractPacket(XMPPAddress sender, XMPPAddress recipient, Session session, int packetType) {
        this.sender = sender;
        this.recipient = recipient;
        this.session = session;
        this.packetType = packetType;
        if (session != null) {
            serverName = session.getServerName();
        }
    }

    public int getPacketType() {
        return packetType;
    }

    public XMPPAddress getRecipient() {
        return recipient;
    }

    public XMPPAddress getSender() {
        return sender;
    }

    public Session getOriginatingSession() {
        return session;
    }

    public XMPPError getError() {
        return error;
    }

    public XMPPPacket.Type typeFromString(String type) {
        return ERROR;
    }

}
