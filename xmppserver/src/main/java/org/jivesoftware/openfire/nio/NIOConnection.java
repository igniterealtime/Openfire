/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.COMPRESSION_FILTER_NAME;
import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.EXECUTOR_FILTER_NAME;
import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.TLS_FILTER_NAME;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.*;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.compression.CompressionFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

/**
 * Implementation of {@link Connection} interface specific for NIO connections when using the Apache MINA framework.
 *
 * @author Gaston Dombiak
 * @see <a href="http://mina.apache.org">Apache MINA</a>
 */
public class NIOConnection implements Connection {

    private static final Logger Log = LoggerFactory.getLogger(NIOConnection.class);
    private ConnectionConfiguration configuration;

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";

    private LocalSession session;
    private IoSession ioSession;

    final private Map<ConnectionCloseListener, Object> closeListeners = new HashMap<>();

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private PacketDeliverer backupDeliverer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;

    /**
     * TLS policy currently in use for this connection.
     */
    private TLSPolicy tlsPolicy = TLSPolicy.optional;
    private boolean usingSelfSignedCertificate;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;
    private static final ThreadLocal<CharsetEncoder> encoder = new ThreadLocalEncoder();

    /**
     * Flag that specifies if the connection should be considered closed. Closing a NIO connection
     * is an asynch operation so instead of waiting for the connection to be actually closed just
     * keep this flag to avoid using the connection between #close was used and the socket is actually
     * closed.
     */
    private AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    
    /**
     * Lock used to ensure the integrity of the underlying IoSession (refer to
     * https://issues.apache.org/jira/browse/DIRMINA-653 for details)
     * <p>
     * This lock can be removed once Openfire guarantees a stable delivery
     * order, in which case {@link #deliver(Packet)} won't be called
     * concurrently any more, which made this lock necessary in the first place.
     * </p>
     */
    private final ReentrantLock ioSessionLock = new ReentrantLock(true);

    public NIOConnection( IoSession session, PacketDeliverer packetDeliverer, ConnectionConfiguration configuration ) {
        this.ioSession = session;
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
        final SocketAddress remoteAddress = ioSession.getRemoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress address = socketAddress.getAddress();
        return address.getAddress();
    }

    @Override
    public String getHostAddress() throws UnknownHostException {
        final SocketAddress remoteAddress = ioSession.getRemoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress inetAddress = socketAddress.getAddress();
        return inetAddress.getHostAddress();
    }

    @Override
    public String getHostName() throws UnknownHostException {
        final SocketAddress remoteAddress = ioSession.getRemoteAddress();
        if (remoteAddress == null) throw new UnknownHostException();
        final InetSocketAddress socketAddress = (InetSocketAddress) remoteAddress;
        final InetAddress inetAddress = socketAddress.getAddress();
        return inetAddress.getHostName();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        SSLSession sslSession = (SSLSession) ioSession.getAttribute(SslFilter.SSL_SESSION);
        if (sslSession != null) {
            return sslSession.getLocalCertificates();
        }
        return new Certificate[0];
    }

    @Override
    public Certificate[] getPeerCertificates() {
        try {
            SSLSession sslSession = (SSLSession) ioSession.getAttribute(SslFilter.SSL_SESSION);
            if (sslSession != null) {
                return sslSession.getPeerCertificates();
            }
        } catch (SSLPeerUnverifiedException e) {
            if (Log.isTraceEnabled()) {
                // This is perfectly acceptable when mutual authentication is not enforced by Openfire configuration.
                Log.trace( "Peer does not offer certificates in session: " + session, e);
            }
        }
        return new Certificate[0];
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
    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    @Override
    public void close() {
        if (state.compareAndSet(State.OPEN, State.CLOSED)) {

            // Ensure that the state of this connection, its session and the MINA context are eventually closed.

            if (session != null) {
                session.setStatus(Session.STATUS_CLOSED);
            }

            try {
                deliverRawText0(flashClient ? "</flash:stream>" : "</stream:stream>");
            } catch (Exception e) {
                Log.error("Failed to deliver stream close tag: " + e.getMessage());
            }

            try {
                ioSession.closeOnFlush();
            } catch (Exception e) {
                Log.error("Exception while closing MINA session", e);
            }
            notifyCloseListeners(); // clean up session, etc.
            closeListeners.clear();
        }
    }

    @Override
    public void systemShutdown() {
        deliverRawText("<stream:error><system-shutdown " +
                "xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");
        close();
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
        StanzaHandler stanzaHandler = getStanzaHandler();
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

    protected StanzaHandler getStanzaHandler() {
        return (StanzaHandler)ioSession.getAttribute(ConnectionHandler.HANDLER);
    }

    @Override
    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    @Override
    public boolean isSecure() {
        return ioSession.getFilterChain().contains(TLS_FILTER_NAME);
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException {
        if (isClosed()) {
            backupDeliverer.deliver(packet);
        }
        else {
            boolean errorDelivering = false;
            IoBuffer buffer = IoBuffer.allocate(4096);
            buffer.setAutoExpand(true);
            try {
                buffer.putString(packet.getElement().asXML(), encoder.get());
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                
                ioSessionLock.lock();
                try {
                    ioSession.write(buffer);
                } finally {
                    ioSessionLock.unlock();
                }
            }
            catch (Exception e) {
                Log.debug("Error delivering packet:\n" + packet, e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                backupDeliverer.deliver(packet);
            }
            else {
                session.incrementServerPacketCount();
            }
        }
    }

    @Override
    public void deliverRawText(String text) {
        if (!isClosed()) {
            deliverRawText0(text);
        }
    }

    private void deliverRawText0(String text){
        boolean errorDelivering = false;
        IoBuffer buffer = IoBuffer.allocate(text.length());
        buffer.setAutoExpand(true);
        try {
            //Charset charset = Charset.forName(CHARSET);
            //buffer.putString(text, charset.newEncoder());
            buffer.put(text.getBytes(StandardCharsets.UTF_8));
            if (flashClient) {
                buffer.put((byte) '\0');
            }
            buffer.flip();
            ioSessionLock.lock();
            try {
                ioSession.write(buffer);
            }
            finally {
                ioSessionLock.unlock();
            }
        }
        catch (Exception e) {
            Log.debug("Error delivering raw text:\n" + text, e);
            errorDelivering = true;
        }

        // Attempt to close the connection if delivering text fails.
        if (errorDelivering) {
            close();
        }
    }

    public void startTLS(boolean clientMode, boolean directTLS) throws Exception {

        final EncryptionArtifactFactory factory = new EncryptionArtifactFactory( configuration );
        final SslFilter filter;
        if ( clientMode )
        {
            filter = factory.createClientModeSslFilter();
        }
        else
        {
            filter = factory.createServerModeSslFilter();
        }

        ioSession.getFilterChain().addBefore(EXECUTOR_FILTER_NAME, TLS_FILTER_NAME, filter);

        if (!directTLS)
        {
            ioSession.setAttribute( SslFilter.DISABLE_ENCRYPTION_ONCE, Boolean.TRUE );
        }

        if ( !clientMode && !directTLS ) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText( "<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>" );
        }
    }

    @Override
    public void addCompression() {
        IoFilterChain chain = ioSession.getFilterChain();
        String baseFilter = EXECUTOR_FILTER_NAME;
        if (chain.contains(TLS_FILTER_NAME)) {
            baseFilter = TLS_FILTER_NAME;
        }
        chain.addAfter(baseFilter, COMPRESSION_FILTER_NAME, new CompressionFilter(true, false, CompressionFilter.COMPRESSION_MAX));
    }

    @Override
    public void startCompression() {
        CompressionFilter ioFilter = (CompressionFilter) ioSession.getFilterChain().get(COMPRESSION_FILTER_NAME);
        ioFilter.setCompressOutbound(true);
    }

    @Override
    public ConnectionConfiguration getConfiguration()
    {
        return configuration;
    }

    public boolean isFlashClient() {
        return flashClient;
    }

    @Override
    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
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
        return ioSession.getFilterChain().contains(COMPRESSION_FILTER_NAME);
    }

    @Override
    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    @Override
    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    @Override
    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    @Override
    public void setTlsPolicy(TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    @Override
    public String toString() {
        return super.toString() + " MINA Session: " + ioSession;
    }

    private static class ThreadLocalEncoder extends ThreadLocal<CharsetEncoder> {

        @Override
        protected CharsetEncoder initialValue() {
            return StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }
}
