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
import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.StringUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides a network entity that mimics the behavior of a remote XMPP server, when accepting a socket connection.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RemoteServerDummy implements AutoCloseable
{
    public static final String XMPP_DOMAIN = "remote-dummy.example.org";
    private static final Duration SO_TIMEOUT = Duration.ofMillis(500);

    private boolean useExpiredEndEntityCertificate;
    private boolean useSelfSignedCertificate;
    private boolean disableDialback;
    private ServerSettings.EncryptionPolicy encryptionPolicy = ServerSettings.EncryptionPolicy.OPTIONAL;

    private KeystoreTestUtils.ResultHolder generatedPKIX;

    private ServerSocket server;

    private Thread acceptThread;
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

        acceptThread = new Thread(new Acceptor());
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

    /**
     * Updates the TLS encryption policy that's observed by this server.
     */
    public void setEncryptionPolicy(ServerSettings.EncryptionPolicy encryptionPolicy)
    {
        this.encryptionPolicy = encryptionPolicy;
    }

    /**
     * When set to 'true', this instance will identify itself with a TLS certificate that is self-signed.
     *
     * Must be invoked before {@link #preparePKIX()} is invoked.
     *
     * @param useSelfSignedCertificate 'true' to use a self-signed certificate
     */
    public void setUseSelfSignedCertificate(boolean useSelfSignedCertificate)
    {
        if (generatedPKIX != null) {
            throw new IllegalStateException("Cannot change PKIX settings after PKIX has been prepared.");
        }
        this.useSelfSignedCertificate = useSelfSignedCertificate;
    }

    /**
     * When set to 'true', this instance will identify itself with a TLS certificate that is expired (its 'notBefore'
     * and 'notAfter' values define a period of validity that does not include the current date and time).
     *
     * Must be invoked before {@link #preparePKIX()} is invoked.
     *
     * @param useExpiredEndEntityCertificate 'true' to use an expired certificate
     */
    public void setUseExpiredEndEntityCertificate(boolean useExpiredEndEntityCertificate)
    {
        if (generatedPKIX != null) {
            throw new IllegalStateException("Cannot change PKIX settings after PKIX has been prepared.");
        }
        this.useExpiredEndEntityCertificate = useExpiredEndEntityCertificate;
    }

    /**
     * When set to 'true', this instance will NOT advertise support for the Dialback authentication mechanism, and will
     * reject Dialback authentication attempts.
     */
    public void setDisableDialback(boolean disableDialback) {
        this.disableDialback = disableDialback;
    }

    /**
     * Generates KeyPairs and certificates that are used to identify this server using TLS.
     *
     * The data that is generated by this method can be configured by invoking methods such as
     * {@link #setUseSelfSignedCertificate(boolean)} and
     * {@link #setUseExpiredEndEntityCertificate(boolean)}. These must be invoked before invoking #preparePKIX
     */
    public void preparePKIX() throws Exception
    {
        if (generatedPKIX != null) {
            throw new IllegalStateException("PKIX already prepared.");
        }

        final String commonName = XMPP_DOMAIN;
        if (useSelfSignedCertificate) {
            generatedPKIX = useExpiredEndEntityCertificate ? KeystoreTestUtils.generateExpiredSelfSignedCertificate(commonName) : KeystoreTestUtils.generateSelfSignedCertificate(commonName);
        } else {
            generatedPKIX = useExpiredEndEntityCertificate ? KeystoreTestUtils.generateCertificateChainWithExpiredEndEntityCert(commonName) : KeystoreTestUtils.generateValidCertificateChain(commonName);
        }
    }

    /**
     * Returns the KeyPairs and certificates that are used to identify this server using TLS.
     *
     * @return TLS identification material for this server.
     */
    public KeystoreTestUtils.ResultHolder getGeneratedPKIX() {
        return generatedPKIX;
    }


    public synchronized void stopAcceptThread() throws InterruptedException
    {
        acceptThread.interrupt();
        final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(2));
        while (Instant.now().isBefore(end) && acceptThread.getState() != Thread.State.TERMINATED) {
            Thread.sleep(SO_TIMEOUT.dividedBy(10).toMillis());
        }
        final Thread.State finalState = acceptThread.getState();
        if (finalState != Thread.State.TERMINATED) {
            System.out.println("Accept thread not terminating after it was stopped. Current state: " + finalState);
        }
        acceptThread = null;
    }

    public synchronized void stopProcessingService() throws InterruptedException
    {
        processingService.shutdown();
        final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(2));
        while (Instant.now().isBefore(end) && !processingService.isTerminated()) {
            Thread.sleep(SO_TIMEOUT.dividedBy(10).toMillis());
        }
        if (!processingService.isTerminated()) {
            processingService.shutdownNow();
        }
    }

    private class Acceptor implements Runnable
    {
        @Override
        public void run()
        {
            System.out.println("Start accepting socket connections.");
            while (true) {
                try {
                    final Socket socket = server.accept();
                    System.out.println("Accepted new socket connection.");

                    processingService.submit(new SocketProcessor(socket));
                } catch (Throwable t) {
                    // Log exception only when not cleanly closed.
                    if (acceptThread != null && !acceptThread.isInterrupted()) {
                        t.printStackTrace();
                    } else {
                        break;
                    }
                }
            }
            System.out.println("Stopped socket accepting connections.");
        }
    }

    private class SocketProcessor implements Runnable
    {
        private Socket socket;
        private OutputStream os;
        private InputStream is;
        private boolean isAuthenticated = false;

        private SocketProcessor(Socket socket) throws IOException
        {
            System.out.println("New session on socket.");

            this.socket = socket;
            os = socket.getOutputStream();
            is = socket.getInputStream();
        }

        public synchronized void send(final String data) throws IOException
        {
            System.out.println("# send from remote to Openfire");
            System.out.println(data);
            System.out.println();
            os.write(data.getBytes());
            os.flush();
        }

        @Override
        public void run()
        {
            System.out.println("Start reading from socket.");
            try {
                final byte[] buffer = new byte[1024 * 16];
                int count;
                while ((count = is.read(buffer)) > 0) {
                    String read = new String(buffer, 0, count);

                    // Ugly hack to get Dialback to work.
                    if (read.startsWith("<db:")) {
                        read = read.replaceFirst(" ", " xmlns:db=\"jabber:server:dialback\" ");
                        System.out.println("# recv (Hacked inbound stanza to include Dialback namespace declaration)");
                    } else {
                        System.out.println("# recv from Openfire");
                    }
                    System.out.println(read);
                    System.out.println();

                    // THIS CONTROLS THE REMOTE SERVER TLS / AUTH RESPONSES
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
                                System.out.println("Received stanza '" + inbound.getName() + "' that I don't know how to respond to.");
                        }
                    }
                }
            } catch (InterruptedIOException e) {

            } catch (Throwable t) {
                // Log exception only when not cleanly closed.
                t.printStackTrace();
            }
            System.out.println("Stopped reading from socket.");

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
            final Document root = DocumentHelper.createDocument();
            final Element features = root.addElement("features");
            if (!(socket instanceof SSLSocket)) {
                if (encryptionPolicy != ServerSettings.EncryptionPolicy.DISABLED) {
                    final Element startTLS = features.addElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
                    if (encryptionPolicy == ServerSettings.EncryptionPolicy.REQUIRED) {
                        startTLS.addElement("required");
                    }
                }
                if (!isAuthenticated) {
                    if (!disableDialback && encryptionPolicy != ServerSettings.EncryptionPolicy.REQUIRED) { // do not offer Dialback if we expect TLS first.
                        features.addElement(QName.get("dialback", "urn:xmpp:features:dialback"));
                    }
                }
            } else if (!isAuthenticated) {
                if (!disableDialback) {
                    features.addElement(QName.get("dialback", "urn:xmpp:features:dialback"));
                }
                final Element mechanisms = features.addElement(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
                System.out.println(((SSLSocket) socket).getSession().getProtocol());
                System.out.println(((SSLSocket) socket).getSession().getCipherSuite());

                try {
                    // Throws an exception if the peer (local server) doesn't send a certificate
                    System.out.println(((SSLSocket) socket).getSession().getPeerPrincipal());
                    Certificate[] certificates = ((SSLSocket) socket).getSession().getPeerCertificates();
                    if (certificates != null && encryptionPolicy != ServerSettings.EncryptionPolicy.DISABLED) {
                        try {
                            ((X509Certificate) certificates[0]).checkValidity(); // first peer certificate will belong to the local server
                            mechanisms.addElement("mechanism").addText("EXTERNAL");
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                            System.out.println("local certificate is invalid");
                        }
                    }
                } catch (SSLPeerUnverifiedException e) {
                    System.out.println("local certificate is missing/unverified");
                }
            }

            send(root.getRootElement().asXML());
        }

        private synchronized void sendStartTlsProceed(Element inbound) throws Exception
        {
            if (encryptionPolicy == ServerSettings.EncryptionPolicy.DISABLED || generatedPKIX == null) {
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

            System.out.println("Replace the socket with one that will do TLS on the next inbound and outbound data");

            final SSLContext sc = SSLContext.getInstance("TLSv1.2");

            sc.init(createKeyManager(generatedPKIX.getKeyPair(), generatedPKIX.getCertificateChain()), createTrustManagerThatTrustsAll(), new java.security.SecureRandom());
            SSLContext.setDefault(sc);

            final SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, null, true);
            sslSocket.setSoTimeout((int) SO_TIMEOUT.toMillis());

            // Just indicate that we would like to authenticate the client but if client
            // certificates are self-signed or have no certificate chain then we are still
            // good
            sslSocket.setWantClientAuth(true); // DO WE NEED TO BRING THIS INTO OUR LocalOutgoingServerSessionParameterizedTest MATRIX?

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
            if (encryptionPolicy == ServerSettings.EncryptionPolicy.DISABLED) {
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

            if (encryptionPolicy == ServerSettings.EncryptionPolicy.REQUIRED && !(socket instanceof SSLSocket)) {
                final Document outbound = DocumentHelper.createDocument();
                final Element result = outbound.addElement(QName.get("result", "db", "urn:xmpp:features:dialback"));
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
            final Element result = outbound.addElement(QName.get("result", "db", "urn:xmpp:features:dialback"));
            result.addAttribute("from", XMPP_DOMAIN);
            result.addAttribute("to", inbound.attributeValue("from", null));
            result.addAttribute("type", "valid");

            send(outbound.getRootElement().asXML());
        }
    }

    /**
     * Parses text as an XML element.
     *
     * When the provided input is an element that is not closed, then a closing element is automatically generated. This
     * helps to parse `stream` elements, that are closed only when the XMPP session ends.
     *
     * @param xml The data to parse
     * @return an XML element
     */
    public static Element parse(final String xml) throws DocumentException
    {
        String toParse = xml;

        if (!xml.endsWith("/>")) {
            Matcher matcher = Pattern.compile("[A-Za-z:]+").matcher(xml);
            if (matcher.find()) {
                final String fakeEndTag = "</" + matcher.group() + ">";
                if (!xml.trim().endsWith(fakeEndTag)) {
                    toParse += fakeEndTag;
                }
            }
        }

        return DocumentHelper.parseText(toParse).getRootElement();
    }

    /**
     * Creates a TrustManager that will blindly accept all certificates.
     */
    public static TrustManager[] createTrustManagerThatTrustsAll()
    {
        // Create a trust manager that does not validate certificate chains
        return new TrustManager[]{
            new X509TrustManager()
            {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    if (Instant.now().isAfter(certs[0].getNotAfter().toInstant()) || Instant.now().isBefore(certs[0].getNotBefore().toInstant())) {
                        throw new CertificateException("Peer certificate is expired.");
                    }
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    if (Instant.now().isAfter(certs[0].getNotAfter().toInstant()) || Instant.now().isBefore(certs[0].getNotBefore().toInstant())) {
                        throw new CertificateException("Peer certificate is expired.");
                    }
                }
            }
        };
    }

    /**
     * Creates a KeyManager that identifies with the provided keyPair and certificate chain.
     */
    public static KeyManager[] createKeyManager(final KeyPair keyPair, final X509Certificate... chain)
    {
        return new KeyManager[]{
            new X509KeyManager()
            {
                @Override
                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    throw new IllegalStateException("Should not be used.");
                }

                @Override
                public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                    throw new IllegalStateException("Should not be used.");
                }

                @Override
                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return new String[] { XMPP_DOMAIN };
                }

                @Override
                public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                    return XMPP_DOMAIN;
                }

                @Override
                public X509Certificate[] getCertificateChain(String alias) {
                    return chain;
                }

                @Override
                public PrivateKey getPrivateKey(String alias) {
                    return keyPair.getPrivate();
                }
            }
        };
    }
}