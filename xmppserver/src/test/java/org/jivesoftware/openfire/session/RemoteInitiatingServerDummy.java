package org.jivesoftware.openfire.session;

import org.dom4j.*;
import org.jivesoftware.openfire.Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RemoteInitiatingServerDummy extends AbstractRemoteServerDummy
{
    private ServerSocket dialbackAuthoritativeServer;
    private Thread dialbackAcceptThread;

    private final String connectTo;

    private ExecutorService processingService;

    /**
     * A monitor that is used to flag when this dummy has finished trying to set up a connection to Openfire. This is to
     * help the unit test know when it can start verifying the test outcome.
     */
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

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
        System.out.println("connect");
        processingService = Executors.newCachedThreadPool();

        if (dialbackAuthoritativeServer != null) {
            dialbackAcceptThread = new Thread(new DialbackAcceptor());
            dialbackAcceptThread.start();
        }

        final SocketProcessor socketProcessor = new SocketProcessor(port);
        processingService.submit(socketProcessor);
        socketProcessor.sendStreamHeader();

        Thread.sleep(1000); // FIXME replace this with some kind of flag that indicates when the test result is ready to be verified.
    }

    public void blockUntilDone(final long timeout, final TimeUnit unit) {
        try {
            if (!countDownLatch.await(timeout, unit)) {
                throw new RuntimeException("Test scenario never reached 'done' state");
            }
        } catch (InterruptedException e) {
        }
    }

    protected void done() {
        countDownLatch.countDown();
    }

    public void disconnect() throws InterruptedException, IOException
    {
        System.out.println("disconnect");
        stopProcessingService();
        stopAcceptThread();
        if (dialbackAuthoritativeServer != null) {
            dialbackAuthoritativeServer.close();
            dialbackAuthoritativeServer = null;
        }
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

    public synchronized void stopAcceptThread() throws InterruptedException
    {
        if (dialbackAcceptThread == null) {
            return;
        }
        dialbackAcceptThread.interrupt();
        final Instant end = Instant.now().plus(SO_TIMEOUT.multipliedBy(2));
        while (Instant.now().isBefore(end) && dialbackAcceptThread.getState() != Thread.State.TERMINATED) {
            Thread.sleep(SO_TIMEOUT.dividedBy(10).toMillis());
        }
        final Thread.State finalState = dialbackAcceptThread.getState();
        if (finalState != Thread.State.TERMINATED) {
            System.out.println("Accept thread not terminating after it was stopped. Current state: " + finalState);
        }
        dialbackAcceptThread = null;
    }

    public int getDialbackAuthoritativeServerPort()
    {
        return dialbackAuthoritativeServer != null ? dialbackAuthoritativeServer.getLocalPort() : -1;
    }

    private class DialbackAcceptor implements Runnable
    {
        @Override
        public void run()
        {
            System.out.println("Start accepting socket connections (as Server Dialback Authoritative Server).");
            while (true) {
                try {
                    final Socket socket = dialbackAuthoritativeServer.accept();
                    final InputStream is = socket.getInputStream();
                    final OutputStream os = socket.getOutputStream();
                    System.out.println("DIALBACK AUTH SERVER: Accepted new socket connection.");

                    Thread.sleep(100);
                    final byte[] buffer = new byte[1024 * 16];
                    int count;
                    while ((count = is.read(buffer)) > 0) {
                        String read = new String(buffer, 0, count);
                        System.out.println("# DIALBACK AUTH SERVER recv");
                        System.out.println(read);
                        System.out.println();

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
                            System.out.println("I don't know how to process this data.");
                        }

                        if (response != null) {
                            System.out.println("# DIALBACK AUTH SERVER send to Openfire");
                            System.out.println(response);
                            System.out.println();
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
                    if (dialbackAcceptThread != null && !dialbackAcceptThread.isInterrupted()) {
                        t.printStackTrace();
                    } else {
                        break;
                    }
                }
            }
            System.out.println("Stopped accepting socket connections (as Server Dialback Authoritative Server).");
        }
    }

    private class SocketProcessor implements Runnable
    {
        private Socket socket;
        private OutputStream os;
        private InputStream is;
        boolean didEncryptionNegotiation = false;
        boolean peerAdvertisedDialbackNamespace = false;

        private SocketProcessor(int port) throws IOException
        {
            socket = new Socket();
            final InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            System.out.println("Creating new socket to " + socketAddress);
            socket.connect(socketAddress, (int) SO_TIMEOUT.toMillis());
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
            try {
                final byte[] buffer = new byte[1024 * 16];
                int count;
                while ((count = is.read(buffer)) > 0) {
                    String read = new String(buffer, 0, count);

                    if (read.startsWith("<stream:") && !read.contains("xmlns:stream=")) {
                        // Ugly hack to get stream prefix to work.
                        read = read.replaceFirst(">", " xmlns:stream=\"http://etherx.jabber.org/streams\">");
                        System.out.println("# recv (Hacked inbound stanza to include stream namespace declaration)");
                    } else if (read.startsWith("<db:") && !read.contains("xmlns:db=")) {
                        // Ugly hack to get Dialback to work.
                        read = read.replaceFirst(" ", " xmlns:db=\"jabber:server:dialback\" ");
                        System.out.println("# recv (Hacked inbound stanza to include Dialback namespace declaration)");
                    } else {
                        System.out.println("# recv from Openfire");
                    }
                    System.out.println(read);
                    System.out.println();

                    if (!read.equals("</stream:stream>")) {
                        final Element inbound = parse(read);
                        switch (inbound.getName()) {
                            case "stream":
                                // This is expected to be the response stream header. No need to act on this, but if it contains a dialback namespace, then this suggests that the peer supports dialback.
                                peerAdvertisedDialbackNamespace = inbound.declaredNamespaces().stream().anyMatch(namespace -> "jabber:server:dialback".equals(namespace.getURI()));
                                break;
                            case "features":
                                negotiateFeatures(inbound);
                                break;
                            case "result":
                                processDialbackResult(inbound);
                                break;
                            case "success": // intended fall-through
                            case "failure":
                                if (inbound.getNamespaceURI().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
                                    processSaslResponse(inbound);
                                    break;
                                }
                                // intended fall-through
                            default:
                                System.out.println("Received stanza '" + inbound.getName() + "' that I don't know how to respond to.");
                        }
                    } else {
                        // received an end of stream: if the peer closes the connection, then we're done trying.
                        break;
                    }

                }
            } catch (InterruptedIOException e) {

            } catch (Throwable t) {
                // Log exception only when not cleanly closed.
                t.printStackTrace();
            }
            System.out.println("Stopped reading from socket.");
            done();
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
            if (!didEncryptionNegotiation) {
                negotiateEncryption(features);
                didEncryptionNegotiation = true;
            }
            negotiateAuthentication(features);
        }

        private void negotiateEncryption(final Element features) throws IOException
        {
            final Element startTLSel = features.element(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
            final boolean peerSupportsStartTLS = startTLSel != null;
            final boolean peerRequiresStartTLS = peerSupportsStartTLS && startTLSel.element("required") != null;
            System.out.println("Openfire " + (peerRequiresStartTLS ? "requires" : (peerSupportsStartTLS ? "supports" : "does not support" )) + " StartTLS. Our own policy: " + encryptionPolicy + ".");

            switch (encryptionPolicy) {
                case disabled:
                    if (peerRequiresStartTLS) {
                        final Document outbound = DocumentHelper.createDocument();
                        final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                        final Element root = outbound.addElement(QName.get("stream", namespace));
                        root.add(Namespace.get("jabber:server"));
                        final Element error = root.addElement(QName.get("error", "stream", "http://etherx.jabber.org/streams"));
                        error.addElement(QName.get("undefined-condition", "urn:ietf:params:xml:ns:xmpp-streams"));

                        send(root.asXML().substring(root.asXML().indexOf(">")+1));
                        throw new InterruptedIOException("Openfire requires TLS, we disabled it.");
                    }
                    break;
                case optional:
                    if (peerSupportsStartTLS) {
                        initiateTLS();
                    }
                    break;
                case required:
                    if (!peerSupportsStartTLS) {
                        final Document outbound = DocumentHelper.createDocument();
                        final Namespace namespace = new Namespace("stream", "http://etherx.jabber.org/streams");
                        final Element root = outbound.addElement(QName.get("stream", namespace));
                        root.add(Namespace.get("jabber:server"));
                        final Element error = root.addElement(QName.get("error", "stream", "http://etherx.jabber.org/streams"));
                        error.addElement(QName.get("undefined-condition", "urn:ietf:params:xml:ns:xmpp-streams"));

                        send(root.asXML().substring(root.asXML().indexOf(">")+1));
                        throw new InterruptedIOException("Openfire disabled TLS, we require it.");
                    }
                    else
                    {
                        initiateTLS();
                    }
                    break;
                default:
                    throw new IllegalStateException("This implementation does not supported encryption policy: " + encryptionPolicy);
            }
        }
        private void initiateTLS() throws IOException {
            System.out.println("Initiating TLS...");
            //FIXME do TLS.
        }

        private void negotiateAuthentication(final Element features) throws IOException {
            final Element mechanismsEl = features.element(QName.get("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
            final boolean peerSupportsSASLExternal = mechanismsEl != null && mechanismsEl.elements().stream().anyMatch(element -> "mechanism".equals(element.getName()) && "EXTERNAL".equals(element.getTextTrim()));
            final boolean peerSupportsDialback = peerAdvertisedDialbackNamespace || features.element(QName.get("dialback", "urn:xmpp:features:dialback")) != null;
            System.out.println("Openfire " + (peerSupportsSASLExternal ? "offers" : "does not offer") + " SASL EXTERNAL, " + (peerSupportsDialback ? "supports" : "does not support") + " Server Dialback. Our own policy: SASL EXTERNAL " + (encryptionPolicy != Connection.TLSPolicy.disabled ? "available" : "not available") + ", Dialback: " + (!disableDialback ? "supported" : "not supported") + ".");

            if (peerSupportsSASLExternal && encryptionPolicy != Connection.TLSPolicy.disabled && authenticateUsingSaslExternal()) {
                System.out.println("Authenticating using SASL EXTERNAL");
            } else if (peerSupportsDialback && !disableDialback) {
                startDialbackAuth();
            } else {
                System.out.println("Unable to do authentication.");
                throw new InterruptedIOException("Unable to do authentication.");
            }
        }

        private boolean authenticateUsingSaslExternal() throws IOException {
            // FIXME implement SASL EXTERNAL authentication.
            return false;
        }

        private void startDialbackAuth() throws IOException {
            System.out.println("Authenticating using Server Dialback");
            final String key = "UNITTESTDIALBACKKEY";

            final Document outbound = DocumentHelper.createDocument();
            final Element root = outbound.addElement(QName.get("result", "db", "urn:xmpp:features:dialback"));
            root.addAttribute("from", XMPP_DOMAIN);
            root.addAttribute("to", connectTo);
            root.setText(key);

            send(root.asXML().replace(" xmlns:db=\"urn:xmpp:features:dialback\"",""));
        }

        private void processDialbackResult(final Element result) throws IOException {
            final String type = result.attributeValue("type");
            System.out.println("Openfire reports Server Dialback result of type " + type);
            if (!"valid".equals(type)) {
                throw new InterruptedIOException("Server Dialback failed");
            }

            System.out.println("Successfully authenticated using Server Dialback! We're done setting up a connection.");
            done();
        }

        private void processSaslResponse(final Element result) throws IOException {
            final String name = result.getName();
            System.out.println("Openfire reports SASL result of type " + name);
            if (!"success".equals(name)) {
                throw new InterruptedIOException("SASL Auth failed");
            }

            System.out.println("Successfully authenticated using SASL! We're done setting up a connection.");
            done();
        }
    }
}
