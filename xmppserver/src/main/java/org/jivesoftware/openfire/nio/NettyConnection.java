/*
 * Copyright (C) 2005-2008 Jive Software, 2022-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.concurrent.Future;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.ServerTrafficCounter;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.jcraft.jzlib.JZlib.Z_BEST_COMPRESSION;
import static org.jivesoftware.openfire.nio.NettyConnectionHandler.WRITTEN_BYTES;
import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * Implementation of {@link Connection} interface specific for Netty connections.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public class NettyConnection implements Connection<Channel> {

    private static final Logger Log = LoggerFactory.getLogger(NettyConnection.class);
    public static final String SSL_HANDLER_NAME = "ssl";
    private final ConnectionConfiguration configuration;

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";

    public LocalSession session;
    private final ChannelHandlerContext channelHandlerContext;

    final private Map<ConnectionCloseListener, Object> closeListeners = new HashMap<>();

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private final PacketDeliverer backupDeliverer;
    private int majorVersion = 1;
    private int minorVersion = 0;

    private boolean usingSelfSignedCertificate;

    /**
     * Flag that specifies if the connection should be considered closed. Closing a NIO connection
     * is an asynch operation so instead of waiting for the connection to be actually closed just
     * keep this flag to avoid using the connection between #close was used and the socket is actually
     * closed.
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    private boolean isEncrypted = false;

    public NettyConnection(ChannelHandlerContext channelHandlerContext, @Nullable PacketDeliverer packetDeliverer, ConnectionConfiguration configuration ) {
        this.channelHandlerContext = channelHandlerContext;
        this.backupDeliverer = packetDeliverer;
        this.configuration = configuration;
    }

    @Override
    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        deliverRawText(" ");
        return !isClosed();
    }

    @Override
    public void registerCloseListener(ConnectionCloseListener listener, Object callback) {
        if (isClosed()) {
            listener.onConnectionClose(session);
        }
        else {
            closeListeners.put( listener, callback );
        }
    }

    @Override
    public void removeCloseListener(ConnectionCloseListener listener) {
        closeListeners.remove( listener );
    }

    @Override
    public byte[] getAddress() throws UnknownHostException {
        final SocketAddress remoteAddress = channelHandlerContext.channel().remoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress address = socketAddress.getAddress();
        return address.getAddress();
    }

    @Override
    public String getHostAddress() throws UnknownHostException {
        final SocketAddress remoteAddress = channelHandlerContext.channel().remoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress inetAddress = socketAddress.getAddress();
        return inetAddress.getHostAddress();
    }

    @Override
    public String getHostName() throws UnknownHostException {
        final SocketAddress remoteAddress = channelHandlerContext.channel().remoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress inetAddress = socketAddress.getAddress();
        return inetAddress.getHostName();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        SslHandler sslhandler = (SslHandler) channelHandlerContext.channel().pipeline().get(SSL_HANDLER_NAME);

        if (sslhandler != null) {
            return sslhandler.engine().getSession().getLocalCertificates();
        }
        return new Certificate[0];
    }

    @Override
    public Certificate[] getPeerCertificates() {
        SslHandler sslhandler = (SslHandler) channelHandlerContext.channel().pipeline().get(SSL_HANDLER_NAME);
        try {
            if (sslhandler != null) {
                return sslhandler.engine().getSession().getPeerCertificates();
            }
        } catch (SSLPeerUnverifiedException e) {
            if (Log.isTraceEnabled()) {
                // This is perfectly acceptable when mutual authentication is not enforced by Openfire configuration.
                Log.trace("Peer does not offer certificates in session: " + session, e);
            }
        }
        return new Certificate[0];
    }

    @Override
    public Optional<String> getTLSProtocolName() {
        SslHandler sslhandler = (SslHandler) channelHandlerContext.channel().pipeline().get(SSL_HANDLER_NAME);
        return Optional.ofNullable(sslhandler.engine().getSession().getProtocol());
    }

    @Override
    public Optional<String> getCipherSuiteName() {
        SslHandler sslhandler = (SslHandler) channelHandlerContext.channel().pipeline().get(SSL_HANDLER_NAME);
        return Optional.ofNullable(sslhandler.engine().getSession().getCipherSuite());
    }

    @Override
    public void setUsingSelfSignedCertificate(boolean isSelfSigned) {
        this.usingSelfSignedCertificate = isSelfSigned;
    }

    @Override
    public boolean isUsingSelfSignedCertificate() {
        return usingSelfSignedCertificate;
    }

    @Override
    @Nullable
    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    @Override
    public void close() {
        close(null);
    }

    @Override
    public void close(@Nullable final StreamError error) {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {

            // Ensure that the state of this connection, its session and the MINA context are eventually closed.

            if (session != null) {
                session.setStatus(Session.Status.CLOSED);
            }

            String rawEndStream = "";
            if (error != null) {
                rawEndStream = error.toXML();
            }
            rawEndStream += "</stream:stream>";

            try {
                deliverRawText(rawEndStream);
            } catch (Exception e) {
                Log.error("Failed to deliver stream close tag: " + e.getMessage());
            }

            try {
                // TODO don't block, handle errors async with custom ChannelFutureListener
                this.channelHandlerContext.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE).sync();
            } catch (Exception e) {
                Log.error("Exception while closing Netty session", e);
            }
            notifyCloseListeners(); // clean up session, etc.
            closeListeners.clear();
        }
    }

    @Override
    public void systemShutdown() {
        close(new StreamError(StreamError.Condition.system_shutdown));
    }

    /**
     * Notifies all close listeners that the connection has been closed.
     * Used by subclasses to properly finish closing the connection.
     */
    private void notifyCloseListeners() {
        for( final Map.Entry<ConnectionCloseListener, Object> entry : closeListeners.entrySet() )
        {
            if (entry.getKey() != null) {
                try {
                    entry.getKey().onConnectionClose(entry.getValue());
                } catch (Exception e) {
                    Log.error("Error notifying listener: " + entry.getKey(), e);
                }
            }
        }
    }

    @Override
    public void init(LocalSession owner) {
        session = owner;
    }

    @Override
    public void reinit(LocalSession owner) {
        session = owner;
        StanzaHandler stanzaHandler = this.channelHandlerContext.channel().attr(NettyConnectionHandler.HANDLER).get();
        stanzaHandler.setSession(owner);

        // ConnectionCloseListeners are registered with their session instance as a callback object. When re-initializing,
        // this object needs to be replaced with the new session instance (or otherwise, the old session will be used
        // during the callback. OF-2014
        for ( final Map.Entry<ConnectionCloseListener, Object> entry : closeListeners.entrySet() )
        {
            if ( entry.getValue() instanceof LocalSession ) {
                entry.setValue( owner );
            }
        }
    }

    @Override
    public boolean isInitialized() {
        return session != null && !isClosed();
    }

    @Override
    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    @Override
    @Deprecated // Remove in Openfire 4.9 or later.
    public boolean isSecure() {
        return isEncrypted();
    }

    @Override
    public boolean isEncrypted() {
        return isEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        isEncrypted = encrypted;
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException {
        if (isClosed()) {
            if (backupDeliverer != null) {
                backupDeliverer.deliver(packet);
            } else {
                Log.trace("Discarding packet that was due to be delivered on closed connection {}, for which no backup deliverer was configured.", this);
            }
        }
        else {
            boolean errorDelivering = false;
            try {
                ChannelFuture f = channelHandlerContext.writeAndFlush(packet.getElement().asXML());
                updateWrittenBytesCounter(channelHandlerContext);
                // TODO - handle errors more specifically
                // Currently errors are handled by the default exceptionCaught method (log error, close channel)
                // We can add a new listener to the ChannelFuture f for more specific error handling.
            }
            catch (Exception e) {
                Log.debug("Error delivering packet:\n" + packet, e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                if (backupDeliverer != null) {
                    backupDeliverer.deliver(packet);
                } else {
                    Log.trace("Discarding packet that failed to be delivered to connection {}, for which no backup deliverer was configured.", this);
                }
            }
            else {
                session.incrementServerPacketCount();
            }
        }
    }

    @Override
    public void deliverRawText(String text) {
        Log.trace("Sending: " + text);
        if (!isClosed()) {
            ChannelFuture f = channelHandlerContext.writeAndFlush(text);
            updateWrittenBytesCounter(channelHandlerContext);
            // TODO - handle errors more specifically
            // Currently errors are handled by the default exceptionCaught method (log error, close channel)
            // We can add a new listener to the ChannelFuture f for more specific error handling.
        }
    }

    /**
     * Updates the system counter of written bytes. This information is used by the outgoing
     * bytes statistic.
     *
     * @param ctx the context for the channel writing bytes
     */
    private void updateWrittenBytesCounter(ChannelHandlerContext ctx) {
        ChannelTrafficShapingHandler handler = (ChannelTrafficShapingHandler) ctx.channel().pipeline().get(TRAFFIC_HANDLER_NAME);
        if (handler != null) {
            long currentBytes = handler.trafficCounter().lastWrittenBytes();
            Long prevBytes = ctx.channel().attr(WRITTEN_BYTES).get();
            long delta;
            if (prevBytes == null) {
                delta = currentBytes;
            } else {
                delta = currentBytes - prevBytes;
            }
            ctx.channel().attr(WRITTEN_BYTES).set(currentBytes);
            ServerTrafficCounter.incrementOutgoingCounter(delta);
        }
    }

    public Future<Channel> startTLS(boolean clientMode, boolean directTLS) throws Exception {

        final EncryptionArtifactFactory factory = new EncryptionArtifactFactory( configuration );

        final SslContext sslContext;
        if ( clientMode ) {
            sslContext= factory.createClientModeSslContext();
        } else {
            sslContext = factory.createServerModeSslContext(directTLS);
        }

        final SslHandler sslHandler = sslContext.newHandler(channelHandlerContext.alloc());
        channelHandlerContext.pipeline().addFirst(SSL_HANDLER_NAME, sslHandler);

        if ( !clientMode && !directTLS ) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText( "<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>" );
        }

        return sslHandler.handshakeFuture();
    }

    @Override
    public void addCompression() {
        // Inbound traffic only
        channelHandlerContext.channel().pipeline().addFirst(new JZlibDecoder());
    }

    @Override
    public void startCompression() {
        // Outbound traffic only
        channelHandlerContext.channel().pipeline().addFirst(new JZlibEncoder(Z_BEST_COMPRESSION));
        // Z_BEST_COMPRESSION is the same level as COMPRESSION_MAX in MINA
    }

    @Override
    public ConnectionConfiguration getConfiguration()
    {
        return configuration;
    }

    @Override
    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    @Override
    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    public boolean isCompressed() {
        return channelHandlerContext.channel().pipeline().get(JZlibDecoder.class) != null;
    }

    @Override
    public String toString() {
        return super.toString() + " Netty Session: " + channelHandlerContext.name();
    }

}
