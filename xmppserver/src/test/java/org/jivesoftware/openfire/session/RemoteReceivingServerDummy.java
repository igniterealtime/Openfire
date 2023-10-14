/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.dom4j.*;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.util.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides a network entity that mimics the behavior of a remote XMPP server, when accepting a socket connection.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RemoteReceivingServerDummy extends AbstractRemoteServerDummy implements AutoCloseable
{
    /**
     * When switched to 'true', most XMPP interaction will be printed to standard-out.
     */
    public static final boolean doLog = false;

    private ServerSocket server;

    private Thread acceptThread;
    private Acceptor acceptor = new Acceptor();

    private ExecutorService processingService;

    /**
     * Start accepting socket connections.
     *
     * The port on which socket connections are accepted is automatically allocated. Use {@link #getPort()} to obtain it.
     */
    public void open() throws Exception
    {
        if (server != null) {
            throw new IllegalStateException("Server already open.");
        }

        server = new ServerSocket(0);

        processingService = Executors.newCachedThreadPool();

        acceptThread = new Thread(acceptor);
        acceptThread.start();
    }

    /**
     * Stops accepting socket connections and halts processing of data.
     */
    @Override
    public void close() throws Exception
    {
        stopAcceptThread();

        stopProcessingService();

        if (server != null) {
            server.close();
            server = null;
        }
    }

    /**
     * Get the port on which this instance is accepting sockets.
     *
     * @return a port number.
     */
    public int getPort() throws IOException
    {
        if (server == null) {
            throw new IllegalStateException("Server not yet running. Did you call 'open()'?");
        }
        return server.getLocalPort();
    }

    public void stopAcceptThread() throws InterruptedException
    {
        acceptor.stop();
        acceptThread.interrupt();

        /* This is graceful, but takes a lot of time when combining all unit test executions.
        final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(20));
        while (Instant.now().isBefore(end) && acceptThread.getState() != Thread.State.TERMINATED) {
            Thread.sleep(SO_TIMEOUT.dividedBy(10).toMillis());
        } */
        final Thread.State finalState = acceptThread.getState();
        if (finalState != Thread.State.TERMINATED) {
            if (doLog) System.err.println("Accept thread not terminating after it was stopped. Current state: " + finalState);
            if (doLog) Arrays.stream(acceptThread.getStackTrace()).forEach(System.err::println);
            acceptThread.stop();
        }
        acceptThread = null;
    }

    public synchronized void stopProcessingService() throws InterruptedException
    {
        processingService.shutdown();

        /* This is graceful, but takes a lot of time when combining all unit test executions.
        final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(20));
        while (Instant.now().isBefore(end) && !processingService.isTerminated()) {
            Thread.sleep(SO_TIMEOUT.dividedBy(10).toMillis());
        } */

        if (!processingService.isTerminated()) {
            processingService.shutdownNow();
        }
    }

    private class Acceptor implements Runnable
    {
        boolean shouldStop = false;

        void stop() {
            shouldStop = true;
        }

        @Override
        public void run()
        {
            if (doLog) System.out.println("Start accepting socket connections.");
            while (!shouldStop) {
                try {
                    server.setSoTimeout((int)SO_TIMEOUT.multipliedBy(10).toMillis());
                    final Socket socket = server.accept();
                    if (doLog) System.out.println("Accepted new socket connection.");

                    processingService.submit(new SocketProcessor(socket));
                } catch (Throwable t) {
                    // Log exception only when not cleanly closed.
                    if (acceptThread != null && !acceptThread.isInterrupted()) {
                        if (!(t instanceof SocketTimeoutException)) {
                            t.printStackTrace();
                        }
                    } else {
                        break;
                    }
                }
            }
            if (doLog) System.out.println("Stopped socket accepting connections.");
        }
    }

    private class SocketProcessor implements Runnable
    {
        private Socket socket;
        private OutputStream os;
        private InputStream is;
        private boolean isAuthenticated = false;

        /**
         * To speed up the test execution, SO_TIMEOUT (the socket read timeout) has been set to a low value. When negotiating Server Dialback, a second
         * socket connection is used. XMPP session establishment on the first socket connection is paused while the Server Dialback negotiation takes
         * place. This can easily cause the SO_TIMEOUT to run out. To prevent issues, this implementation allows for reads of the first socket connection
         * to time out for a certain number of times, before treating this as a terminal exception.
         */
        private int allowableSocketTimeouts = 0;

        private SocketProcessor(Socket socket) throws IOException
        {
            if (doLog) System.out.println("New session on socket.");

            if (socket instanceof SSLSocket) {
                allowableSocketTimeouts = 10; // A new TLS-connection has been observed to require some extra time (when Dialback-over-TLS is happening).
            }
            this.socket = socket;
            os = socket.getOutputStream();
            is = socket.getInputStream();
        }

        public synchronized void send(final String data) throws IOException
        {
            if (doLog) System.out.println("# send from remote to Openfire" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
            if (doLog) System.out.println(data);
            if (doLog) System.out.println();
            os.write(data.getBytes());
            os.flush();
        }

        @Override
        public void run()
        {
            if (doLog) System.out.println("Start reading from socket.");
            try {
                final ByteBuffer buffer = ByteBuffer.allocate(1024*16);
                ReadableByteChannel channel = Channels.newChannel(is);
                do {
                    try {
                        while (!processingService.isShutdown() && channel.read(buffer) >= 0) {
                            buffer.flip();
                            String read = StandardCharsets.UTF_8.decode(buffer).toString();
                            buffer.compact();
                            // Ugly hack to get Dialback to work.
                            if (read.startsWith("<db:") && !read.contains("xmlns:db=")) {
                                read = read.replaceFirst(" ", " xmlns:db=\"jabber:server:dialback\" ");
                                if (doLog) System.out.println("# recv (Hacked inbound stanza to include Dialback namespace declaration)" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                            } else {
                                if (doLog) System.out.println("# recv from Openfire" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                            }
                            if (doLog) System.out.println(read);
                            if (doLog) System.out.println();

                            // THIS CONTROLS THE REMOTE SERVER TLS / AUTH RESPONSES
                            if (read.startsWith("<stream:error ") && read.endsWith("</stream:stream>")) {
                                if (doLog) System.out.println("Peer sends a stream error. Can't use this connection anymore.");
                                return;
                            }
                            if (!read.equals("</stream:stream>")) {
                                final Element inbound = parse(read);
                                switch (inbound.getName()) {
                                    case "stream":
                                        sendStreamHeader(inbound);
                                        sendStreamFeatures();
                                        break;
                                    case "starttls":
                                        sendStartTlsProceed(inbound);
                                        return; // Stop reading from this socket immediately, as it is replaced by a secure socket.
                                    case "auth":
                                        sendAuthResponse(inbound);
                                        break;
                                    case "result":
                                        processDialback(inbound);
                                        break;
                                    default:
                                        if (doLog) System.out.println("Received stanza '" + inbound.getName() + "' that I don't know how to respond to.");
                                }
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        allowableSocketTimeouts--;
                        if (allowableSocketTimeouts <= 0) {
                            throw e;
                        }
                    }
                } while (!processingService.isShutdown() && allowableSocketTimeouts > 0);
                if (doLog) System.out.println("Ending read loop." + (socket instanceof SSLSocket ? " (encrypted)" : ""));
            } catch (Throwable t) {
                // Log exception only when not cleanly closed.
                if (doLog && !processingService.isShutdown()) {
                    t.printStackTrace();
                }
            } finally {
                if (doLog) System.out.println("Stopped reading from socket");
            }
        }

        private synchronized void sendStreamHeader(Element inbound) throws IOException
        {
            final Document outbound = DocumentHelper.createDocument();
            final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
            final Element root = outbound.addElement(QName.get("stream", namespace));
            root.add(Namespace.get("jabber:server"));

            if (!disableDialback) {
                root.add(new Namespace("db", "jabber:server:dialback"));
            }
            root.addAttribute("from", XMPP_DOMAIN);
            root.addAttribute("to", inbound.attributeValue("from", null));
            root.addAttribute("version", "1.0");
            root.addAttribute("id", StringUtils.randomString(5));

            send(root.asXML().substring(0, root.asXML().indexOf("</stream:stream>")));
        }

        private synchronized void sendStreamFeatures() throws IOException
        {
            final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
            final Document document = DocumentHelper.createDocument(stream);
            final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
            document.getRootElement().add(features);

            if (!(socket instanceof SSLSocket)) {
                if (encryptionPolicy != Connection.TLSPolicy.disabled) {
                    final Element startTLS = features.addElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
                    if (encryptionPolicy == Connection.TLSPolicy.required) {
                        startTLS.addElement("required");
                    }
                }
                if (!isAuthenticated) {
                    if (!disableDialback && encryptionPolicy != Connection.TLSPolicy.required) { // do not offer Dialback if we expect TLS first.
                        features.addElement(QName.get("dialback", "urn:xmpp:features:dialback"));
                        allowableSocketTimeouts = 10; // It's possible that the peer will start dialback. If that's happening, we need to be more forgiving in regard to socket timeouts.
                    }
                }
            } else if (!isAuthenticated) {
                if (!disableDialback) {
                    features.addElement(QName.get("dialback", "urn:xmpp:features:dialback"));
                    allowableSocketTimeouts = 10; // It's possible that the peer will start dialback. If that's happening, we need to be more forgiving in regard to socket timeouts.
                }
                final Element mechanisms = features.addElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
                if (doLog) System.out.println(((SSLSocket) socket).getSession().getProtocol());
                if (doLog) System.out.println(((SSLSocket) socket).getSession().getCipherSuite());

                try {
                    // Throws an exception if the peer (local server) doesn't send a certificate
                    if (doLog) System.out.println(((SSLSocket) socket).getSession().getPeerPrincipal());
                    Certificate[] certificates = ((SSLSocket) socket).getSession().getPeerCertificates();
                    if (certificates != null && encryptionPolicy != Connection.TLSPolicy.disabled) {
                        try {
                            ((X509Certificate) certificates[0]).checkValidity(); // first peer certificate will belong to the local server
                            mechanisms.addElement("mechanism").addText("EXTERNAL");
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                            if (doLog) System.out.println("local certificate is invalid");
                        }
                    }
                } catch (SSLPeerUnverifiedException e) {
                    if (doLog) System.out.println("local certificate is missing/unverified");
                }
            }

            final String result = stream.asXML(); // String opening and closing element. Opening element needs to be written by parser, to avoid namespace declaration on child element.
            final String streamStripped = result.substring(result.indexOf('>')+1, result.lastIndexOf("</stream:stream>")).trim();
            send(streamStripped);
        }

        private synchronized void sendStartTlsProceed(Element inbound) throws Exception
        {
            if (encryptionPolicy == Connection.TLSPolicy.disabled) {
                final Document outbound = DocumentHelper.createDocument();
                final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                final Element root = outbound.addElement(QName.get("stream", namespace));
                root.add(Namespace.get("jabber:server"));
                root.addElement(QName.get("failure", "urn:ietf:params:xml:ns:xmpp-tls"));

                send(root.asXML().substring(root.asXML().indexOf(">")+1));
                throw new InterruptedIOException("TLS Start received while feature is disabled. Kill the connection");
            }

            final Document outbound = DocumentHelper.createDocument();
            outbound.addElement(QName.get("proceed", "urn:ietf:params:xml:ns:xmpp-tls"));

            send(outbound.getRootElement().asXML());

            if (doLog) System.out.println("Replace the socket with one that will do TLS on the next inbound and outbound data");

            final SSLContext sc = SSLContext.getInstance("TLSv1.3");

            sc.init(createKeyManager(generatedPKIX == null ? null : generatedPKIX.getKeyPair(), generatedPKIX == null ? null : generatedPKIX.getCertificateChain()), createTrustManagerThatTrustsAll(), new java.security.SecureRandom());
            SSLContext.setDefault(sc);

            final SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, null, true);
            sslSocket.setSoTimeout((int) SO_TIMEOUT.toMillis());

            // Just indicate that we would like to authenticate the client but if client
            // certificates are self-signed or have no certificate chain then we are still
            // good
            sslSocket.setWantClientAuth(true); // DO WE NEED TO BRING THIS INTO OUR LocalOutgoingServerSessionTest MATRIX?

            processingService.submit(new SocketProcessor(sslSocket));
        }

        /**
         * Responds to a SASL auth request with a SASL result indicating that authentication succeeded.
         *
         * Very basic verification is performed by this implementation: if the peer provides a certificate that's not expired, authentication is accepted.
         *
         * If TLS is disabled, this sends an authentication failure response.
         *
         * @param inbound The SASL request to authenticate.
         */
        private synchronized void sendAuthResponse(Element inbound) throws IOException
        {
            if (encryptionPolicy == Connection.TLSPolicy.disabled) {
                isAuthenticated = false;

                final Document outbound = DocumentHelper.createDocument();
                final Element failure = outbound.addElement(QName.get("failure", "urn:ietf:params:xml:ns:xmpp-sasl"));
                failure.addElement(QName.get("not-authorized"));

                send(failure.asXML());
            }

            if (!(socket instanceof SSLSocket)) {
                final Document outbound = DocumentHelper.createDocument();
                final Element failure = outbound.addElement(QName.get("failure", "urn:ietf:params:xml:ns:xmpp-sasl"));
                failure.addElement(QName.get("encryption-required"));

                send(failure.asXML());
            }

            final X509Certificate[] peerCertificates = (X509Certificate[]) ((SSLSocket) socket).getSession().getPeerCertificates();
            if (peerCertificates == null || peerCertificates.length == 0 || Instant.now().isAfter(peerCertificates[0].getNotAfter().toInstant()) || Instant.now().isBefore(peerCertificates[0].getNotBefore().toInstant())) {
                final Document outbound = DocumentHelper.createDocument();
                final Element failure = outbound.addElement(QName.get("failure", "urn:ietf:params:xml:ns:xmpp-sasl"));
                failure.addElement(QName.get("not-authorized"));

                send(failure.asXML());
            }


            isAuthenticated = true;
            final Document root = DocumentHelper.createDocument();
            root.addElement(QName.get("success", "urn:ietf:params:xml:ns:xmpp-sasl"));
            send(root.getRootElement().asXML());
        }

        /**
         * Responds to a Dialback request with a Dialback result indicating that authentication succeeded.
         *
         * Proper Dialback should first verify with an Authoritive Server from the remote domain. This method skips that,
         * and authenticates blindly.
         *
         * @param inbound The Dialback request to authenticate.
         */
        private synchronized void processDialback(Element inbound) throws IOException
        {
            allowableSocketTimeouts = 10;

            if (disableDialback) {
                final Document outbound = DocumentHelper.createDocument();
                final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                final Element root = outbound.addElement(QName.get("stream", namespace));
                root.add(Namespace.get("jabber:server"));
                final Element error = root.addElement(QName.get("error", "stream", "http://etherx.jabber.org/streams"));
                error.addElement(QName.get("unsupported-stanza-type", "urn:ietf:params:xml:ns:xmpp-streams"));

                send(root.asXML().substring(root.asXML().indexOf(">")+1));
                throw new InterruptedIOException("Dialback received while feature is disabled. Kill the connection");
            }

            if (encryptionPolicy == Connection.TLSPolicy.required && !(socket instanceof SSLSocket)) {
                final Document outbound = DocumentHelper.createDocument();
                final Element result = outbound.addElement(QName.get("result", "db", "jabber:server:dialback"));
                result.addAttribute("from", XMPP_DOMAIN);
                result.addAttribute("to", inbound.attributeValue("from", null));
                result.addAttribute("type", "error");
                final Element error = result.addElement("error");
                error.addAttribute("type", "cancel");
                error.addElement("policy-violation", "urn:ietf:params:xml:ns:xmpp-stanzas");

                send(outbound.getRootElement().asXML());
                return; // spec says to not kill the connection.
            }

            if (inbound.getTextTrim().isEmpty()) {
                throw new IllegalStateException("Not supporting processing anything but an initial dialback key submission.");
            }

            // Skip the check with an Authoritative Server (which is what Dialback _should_ do). Simply report a faked validation result.
            final Document outbound = DocumentHelper.createDocument();
            final Element result = outbound.addElement(QName.get("result", "db", "jabber:server:dialback"));
            result.addAttribute("from", XMPP_DOMAIN);
            result.addAttribute("to", inbound.attributeValue("from", null));
            result.addAttribute("type", "valid");

            send(outbound.getRootElement().asXML());
        }
    }
}
