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

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.security.KeyStore;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.CompressionFilter;
import org.apache.mina.filter.SSLFilter;
import org.dom4j.io.OutputFormat;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.ClientTrustManager;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.SSLJiveKeyManagerFactory;
import org.jivesoftware.openfire.net.SSLJiveTrustManagerFactory;
import org.jivesoftware.openfire.net.ServerTrustManager;
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

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";

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
    private boolean closed;


    public NIOConnection(IoSession session, PacketDeliverer packetDeliverer) {
        this.ioSession = session;
        this.backupDeliverer = packetDeliverer;
        closed = false;
    }

    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        deliverRawText(" ");
        return !isClosed();
    }

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

    public void removeCloseListener(ConnectionCloseListener listener) {
        if (closeListener == listener) {
            closeListener = null;
        }
    }

    public byte[] getAddress() throws UnknownHostException {
        return ((InetSocketAddress) ioSession.getRemoteAddress()).getAddress().getAddress();
    }

    public String getHostAddress() throws UnknownHostException {
        return ((InetSocketAddress) ioSession.getRemoteAddress()).getAddress().getHostAddress();
    }

    public String getHostName() throws UnknownHostException {
        return ((InetSocketAddress) ioSession.getRemoteAddress()).getAddress().getHostName();
    }

    public Certificate[] getLocalCertificates() {
        SSLSession sslSession = (SSLSession) ioSession.getAttribute(SSLFilter.SSL_SESSION);
        if (sslSession != null) {
            return sslSession.getLocalCertificates();
        }
        return new Certificate[0];
    }

    public Certificate[] getPeerCertificates() {
        try {
            SSLSession sslSession = (SSLSession) ioSession.getAttribute(SSLFilter.SSL_SESSION);
            if (sslSession != null) {
                return sslSession.getPeerCertificates();
            }
        } catch (SSLPeerUnverifiedException e) {
            Log.warn("Error retrieving client certificates of: " + session, e);
        }
        return new Certificate[0];
    }

    public void setUsingSelfSignedCertificate(boolean isSelfSigned) {
        this.usingSelfSignedCertificate = isSelfSigned;
    }

    public boolean isUsingSelfSignedCertificate() {
        return usingSelfSignedCertificate;
    }

    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    public void close() {
        boolean closedSuccessfully = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    deliverRawText(flashClient ? "</flash:stream>" : "</stream:stream>", false);
                } catch (Exception e) {
                    // Ignore
                }
                if (session != null) {
                    session.setStatus(Session.STATUS_CLOSED);
                }
                ioSession.close();
                closed = true;
                closedSuccessfully = true;
            }
        }
        if (closedSuccessfully) {
            notifyCloseListeners();
        }
    }

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

    public void init(LocalSession owner) {
        session = owner;
    }

    public boolean isClosed() {
        if (session == null) {
            return closed;
        }
        return session.getStatus() == Session.STATUS_CLOSED;
    }

    public boolean isSecure() {
        return ioSession.getFilterChain().contains("tls");
    }

    public void deliver(Packet packet) throws UnauthorizedException {
        if (isClosed()) {
            backupDeliverer.deliver(packet);
        }
        else {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                XMLWriter xmlSerializer =
                        new XMLWriter(new ByteBufferWriter(buffer, encoder.get()), new OutputFormat());
                xmlSerializer.write(packet.getElement());
                xmlSerializer.flush();
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                ioSession.write(buffer);
            }
            catch (Exception e) {
                Log.debug("NIOConnection: Error delivering packet" + "\n" + this.toString(), e);
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

    public void deliverRawText(String text) {
        // Deliver the packet in asynchronous mode
        deliverRawText(text, true);
    }

    private void deliverRawText(String text, boolean asynchronous) {
        if (!isClosed()) {
            ByteBuffer buffer = ByteBuffer.allocate(text.length());
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                //Charset charset = Charset.forName(CHARSET);
                //buffer.putString(text, charset.newEncoder());
                buffer.put(text.getBytes(CHARSET));
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                if (asynchronous) {
                    ioSession.write(buffer);
                }
                else {
                    // Send stanza and wait for ACK (using a 2 seconds default timeout)
                    boolean ok =
                            ioSession.write(buffer).join(JiveGlobals.getIntProperty("connection.ack.timeout", 2000));
                    if (!ok) {
                        Log.warn("No ACK was received when sending stanza to: " + this.toString());
                    }
                }
            }
            catch (Exception e) {
                Log.debug("NIOConnection: Error delivering raw text" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            // Close the connection if delivering text fails and we are already not closing the connection
            if (errorDelivering && asynchronous) {
                close();
            }
        }
    }

    public void startTLS(boolean clientMode, String remoteServer, ClientAuth authentication) throws Exception {
        boolean c2s = (remoteServer == null);
        KeyStore ksKeys = SSLConfig.getKeyStore();
        String keypass = SSLConfig.getKeyPassword();

        KeyStore ksTrust = (c2s ? SSLConfig.getc2sTrustStore() : SSLConfig.gets2sTrustStore() );
        String trustpass = (c2s ? SSLConfig.getc2sTrustPassword() : SSLConfig.gets2sTrustPassword() );
        if (c2s)  Log.debug("NIOConnection: startTLS: using c2s");
        else Log.debug("NIOConnection: startTLS: using s2s");
        // KeyManager's decide which key material to use.
        KeyManager[] km = SSLJiveKeyManagerFactory.getKeyManagers(ksKeys, keypass);

        // TrustManager's decide whether to allow connections.
        TrustManager[] tm = SSLJiveTrustManagerFactory.getTrustManagers(ksTrust, trustpass);

        if (clientMode || authentication == ClientAuth.needed || authentication == ClientAuth.wanted) {
            // We might need to verify a certificate from our peer, so get different TrustManager[]'s
            if(c2s) {
                // Check if we can trust certificates presented by the client
                tm = new TrustManager[]{new ClientTrustManager(ksTrust)};
            } else {
                // Check if we can trust certificates presented by the server
                tm = new TrustManager[]{new ServerTrustManager(remoteServer, ksTrust, this)};
            }
        }

        SSLContext tlsContext = SSLContext.getInstance("TLS");

        tlsContext.init(km, tm, null);

        SSLFilter filter = new SSLFilter(tlsContext);
        filter.setUseClientMode(clientMode);
        if (authentication == ClientAuth.needed) {
            filter.setNeedClientAuth(true);
        }
        else if (authentication == ClientAuth.wanted) {
            // Just indicate that we would like to authenticate the client but if client
            // certificates are self-signed or have no certificate chain then we are still
            // good
            filter.setWantClientAuth(true);
        }
        // TODO Temporary workaround (placing SSLFilter before ExecutorFilter) to avoid deadlock. Waiting for
        // MINA devs feedback
        ioSession.getFilterChain().addBefore("org.apache.mina.common.ExecutorThreadModel", "tls", filter);
        //ioSession.getFilterChain().addAfter("org.apache.mina.common.ExecutorThreadModel", "tls", filter);
        ioSession.setAttribute(SSLFilter.DISABLE_ENCRYPTION_ONCE, Boolean.TRUE);
        if (!clientMode) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
        }
    }

    public void addCompression() {
        IoFilterChain chain = ioSession.getFilterChain();
        String baseFilter = "org.apache.mina.common.ExecutorThreadModel";
        if (chain.contains("tls")) {
            baseFilter = "tls";
        }
        chain.addAfter(baseFilter, "compression", new CompressionFilter(true, false, CompressionFilter.COMPRESSION_MAX));
    }

    public void startCompression() {
        CompressionFilter ioFilter = (CompressionFilter) ioSession.getFilterChain().get("compression");
        ioFilter.setCompressOutbound(true);
    }

    public boolean isFlashClient() {
        return flashClient;
    }

    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
    }

    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanaguage(String language) {
        this.language = language;
    }

    public boolean isCompressed() {
        return ioSession.getFilterChain().contains("compression");
    }

    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

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
            return Charset.forName(CHARSET).newEncoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
        }
    }
}
