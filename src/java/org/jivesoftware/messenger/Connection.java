/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.Packet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * <p>Represents a connection on the server.</p>
 *
 * @author Iain Shigeoka
 */
public interface Connection {

    /**
     * <p>Verify that the connection is still live.</p>
     * <p>Typically this is done by sending a whitespace character between packets.</p>
     *
     * @return True if the socket remains valid, false otherwise
     */
    boolean validate();

    /**
     * Initializes the connection with it's owning session. Allows the
     * connection class to configure itself with session related information
     * (e.g. stream ID).
     *
     * @param session The session that owns this connection
     */
    void init(Session session);

    /**
     * <p>Obtain the InetAddress describing the connection.</p>
     *
     * @return The InetAddress describing the underlying connection properties
     * @throws UnauthorizedException If caller doesn't have permission to
     *                               access this resource
     */
    InetAddress getInetAddress() throws UnauthorizedException, UnknownHostException;

    /**
     * <p>Obtain the XmlSerializer used to send data to the client.</p>
     * <P>The serializer should only be used to obtain information about the
     * serialization and should not be written to directly. Other threads maybe
     * trying to write to the serializer so it is important that all writes are
     * properly synchronized.</p>
     *
     * @return The XmlSerializer underlying this connection
     * @throws UnauthorizedException If caller doesn't have permission to access this resource
     */
    XMLStreamWriter getSerializer() throws UnauthorizedException;

    /**
     * Close this session including associated socket connection.
     * <p/>
     * Any selector registrations (if using nio) are also removed.
     * The order of events for closing the session is:
     * <ul>
     * <li>set closing flag to prevent redundant shutdowns
     * <li>notifyEvent all listeners that the channel is shutting down
     * <li>close the socket
     * </ul>
     *
     * @throws UnauthorizedException If caller doesn't have permission to access this resource
     */
    void close() throws UnauthorizedException;

    /**
     * Retrieve the closed state of the Session.
     *
     * @return True if the session is closed
     */
    boolean isClosed();

    /**
     * <p>Determines if this connection is secure.</p>
     *
     * @return True if the connection is secure (e.g. SSL/TLS)
     */
    boolean isSecure();

    /**
     * Register a listener for close event notification.  Registrations after
     * the Session is closed will be immediately notified <em>before</em>
     * the registration call returns (within the context of the
     * registration call). An optional handback object can be associated with
     * the registration if the same listener is registered to listen for multiple
     * connection closures.
     *
     * @param listener        The listener to register for events
     * @param handbackMessage The object to send in the event notification
     * @return The message previously registered for this channel or null if no registration existed
     * @throws UnauthorizedException If caller doesn't have permission to access this resource
     */
    Object registerCloseListener(ConnectionCloseListener listener, Object handbackMessage) throws UnauthorizedException;

    /**
     * 9
     * Remove a registered close event listener.  Registered listeners must
     * be able to receive close events up until the time this method returns.
     * (i.e. It is possible to call unregister, receive a close event registration,
     * and then have the unregister call return.)
     *
     * @param listener The listener  to deregister for close events
     * @return The Message registered with this listener or null if the channel was never registered
     * @throws UnauthorizedException If caller doesn't have permission to access this resource
     */
    Object removeCloseListener(ConnectionCloseListener listener) throws UnauthorizedException;

    /**
     * Delivers the packet to this XMPPAddress without checking the recipient.
     * The method essentially calls
     * <code>socket.send(packet.getWriteBuffer())</code>
     *
     * @param packet The packet to deliver.
     * @throws UnauthorizedException If caller doesn't have permission to access this resource
     * @throws XMLStreamException    if there was a problem sending the packet
     */
    void deliver(Packet packet) throws UnauthorizedException, XMLStreamException;
}
