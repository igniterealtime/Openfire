/**
 * $RCSfile: Connection.java,v $
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Packet;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
     * @throws java.net.UnknownHostException if IP address of host could not be determined.
     */
    public InetAddress getInetAddress() throws UnknownHostException;

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
     * Notification message indicating that the server is being shutdown. Implementors
     * should send a stream error whose condition is system-shutdown before closing
     * the connection.
     */
    public void systemShutdown();

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
     */
    public void registerCloseListener(ConnectionCloseListener listener, Object handbackMessage);

    /**
     * Removes a registered close event listener. Registered listeners must
     * be able to receive close events up until the time this method returns.
     * (i.e. it is possible to call unregister, receive a close event registration,
     * and then have the unregister call return.)
     *
     * @param listener the listener to deregister for close events.
     */
    public void removeCloseListener(ConnectionCloseListener listener);

    /**
     * Delivers the packet to this connection without checking the recipient.
     * The method essentially calls <code>socket.send(packet.getWriteBuffer())</code>.
     *
     * @param packet the packet to deliver.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException if a permission error was detected.
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
     * Sets whether the connected client is a flash client. Flash clients need to
     * receive a special character (i.e. \0) at the end of each xml packet. Flash
     * clients may send the character \0 in incoming packets and may start a
     * connection using another openning tag such as: "flash:client".
     *
     * @param flashClient true if the if the connection is a flash client.
     */
    public void setFlashClient(boolean flashClient);

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
     * Sets the XMPP version information. In most cases, the version should be "1.0".
     * However, older clients using the "Jabber" protocol do not set a version. In that
     * case, the version is "0.0".
     *
     * @param majorVersion the major version.
     * @param minorVersion the minor version.
     */
    public void setXMPPVersion(int majorVersion, int minorVersion);

    /**
     * Returns the language code that should be used for this connection
     * (e.g. "en").
     *
     * @return the language code for the connection.
     */
    public String getLanguage();

    /**
     * Sets the language code that should be used for this connection (e.g. "en").
     *
     * @param language the language code.
     */
    public void setLanaguage(String language);

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
     * Returns the packet deliverer to use when delivering a packet over the socket fails. The
     * packet deliverer will retry to send the packet using some other connection, will store
     * the packet offline for later retrieval or will just drop it.
     *
     * @return the packet deliverer to use when delivering a packet over the socket fails.
     */
    PacketDeliverer getPacketDeliverer();

    /**
     * Secures the plain connection by negotiating TLS with the client. When connecting
     * to a remote server then <tt>clientMode</tt> will be <code>true</code> and
     * <tt>remoteServer</tt> is the server name of the remote server. Otherwise <tt>clientMode</tt>
     * will be <code>false</code> and  <tt>remoteServer</tt> null.
     *
     * @param clientMode boolean indicating if this entity is a client or a server.
     * @param remoteServer server name of the remote server we are connecting to or <tt>null</tt>
     *        when not in client mode.
     * @throws Exception if an error occured while securing the connection.
     */
    void startTLS(boolean clientMode, String remoteServer) throws Exception;

    /**
     * Start using compression for this connection. Compression will only be available after TLS
     * has been negotiated. This means that a connection can never be using compression before
     * TLS. However, it is possible to use compression without TLS.
     */
    void startCompression();

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
        disabled
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
        disabled
    }
}
