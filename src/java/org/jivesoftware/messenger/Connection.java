/**
 * $RCSfile: Connection.java,v $
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.xmpp.packet.Packet;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.net.SocketConnection;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.Writer;

/**
 * Represents a connection on the server.
 *
 * @author Iain Shigeoka
 */
public interface Connection {

    /**
     * Verifies that the connection is still live. Typically this is done by
     * sending a whitespace character between packets.
     *
     * @return true if the socket remains valid, false otherwise.
     */
    public boolean validate();

    /**
     * Initializes the connection with it's owning session. Allows the
     * connection class to configure itself with session related information
     * (e.g. stream ID).
     *
     * @param session the session that owns this connection
     */
    public void init(Session session);

    /**
     * Returns the InetAddress describing the connection.
     *
     * @return the InetAddress describing the underlying connection properties.
     */
    public InetAddress getInetAddress() throws UnknownHostException;

     /**
      * Returns the Writer used to send data to the connection. The writer should be
      * used with caution. In the majority of cases, the {@link #deliver(Packet)}
      * method should be used to send data instead of using the writer directly.
      * You must synchronize on the writer before writing data to it to ensure
      * data consistency:
      *
      * <pre>
      * Writer writer = connection.getWriter();
      * synchronized(writer) {
      *     // write data....
      * }</pre>
      *
      * @return the Writer for this connection.
      */
    public Writer getWriter();

    /**
     * Close this session including associated socket connection. The order of
     * events for closing the session is:
     * <ul>
     *      <li>Set closing flag to prevent redundant shutdowns.
     *      <li>Call notifyEvent all listeners that the channel is shutting down.
     *      <li>Close the socket.
     * </ul>
     */
    public void close();

    /**
     * Returns true if the connection/session is closed.
     *
     * @return true if the connection is closed.
     */
    public boolean isClosed();

    /**
     * Returns true if this connection is secure.
     *
     * @return true if the connection is secure (e.g. SSL/TLS)
     */
    public boolean isSecure();

    /**
     * Registers a listener for close event notification. Registrations after
     * the Session is closed will be immediately notified <em>before</em>
     * the registration call returns (within the context of the
     * registration call). An optional handback object can be associated with
     * the registration if the same listener is registered to listen for multiple
     * connection closures.
     *
     * @param listener the listener to register for events.
     * @param handbackMessage the object to send in the event notification.
     * @return the message previously registered for this channel or <tt>null</tt>
     *      if no registration existed
     */
    public Object registerCloseListener(ConnectionCloseListener listener, Object handbackMessage);

    /**
     * Removes a registered close event listener. Registered listeners must
     * be able to receive close events up until the time this method returns.
     * (i.e. it is possible to call unregister, receive a close event registration,
     * and then have the unregister call return.)
     *
     * @param listener the listener to deregister for close events.
     * @return the Message registered with this listener or <tt>null</tt> if the
     *      channel was never registered.
     */
    public Object removeCloseListener(ConnectionCloseListener listener);

    /**
     * Delivers the packet to this connection without checking the recipient.
     * The method essentially calls <code>socket.send(packet.getWriteBuffer())</code>.
     *
     * @param packet the packet to deliver.
     */
    public void deliver(Packet packet) throws UnauthorizedException;

    /**
     * Delivers raw text to this connection. This is a very low level way for sending
     * XML stanzas to the client. This method should not be used unless you have very
     * good reasons for not using {@link #deliver(org.xmpp.packet.Packet)}.<p>
     *
     * This method avoids having to get the writer of this connection and mess directly
     * with the writer. Therefore, this method ensures a correct delivery of the stanza
     * even if other threads were sending data concurrently.
     *
     * @param text the XML stanzas represented kept in a String.
     */
    public void deliverRawText(String text);

    /**
     * Returns true if the connected client is a flash client. Flash clients need
     * to receive a special character (i.e. \0) at the end of each xml packet. Flash
     * clients may send the character \0 in incoming packets and may start a connection
     * using another openning tag such as: "flash:client".
     *
     * @return true if the connected client is a flash client.
     */
    public boolean isFlashClient();

    /**
     * Returns the major version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the major XMPP version being used by this connection.
     */
    public int getMajorXMPPVersion();

    /**
     * Returns the minor version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the minor XMPP version being used by this connection.
     */
    public int getMinorXMPPVersion();

    /**
     * Returns the language code that should be used for this connection
     * (e.g. "en").
     *
     * @return the language code for the connection.
     */
    public String getLanguage();

    /**
     * Returns true if the connection is using compression.
     *
     * @return true if the connection is using compression.
     */
    boolean isCompressed();

    /**
     * Returns whether compression is optional or is disabled.
     *
     * @return whether compression is optional or is disabled.
     */
    CompressionPolicy getCompressionPolicy();

    /**
     * Sets whether compression is enabled or is disabled.
     *
     * @param compressionPolicy whether Compression is enabled or is disabled.
     */
    void setCompressionPolicy(CompressionPolicy compressionPolicy);

    /**
     * Returns whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @return whether TLS is mandatory, optional or is disabled.
     */
    TLSPolicy getTlsPolicy();

    /**
     * Sets whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @param tlsPolicy whether TLS is mandatory, optional or is disabled.
     */
    void setTlsPolicy(TLSPolicy tlsPolicy);

    /**
     * Enumeration of possible compression policies required to interact with the server.
     */
    enum CompressionPolicy {

        /**
         * compression is optional to interact with the server.
         */
        optional,

        /**
         * compression is not available. Entities that request a compression negotiation
         * will get a stream error and their connections will be closed.
         */
        disabled;
    }

    /**
     * Enumeration of possible TLS policies required to interact with the server.
     */
    enum TLSPolicy {

        /**
         * TLS is required to interact with the server. Entities that do not secure their
         * connections using TLS will get a stream error and their connections will be closed.
         */
        required,

        /**
         * TLS is optional to interact with the server. Entities may or may not secure their
         * connections using TLS.
         */
        optional,

        /**
         * TLS is not available. Entities that request a TLS negotiation will get a stream
         * error and their connections will be closed.
         */
        disabled;
    }
}
