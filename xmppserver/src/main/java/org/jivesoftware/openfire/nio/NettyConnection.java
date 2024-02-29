/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.AbstractConnection;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
public class NettyConnection extends AbstractConnection
{
    private static final Logger Log = LoggerFactory.getLogger(NettyConnection.class);
    public static final String SSL_HANDLER_NAME = "ssl";
    private final ConnectionConfiguration configuration;
    private final ChannelHandlerContext channelHandlerContext;

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private final PacketDeliverer backupDeliverer;

    private boolean usingSelfSignedCertificate;

    /**
     * Flag that specifies if the connection should be considered closed. Closing a NIO connection
     * is an asynchronous operation so instead of waiting for the connection to be actually closed just
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
        if (sslhandler == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sslhandler.engine().getSession().getProtocol());
    }

    @Override
    public Optional<String> getCipherSuiteName() {
        SslHandler sslhandler = (SslHandler) channelHandlerContext.channel().pipeline().get(SSL_HANDLER_NAME);
        if (sslhandler == null) {
            return Optional.empty();
        }
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
    public void close(@Nullable final StreamError error, final boolean networkInterruption) {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {
            Log.trace("Closing {} with optional error: {}", this, error);

            ChannelFuture f;

            if (session != null) {

                if (!networkInterruption) {
                    // A 'clean' closure should never be resumed (OF-2752).
                    session.getStreamManager().formalClose();
                }

                // Ensure that the state of this connection, its session and the Netty Channel are eventually closed.
                session.setStatus(Session.Status.CLOSED);

                // Only send errors or stream closures if the open <stream:stream> occurred, inferred by having a session.
                // TODO: Are there edge cases here?
                String rawEndStream = "";
                if (error != null) {
                    rawEndStream = error.toXML();
                }
                rawEndStream += "</stream:stream>";

                f = channelHandlerContext.writeAndFlush(rawEndStream);
            } else {
                f = channelHandlerContext.newSucceededFuture();
            }

            // OF-2808: Ensure that the connection is done invoking its 'close' listeners before returning from this
            // method, otherwise stream management's "resume" functionality breaks (the 'close' listeners have been
            // observed to act on a newly attached stream/connection, instead of the old one).
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                    f.addListener(e -> Log.trace("Flushed any final bytes, closing connection."))
                    .addListener(ChannelFutureListener.CLOSE)
                    .addListener(e -> {
                        Log.trace("Notifying close listeners.");
                        notifyCloseListeners();
                        closeListeners.clear();
                        latch.countDown();
                    })
                    .addListener(e -> Log.trace("Finished closing connection."))
                    .sync(); // TODO: OF-2811 Remove this blocking operation (which may have been made redundant by the fix for OF-2808 anyway).
            } catch (Exception e) {
                Log.error("Problem during connection close or cleanup", e);
            }
            try {
                // TODO: OF-2811 Remove this blocking operation, by allowing the invokers of this method to use a Future.
                latch.await(10, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Log.debug("Stopped waiting on connection being closed, as an interrupt happened.", e);
            }
        }
    }

    @Override
    public void systemShutdown() {
        close(new StreamError(StreamError.Condition.system_shutdown));
    }

    @Override
    public void reinit(LocalSession owner) {
        super.reinit(owner);
        StanzaHandler stanzaHandler = this.channelHandlerContext.channel().attr(NettyConnectionHandler.HANDLER).get();
        stanzaHandler.setSession(owner);
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
                channelHandlerContext.writeAndFlush(packet.getElement().asXML())
                    .addListener(l ->
                        updateWrittenBytesCounter(channelHandlerContext)
                    );
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
        if (!isClosed()) {
            Log.trace("Sending: {}", text);
            channelHandlerContext.writeAndFlush(text).addListener(l ->
                updateWrittenBytesCounter(channelHandlerContext)
            );
            // TODO - handle errors more specifically
            // Currently errors are handled by the default exceptionCaught method (log error, close channel)
            // We can add a new listener to the ChannelFuture f for more specific error handling.
        } else {
            Log.debug("Cannot send data to as connection is already closed: " + this);
        }
    }

    /**
     * Updates the system counter of written bytes. This information is used by the "outgoing bytes" statistic.
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

    public void startTLS(boolean clientMode, boolean directTLS) throws Exception {

        final EncryptionArtifactFactory factory = new EncryptionArtifactFactory( configuration );

        final SslHandler sslHandler;
        if (clientMode) {
            final SslContext sslContext = factory.createClientModeSslContext();

            // OF-2738: Send along the XMPP domain that's needed for SNI
            final NettyOutboundConnectionHandler handler = channelHandlerContext.channel().pipeline().get(NettyOutboundConnectionHandler.class);

            sslHandler = sslContext.newHandler(channelHandlerContext.alloc(), handler.getDomainPair().getRemote(), handler.getPort());
        } else {
            final SslContext sslContext = factory.createServerModeSslContext(directTLS);
            sslHandler = sslContext.newHandler(channelHandlerContext.alloc());
        }

        channelHandlerContext.pipeline().addFirst(SSL_HANDLER_NAME, sslHandler);

        if ( !clientMode && !directTLS ) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText( "<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>" );
        }
    }

    @Override
    public void addCompression() {
        // Inbound traffic only
        if (isEncrypted()) {
            channelHandlerContext.channel().pipeline().addAfter(SSL_HANDLER_NAME, "inboundCompressionHandler", new JZlibDecoder());
        }  else {
            channelHandlerContext.channel().pipeline().addFirst(new JZlibDecoder());
        }
    }

    @Override
    public void startCompression() {
        // Outbound traffic only
        if (isEncrypted()) {
            channelHandlerContext.channel().pipeline().addAfter(SSL_HANDLER_NAME, "outboundCompressionHandler", new JZlibEncoder(Z_BEST_COMPRESSION));
        }  else {
            channelHandlerContext.channel().pipeline().addFirst(new JZlibEncoder(Z_BEST_COMPRESSION));
        }
        // Z_BEST_COMPRESSION is the same level as COMPRESSION_MAX in MINA
    }

    @Override
    public ConnectionConfiguration getConfiguration()
    {
        return configuration;
    }

    @Override
    public boolean isCompressed() {
        return channelHandlerContext.channel().pipeline().get(JZlibDecoder.class) != null;
    }

    @Override
    public String toString() {
        final SocketAddress peer = channelHandlerContext.channel().remoteAddress();
        return this.getClass().getSimpleName() + "{peer: " + (peer == null ? "(unknown)" : peer) + ", state: " + state + ", session: " + session + ", Netty channel handler context name: " + channelHandlerContext.name() + "}";
    }
}
