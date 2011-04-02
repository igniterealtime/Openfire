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

package org.jivesoftware.openfire.http;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * A session represents a series of interactions with an XMPP client sending packets using the HTTP
 * Binding protocol specified in <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 * A session can have several client connections open simultaneously while awaiting packets bound
 * for the client from the server.
 *
 * @author Alexander Wenckus
 */
public class HttpSession extends LocalClientSession {
	
	private static final Logger Log = LoggerFactory.getLogger(HttpSession.class);

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
            @Override
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
    private int defaultInactivityTimeout;
    private long lastActivity;
    private long lastRequestID;
    private boolean lastResponseEmpty;
    private int maxRequests;
    private int maxPause;
    private PacketDeliverer backupDeliverer;
    private int majorVersion = -1;
    private int minorVersion = -1;
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

        if (getAuthToken() == null) {
	        Element sasl = SASLAuthentication.getSASLMechanismsElement(this);
	        if (sasl != null) {
	            elements.add(sasl);
	        }
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

    @Override
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
    @Override
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
    @Override
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
    public void setLanguage(String language) {
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
     * Sets the maximum length of a temporary session pause (in seconds) that the client MAY
     * request.
     *
     * @param maxPause the maximum length of a temporary session pause (in seconds) that the client
     * MAY request.
     */
    public void setMaxPause(int maxPause) {
        this.maxPause = maxPause;
    }

    /**
     * Returns the maximum length of a temporary session pause (in seconds) that the client MAY
     * request.
     *
     * @return the maximum length of a temporary session pause (in seconds) that the client MAY
     *         request.
     */
    public int getMaxPause() {
        return this.maxPause;
    }

    /**
     * Returns true if all connections on this session should be secured, and false if they should
     * not.
     *
     * @return true if all connections on this session should be secured, and false if they should
     *         not.
     */
    @Override
	public boolean isSecure() {
        return isSecure;
    }

    /**
     * Returns true if this session is a polling session. Some clients may be restricted to open
     * only one connection to the server. In this case the client SHOULD inform the server by
     * setting the values of the 'wait' and/or 'hold' attributes in its session creation request
     * to "0", and then "poll" the server at regular intervals throughout the session for stanzas
     * it may have received from the server.
     *
     * @return true if this session is a polling session.
     */
    public boolean isPollingSession() {
        return (this.wait == 0 || this.hold == 0);
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
     * Sets the default inactivity timeout of this session. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     *
     * @see #pause(int)
     *
     * @param defaultInactivityTimeout the default inactivity timeout of this session.
     */
    public void setDefaultInactivityTimeout(int defaultInactivityTimeout) {
        this.defaultInactivityTimeout = defaultInactivityTimeout;
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
     * Resets the inactivity timeout of this session to default. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     *
     * @see #pause(int)
     */
    public void resetInactivityTimeout() {
        this.inactivityTimeout = this.defaultInactivityTimeout;
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
     * Pauses the session for the given amount of time. If a client encounters an exceptional
     * temporary situation during which it will be unable to send requests to the connection
     * manager for a period of time greater than the maximum inactivity period, then the client MAY
     * request a temporary increase to the maximum inactivity period by including a 'pause'
     * attribute in a request.
     *
     * @param duration the time, in seconds, after which this session will be considered inactive
     *        and terminated.
     */
    public void pause(int duration) {
    	// Respond immediately to all pending requests
        for (HttpConnection toClose : connectionQueue) {
            if (!toClose.isClosed()) {
                toClose.close();
                lastRequestID = toClose.getRequestId();
            }
        }
    	setInactivityTimeout(duration);
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
     * Returns the highest 'rid' attribute the server has received where it has also received
     * all requests with lower 'rid' values. When responding to a request that it has been
     * holding, if the server finds it has already received another request with a higher 'rid'
     * attribute (typically while it was holding the first request), then it MAY acknowledge the
     * reception to the client.
     *
     * @return the highest 'rid' attribute the server has received where it has also received
     * all requests with lower 'rid' values.
     */
    public long getLastAcknowledged() {
    	long ack = lastRequestID;
    	Collections.sort(connectionQueue, connectionComparator);
        for (HttpConnection connection : connectionQueue) {
            if (connection.getRequestId() == ack + 1) {
            	ack++;
            }
        }
        return ack;
    }

    /**
     * Sets the major version of BOSH which the client implements. Currently, the only versions
     * supported by Openfire are 1.5 and 1.6.
     *
     * @param majorVersion the major version of BOSH which the client implements.
     */
    public void setMajorVersion(int majorVersion) {
        if(majorVersion != 1) {
            return;
        }
        this.majorVersion = majorVersion;
    }

    /**
     * Returns the major version of BOSH which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements. If the client did not specify
     * a version 1 is returned as 1.5 is the last version of the <a
     * href="http://www.xmpp.org/extensions/xep-0124.html">XEP</a> that the client was not
     * required to pass along its version information when creating a session.
     *
     * @return the major version of the BOSH XEP which the client is utilizing.
     */
    public int getMajorVersion() {
        if (this.majorVersion != -1) {
            return this.majorVersion;
        }
        else {
            return 1;
        }
    }

    /**
     * Sets the minor version of BOSH which the client implements. Currently, the only versions
     * supported by Openfire are 1.5 and 1.6. Any versions less than or equal to 5 will be
     * interpreted as 5 and any values greater than or equal to 6 will be interpreted as 6.
     *
     * @param minorVersion the minor version of BOSH which the client implements.
     */
    public void setMinorVersion(int minorVersion) {
    	if(minorVersion <= 5) {
        	this.minorVersion = 5;
        }
    	else if(minorVersion >= 6) {
        	this.minorVersion = 6;
        }
    }

    /**
     * Returns the major version of BOSH which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements. If the client did not specify
     * a version 5 is returned as 1.5 is the last version of the <a
     * href="http://www.xmpp.org/extensions/xep-0124.html">XEP</a> that the client was not
     * required to pass along its version information when creating a session.
     *
     * @return the minor version of the BOSH XEP which the client is utilizing.
     */
    public int getMinorVersion() {
        if (this.minorVersion != -1) {
            return this.minorVersion;
        }
        else {
            return 5;
        }
    }

    /**
     * lastResponseEmpty true if last response of this session is an empty body element. This
     * is used in overactivity checking.
     *
     * @param lastResponseEmpty true if last response of this session is an empty body element.
     */
	public void setLastResponseEmpty(boolean lastResponseEmpty) {
		this.lastResponseEmpty = lastResponseEmpty;
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
            setLastResponseEmpty(true);
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
                                                 boolean isSecure, boolean isPoll)
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
        else if (rid > (lastRequestID + maxRequests)) {
            Log.warn("Request " + rid + " > " + (lastRequestID + maxRequests) + ", ending session.");
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
        }

        if (packetsToBeSent.size() > 0) {
            packetsToSend.add(packetsToBeSent);
        }
        addConnection(connection, isPoll);
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

        checkOveractivity(isPoll);

        if (isSecure && !connection.isSecure()) {
            throw new HttpBindException("Session was started from secure connection, all " +
                    "connections on this session must be secured.", BoshBindingError.badRequest);
        }

        sslCertificates = connection.getPeerCertificates();

        connection.setSession(this);
        // We aren't supposed to hold connections open or we already have some packets waiting
        // to be sent to the client.
        if (isPollingSession() || (pendingElements.size() > 0 && connection.getRequestId() == lastRequestID + 1)) {
            deliver(connection, pendingElements);
            lastRequestID = connection.getRequestId();
            pendingElements.clear();
            connectionQueue.add(connection);
            Collections.sort(connectionQueue, connectionComparator);
        }
        else {
            // With this connection we need to check if we will have too many connections open,
            // closing any extras.

            connectionQueue.add(connection);
            Collections.sort(connectionQueue, connectionComparator);

            int connectionsToClose;
            if(connectionQueue.get(connectionQueue.size() - 1) != connection) {
            	// Current connection does not have the greatest rid. That means
            	// requests were received out of order, respond to all.
            	connectionsToClose = connectionQueue.size();
            }
            else {
                // Everything's fine, number of current connections open tells us
            	// how many that we need to close.
            	connectionsToClose = getOpenConnectionCount() - hold;
            }
            int closed = 0;
            for (int i = 0; i < connectionQueue.size() && closed < connectionsToClose; i++) {
                HttpConnection toClose = connectionQueue.get(i);
                if (!toClose.isClosed() && toClose.getRequestId() == lastRequestID + 1) {
                    if(toClose == connection) {
                    	// Current connection has no continuation yet, just deliver.
                    	deliver("");
                    }
                    else {
                        toClose.close();
                    }
                    lastRequestID = toClose.getRequestId();
                    closed++;
                }
            }
        }
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

    /**
     * Check that the client SHOULD NOT make more simultaneous requests than specified
     * by the 'requests' attribute in the connection manager's Session Creation Response.
     * However the client MAY make one additional request if it is to pause or terminate a session.
     *
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#overactive">overactive</a>.
     * @param isPoll true if the session is using polling.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     *         protocol.
     */
    private void checkOveractivity(boolean isPoll) throws HttpBindException {
    	int pendingConnections = 0;
    	boolean overactivity = false;
    	String errorMessage = "Overactivity detected";

        for (HttpConnection conn : connectionQueue) {
            if (!conn.isClosed()) {
                pendingConnections++;
            }
        }

        if(pendingConnections >= maxRequests) {
        	overactivity = true;
        	errorMessage += ", too many simultaneous requests.";
        }
        else if(isPoll) {
	    	long time = System.currentTimeMillis();
	        if (time - lastPoll < maxPollingInterval * JiveConstants.SECOND) {
	        	if(isPollingSession()) {
	        		overactivity = lastResponseEmpty;
	        	}
	        	else {
	        		overactivity = (pendingConnections >= maxRequests - 1);
	        	}
	        }
	        errorMessage += ", minimum polling interval is "
	        	+ maxPollingInterval + ", current interval " + ((time - lastPoll) / 1000);
	        lastPoll = time;
        }
        setLastResponseEmpty(false);

        if(overactivity) {
        	Log.debug(errorMessage);
            if (!JiveGlobals.getBooleanProperty("xmpp.httpbind.client.requests.ignoreOveractivity", false)) {
                throw new HttpBindException(errorMessage, BoshBindingError.policyViolation);
            }
        }
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
        builder.append("<body xmlns='" + "http://jabber.org/protocol/httpbind" + "'");

        long ack = getLastAcknowledged();
        if(ack > lastRequestID)
            builder.append(" ack='").append(ack).append("'");

        builder.append(">");

        setLastResponseEmpty(elements.size() == 0);
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
                    Log.warn("Packets could not be found for session " + getStreamID() + " cannot " +
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


    private String createEmptyBody() {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        long ack = getLastAcknowledged();
        if(ack > lastRequestID)
        	body.addAttribute("ack", String.valueOf(ack));
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

        @Override
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
            close();
        }

        public void deliver(Packet packet) throws UnauthorizedException {
            ((HttpSession) session).deliver(packet);
        }

        public void deliverRawText(String text) {
            ((HttpSession) session).deliver(text);
        }

        @Override
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
                // Rewrite packet namespace according XEP-0206
                if (packet instanceof Presence) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("<presence xmlns=\"jabber:client\"");
                    sb.append(packet.toXML().substring(9));
                    this.packets.add(sb.toString());
                }
                else if (packet instanceof IQ) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("<iq xmlns=\"jabber:client\"");
                    sb.append(packet.toXML().substring(3));
                    this.packets.add(sb.toString());
                }
                else if (packet instanceof Message) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("<message xmlns=\"jabber:client\"");
                    sb.append(packet.toXML().substring(8));
                    this.packets.add(sb.toString());
                }
                else {
                    this.packets.add(packet.toXML());
                }
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
            // Check if the Deliverable is about Packets or raw XML
            if (packets == null) {
                // No packets here (should be just raw XML like <stream> so return nothing
                return null;
            }
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
