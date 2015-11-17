/**
 * $Revision: $
 * $Date: $
 *
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.compression.CompressionFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.dom4j.io.OutputFormat;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.keystore.IdentityStoreConfig;
import org.jivesoftware.openfire.keystore.Purpose;
import org.jivesoftware.openfire.keystore.TrustStoreConfig;
import org.jivesoftware.openfire.net.ClientTrustManager;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.ServerTrustManager;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

/**
 * Implementation of {@link Connection} inteface specific for NIO connections when using
 * the MINA framework.<p>
 *
 * MINA project can be found at <a href="http://mina.apache.org">here</a>.
 *
 * @author Gaston Dombiak
 */
public class NIOConnection implements Connection {

	private static final Logger Log = LoggerFactory.getLogger(NIOConnection.class);

    private LocalSession session;
    private IoSession ioSession;

    private ConnectionCloseListener closeListener;

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private PacketDeliverer backupDeliverer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;

    // TODO Uso el #checkHealth????
    /**
     * TLS policy currently in use for this connection.
     */
    private TLSPolicy tlsPolicy = TLSPolicy.optional;
    private boolean usingSelfSignedCertificate;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;
    private static ThreadLocal<CharsetEncoder> encoder = new ThreadLocalEncoder();

    /**
     * Flag that specifies if the connection should be considered closed. Closing a NIO connection
     * is an asynch operation so instead of waiting for the connection to be actually closed just
     * keep this flag to avoid using the connection between #close was used and the socket is actually
     * closed.
     */
    private AtomicReference<State> state = new AtomicReference<State>(State.OPEN);
    
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

    public NIOConnection(IoSession session, PacketDeliverer packetDeliverer) {
        this.ioSession = session;
        this.backupDeliverer = packetDeliverer;
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
    public void registerCloseListener(ConnectionCloseListener listener, Object ignore) {
        if (closeListener != null) {
            throw new IllegalStateException("Close listener already configured");
        }
        if (isClosed()) {
            listener.onConnectionClose(session);
        }
        else {
            closeListener = listener;
        }
    }

    @Override
    public void removeCloseListener(ConnectionCloseListener listener) {
        if (closeListener == listener) {
            closeListener = null;
        }
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

    		if ( session != null ) {
                session.setStatus( Session.STATUS_CLOSED );
            }

            try {
                deliverRawText( flashClient ? "</flash:stream>" : "</stream:stream>" );
            } catch ( Exception e ) {
                Log.error("Failed to deliver stream close tag: " + e.getMessage());
            }
            
            try {
            	ioSession.close( true );
            } catch (Exception e) {
                Log.error("Exception while closing MINA session", e);
            }
        
            notifyCloseListeners(); // clean up session, etc.

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
        if (closeListener != null) {
            try {
                closeListener.onConnectionClose(session);
            } catch (Exception e) {
                Log.error("Error notifying listener: " + closeListener, e);
            }
        }
    }

    @Override
    public void init(LocalSession owner) {
        session = owner;
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
            	// OF-464: if the connection has been dropped, fail over to backupDeliverer (offline)
            	if (!ioSession.isConnected()) {
            		throw new IOException("Connection reset/closed by peer");
            	}
                XMLWriter xmlSerializer =
                        new XMLWriter(new ByteBufferWriter(buffer, encoder.get()), new OutputFormat());
                xmlSerializer.write(packet.getElement());
                xmlSerializer.flush();
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
                    // OF-464: handle dropped connections (no backupDeliverer in this case?)
                    if (!ioSession.isConnected()) {
                        throw new IOException("Connection reset/closed by peer");
                    }
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
    }

    @Override
    public void startTLS(boolean clientMode, String remoteServer, ClientAuth authentication) throws Exception {
        final boolean isClientToServer = (remoteServer == null);

        Log.debug( "StartTLS: using {}", isClientToServer ? "c2s" : "s2s" );

        final SSLConfig sslConfig = SSLConfig.getInstance();
        final TrustStoreConfig storeConfig;
        if (isClientToServer) {
            storeConfig = (TrustStoreConfig) sslConfig.getStoreConfig( Purpose.SOCKETBASED_C2S_TRUSTSTORE );
        } else {
            storeConfig = (TrustStoreConfig) sslConfig.getStoreConfig( Purpose.SOCKETBASED_S2S_TRUSTSTORE );
        }

        final TrustManager[] tm;
        if (clientMode || authentication == ClientAuth.needed || authentication == ClientAuth.wanted) {
            // We might need to verify a certificate from our peer, so get different TrustManager[]'s
            final KeyStore ksTrust = storeConfig.getStore();
            if(isClientToServer) {
                // Check if we can trust certificates presented by the client
                tm = new TrustManager[]{new ClientTrustManager(ksTrust)};
            } else {
                // Check if we can trust certificates presented by the server
                tm = new TrustManager[]{new ServerTrustManager(remoteServer, ksTrust, this)};
            }
        } else {
            tm = storeConfig.getTrustManagers();
        }

        String algorithm = JiveGlobals.getProperty(ConnectionSettings.Client.TLS_ALGORITHM, "TLS");
        SSLContext tlsContext = SSLContext.getInstance( algorithm );

        final IdentityStoreConfig identityStoreConfig = (IdentityStoreConfig) sslConfig.getStoreConfig( Purpose.SOCKETBASED_IDENTITYSTORE );
        tlsContext.init( identityStoreConfig.getKeyManagers(), tm, null);

        SslFilter filter = new SslFilter(tlsContext);
        filter.setUseClientMode(clientMode);
        // Disable SSLv3 due to POODLE vulnerability.
        if (clientMode) {
            filter.setEnabledProtocols(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"});
        } else {
            // ... but accept a SSLv2 Hello when in server mode.
            filter.setEnabledProtocols(new String[]{"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"});
        }
        if (authentication == ClientAuth.needed) {
            filter.setNeedClientAuth(true);
        }
        else if (authentication == ClientAuth.wanted) {
            // Just indicate that we would like to authenticate the client but if client
            // certificates are self-signed or have no certificate chain then we are still
            // good
            filter.setWantClientAuth(true);
        }
        ioSession.getFilterChain().addBefore(EXECUTOR_FILTER_NAME, TLS_FILTER_NAME, filter);
        ioSession.setAttribute(SslFilter.DISABLE_ENCRYPTION_ONCE, Boolean.TRUE);

        if (!clientMode) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
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
    public String getLanguage() {
        return language;
    }

    @Override
    public void setLanaguage(String language) {
        this.language = language;
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
