/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A packet is a routeable XML fragment containing information on sender,
 * recipient, and XMPP standard packet type.
 *
 * @author Iain Shigeoka
 */
public interface XMPPPacket extends XMPPFragment {

    public static final Type ERROR = new Type("error");

    /**
     * <p>Obtain the flag indicating whether this packet is being
     * sent (true) or received (false) by the server.</p>
     * <p/>
     * <p>The same packet may be received by the server at a c2s
     * connection making isSending() false, then the server routes
     * the packet and sets the isSending() to true for delivery
     * out another c2s connection.</p>
     *
     * @return true if the packet is in the process of being sent.
     */
    boolean isSending();

    /**
     * <p>Set a flag indicating whether this packet is being
     * sent (true) or received (false) by the server.</p>
     *
     * @param isSending true if the packet is in the process of being sent.
     */
    void setSending(boolean isSending);

    /**
     * <p>Returns the priority of the packet for routing and handling.</p>
     * <p>Valid values are <tt>XMPPPacket.ROUTE_PRIORITY_HIGH</tt>,
     * <tt>XMPPPacket.ROUTE_PRIORITY_NORMAL</tt> or
     * <tt>XMPPPacket.ROUTE_PRIORITY_LOW</tt>. The
     * priority determines how quickly a packet will be routed through
     * a Channel, with higher priority packet being handled first. Any
     * ChannelHandler can set the priority of a packet. Subsequently,
     * that priority should apply for all future channels until a
     * new priority setting is made.</p>
     *
     * @return the routing priority of the packet.
     */
    RoutePriority getRoutePriority();

    /**
     * <p>Sets the priority of the packet for routing and handling.</p>
     * <p>Valid values are <tt>XMPPPacket.ROUTE_PRIORITY_HIGH</tt>,
     * <tt>XMPPPacket.ROUTE_PRIORITY_NORMAL</tt> or
     * <tt>XMPPPacket.ROUTE_PRIORITY_LOW</tt>. The
     * priority determines how quickly a packet will be routed
     * through a Channel, with higher priority packet being
     * handled first. Any ChannelHandler can set the priority
     * of a packet. Subsequently, that priority should apply
     * for all future channels until a new priority setting is made.</p>
     *
     * @param priority the routing priority of the packet.
     */
    void setRoutePriority(RoutePriority priority);

    /**
     * <p>Makes this packet an error packet and sets it's error
     * code to the given value.</p>
     * <p/>
     * <p>Errors are generically handled for XMPP 1.0 errors and
     * old Jabber style errors.</p>
     *
     * @param errorCode The new error code for this packet
     */
    void setError(XMPPError.Code errorCode);

    /**
     * <p>Get the error for this packet if the packet is of type 'error'.</p>
     * <p>Packets of type 'error' MUST have an error payload. If the error
     * packet is of type 'error' and no error object is currenly set, this method
     * MUST return an unknown error (not null). Similarly, if the packet type is
     * not 'error' this method will always return null.</p>
     *
     * @return error The error payload for this packet or null if none exists
     */
    XMPPError getError();

    /**
     * <p>Obtain the id attribute of the packet.</p>
     * <p/>
     * <p>IDs are used to identify related packets. In IQ request-response
     * pairs, the ID on the request is always set to the same value on
     * the response to make matching of the two possible.</p>
     *
     * @return The ID of the packet or null if none is set
     */
    String getID();

    /**
     * <p>Set the id attribute of the packet.</p>
     *
     * @param id The ID of the packet or null if none is set
     * @see #getID
     */
    void setID(String id);

    /**
     * Get the recipient JID for this packet. This taken from the "to" attribute
     * of incoming packets or set if you want to force a destination.
     *
     * @return The recipient's JID
     */
    XMPPAddress getRecipient();

    /**
     * <p>Set the recipient JID for this packet (will be used with the 'to'
     * attribute of the packet).</p>
     * <p/>
     * <p>Setting the recipient to null leaves the 'to' attribute blank
     * and eliminates the ability for the server to route the packet by
     * recipient address. In other words, a packet with null recipient
     * will not be routed automatically by the server.</p>
     *
     * @param recipient The address of the recipient of this packet or
     *      null to leave the field blank.
     */
    void setRecipient(XMPPAddress recipient);

    /**
     * <p>Sets the sender JID for this packet (will be used with the 'from'
     * attribute of the packet).</p>
     * <p/>
     * <p>The sender can be null, in which case the server will attempt to
     * set the from field using the address associated with the originating
     * session. If the originating session is null, or does not have an
     * address, then the from attribute is not set on the packet.</p>
     *
     * @param sender The sender of this packet
     */
    void setSender(XMPPAddress sender);

    /**
     * Get the sender's JID for this packet. This forced to be the originating
     * sender on c2s packets or taken from the "from" attribute on
     * of incoming s2s packets. The value of the "from" attribute is set
     * to match the sender on outgoing packets regardless
     * of the presence or value of the "from" attribute.
     *
     * @return the sender's JID.
     */
    XMPPAddress getSender();

    /**
     * Gets the session that created this packet. The default is null
     * which indicates that the packet was created by the server.
     * The distinction between sender and originating session is needed
     * for server and s2s generated packets.
     *
     * @return the session that created this packet.
     */
    Session getOriginatingSession();

    /**
     * <p>Sets the session that created this packet.</p>
     *
     * @param session the session that created this packet or null if
     *      the server sent the packet
     */
    void setOriginatingSession(Session session);

    /**
     * <p>Parse the packet from the given parser.</p>
     * <p>The XPP parser MUST be set to the start tag of the packet's root element
     * before calling this method and will always return with the xpp parser
     * on the end tag token of the packet's root element.</p>
     *
     * @param xpp the XPP to pull the packet from.
     */
    void parse(XMLStreamReader xpp) throws XMLStreamException;

    /**
     * <p>Obtain the type-safe type attribute from a string.</p>
     *
     * @param type the string type (may be null).
     * @return the corrresponding type.
     */
    Type typeFromString(String type);

    /**
     * Sets the type of packet. The type is used in the 'type'
     * attribute of the packet and for determining output formatting
     * such as whether to use an Error packet to decorate this packet.
     *
     * @param type the type of this packet.
     */
    void setType(Type type);

    /**
     * Obtain the type of this packet.
     *
     * @return the type of this packet.
     */
    Type getType();

    /**
     * Represents the type-safe 'type' attribute of a packet.
     */
    class Type {

        String value;

        protected Type(String value) {
            this.value = value;
        }

        public String toString() {
            return value;
        }
    }

    /**
     * Routing priority of a packet.
     */
    public enum RoutePriority {
        high, normal, low;
    }
}