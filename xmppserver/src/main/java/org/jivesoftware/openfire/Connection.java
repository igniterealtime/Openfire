/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a connection on the server.
 *
 * @author Iain Shigeoka
 */
public interface Connection extends Closeable {

    /**
     * Verifies that the connection is still live. Typically, this is done by
     * sending a whitespace character between packets.
     *
     * @return true if the socket remains valid, false otherwise.
     */
    boolean validate();

    /**
     * Initializes the connection with it's owning session. Allows the
     * connection class to configure itself with session related information
     * (e.g. stream ID).
     *
     * @param session the session that owns this connection
     */
    void init( LocalSession session );

    /**
     * Reinitializes the connection to switch to a different session. This allows for
     * XEP-0198 resumption and transport-switching.
     *
     * @param session The new session now owning the connection.
     */
    void reinit( LocalSession session );

    /**
     * Checks if the connection has finished initialization.
     *
     * @return true if connection has finished initialization.
     */
    boolean isInitialized();

    /**
     * Returns the raw IP address of this <code>InetAddress</code>
     * object. The result is in network byte order: the highest order
     * byte of the address is in <code>getAddress()[0]</code>.
     *
     * @return  the raw IP address of this object.
     * @throws java.net.UnknownHostException if IP address of host could not be determined.
     */
    byte[] getAddress() throws UnknownHostException;

    /**
     * Returns the IP address string in textual presentation.
     *
     * @return  the raw IP address in a string format.
     * @throws java.net.UnknownHostException if IP address of host could not be determined.
     */
    String getHostAddress() throws UnknownHostException;

    /**
     * Gets the host name for this IP address.
     *
     * <p>If this InetAddress was created with a host name,
     * this host name will be remembered and returned;
     * otherwise, a reverse name lookup will be performed
     * and the result will be returned based on the system
     * configured name lookup service. If a lookup of the name service
     * is required, call
     * {@link java.net.InetAddress#getCanonicalHostName() getCanonicalHostName}.
     *
     * <p>If there is a security manager, its
     * <code>checkConnect</code> method is first called
     * with the hostname and <code>-1</code>
     * as its arguments to see if the operation is allowed.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     * @throws java.net.UnknownHostException if IP address of host could not be determined.
     *
     * @see java.net.InetAddress#getCanonicalHostName
     * @see SecurityManager#checkConnect
     */
    String getHostName() throws UnknownHostException;

    /**
     * Returns the local underlying {@link javax.security.cert.X509Certificate}
     * chain for the connection.
     * 
     * @return an ordered array of certificates, with the local certificate
     *         first followed by any certificate authorities. If no certificates
     *         is present for the connection, then {@code null} is returned.
     */
    Certificate[] getLocalCertificates();

    /**
     * Returns the underlying {@link javax.security.cert.X509Certificate} for
     * the connection of the peer.
     * 
     * @return an ordered array of peer certificates, with the peer's own
     *         certificate first followed by any certificate authorities.
     */
    Certificate[] getPeerCertificates();

    /**
     * Keeps track if the other peer of this session presented a self-signed certificate. When
     * using self-signed certificate for server-2-server sessions then SASL EXTERNAL will not be
     * used and instead server-dialback will be preferred for vcerifying the identify of the remote
     * server.
     *
     * @param isSelfSigned true if the other peer presented a self-signed certificate.
     */
    void setUsingSelfSignedCertificate( boolean isSelfSigned );

    /**
     * Returns true if the other peer of this session presented a self-signed certificate. When
     * using self-signed certificate for server-2-server sessions then SASL EXTERNAL will not be
     * used and instead server-dialback will be preferred for vcerifying the identify of the remote
     * server.
     *
     * @return true if the other peer of this session presented a self-signed certificate.
     */
    boolean isUsingSelfSignedCertificate();

    /**
     * Close this connection including associated session. The events for closing
     * the connection are:
     *
     * <ul>
     *      <li>Set closing flag to prevent redundant shutdowns.
     *      <li>Close the socket.
     *      <li>Notify all listeners that the channel is shut down.
     * </ul>
     *
     * Not all implementations use the same order of events.
     *
     * Invocation of this method is expected to occur when a coordinated, 'clean' disconnect occurs. Such disconnects
     * are expected to be user (or server) initiated. As a result, a session closed by this method is not resumable,
     * even if Stream Management was activated for this session. Refer to {@link #close(StreamError, boolean)} for
     * processing of unexpected disconnects (that <em>are</em> potentially resumable).
     */
    @Override
    default void close() {
        close(null, false);
    }

    /**
     * Close this connection including associated session, optionally citing a stream error. The events for closing
     * the connection are:
     *
     * <ul>
     *      <li>Set closing flag to prevent redundant shutdowns.
     *      <li>Close the socket.
     *      <li>Notify all listeners that the channel is shut down.
     * </ul>
     *
     * Not all implementations use the same order of events.
     *
     * Invocation of this method is expected to occur when a coordinated, 'clean' disconnect occurs. Such disconnects
     * are expected to be user (or server) initiated. As a result, a session closed by this method is not resumable,
     * even if Stream Management was activated for this session. Refer to {@link #close(StreamError, boolean)} for
     * processing of unexpected disconnects (that <em>are</em> potentially resumable).
     *
     * @param error If non-null, the end-stream tag will be preceded with this error.
     */
    default void close(@Nullable final StreamError error) {
        close(error, false);
    }

    /**
     * Close this connection including associated session, optionally citing a stream error.
     *
     * The 'networkInterruption' argument should be set to 'true' if the connection is being closed because it is known
     * or assumed that the network connection between Openfire and the peer was unexpectedly terminated (eg: due to a
     * networking failure). These typically are scenarios where a peer becomes unresponsive (without having terminated
     * its session with a <tt></stream:stream></tt> or comparable message).
     *
     * When the 'networkInterruption' argument is set to 'true', then a session is eligible for resumption (if Stream
     * Management was activiated for the session).
     *
     * The events for closing the connection are:
     * <ul>
     *      <li>Set closing flag to prevent redundant shutdowns.
     *      <li>Close the socket.
     *      <li>Notify all listeners that the channel is shut down.
     * </ul>
     *
     * Not all implementations use the same order of events.
     *
     * @param error If non-null, the end-stream tag will be preceded with this error.
     */
    void close(@Nullable final StreamError error, final boolean networkInterruption);

    /**
     * Notification message indicating that the server is being shutdown. Implementors
     * should send a stream error whose condition is system-shutdown before closing
     * the connection.
     */
    void systemShutdown();

    /**
     * Returns true if the connection/session is closed.
     *
     * @return true if the connection is closed.
     */
    boolean isClosed();

    /**
     * Returns true if this connection is secure.
     *
     * @return true if the connection is secure (e.g. TLS)
     * @deprecated Renamed. Use {@link #isEncrypted()} instead.
     */
    @Deprecated // Remove in Openfire 4.9 or later.
    boolean isSecure();

    /**
     * Returns true if this connection is encrypted.
     *
     * @return true if the connection is encrypted (e.g. uses TLS)
     */
    default boolean isEncrypted() {
        return isSecure();
    }

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
    void registerCloseListener( ConnectionCloseListener listener, Object handbackMessage );

    /**
     * Removes a registered close event listener. Registered listeners must
     * be able to receive close events up until the time this method returns.
     * (i.e. it is possible to call unregister, receive a close event registration,
     * and then have the unregister call return.)
     *
     * @param listener the listener to deregister for close events.
     */
    void removeCloseListener( ConnectionCloseListener listener );

    /**
     * Delivers the packet to this connection without checking the recipient.
     * The method essentially calls <code>socket.send(packet.getWriteBuffer())</code>.
     *
     * Use with caution! This code is unlikely to be called directly. Instead, ensure
     * that data sent to the entities is sent through the appropriate LocalSession object.
     * For clients, this prevents, for example, synchronisation issues with stanza counts
     * related to Stream Management (XEP-0198).
     *
     * @param packet the packet to deliver.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException if a permission error was detected.
     */
    void deliver( Packet packet ) throws UnauthorizedException;

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
    void deliverRawText( String text );

    /**
     * Returns the major version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the major XMPP version being used by this connection.
     */
    int getMajorXMPPVersion();

    /**
     * Returns the minor version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the minor XMPP version being used by this connection.
     */
    int getMinorXMPPVersion();

    /**
     * Sets the XMPP version information. In most cases, the version should be "1.0".
     * However, older clients using the "Jabber" protocol do not set a version. In that
     * case, the version is "0.0".
     *
     * @param majorVersion the major version.
     * @param minorVersion the minor version.
     */
    void setXMPPVersion( int majorVersion, int minorVersion );

    /**
     * Returns true if the connection is using compression.
     *
     * @return true if the connection is using compression.
     */
    boolean isCompressed();

    /**
     * Returns the TLS protocol name used by the connection of the session, if any.
     *
     * @return a TLS protocol (version) name.
     */
    Optional<String> getTLSProtocolName();

    /**
     * Returns the TLS cipher suite name used by the connection of the session, if any.
     *
     * @return cipher suite name.
     */
    Optional<String> getCipherSuiteName();

    /**
     * Returns the packet deliverer to use when delivering a packet over the socket fails. The
     * packet deliverer will retry to send the packet using some other connection or will store
     * the packet offline for later retrieval. When null, packets will just be dropped.
     *
     * @return the packet deliverer to use when delivering a packet over the socket fails.
     */
    @Nullable
    PacketDeliverer getPacketDeliverer();

    /**
     * Encrypts the plain connection by negotiating TLS with the other peer. In a server-2-server
     * connection the server requesting the TLS negotiation will be the client and the other server
     * will be the server during the TLS negotiation. Therefore, the server requesting the TLS
     * negotiation must pass <code>true</code> in the {@code clientMode} parameter and the server
     * receiving the TLS request must pass <code>false</code> in the {@code clientMode} parameter.<p>
     * <p>
     * In the case of client-2-server the XMPP server must pass <code>false</code> in the
     * {@code clientMode} parameter since it will behave as the server in the TLS negotiation.
     *
     * @param clientMode boolean indicating if this entity is a client or a server in the TLS negotiation.
     * @param directTLS  boolean indicating if the negotiation is directTLS (true) or startTLS (false).
     * @throws Exception if an error occurred while encrypting the connection.
     */
    void startTLS(boolean clientMode, boolean directTLS) throws Exception;

    /**
     * Adds the compression filter to the connection but only filter incoming traffic. Do not filter
     * outgoing traffic since we still need to send an uncompressed stanza to the client indicating
     * that he can start compressing the traffic. After we sent the uncompresses stanza we can
     * start compression outgoing traffic as well.
     */
    void addCompression();

    /**
     * Start compressing outgoing traffic for this connection. Compression will only be available after
     * TLS has been negotiated. This means that a connection can never be using compression before
     * TLS. However, it is possible to use compression without TLS.
     */
    void startCompression();

    /**
     * Returns a representation of the desired state for this connection. Note that this is different from the current
     * state of the connection. For example, TLS can be required by configuration, but while the connection has yet to
     * be fully initialized, the current state might not be TLS-encrypted.
     *
     * @return The desired configuration for the connection (never null).
     */
    ConnectionConfiguration getConfiguration();

    /**
     * When a connection is used to transmit an XML data, the root element of that data can define XML namespaces other
     * than the ones that are default (eg: 'jabber:client', 'jabber:server', etc). For an XML parser to be able to parse
     * stanzas or other elements that are defined in that namespace (eg: are prefixed), these namespaces are recorded
     * here.
     *
     * @return A collection that contains all non-default namespaces that the peer defined when last opening a new stream.
     */
    Set<Namespace> getAdditionalNamespaces();

    /**
     * When a connection is used to transmit an XML data, the root element of that data can define XML namespaces other
     * than the ones that are default (eg: 'jabber:client', 'jabber:server', etc). For an XML parser to be able to parse
     * stanzas or other elements that are defined in that namespace (eg: are prefixed), these namespaces are recorded
     * here.
     *
     * @param additionalNamespaces A collection that contains all non-default namespaces that the peer defined when last
     *                            opening a new stream.
     */
    void setAdditionalNamespaces(@Nonnull final Set<Namespace> additionalNamespaces);

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
         * TLS is required to interact with the server. Entities that do not encrypt their
         * connections using TLS will get a stream error and their connections will be closed.
         */
        required,

        /**
         * TLS is optional to interact with the server. Entities may or may not encrypt their
         * connections using TLS.
         */
        optional,

        /**
         * TLS is not available. Entities that request a TLS negotiation will get a stream
         * error and their connections will be closed.
         */
        disabled,

        /**
         * A policy that requires connections to be encrypted immediately (as opposed to the
         * 'required' policy, that allows for an initially unencrypted connection to become
         * encrypted through StartTLS.
         */
        directTLS
    }

    /**
     * Enumeration that specifies if clients should be authenticated (and how) while
     * negotiating TLS.
     */
    enum ClientAuth {

        /**
         * No authentication will be performed on the client. Client credentials will not
         * be verified while negotiating TLS.
         */
        disabled,

        /**
         * Clients will try to be authenticated. Unlike {@link #needed}, if the client
         * chooses not to provide authentication information about itself, the TLS negotiations
         * will stop and the connection will be dropped. This option is only useful for
         * engines in the server mode.
         */
        wanted,

        /**
         * Clients need to be authenticated. Unlike {@link #wanted}, if the client
         * chooses not to provide authentication information about itself, the TLS negotiations
         * will continue. This option is only useful for engines in the server mode.
         */
        needed
    }

    /**
     * Used to specify operational status for the corresponding connection
     */
    enum State { OPEN, CLOSED }

}
