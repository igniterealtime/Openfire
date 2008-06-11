/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.http;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.multiplex.UnknownStanzaException;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A session represents a serious of interactions with an XMPP client sending packets using the HTTP
 * Binding protocol specified in <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 * A session can have several client connections open simultaneously while awaiting packets bound
 * for the client from the server.
 *
 * @author Alexander Wenckus
 */
public class HttpSession extends LocalClientSession {
    private static XmlPullParserFactory factory = null;
    private static ThreadLocal<XMPPPacketReader> localParser = null;
    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        // Create xmpp parser to keep in each thread
        localParser = new ThreadLocal<XMPPPacketReader>() {
            protected XMPPPacketReader initialValue() {
                XMPPPacketReader parser = new XMPPPacketReader();
                factory.setNamespaceAware(true);
                parser.setXPPFactory(factory);
                return parser;
            }
        };
    }

    private int wait;
    private int hold = 0;
    private String language;
    private final List<HttpConnection> connectionQueue = new LinkedList<HttpConnection>();
    private final List<Deliverable> pendingElements = new ArrayList<Deliverable>();
    private final List<Delivered> sentElements = new ArrayList<Delivered>();
    private boolean isSecure;
    private int maxPollingInterval;
    private long lastPoll = -1;
    private Set<SessionListener> listeners = new CopyOnWriteArraySet<SessionListener>();
    private volatile boolean isClosed;
    private int inactivityTimeout;
    private long lastActivity;
    private long lastRequestID;
    private int maxRequests;
    private PacketDeliverer backupDeliverer;
    private Double version = Double.NaN;
    private X509Certificate[] sslCertificates;

    private final Queue<Collection<Element>> packetsToSend = new LinkedList<Collection<Element>>();
    // Semaphore which protects the packets to send, so, there can only be one consumer at a time.
    private SessionPacketRouter router;

    private static final Comparator<HttpConnection> connectionComparator
            = new Comparator<HttpConnection>() {
        public int compare(HttpConnection o1, HttpConnection o2) {
            return (int) (o1.getRequestId() - o2.getRequestId());
        }
    };

    public HttpSession(PacketDeliverer backupDeliverer, String serverName, InetAddress address,
                       StreamID streamID, long rid, HttpConnection connection) {
        super(serverName, null, streamID);
        conn = new HttpVirtualConnection(address);
        this.lastActivity = System.currentTimeMillis();
        this.lastRequestID = rid;
        this.backupDeliverer = backupDeliverer;
        this.sslCertificates = connection.getPeerCertificates();
    }

    /**
     * Returns the stream features which are available for this session.
     *
     * @return the stream features which are available for this session.
     */
    public Collection<Element> getAvailableStreamFeaturesElements() {
        List<Element> elements = new ArrayList<Element>();

        Element sasl = SASLAuthentication.getSASLMechanismsElement(this);
        if (sasl != null) {
            elements.add(sasl);
        }

        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            Element compression = DocumentHelper.createElement(new QName("compression",
                    new Namespace("", "http://jabber.org/features/compress")));
            Element method = compression.addElement("method");
            method.setText("zlib");

            elements.add(compression);
        }
        Element bind = DocumentHelper.createElement(new QName("bind",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind")));
        elements.add(bind);

        Element session = DocumentHelper.createElement(new QName("session",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
        elements.add(session);
        return elements;
    }

    public String getAvailableStreamFeatures() {
        StringBuilder sb = new StringBuilder(200);
        for (Element element : getAvailableStreamFeaturesElements()) {
            sb.append(element.asXML());
        }
        return sb.toString();
    }

    /**
     * Closes the session. After a session has been closed it will no longer accept new connections
     * on the session ID.
     */
    public void close() {
        if (isClosed) {
            return;
        }
        conn.close();
    }

    /**
     * Returns true if this session has been closed and no longer activley accepting connections.
     *
     * @return true if this session has been closed and no longer activley accepting connections.
     */
    public synchronized boolean isClosed() {
        return isClosed;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @param wait the longest time it is permissible to wait for a response.
     */
    public void setWait(int wait) {
        this.wait = wait;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @return the longest time it is permissible to wait for a response.
     */
    public int getWait() {
        return wait;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @param hold the maximum number of simultaneous waiting requests.
     */
    public void setHold(int hold) {
        this.hold = hold;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @return the maximum number of simultaneous waiting requests
     */
    public int getHold() {
        return hold;
    }

    /**
     * Sets the language this session is using.
     *
     * @param language the language this session is using.
     */
    public void setLanaguage(String language) {
        this.language = language;
    }

    /**
     * Returns the language this session is using.
     *
     * @return the language this session is using.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @param maxPollingInterval time in seconds a client needs to wait before sending polls to the
     * server, a negative <i>int</i> indicates that there is no limit.
     */
    public void setMaxPollingInterval(int maxPollingInterval) {
        this.maxPollingInterval = maxPollingInterval;
    }

    /**
     * Returns the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @return the max interval within which a client can send polling requests. If more than one
     *         request occurs in the interval the session will be terminated.
     */
    public int getMaxPollingInterval() {
        return this.maxPollingInterval;
    }

    /**
     * The max number of requests it is permissable for this session to have open at any one time.
     *
     * @param maxRequests The max number of requests it is permissable for this session to have open
     * at any one time.
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * Returns the max number of requests it is permissable for this session to have open at any one
     * time.
     *
     * @return the max number of requests it is permissable for this session to have open at any one
     *         time.
     */
    public int getMaxRequests() {
        return this.maxRequests;
    }

    /**
     * Returns true if all connections on this session should be secured, and false if they should
     * not.
     *
     * @return true if all connections on this session should be secured, and false if they should
     *         not.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Adds a {@link org.jivesoftware.openfire.http.SessionListener} to this session. The listener
     * will be notified of changes to the session.
     *
     * @param listener the listener which is being added to the session.
     */
    public void addSessionCloseListener(SessionListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a {@link org.jivesoftware.openfire.http.SessionListener} from this session. The
     * listener will no longer be updated when an event occurs on the session.
     *
     * @param listener the session listener that is to be removed.
     */
    public void removeSessionCloseListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sets the time, in seconds, after which this session will be considered inactive and be be
     * terminated.
     *
     * @param inactivityTimeout the time, in seconds, after which this session will be considered
     * inactive and be terminated.
     */
    public void setInactivityTimeout(int inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    /**
     * Returns the time, in seconds, after which this session will be considered inactive and
     * terminated.
     *
     * @return the time, in seconds, after which this session will be considered inactive and
     *         terminated.
     */
    public int getInactivityTimeout() {
        return inactivityTimeout;
    }

    /**
     * Returns the time in milliseconds since the epoch that this session was last active. Activity
     * is a request was either made or responded to. If the session is currently active, meaning
     * there are connections awaiting a response, the current time is returned.
     *
     * @return the time in milliseconds since the epoch that this session was last active.
     */
    public synchronized long getLastActivity() {
        if (connectionQueue.isEmpty()) {
            return lastActivity;
        }
        else {
            for (HttpConnection connection : connectionQueue) {
                // The session is currently active, return the current time.
                if (!connection.isClosed()) {
                    return System.currentTimeMillis();
                }
            }
            // We have no currently open connections therefore we can assume that lastActivity is
            // the last time the client did anything.
            return lastActivity;
        }
    }

    /**
     * Sets the version of BOSH which the client implements. Currently, the only versions supported
     * by Openfire are 1.5 and 1.6. Any versions less than or equal to 1.5 will be interpreted as
     * 1.5 and any values greater than or equal to 1.6 will be interpreted as 1.6.
     *
     * @param version the version of BOSH which the client implements, represented as a Double,
     * {major version}.{minor version}.
     */
    public void setVersion(double version) {
        if(version <= 1.5) {
            return;
        }
        else if(version >= 1.6) {
            version = 1.6;
        }
        this.version = version;
    }

    /**
     * Returns the BOSH version which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements. If the client did not specify
     * a version 1.5 is returned as this is the last version of the <a
     * href="http://www.xmpp.org/extensions/xep-0124.html">XEP</a> that the client was not
     * required to pass along its version information when creating a session.
     *
     * @return the version of the BOSH XEP which the client is utilizing.
     */
    public double getVersion() {
        if (!Double.isNaN(this.version)) {
            return this.version;
        }
        else {
            return 1.5;
        }
    }

    public String getResponse(long requestID) throws HttpBindException {
        for (HttpConnection connection : connectionQueue) {
            if (connection.getRequestId() == requestID) {
                String response = getResponse(connection);

                // connection needs to be removed after response is returned to maintain idempotence
                // otherwise if this method is called again, after 'waiting', the InternalError
                // will be thrown because the connection is no longer in the queue.
                connectionQueue.remove(connection);
                fireConnectionClosed(connection);
                return response;
            }
        }
        throw new InternalError("Could not locate connection: " + requestID);
    }

    private String getResponse(HttpConnection connection) throws HttpBindException {
        String response = null;
        try {
            response = connection.getResponse();
        }
        catch (HttpBindTimeoutException e) {
            // This connection timed out we need to increment the request count
            if (connection.getRequestId() != lastRequestID + 1) {
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
            }
            lastRequestID = connection.getRequestId();
        }
        if (response == null) {
            response = createEmptyBody();
        }
        return response;
    }

    /**
     * Sets whether the initial request on the session was secure.
     *
     * @param isSecure true if the initial request was secure and false if it wasn't.
     */
    protected void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    /**
     * This methods sends any pending packets in the session. If no packets are
     * pending, this method simply returns. The method is internally synchronized
     * to avoid simultanious sending operations on this Session. If two
     * threads try to run this method simultaniously, the first one will trigger
     * the pending packets to be sent, while the second one will simply return
     * (as there are no packets left to send).
     */
    protected void sendPendingPackets() {
        // access blocked only on send to prevent deadlocks
        synchronized (packetsToSend) {
            if (packetsToSend.isEmpty()) {
                return;
            }

            if (router == null) {
                router = new SessionPacketRouter(this);
            }

            for (Element packet : packetsToSend.remove()) {
                try {
                    router.route(packet);
                }
                catch (UnsupportedEncodingException e) {
                    Log.error(
                            "Client provided unsupported encoding type in auth request",
                            e);
                }
                catch (UnknownStanzaException e) {
                    Log.error("Client provided unknown packet type", e);
                }
            }
        }
    }
    
    /**
     * Return the X509Certificates associated with this session.
     * 
     * @return the X509Certificate associated with this session.
     */
    public X509Certificate[] getPeerCertificates() {
        return sslCertificates;
    }

    /**
     * Creates a new connection on this session. If a response is currently available for this
     * session the connection is responded to immediately, otherwise it is queued awaiting a
     * response.
     *
     * @param rid the request id related to the connection.
     * @param packetsToBeSent any packets that this connection should send.
     * @param isSecure true if the connection was secured using HTTPS.
     * @return the created {@link org.jivesoftware.openfire.http.HttpConnection} which represents
     *         the connection.
     *
     * @throws HttpConnectionClosedException if the connection was closed before a response could be
     * delivered.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     * protocol.
     */
    synchronized HttpConnection createConnection(long rid, Collection<Element> packetsToBeSent,
                                                 boolean isSecure)
            throws HttpConnectionClosedException, HttpBindException
    {
        HttpConnection connection = new HttpConnection(rid, isSecure, sslCertificates);
        if (rid <= lastRequestID) {
            Delivered deliverable = retrieveDeliverable(rid);
            if (deliverable == null) {
                Log.warn("Deliverable unavailable for " + rid);
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
            }
            connection.deliverBody(createDeliverable(deliverable.deliverables));
            return connection;
        }
        else if (rid > (lastRequestID + hold + 1)) {
            // TODO handle the case of greater RID which basically has it wait
            Log.warn("Request " + rid + " > " + (lastRequestID + hold + 1) + ", ending session.");
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
        }

        if (packetsToBeSent.size() > 0) {
            packetsToSend.add(packetsToBeSent);
        }
        addConnection(connection, packetsToBeSent.size() <= 0);
        return connection;
    }

    private Delivered retrieveDeliverable(long rid) {
        for (Delivered delivered : sentElements) {
            if (delivered.getRequestID() == rid) {
                return delivered;
            }
        }
        return null;
    }

    private void addConnection(HttpConnection connection, boolean isPoll) throws HttpBindException,
            HttpConnectionClosedException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        if (isPoll) {
            checkPollingInterval();
        }

        if (isSecure && !connection.isSecure()) {
            throw new HttpBindException("Session was started from secure connection, all " +
                    "connections on this session must be secured.", BoshBindingError.badRequest);
        }

        sslCertificates = connection.getPeerCertificates();
        
        connection.setSession(this);
        // We aren't supposed to hold connections open or we already have some packets waiting
        // to be sent to the client.
        if (hold <= 0 || (pendingElements.size() > 0 && connection.getRequestId() == lastRequestID + 1)) {
            deliver(connection, pendingElements);
            lastRequestID = connection.getRequestId();
            pendingElements.clear();
        }
        else {
            // With this connection we need to check if we will have too many connections open,
            // closing any extras.

            // Number of current connections open plus the current one tells us how many that
            // we need to close.
            int connectionsToClose = (getOpenConnectionCount() + 1) - hold;
            int closed = 0;
            for (int i = 0; i < connectionQueue.size() && closed < connectionsToClose; i++) {
                HttpConnection toClose = connectionQueue.get(i);
                if (!toClose.isClosed()) {
                    lastRequestID = toClose.getRequestId();
                    toClose.close();
                    closed++;
                }
            }
        }
        connectionQueue.add(connection);
        Collections.sort(connectionQueue, connectionComparator);
        fireConnectionOpened(connection);
    }

    private int getOpenConnectionCount() {
        int count = 0;
        for (HttpConnection connection : connectionQueue) {
            if (!connection.isClosed()) {
                count++;
            }
        }
        return count;
    }

    private void deliver(HttpConnection connection, Collection<Deliverable> deliverable)
            throws HttpConnectionClosedException {
        connection.deliverBody(createDeliverable(deliverable));

        Delivered delivered = new Delivered(deliverable);
        delivered.setRequestID(connection.getRequestId());
        while (sentElements.size() > hold) {
            sentElements.remove(0);
        }

        sentElements.add(delivered);
    }

    private void fireConnectionOpened(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionOpened(this, connection);
        }
    }

    private void checkPollingInterval() throws HttpBindException {
        long time = System.currentTimeMillis();
        if (((time - lastPoll) / 1000) < maxPollingInterval) {
            throw new HttpBindException("Too frequent polling minimum interval is "
                    + maxPollingInterval + ", current interval " + ((time - lastPoll) / 1000),
                    BoshBindingError.policyViolation);
        }
        lastPoll = time;
    }

    private synchronized void deliver(String text) {
        if (text == null) {
            // Do nothing if someone asked to send nothing :)
            return;
        }
        deliver(new Deliverable(text));
    }

    private synchronized void deliver(Packet stanza) {
        deliver(new Deliverable(Arrays.asList(stanza)));
    }

    private void deliver(Deliverable stanza) {
        Collection<Deliverable> deliverable = Arrays.asList(stanza);
        boolean delivered = false;
        for (HttpConnection connection : connectionQueue) {
            try {
                if (connection.getRequestId() == lastRequestID + 1) {
                    lastRequestID = connection.getRequestId();
                    deliver(connection, deliverable);
                    delivered = true;
                    break;
                }
            }
            catch (HttpConnectionClosedException e) {
                /* Connection was closed, try the next one */
            }
        }

        if (!delivered) {
            pendingElements.add(stanza);
        }
    }

    private void fireConnectionClosed(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionClosed(this, connection);
        }
    }

    private String createDeliverable(Collection<Deliverable> elements) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body xmlns='" + "http://jabber.org/protocol/httpbind" + "'>");
        for (Deliverable child : elements) {
            builder.append(child.getDeliverable());
        }
        builder.append("</body>");
        return builder.toString();
    }

    private synchronized void closeConnection() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        if (pendingElements.size() > 0) {
            failDelivery();
        }

        for (SessionListener listener : listeners) {
            listener.sessionClosed(this);
        }
        this.listeners.clear();
    }

    private void failDelivery() {
        for (Deliverable deliverable : pendingElements) {
            Collection<Packet> packet = deliverable.getPackets();
            if (packet != null) {
                failDelivery(packet);
            }
        }

        for (HttpConnection toClose : connectionQueue) {
            if (!toClose.isDelivered()) {
                Delivered delivered = retrieveDeliverable(toClose.getRequestId());
                if (delivered != null) {
                    failDelivery(delivered.getPackets());
                }
                else {
                    Log.warn("Packets could not be found for session " + getStreamID() + " cannot" +
                            "be delivered to client");
                }
            }
            toClose.close();
            fireConnectionClosed(toClose);
        }
        pendingElements.clear();
    }

    private void failDelivery(Collection<Packet> packets) {
        if (packets == null) {
            // Do nothing if someone asked to deliver nothing :)
            return;
        }
        for (Packet packet : packets) {
            try {
                backupDeliverer.deliver(packet);
            }
            catch (UnauthorizedException e) {
                Log.error("Unable to deliver message to backup deliverer", e);
            }
        }
    }


    private static String createEmptyBody() {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        return body.asXML();
    }

    /**
     * A virtual server connection relates to a http session which its self can relate to many http
     * connections.
     */
    public static class HttpVirtualConnection extends VirtualConnection {

        private InetAddress address;

        public HttpVirtualConnection(InetAddress address) {
            this.address = address;
        }

        public void closeVirtualConnection() {
            ((HttpSession) session).closeConnection();
        }

        public byte[] getAddress() throws UnknownHostException {
            return address.getAddress();
        }

        public String getHostAddress() throws UnknownHostException {
            return address.getHostAddress();
        }

        public String getHostName() throws UnknownHostException {
            return address.getHostName();
        }

        public void systemShutdown() {
            ((HttpSession) session).closeConnection();
        }

        public void deliver(Packet packet) throws UnauthorizedException {
            ((HttpSession) session).deliver(packet);
        }

        public void deliverRawText(String text) {
            ((HttpSession) session).deliver(text);
        }
        
        public Certificate[] getPeerCertificates() {
            return ((HttpSession) session).getPeerCertificates();
        }
    }

    private class Deliverable implements Comparable<Deliverable> {
        private final String text;
        private final Collection<String> packets;
        private long requestID;

        public Deliverable(String text) {
            this.text = text;
            this.packets = null;
        }

        public Deliverable(Collection<Packet> elements) {
            this.text = null;
            this.packets = new ArrayList<String>();
            for (Packet packet : elements) {
                this.packets.add(packet.toXML());
            }
        }

        public String getDeliverable() {
            if (text == null) {
                StringBuilder builder = new StringBuilder();
                for (String packet : packets) {
                    builder.append(packet);
                }
                return builder.toString();
            }
            else {
                return text;
            }
        }

        public void setRequestID(long requestID) {
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        public Collection<Packet> getPackets() {
            List<Packet> answer = new ArrayList<Packet>();
            for (String packetXML : packets) {
                try {
                    Packet packet = null;
                    // Parse the XML stanza
                    Element element = localParser.get().read(new StringReader(packetXML)).getRootElement();
                    String tag = element.getName();
                    if ("message".equals(tag)) {
                        packet = new Message(element, true);
                    }
                    else if ("presence".equals(tag)) {
                        packet = new Presence(element, true);
                    }
                    else if ("iq".equals(tag)) {
                        packet = new IQ(element, true);
                    }
                    // Add the reconstructed packet to the result
                    answer.add(packet);
                }
                catch (Exception e) {
                    Log.error("Error while parsing Privacy Property", e);
                }
            }
            return answer;
        }

        public int compareTo(Deliverable o) {
            return (int) (o.getRequestID() - requestID);
        }
    }

    private class Delivered {
        private long requestID;
        private Collection<Deliverable> deliverables;

        public Delivered(Collection<Deliverable> deliverables) {
            this.deliverables = deliverables;
        }

        public void setRequestID(long requestID) {
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        public Collection<Packet> getPackets() {
            List<Packet> packets = new ArrayList<Packet>();
            for (Deliverable deliverable : deliverables) {
                if (deliverable.packets != null) {
                    packets.addAll(deliverable.getPackets());
                }
            }
            return packets;
        }
    }
}
