/*
 * Copyright (C) 2023-2025 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class RemoteInitiatingServerDummy extends AbstractRemoteServerDummy
{
    private ServerSocket dialbackAuthoritativeServer;
    private Thread dialbackAcceptThread;
    private DialbackAcceptor dialbackAcceptor = new DialbackAcceptor();

    private final String connectTo;
    boolean attemptedEncryptionNegotiation = false;
    boolean alreadyTriedSaslExternal = false;
    boolean peerSupportsDialback;
    private ExecutorService processingService;
    private final List<StreamID> receivedStreamIDs = new ArrayList<>();
    private final List<StreamID> processedStreamIDs = new ArrayList<>();
    private final List<String> receivedStreamFromValues = new ArrayList<>();
    private final List<String> receivedStreamToValues = new ArrayList<>();

    /**
     * A monitor that is used to flag when this dummy has finished trying to set up a connection to Openfire. This is to
     * help the unit test know when it can start verifying the test outcome.
     */
    private final Phaser phaser = new Phaser(0);
    
    public RemoteInitiatingServerDummy(final String connectTo)
    {
        this.connectTo = connectTo;
    }

    public void init() throws IOException
    {
        if (!disableDialback) {
            dialbackAuthoritativeServer = new ServerSocket(0);
        }
    }

    public void connect(int port) throws IOException, InterruptedException
    {
        log("connect");
        processingService = Executors.newCachedThreadPool();

        if (dialbackAuthoritativeServer != null) {
            dialbackAcceptThread = new Thread(dialbackAcceptor);
            dialbackAcceptThread.start();
        }

        phaser.register();

        final SocketProcessor socketProcessor = new SocketProcessor(port);
        processingService.submit(socketProcessor);
    }

    public void blockUntilDone(final long timeout, final TimeUnit unit) {
        try {
            phaser.awaitAdvanceInterruptibly(0, timeout, unit);
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException("Test scenario never reached 'done' state");
        }
    }

    protected void done()
    {
        log("Start being done");
        if (!getNonProcessedStreamIDs().isEmpty()) {
            // If we recorded a stream ID, wait for this stream to be registered in the session manager before
            // continuing to prevent a race condition.
            final StreamID lastReceivedID = getNonProcessedStreamIDs().get(getNonProcessedStreamIDs().size()-1);
            log("Wait for stream to be registered in the session manager: " + lastReceivedID);
            final Instant stopWaiting = Instant.now().plus(500, ChronoUnit.MILLIS);
            try {
                final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
                boolean found = false;
                while (Instant.now().isBefore(stopWaiting)) {
                    if (sessionManager.getIncomingServerSession( lastReceivedID ) != null) {
                        log("Found stream registered in the session manager: " + lastReceivedID);
                        found = true;
                        break;
                    }
                    Thread.sleep(10);
                }
                if (!found) log("NEVER FOUND STREAM WE WERE (pointlessly?) WAITING FOR: " + lastReceivedID);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        log("Phaser arriving and registering");
        phaser.arriveAndDeregister();
        log("Done being done");
    }

    public void disconnect() throws InterruptedException, IOException
    {
        log("disconnect");
        stopProcessingService();
        stopDialbackAcceptThread();
        if (dialbackAuthoritativeServer != null) {
            dialbackAuthoritativeServer.close();
            dialbackAuthoritativeServer = null;
        }
        log("disconnected");
    }

    public synchronized void stopProcessingService() throws InterruptedException
    {
        if (processingService != null) {
            processingService.shutdown();

            /* This is graceful, but takes a lot of time when combining all unit test executions.
            final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(20));
            while (Instant.now().isBefore(end) && !processingService.isTerminated()) {
                Thread.sleep(Math.max(100, SO_TIMEOUT.dividedBy(50).toMillis()));
            } */
            if (!processingService.isTerminated()) {
                processingService.shutdownNow();
            }
        }
    }

    public synchronized void stopDialbackAcceptThread() throws InterruptedException
    {
        if (dialbackAcceptThread == null) {
            return;
        }
        dialbackAcceptor.stop();
        dialbackAcceptThread.interrupt();
        dialbackAcceptThread.join(SO_TIMEOUT.multipliedBy(20).toMillis());

        final Thread.State finalState = dialbackAcceptThread.getState();
        if (finalState != Thread.State.TERMINATED) {
            if (doLog) System.err.println("Dialback Accept thread not terminating after it was stopped. Current state: " + finalState);
            if (doLog) Arrays.stream(dialbackAcceptThread.getStackTrace()).forEach(System.err::println);
        }
        dialbackAcceptThread = null;
    }

    public int getDialbackAuthoritativeServerPort()
    {
        return dialbackAuthoritativeServer != null ? dialbackAuthoritativeServer.getLocalPort() : -1;
    }

    /**
     * Returns all stream IDs (potential duplicates, but no null values) that were received from the peer during the
     * setup.
     *
     * @return all stream IDs received from the peer.
     */
    public List<StreamID> getReceivedStreamIDs()
    {
        return receivedStreamIDs;
    }

    /**
     * Returns all stream IDs that have been received, but have not yet been marked as being processed.
     *
     * @return Stream IDs still being processed.
     */
    public List<StreamID> getNonProcessedStreamIDs()
    {
        final List<StreamID> result = new ArrayList<>(receivedStreamIDs);
        result.removeAll(processedStreamIDs);
        return result;
    }

    /**
     * Mark the last received stream ID as being fully processed. This prevents the teardown from waiting for this stream
     * to be established.
     */
    public void markLastStreamIDasProcessed() {
        final StreamID streamID = receivedStreamIDs.get(receivedStreamIDs.size() - 1);
        processedStreamIDs.add(streamID);
        log("Marked as processed: " + streamID);
    }

    /**
     * Returns all stream 'from' attribute values (potential duplicates, but no null values) that were received from the
     * peer during the setup.
     *
     * @return all stream 'from' attribute values received from the peer.
     */
    public List<String> getReceivedStreamFromValues()
    {
        return receivedStreamFromValues;
    }

    /**
     * Returns all stream 'to' attribute values (potential duplicates, but no null values) that were received from the
     * peer during the setup.
     *
     * @return all stream 'to' attribute values received from the peer.
     */
    public List<String> getReceivedStreamToValues()
    {
        return receivedStreamToValues;
    }

    private class DialbackAcceptor implements Runnable
    {
        boolean shouldStop = false;

        void stop() {
            log("Start stopping accepting connections (as Server Dialback Authoritative Server).");
            shouldStop = true;
            try {
                dialbackAuthoritativeServer.close();
                log("Closed server accepting connections (as Server Dialback Authoritative Server)");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run()
        {
            log("Start accepting socket connections (as Server Dialback Authoritative Server).");
            while (!shouldStop) {
                try {
                    dialbackAuthoritativeServer.setSoTimeout((int)SO_TIMEOUT.toMillis());
                    final Socket socket = dialbackAuthoritativeServer.accept();
                    final InputStream is = socket.getInputStream();
                    final OutputStream os = socket.getOutputStream();
                    log("DIALBACK AUTH SERVER: Accepted new socket connection.");

                    final byte[] buffer = new byte[1024 * 16];
                    int count;
                    while ((count = is.read(buffer)) > 0) {
                        String read = new String(buffer, 0, count);
                        log("# DIALBACK AUTH SERVER recv");
                        log(read);
                        log("");

                        final Document outbound = DocumentHelper.createDocument();
                        final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                        final Namespace dialbackNamespace = new Namespace("db", "jabber:server:dialback");
                        final Element stream = outbound.addElement(QName.get("stream", namespace));
                        stream.add(Namespace.get("jabber:server"));
                        stream.add(dialbackNamespace);
                        stream.addAttribute("from", XMPP_DOMAIN);
                        stream.addAttribute("to", connectTo);
                        stream.addAttribute("version", "1.0");

                        String response = null;
                        read = read.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "").trim();
                        if (read.startsWith("<stream:stream ")) {
                            stream.addElement(QName.get("features", namespace));
                            response = stream.asXML().substring(0, stream.asXML().indexOf("</stream:stream>"));
                        } else if (read.startsWith("<db:")) {
                            final int idStart = read.indexOf("id=\"") + 4;
                            final int idEnd = read.indexOf('"', idStart);
                            final String id = read.substring(idStart, idEnd);

                            // Blindly verify anything.
                            final Element verify = stream.addElement(QName.get("verify", dialbackNamespace));
                            verify.addAttribute("from", XMPP_DOMAIN);
                            verify.addAttribute("to", connectTo);
                            verify.addAttribute("id", id);
                            verify.addAttribute("type", "valid");
                            response = verify.asXML();
                        } else if (read.equals("</stream:stream>")) {
                            response = "</stream:stream>";
                        } else {
                            log("I don't know how to process this data.");
                        }

                        if (response != null) {
                            log("# DIALBACK AUTH SERVER send to Openfire");
                            log(response);
                            log("");
                            os.write(response.getBytes());
                            os.flush();

                            if (response.equals("</stream:stream>")) {
                                socket.close();
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    // Log exception only when not cleanly closed.
                    if (dialbackAcceptThread != null && !dialbackAcceptThread.isInterrupted() && !shouldStop) {
                        if (!(t instanceof SocketTimeoutException)) { // Ignore SO_TIMEOUT when not stopping.
                            t.printStackTrace();
                        }
                    } else {
                        log("Stop accepting (as Server Dialback Authoritative Server) (interrupted/closed).");
                        break;
                    }
                }
            }
            log("Stopped accepting socket connections (as Server Dialback Authoritative Server).");
        }
    }

    private class SocketProcessor implements Runnable
    {
        private Socket socket;
        private OutputStream os;
        private InputStream is;
        boolean peerAdvertisedDialbackNamespace = false;

        /**
         * To speed up the test execution, SO_TIMEOUT (the socket read timeout) has been set to a low value. When negotiating Server Dialback, a second
         * socket connection is used. XMPP session establishment on the first socket connection is paused while the Server Dialback negotiation takes
         * place. This can easily cause the SO_TIMEOUT to run out. To prevent issues, this implementation allows for reads of the first socket connection
         * to time out for a certain number of times, before treating this as a terminal exception.
         */
        private int allowableSocketTimeouts = 0;

        private SocketProcessor(int port) throws IOException
        {
            socket = new Socket();
            final InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            log("Creating new socket to " + socketAddress);
            socket.connect(socketAddress, (int) SO_TIMEOUT.toMillis());
            os = socket.getOutputStream();
            is = socket.getInputStream();
        }

        private SocketProcessor(Socket socket) throws IOException
        {
            log("New session on socket");

            this.socket = socket;
            os = socket.getOutputStream();
            is = socket.getInputStream();
        }

        public synchronized void send(final String data) throws IOException
        {
            log("# send from remote to Openfire" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
            log(data);
            log("");
            os.write(data.getBytes());
            os.flush();
        }

        @Override
        public void run()
        {
            log("Start reading from socket" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
            try {
                sendStreamHeader();

                do {
                    try {
                        final byte[] buffer = new byte[1024 * 16];
                        int count;
                        while (!processingService.isShutdown() && (count = is.read(buffer)) > 0) {
                            String read = new String(buffer, 0, count);
                            if (read.startsWith("<?")) {
                                log("(stripping prolog from data that's read)");
                                final int endProlog = read.indexOf("?>") + 2;
                                read = read.substring(endProlog);
                            }
                            if (read.startsWith("<stream:") && !read.contains("xmlns:stream=")) {
                                // Ugly hack to get stream prefix to work.
                                read = read.replaceFirst(">", " xmlns:stream=\"http://etherx.jabber.org/streams\">");
                                log("# recv (Hacked inbound stanza to include stream namespace declaration)" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                            } else if (read.startsWith("<db:") && !read.contains("xmlns:db=")) {
                                // Ugly hack to get Dialback to work.
                                read = read.replaceFirst(" ", " xmlns:db=\"jabber:server:dialback\" ");
                                log("# recv (Hacked inbound stanza to include Dialback namespace declaration)" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                            } else {
                                log("# recv from Openfire" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                            }
                            log(read);
                            log("");

                            if (read.contains("<stream:error")) {
                                log("Peer sends a stream error. Can't use this connection anymore.");
                                return;
                            }
                            if (!read.equals("</stream:stream>")) {
                                Element inbound = parse(read);
                                if (inbound.getName().equals("stream")) {
                                    // This is expected to be the response stream header. No need to act on this, but if it contains a dialback namespace, then this suggests that the peer supports dialback.
                                    peerAdvertisedDialbackNamespace = inbound.declaredNamespaces().stream().anyMatch(namespace -> "jabber:server:dialback".equals(namespace.getURI()));
                                    if (inbound.attributeValue("id") != null) receivedStreamIDs.add(BasicStreamIDFactory.createStreamID(inbound.attributeValue("id")));
                                    if (inbound.attributeValue("to") != null) receivedStreamToValues.add(inbound.attributeValue("to"));
                                    if (inbound.attributeValue("from") != null) receivedStreamFromValues.add(inbound.attributeValue("from"));
                                    switch (inbound.elements().size()) {
                                        case 0:
                                            // Done processing the input. Iterate, to try to read more.
                                            continue;
                                        case 1:
                                            // There are child elements to process!
                                            inbound = inbound.elements().get(0);
                                            break;
                                        default:
                                            // More than one child element. This test implementation can't currently handle that.
                                            throw new IllegalStateException("Unable to process more than one child element.");
                                    }
                                }
                                switch (inbound.getName()) {
                                    case "features":
                                        negotiateFeatures(inbound);
                                        break;
                                    case "result":
                                        processDialbackResult(inbound);
                                        break;
                                    case "proceed":
                                        processStartTLSProceed(inbound);
                                        return; // stop reading more from this inputstream.
                                    case "success": // intended fall-through
                                    case "failure":
                                        if (inbound.getNamespaceURI().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
                                            if (processSaslResponse(inbound)) {
                                                log("Successfully authenticated using SASL! We're done setting up a connection.");
                                                return;
                                            } else if (peerSupportsDialback && !disableDialback) {
                                                log("Unable to authenticate using SASL! Dialback seems to be available. Trying that...");
                                                startDialbackAuth();
                                                break;
                                            } else {
                                                throw new InterruptedIOException("Unable to authenticate");
                                            }
                                        } else if (inbound.getNamespaceURI().equals("urn:ietf:params:xml:ns:xmpp-tls")) {
                                            throw new InterruptedIOException("Received StartTLS failure from Openfire. Aborting connection");
                                        }
                                        // intended fall-through
                                    default:
                                        log("Received stanza '" + inbound.getName() + "' that I don't know how to respond to." + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                                }
                            } else {
                                // received an end of stream: if the peer closes the connection, then we're done trying.
                                break;
                            }
                        }
                        // Maybe we should immediately return here, as the stream has ended. Socket timeouts (which are processed by the remainder of this method) seem not relevant in that case. return;
                    } catch (SocketTimeoutException e) {
                        allowableSocketTimeouts--;
                        if (allowableSocketTimeouts <= 0) {
                            throw e;
                        }
                    }
                } while (!processingService.isShutdown() && allowableSocketTimeouts > 0);
                log("Ending read loop" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
            } catch (Throwable t) {
                // Log exception only when not cleanly closed.
                if (doLog && !processingService.isShutdown()) {
                    t.printStackTrace();
                }
            } finally {
                log("Stopped reading from socket" + (socket instanceof SSLSocket ? " (encrypted)" : ""));
                done();
            }
        }

        private synchronized void sendStreamHeader() throws IOException
        {
            final Document outbound = DocumentHelper.createDocument();
            final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
            final Element root = outbound.addElement(QName.get("stream", namespace));
            root.add(Namespace.get("jabber:server"));

            if (!disableDialback) {
                root.add(new Namespace("db", "jabber:server:dialback"));
            }
            root.addAttribute("from", XMPP_DOMAIN);
            root.addAttribute("to", connectTo);
            root.addAttribute("version", "1.0");

            send(root.asXML().substring(0, root.asXML().indexOf("</stream:stream>")));
        }

        private void negotiateFeatures(final Element features) throws IOException
        {
            if (!attemptedEncryptionNegotiation) {
                attemptedEncryptionNegotiation = true;
                if (negotiateEncryption(features)) {
                    return;
                }
            }
            negotiateAuthentication(features);
        }

        /**
         * Returns 'true' if negotiation was started, false if no negotiation was started.
         */
        private boolean negotiateEncryption(final Element features) throws IOException
        {
            log("Negotiating encryption...");
            final Element startTLSel = features.element(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
            final boolean peerSupportsStartTLS = startTLSel != null;
            final boolean peerRequiresStartTLS = peerSupportsStartTLS && startTLSel.element("required") != null;
            log("Openfire " + (peerRequiresStartTLS ? "requires" : (peerSupportsStartTLS ? "supports" : "does not support" )) + " StartTLS. Our own policy: " + encryptionPolicy + ".");

            switch (encryptionPolicy) {
                case disabled:
                    if (peerRequiresStartTLS) {
                        final Document outbound = DocumentHelper.createDocument();
                        final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                        final Element root = outbound.addElement(QName.get("stream", namespace));
                        root.add(Namespace.get("jabber:server"));
                        final Element error = root.addElement(QName.get("error", "stream", "http://etherx.jabber.org/streams"));
                        error.addElement(QName.get("undefined-condition", "urn:ietf:params:xml:ns:xmpp-streams"));

                        markLastStreamIDasProcessed(); // Prevents the code from waiting on this stream to be registered with session manager during test fixture teardown.

                        send(root.asXML().substring(root.asXML().indexOf(">")+1));
                        throw new InterruptedIOException("Openfire requires TLS, we disabled it.");
                    }
                    return false;
                case optional:
                    if (peerSupportsStartTLS) {
                        initiateTLS();
                        return true;
                    }
                    return false;
                case required:
                    if (!peerSupportsStartTLS) {
                        final Document outbound = DocumentHelper.createDocument();
                        final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                        final Element root = outbound.addElement(QName.get("stream", namespace));
                        root.add(Namespace.get("jabber:server"));
                        final Element error = root.addElement(QName.get("error", "stream", "http://etherx.jabber.org/streams"));
                        error.addElement(QName.get("undefined-condition", "urn:ietf:params:xml:ns:xmpp-streams"));

                        markLastStreamIDasProcessed(); // Prevents the code from waiting on this stream to be registered with session manager during test fixture teardown.

                        send(root.asXML().substring(root.asXML().indexOf(">")+1));
                        throw new InterruptedIOException("Openfire disabled TLS, we require it.");
                    }
                    else
                    {
                        initiateTLS();
                        return true;
                    }
                default:
                    throw new IllegalStateException("This implementation does not supported encryption policy: " + encryptionPolicy);
            }
        }

        private void initiateTLS() throws IOException {
            log("Initiating TLS...");
            final Document outbound = DocumentHelper.createDocument();
            final Element startTls = outbound.addElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));

            markLastStreamIDasProcessed(); // Prevents the code from waiting on the stream (to be replaced with an encrypted one) to be registered with session manager during test fixture teardown.

            send(startTls.asXML());
        }

        private void processStartTLSProceed(Element proceed) throws IOException, NoSuchAlgorithmException, KeyManagementException
        {
            log("Received StartTLS proceed.");
            log("Replace the socket with one that will do TLS on the next inbound and outbound data");

            final SSLContext sc = SSLContext.getInstance("TLSv1.3");

            TrustManager[] tm = createTrustManagerThatTrustsAll();
            SecureRandom random = new SecureRandom();


            KeyManager[] km = createKeyManager(new KeyPair(null, null), new X509Certificate[0]);
            if (generatedPKIX != null) {
                KeyPair keyPair = generatedPKIX.getKeyPair();
                X509Certificate[] certificateChain = generatedPKIX.getCertificateChain();
                km = createKeyManager(keyPair, certificateChain);
            }

            sc.init(km, tm , random);
            SSLContext.setDefault(sc);

            final SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, null, socket.getPort(), true);
            sslSocket.setSoTimeout((int) SO_TIMEOUT.multipliedBy(10).toMillis()); // TLS handshaking is resource intensive. Relax the SO_TIMEOUT value a bit, to prevent test failures in constraint environments.
            sslSocket.addHandshakeCompletedListener(event -> log("SSL handshake completed: " + event));
                sslSocket.startHandshake();

            // Just indicate that we would like to authenticate the client but if client
            // certificates are self-signed or have no certificate chain then we are still
            // good
            //sslSocket.setWantClientAuth(true); // DO WE NEED TO BRING THIS INTO OUR LocalOutgoingServerSessionTest MATRIX?
            phaser.register();

            final SocketProcessor sslSocketProcessor = new SocketProcessor(sslSocket);
            processingService.submit(sslSocketProcessor);
        }

        private void negotiateAuthentication(final Element features) throws IOException {
            log("Negotiating authentication...");
            final Element mechanismsEl = features.element(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
            final boolean peerSupportsSASLExternal = mechanismsEl != null && mechanismsEl.elements().stream().anyMatch(element -> "mechanism".equals(element.getName()) && "EXTERNAL".equals(element.getTextTrim()));
            peerSupportsDialback = peerAdvertisedDialbackNamespace || features.element(QName.get("dialback", "urn:xmpp:features:dialback")) != null;
            log("Openfire " + (peerSupportsSASLExternal ? "offers" : "does not offer") + " SASL EXTERNAL, " + (peerSupportsDialback ? "supports" : "does not support") + " Server Dialback. Our own policy: SASL EXTERNAL " + (encryptionPolicy != Connection.TLSPolicy.disabled ? "available" : "not available") + ", Dialback: " + (!disableDialback ? "supported" : "not supported") + ".");

            if (peerSupportsSASLExternal && encryptionPolicy != Connection.TLSPolicy.disabled && !alreadyTriedSaslExternal) {
                authenticateUsingSaslExternal();
            } else if (peerSupportsDialback && !disableDialback) {
                startDialbackAuth();
            } else {
                log("Unable to do authentication.");
                markLastStreamIDasProcessed(); // Prevents the code from waiting on this stream to be registered with session manager during test fixture teardown.
                throw new InterruptedIOException("Unable to do authentication.");
            }
        }

        private void authenticateUsingSaslExternal() throws IOException {
            log("Authenticating using SASL EXTERNAL");
            alreadyTriedSaslExternal = true;
            final Document outbound = DocumentHelper.createDocument();
            final Element root = outbound.addElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"));
            root.addAttribute("mechanism", "EXTERNAL");
            root.setText(Base64.getEncoder().encodeToString(XMPP_DOMAIN.getBytes()));
            send(root.asXML());
        }

        private void startDialbackAuth() throws IOException {
            log("Authenticating using Server Dialback");
            allowableSocketTimeouts = 10;
            final String key = "UNITTESTDIALBACKKEY";

            final Document outbound = DocumentHelper.createDocument();
            final Element root = outbound.addElement(QName.get("result", "db", "jabber:server:dialback"));
            root.addAttribute("from", XMPP_DOMAIN);
            root.addAttribute("to", connectTo);
            root.setText(key);

            send(root.asXML().replace(" xmlns:db=\"jabber:server:dialback\"",""));
        }

        private void processDialbackResult(final Element result) throws IOException {
            final String type = result.attributeValue("type");
            log("Openfire reports Server Dialback result of type " + type);
            if (!"valid".equals(type)) {
                throw new InterruptedIOException("Server Dialback failed");
            }

            log("Successfully authenticated using Server Dialback! We're done setting up a connection.");
            done();
        }

        private boolean processSaslResponse(final Element result) throws IOException {
            final String name = result.getName();
            log("Openfire reports SASL result of type " + name);
            return "success".equals(name);
        }
    }
}
