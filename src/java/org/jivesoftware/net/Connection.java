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

package org.jivesoftware.net;

import org.jivesoftware.messenger.auth.UnauthorizedException;

import java.net.InetAddress;
import java.util.Date;

/**
 * <p>Generic representation of a network connection.</p>
 *
 * <p>This interface intentionally tries to abstract the underlying socket
 * to enable generic use with traditional and nio style sockets.</p>
 *
 * @author Iain Shigeoka
 */
public interface Connection {

    /**
     * <p>Obtain the date/time the connection was initially made.</p>
     */
    Date getConnectDate();

    /**
     * <p>Obtain the number of milliseconds the connection has been open.</p>
     *
     * <p>If the connection is still open, this is the time difference between
     * the result of getConnectDate() and the current time
     * (e.g. System.currentTimeMillis() otherwise it returns the
     * number of milliseconds between the opening and closing of the connection.</p>
     *
     * @return The number of milliseconds the connection has been open
     */
    long getUptime();

    /**
     * <p>Obtain the InetAddress describing the connection.</p>
     *
     * @return The InetAddress describing the underlying connection properties
     * access this resource
     */
    InetAddress getInetAddress();

    /**
     * <p>Obtain the InetAddress describing the local side of the connection.</p>
     *
     * @return The InetAddress describing the underlying connection properties
     */
    InetAddress getLocalInetAddress();

    /**
     * <p>Obtain the data consumer that will is used by the connection
     * to send outgoing data out of it's underlying socket.</p>
     *
     * @return The DataConsumer that sends data using this connection
     * @throws IllegalStateException If the connection is closed
     */
    DataConsumer getDataConsumer() throws IllegalStateException;

    /**
     * <p>Obtain the data producer that the connection will send
     * data read from the connection out on. The producer may
     * not have a thread dedicated to it so consumers attached to
     * this producer should return from their consume() method as
     * quickly as possible.</p>
     *
     * @return The DataProducer that reads data using this connection
     * @throws IllegalStateException If the connection is closed
     */
    DataProducer getDataProducer();

    /**
     * Close this session including associated socket connection.
     *
     * Any selector registrations (if using nio) are also removed.
     * The order of events for closing the session is:
     * <ul>
     * <li>set closing flag to prevent redundant shutdowns
     * <li>notifyEvent all listeners that the channel is shutting down
     * <li>close the socket
     * </ul>
     */
    void close();

    /**
     * Retrieve the closed state of the Session.
     *
     * @return True if the session is closed
     */
    boolean isClosed();

    /**
     * Retrieve the mechanism used to open this connection
     *
     * @return True if the connection was created as a server accept call,
     * false otherwise
     */
    boolean isAcceptCreated();

    /**
     * <p>Determines if this connection is secure.</p>
     *
     * @return True if the connection is secure (e.g. SSL/TLS)
     */
    boolean isSecure();

    /**
     * <p>Sets the security status of this connection.</p>
     *
     * <p>Ordinarily a connection is either insecure (standard Socket
     * or SocketChannel) or secure (SSLSocket). However, many protocols
     * including XMPP and NNTP allow the use of SASL where transport
     * layer security can be established after the connection is created.
     * So application level objects must be able to change the security
     * status of a connection during such negotiations.</p>
     *
     * @param secure True if the connection is secure (e.g. SSL/TLS)
     */
    void setSecure(boolean secure);

    /**
     * Register a listener for close event notification.  Registrations after
     * the Session is closed will be immediately notified <em>before</em>
     * the registration call returns (within the context of the
     * registration call). An optional handback object can be associated with
     * the registration if the same listener is registered to listen for multiple
     * connection closures.
     *
     * @param listener The listener to register for events
     * @param handbackMessage The object to send in the event notification
     * @return The message previously registered for this channel or null
     * if no registration existed
     * @throws UnauthorizedException If caller doesn't have permission to
     * access this resource
     */
    Object registerCloseListener(ConnectionCloseListener listener,
                                 Object handbackMessage)
            throws UnauthorizedException;

    /**
     * Remove a registered close event listener.  Registered listeners must
     * be able to receive close events up until the time this method returns.
     * (i.e. It is possible to call unregister, receive a close event registration,
     * and then have the unregister call return.)
     *
     * @param listener The listener  to deregister for close events
     * @return The Message registered with this listener or null if the
     * channel was never registered
     * @throws UnauthorizedException If caller doesn't have permission
     * to access this resource
     */
    Object removeCloseListener(ConnectionCloseListener listener)
            throws UnauthorizedException;

    /**
     * <p>Sets the connection manager this connection belongs to.</p>
     *
     * <p>Connections may only have on connection manager at a time.</p>
     *
     * @param manager The connection manager for the connection
     */
    void setConnectionManager(ConnectionManager manager);
}
