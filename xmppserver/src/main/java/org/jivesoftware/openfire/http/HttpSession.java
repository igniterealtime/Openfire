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

package org.jivesoftware.openfire.http;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.multiplex.UnknownStanzaException;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

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
    private final List<HttpConnection> connectionQueue = Collections.synchronizedList(new LinkedList<HttpConnection>());
    private final List<Deliverable> pendingElements = Collections.synchronizedList(new ArrayList<Deliverable>());
    private final List<Delivered> sentElements = Collections.synchronizedList(new ArrayList<Delivered>());
    private boolean isSecure;
    private int maxPollingInterval;
    private long lastPoll = -1;
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

    private final Queue<Collection<Element>> packetsToSend = new LinkedList<>();
    // Semaphore which protects the packets to send, so, there can only be one consumer at a time.
    private SessionPacketRouter router;

    private static final Comparator<HttpConnection> connectionComparator
            = new Comparator<HttpConnection>() {
        @Override
        public int compare(HttpConnection o1, HttpConnection o2) {
            return (int) (o1.getRequestId() - o2.getRequestId());
        }
    };

    public HttpSession(PacketDeliverer backupDeliverer, String serverName,
                       StreamID streamID, HttpConnection connection, Locale language) throws UnknownHostException
    {
        super(serverName, new HttpVirtualConnection(connection.getRemoteAddr(), ConnectionType.SOCKET_C2S), streamID, language);
        this.isClosed = false;
        this.lastActivity = System.currentTimeMillis();
        this.lastRequestID = connection.getRequestId();
        this.backupDeliverer = backupDeliverer;
        this.sslCertificates = connection.getPeerCertificates();
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            Log.info("Session " + getStreamID() + " being opened with initial connection " +
                    connection.toString());
        }
    }

    /**
     * Returns the stream features which are available for this session.
     *
     * @return the stream features which are available for this session.
     */
    public Collection<Element> getAvailableStreamFeaturesElements() {
        List<Element> elements = new ArrayList<>();

        if (getAuthToken() == null) {
            Element sasl = SASLAuthentication.getSASLMechanismsElement(this);
            if (sasl != null) {
                elements.add(sasl);
            }
        }

        if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
            elements.add(DocumentHelper.createElement(new QName("register",
                    new Namespace("", "http://jabber.org/features/iq-register"))));
        }
        Element bind = DocumentHelper.createElement(new QName("bind",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind")));
        elements.add(bind);

        Element session = DocumentHelper.createElement(new QName("session",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
        session.addElement("optional");
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
        if (JiveGlobals.getBooleanProperty("log.httpbind.enabled", false)) {
            Log.info("Session " + getStreamID() + " being closed");
        }
        conn.close();
    }

    /**
     * Returns true if this session has been closed and no longer actively accepting connections.
     *
     * @return true if this session has been closed and no longer actively accepting connections.
     */
    @Override
    public boolean isClosed() {
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
     * The max number of requests it is permissible for this session to have open at any one time.
     *
     * @param maxRequests The max number of requests it is permissible for this session to have open
     * at any one time.
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * Returns the max number of requests it is permissible for this session to have open at any one
     * time.
     *
     * @return the max number of requests it is permissible for this session to have open at any one
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
        synchronized (connectionQueue) {
            for (HttpConnection toClose : connectionQueue) {
                if (!toClose.isClosed()) {
                    toClose.close();
                    lastRequestID = toClose.getRequestId();
                }
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
    public long getLastActivity() {
        if (!connectionQueue.isEmpty()) {
            synchronized (connectionQueue) {
                for (HttpConnection connection : connectionQueue) {
                    // The session is currently active, set the last activity to the current time.
                    if (!(connection.isClosed())) {
                        lastActivity = System.currentTimeMillis();
                        break;
                    }
                }
            }
        }
        return lastActivity;
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
        synchronized (connectionQueue) {
            for (HttpConnection connection : connectionQueue) {
                if (connection.getRequestId() == ack + 1) {
                    ack++;
                }
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

    /**
     * Sets whether the initial request on the session was secure.
     *
     * @param isSecure true if the initial request was secure and false if it wasn't.
     */
    protected void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    /**
     * Forwards a client request, which is related to a session, to the server. A connection is
     * created and queued up in the provided session. When a connection reaches the top of a queue
     * any pending packets bound for the client will be forwarded to the client through the
     * connection.
     *
     * @param body the body element that was sent containing the request for a new session.
     * @param context the context of the asynchronous servlet call leading up to this method call.
     *
     * @throws org.jivesoftware.openfire.http.HttpBindException for several reasons: if the encoding inside of an auth packet is
     * not recognized by the server, or if the packet type is not recognized.
     * @throws org.jivesoftware.openfire.http.HttpConnectionClosedException if the session is no longer available.
     * @throws IOException if an input or output exception occurred
     */
    public void forwardRequest(HttpBindBody body, AsyncContext context)
            throws HttpBindException, HttpConnectionClosedException, IOException
    {
        HttpConnection connection = this.createConnection(body.getRid(), body.isPoll(), context);
        if (!body.isEmpty()) {
            // creates the runnable to forward the packets
            synchronized (packetsToSend) {
                packetsToSend.add(body.getStanzaElements());
            }
            new HttpPacketSender(this).init();
        }

        final String type = body.getType();

        if ("terminate".equals(type)) {
            connection.deliverBody(createEmptyBody(true), true);
            close();
            lastRequestID = connection.getRequestId();
        }
        else if (body.isRestart() && body.isEmpty() ) {
            connection.deliverBody(createSessionRestartResponse(), true);
            lastRequestID = connection.getRequestId();
        }
        else if (body.getPause() > 0 && body.getPause() <= getMaxPause()) {
            pause(body.getPause());
            connection.deliverBody(createEmptyBody(false), true);
            lastRequestID = connection.getRequestId();
            setLastResponseEmpty(true);
        }
        else {
            resetInactivityTimeout();
        }
    }

    /**
     * This methods sends any pending packets in the session. If no packets are
     * pending, this method simply returns. The method is internally synchronized
     * to avoid simultaneous sending operations on this Session. If two
     * threads try to run this method simultaneously, the first one will trigger
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

            do {
                Collection<Element> packets = packetsToSend.remove();
                for (Element packet : packets) {
                    try {
                        router.route(packet);
                    } catch (UnknownStanzaException e) {
                        Log.error( "On session " + getStreamID() + " client provided unknown packet type", e);
                    }
                }
            }
            while( !packetsToSend.isEmpty() );
        }
    }

    /**
     * Return the X509Certificates associated with this session.
     *
     * @return the X509Certificate associated with this session.
     */
    @Override
    public X509Certificate[] getPeerCertificates() {
        return sslCertificates;
    }

    /**
     * Creates a new connection on this session. If a response is currently available for this
     * session the connection is responded to immediately, otherwise it is queued awaiting a
     * response.
     *
     * @param rid the request id related to the connection.
     * @return the created {@link org.jivesoftware.openfire.http.HttpConnection} which represents
     *         the connection.
     *
     * @throws HttpConnectionClosedException if the connection was closed before a response could be
     * delivered.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     * protocol.
     */
    synchronized HttpConnection createConnection(long rid, boolean isPoll, AsyncContext context)
            throws HttpConnectionClosedException, HttpBindException, IOException
    {
        final HttpConnection connection = new HttpConnection(rid, context);
        final StreamID streamID = getStreamID();
        boolean logHttpbindEnabled = JiveGlobals.getBooleanProperty("log.httpbind.enabled", false);
        if (logHttpbindEnabled) {
            Log.info( "Creating connection for rid: " + rid + " in session " + streamID );
        }
        connection.setSession(this);
        context.setTimeout(getWait() * JiveConstants.SECOND);
        context.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent asyncEvent) throws IOException {
                if (Log.isDebugEnabled()) {
                    Log.debug("complete event " + asyncEvent + " for " + rid + " in session " + streamID);
                }
                connectionQueue.remove(connection);
                lastActivity = System.currentTimeMillis();
                SessionEventDispatcher.dispatchEvent( HttpSession.this, SessionEventDispatcher.EventType.connection_closed, connection, context );
            }

            @Override
            public void onTimeout(AsyncEvent asyncEvent) throws IOException {
                if( Log.isDebugEnabled()) {
                    Log.debug("timeout event " + asyncEvent + " for " + rid + " in session " + streamID);
                }
                try {
                    // If onTimeout does not result in a complete(), the container falls back to default behavior.
                    // This is why this body is to be delivered in a non-async fashion.
                    deliverOnTimeout(connection);
                    setLastResponseEmpty(true);

                    // This connection timed out we need to increment the request count
                    if (connection.getRequestId() != lastRequestID + 1) {
                        if (logHttpbindEnabled) {
                            Log.info( "Unexpected RID error " + rid + " for session " + streamID);
                        }
                        throw new IOException("Unexpected RID error.");
                    }
                    lastRequestID = connection.getRequestId();
                } catch (HttpConnectionClosedException e) {
                    Log.warn("Unexpected exception while processing connection timeout.", e);
                }

                // Note that 'onComplete' will be invoked.
            }

            @Override
            public void onError(AsyncEvent asyncEvent) throws IOException {
                if (logHttpbindEnabled && Log.isDebugEnabled()) {
                    Log.debug("error event " + asyncEvent + " for " + rid + " in session " + streamID);
                }
                Log.warn("For session " + streamID + " unhandled AsyncListener error: " + asyncEvent.getThrowable());
                connectionQueue.remove(connection);
                SessionEventDispatcher.dispatchEvent( HttpSession.this, SessionEventDispatcher.EventType.connection_closed, connection, context );
            }

            @Override
            public void onStartAsync(AsyncEvent asyncEvent) throws IOException {}
        });

        if (rid <= lastRequestID) {
            Delivered deliverable = retrieveDeliverable(rid);
            if (deliverable == null) {
                Log.warn("Deliverable unavailable for " + rid + " in session " + streamID);
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
            }
            connection.deliverBody(createDeliverable(deliverable.deliverables), true);
            addConnection(connection, context, isPoll);
            return connection;
        }
        else if (rid > (lastRequestID + maxRequests)) {
            Log.warn("Request " + rid + " > " + (lastRequestID + maxRequests) + ", ending session " + streamID);
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
        }

        addConnection(connection, context, isPoll);
        return connection;
    }

    private Delivered retrieveDeliverable(long rid) {
        Delivered result = null;
        synchronized (sentElements) {
            for (Delivered delivered : sentElements) {
                if (delivered.getRequestID() == rid) {
                    result = delivered;
                    break;
                }
            }
        }
        return result;
    }

    private void addConnection(HttpConnection connection, AsyncContext context, boolean isPoll) throws HttpBindException,
            HttpConnectionClosedException, IOException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        if (isSecure && !connection.isSecure()) {
            throw new HttpBindException("Session was started from secure connection, all " +
                    "connections on this session must be secured.", BoshBindingError.badRequest);
        }

        final long rid = connection.getRequestId();
        final StreamID streamid = getStreamID();
        boolean logHttpbindEnabled = JiveGlobals.getBooleanProperty("log.httpbind.enabled", false);
        if (logHttpbindEnabled) {
            Log.info( "Adding connection to stream " + streamid + " with rid " + rid );
        }

        /*
         * Search through the connection queue to see if this rid already exists on it. If it does then we
         * will close and deliver the existing connection (if appropriate), and close and deliver the same
         * deliverable on the new connection. This is under the assumption that a connection has been dropped,
         * and re-requested before jetty has realised.
         */
        synchronized (connectionQueue) {
            for (HttpConnection queuedConnection : connectionQueue) {
                if (queuedConnection.getRequestId() == rid) {
                    if(logHttpbindEnabled && Log.isDebugEnabled()) {
                        Log.debug("Found previous connection in queue with rid " + rid);
                    }
                    if(queuedConnection.isClosed()) {
                        if(logHttpbindEnabled && Log.isDebugEnabled()) {
                            Log.debug("It's closed - copying deliverables");
                        }

                        Delivered deliverable = retrieveDeliverable(rid);
                        if (deliverable == null) {
                            if(logHttpbindEnabled) {
                                Log.warn("In session " + streamid + " deliverable unavailable for " + rid);
                            }
                            throw new HttpBindException("Unexpected RID error.",
                                    BoshBindingError.itemNotFound);
                        }
                        connection.deliverBody(createDeliverable(deliverable.deliverables), true);
                    } else {
                        if(logHttpbindEnabled && Log.isDebugEnabled()) {
                            Log.debug("For session " + streamid + " queued connection is still open - calling close()");
                        }
                        deliver(queuedConnection, Collections.singleton(new Deliverable("")));
                        connection.close();

                        if(rid == (lastRequestID + 1)) {
                            lastRequestID = rid;
                            if( logHttpbindEnabled ) {
                                Log.info( "Updated session " + streamid + " to rid = " + rid );
                            }
                        }
                    }
                    break;
                }
            }
        }

        checkOveractivity(isPoll,streamid,rid,logHttpbindEnabled);

        sslCertificates = connection.getPeerCertificates();

        // We aren't supposed to hold connections open or we already have some packets waiting
        // to be sent to the client.
        if (isPollingSession() || (pendingElements.size() > 0 && connection.getRequestId() == lastRequestID + 1)) {
            lastActivity = System.currentTimeMillis();
            SessionEventDispatcher.dispatchEvent( this, SessionEventDispatcher.EventType.connection_opened, connection, context );
            synchronized(pendingElements) {
                deliver(connection, pendingElements);
                lastRequestID = connection.getRequestId();
                pendingElements.clear();
            }
        }
        else {
            // With this connection we need to check if we will have too many connections open,
            // closing any extras.

            connectionQueue.add(connection);
            Collections.sort(connectionQueue, connectionComparator);

            synchronized (connectionQueue) {
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
        }
    }

    private int getOpenConnectionCount() {
        int count = 0;
        // NOTE: synchronized by caller
        for (HttpConnection connection : connectionQueue) {
            if (!connection.isClosed()) {
                count++;
            }
        }
        return count;
    }

    private void deliver(HttpConnection connection, Collection<Deliverable> deliverable)
            throws HttpConnectionClosedException, IOException {
        connection.deliverBody(createDeliverable(deliverable), true);

        Delivered delivered = new Delivered(deliverable);
        delivered.setRequestID(connection.getRequestId());
        while (sentElements.size() > maxRequests) {
            sentElements.remove(0);
        }

        sentElements.add(delivered);
    }

    private enum OveractivityType {
        NONE,
        TOO_MANY_SIM_REQS,
        POLLING_TOO_QUICK;
    };

    /**
     * Check that the client SHOULD NOT make more simultaneous requests than specified
     * by the 'requests' attribute in the connection manager's Session Creation Response.
     * However the client MAY make one additional request if it is to pause or terminate a session.
     *
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#overactive">overactive</a>
     * @param isPoll true if the session is using polling.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     *         protocol.
     */
    private void checkOveractivity(boolean isPoll,
            StreamID streamID,
            long originRid,
            boolean logHttpbindEnabled) throws HttpBindException {
        int pendingConnections = 0;
        OveractivityType overactivity = OveractivityType.NONE;

        synchronized (connectionQueue) {
            for (HttpConnection conn : connectionQueue) {
                if (!conn.isClosed()) {
                    pendingConnections++;
                    if (logHttpbindEnabled) {
                        Log.info("For session " + streamID + " and origin rid " + originRid +
                                " an open connection is pending with rid " + conn.getRequestId());
                    }
                }
            }
        }

        long time = System.currentTimeMillis();
        long deltaFromLastPoll = time - lastPoll;
        if(pendingConnections >= maxRequests) {
            overactivity = OveractivityType.TOO_MANY_SIM_REQS;
        }
        else if(isPoll) {
            boolean localIsPollingSession = isPollingSession();
            if (deltaFromLastPoll < maxPollingInterval * JiveConstants.SECOND) {
                if (localIsPollingSession) {
                    overactivity = lastResponseEmpty ? OveractivityType.POLLING_TOO_QUICK : OveractivityType.NONE;
                } else {
                    overactivity = pendingConnections >= maxRequests ? OveractivityType.POLLING_TOO_QUICK : OveractivityType.NONE;
                }
            }
            lastPoll = time;
            if (logHttpbindEnabled && Log.isDebugEnabled()) {
                Log.debug("Updated session " + streamID +
                        " lastPoll to " + lastPoll +
                        " with rid " + originRid +
                        " lastResponseEmpty = " + lastResponseEmpty  +
                        " overactivity = " + overactivity +
                        " deltaFromlastPoll = " + deltaFromLastPoll +
                        " isPollingSession() = " + localIsPollingSession +
                        " maxRequests = " + maxRequests +
                        " pendingConnections = " + pendingConnections);
            }
        }
        setLastResponseEmpty(false);

        if( overactivity != OveractivityType.NONE) {
            StringBuilder errorMessage = new StringBuilder("Overactivity detected");
            switch (overactivity) {
                case TOO_MANY_SIM_REQS: {
                    errorMessage.append(", too many simultaneous requests.");
                    break;
                }
                case POLLING_TOO_QUICK: {
                    errorMessage.append(", minimum polling interval is ");
                    errorMessage.append(maxPollingInterval);
                    errorMessage.append(", current session ");
                    errorMessage.append(" interval ");
                    errorMessage.append(deltaFromLastPoll / 1000);
                    break;
                }
                default: {
                    throw new HttpBindException("Unhandled overactivity type: " + overactivity, BoshBindingError.internalServerError);
                }
            }
            String errorMessageStr = errorMessage.toString();
            if (logHttpbindEnabled && Log.isInfoEnabled()) {
                Log.info(errorMessageStr);
            }
            if (!JiveGlobals.getBooleanProperty("xmpp.httpbind.client.requests.ignoreOveractivity", false)) {
                throw new HttpBindException(errorMessageStr, BoshBindingError.policyViolation);
            }
        }
    }

    private void deliver(String text) {
        if (text == null) {
            // Do nothing if someone asked to send nothing :)
            return;
        }
        deliver(new Deliverable(text));
    }

    private void deliverOnTimeout(HttpConnection connection) throws HttpConnectionClosedException, IOException {
        final Deliverable td = new Deliverable("");
        final Collection<Deliverable> deliverable = Arrays.asList(td);

        // Not async - we're closing it afterwards
        connection.deliverBody(createDeliverable(deliverable), false);

        final Delivered delivered = new Delivered(deliverable);
        delivered.setRequestID(connection.getRequestId());
        while (sentElements.size() > maxRequests) {
            final Delivered d = sentElements.remove(0);
        }
        sentElements.add(delivered);
    }

    @Override
    public void deliver(Packet stanza) {
        deliver(new Deliverable(Arrays.asList(stanza)));
    }

    private void deliver(Deliverable stanza) {
        Collection<Deliverable> deliverable = Arrays.asList(stanza);
        boolean delivered = false;
        int pendingConnections = 0;
        synchronized (connectionQueue) {
            for (HttpConnection connection : connectionQueue) {
                if (connection.isClosed()) {
                    continue;
                }
                pendingConnections++;
                try {
                    if (connection.getRequestId() == lastRequestID + 1) {
                        lastRequestID = connection.getRequestId();
                        deliver(connection, deliverable);
                        delivered = true;
                        break;
                    }
                }
                catch (HttpConnectionClosedException e) {
                    /* Connection was closed, try the next one. Indicates a (concurrency?) bug. */
                    StreamID streamID = getStreamID();
                    Log.warn("Iterating over a connection that was closed for session " + streamID +
                            ". Openfire will recover from this problem, but it should not occur in the first place.");
                } catch (IOException e) {
                    StreamID streamID = getStreamID();
                    Log.warn("An unexpected exception occurred while iterating over connections for session " + streamID +
                            ". Openfire will attempt to recover by ignoring this connection.", e);
                }
            }
        }

        if (!delivered) {
            if (pendingConnections > 0) {
                StreamID streamID = getStreamID();
                Log.warn("Unable to deliver a stanza on session " + streamID +
                        "(it is being queued instead), although there are available connections! RID / Connection processing is out of sync!");
            }
            synchronized(pendingElements) {
                pendingElements.add( stanza );
            }
        }
    }

    private String createDeliverable(Collection<Deliverable> elements) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body xmlns='http://jabber.org/protocol/httpbind' ack='")
                .append(getLastAcknowledged()).append("'>");

        setLastResponseEmpty(elements.size() == 0);
        synchronized (elements) {
            for (Deliverable child : elements) {
                builder.append(child.getDeliverable());
            }
        }
        builder.append("</body>");
        return builder.toString();
    }

    private void closeSession() {
        if (isClosed) { return; }
        isClosed = true;

        try {
            // close connection(s) and deliver pending elements (if any)
            synchronized (connectionQueue) {
                for (HttpConnection toClose : connectionQueue) {
                    try {
                        if (!toClose.isClosed()) {
                            if (!pendingElements.isEmpty() && toClose.getRequestId() == lastRequestID + 1) {
                                synchronized(pendingElements) {
                                    deliver(toClose, pendingElements);
                                    lastRequestID = toClose.getRequestId();
                                    pendingElements.clear();
                                }
                            } else {
                                toClose.deliverBody(null, true);
                            }
                        }
                    } catch (HttpConnectionClosedException e) {
                        /* ignore ... already closed */
                    } catch (IOException e) {
                        // Likely caused by closing a stale session / connection.
                        Log.debug("An unexpected exception occurred while closing a session.", e);
                    }
                }
            }

            synchronized (pendingElements) {
                for (Deliverable deliverable : pendingElements) {
                    failDelivery(deliverable.getPackets());
                }
                pendingElements.clear();
            }
        } finally { // ensure the session is removed from the session map
            SessionEventDispatcher.dispatchEvent( this, SessionEventDispatcher.EventType.session_closed, null, null );
        }
    }

    private void failDelivery(final Collection<Packet> packets) {
        if (packets == null) {
            // Do nothing if someone asked to deliver nothing :)
            return;
        }
        // use a separate thread to schedule backup delivery
        TaskEngine.getInstance().submit(new Runnable() {
            @Override
            public void run() {
                for (Packet packet : packets) {
                    try {
                        backupDeliverer.deliver(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error("On session " + getStreamID() + " unable to deliver message to backup deliverer", e);
                    }
                }
            }
        });
    }

    protected String createEmptyBody(boolean terminate)
    {
        final Element body = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        if (terminate) { body.addAttribute("type", "terminate"); }
        body.addAttribute("ack", String.valueOf(getLastAcknowledged()));
        return body.asXML();
    }

    private String createSessionRestartResponse()
    {
        final Element response = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        response.addNamespace("stream", "http://etherx.jabber.org/streams");

        final Element features = response.addElement("stream:features");
        for (Element feature : getAvailableStreamFeaturesElements()) {
            features.add(feature);
        }

        return response.asXML();
    }

    /**
     * A virtual server connection relates to a http session which its self can relate to many http
     * connections.
     */
    public static class HttpVirtualConnection extends VirtualConnection {

        private InetAddress address;
        private ConnectionConfiguration configuration;
        private ConnectionType connectionType;

        public HttpVirtualConnection(InetAddress address) {
            this.address = address;
            this.connectionType = ConnectionType.SOCKET_C2S;
        }

        public HttpVirtualConnection(InetAddress address, ConnectionType connectionType) {
            this.address = address;
            this.connectionType = connectionType;
        }

        @Override
        public void closeVirtualConnection() {
            ((HttpSession) session).closeSession();
        }

        @Override
        public byte[] getAddress() throws UnknownHostException {
            return address.getAddress();
        }

        @Override
        public String getHostAddress() throws UnknownHostException {
            return address.getHostAddress();
        }

        @Override
        public String getHostName() throws UnknownHostException {
            return address.getHostName();
        }

        @Override
        public void systemShutdown() {
            close();
        }

        @Override
        public void deliver(Packet packet) throws UnauthorizedException {
            ((HttpSession) session).deliver(packet);
        }

        @Override
        public void deliverRawText(String text) {
            ((HttpSession) session).deliver(text);
        }

        @Override
        public ConnectionConfiguration getConfiguration() {
            if (configuration == null) {
                final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
                configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
            }
            return configuration;
        }

        @Override
        public Certificate[] getPeerCertificates() {
            return ((HttpSession) session).getPeerCertificates();
        }
    }

    static class Deliverable {
        private final String text;
        private final Collection<String> packets;

        public Deliverable(String text) {
            this.text = text;
            this.packets = null;
        }

        public Deliverable(Collection<Packet> elements) {
            this.text = null;
            this.packets = new ArrayList<>();
            for (Packet packet : elements) {
                // Append packet namespace according XEP-0206 if needed
                if (Namespace.NO_NAMESPACE.equals(packet.getElement().getNamespace())) {
                    // use string-based operation here to avoid cascading xmlns wonkery
                    StringBuilder packetXml = new StringBuilder(packet.toXML());
                    final int noslash = packetXml.indexOf( ">" );
                    final int slash = packetXml.indexOf( "/>" );
                    final int insertAt = ( noslash - 1 == slash ? slash : noslash );
                    packetXml.insert( insertAt, " xmlns=\"jabber:client\"");
                    this.packets.add(packetXml.toString());
                } else {
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

        public Collection<Packet> getPackets() {
            // Check if the Deliverable is about Packets or raw XML
            if (packets == null) {
                // No packets here (should be just raw XML like <stream> so return nothing
                return null;
            }
            List<Packet> answer = new ArrayList<>();
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
            List<Packet> packets = new ArrayList<>();
            synchronized (deliverables) {
                for (Deliverable deliverable : deliverables) {
                    if (deliverable.packets != null) {
                        packets.addAll(deliverable.getPackets());
                    }
                }
            }
            return packets;
        }
    }

    /**
     * A runner that guarantees that the packets per a session will be sent and
     * processed in the order in which they were received.
     */
    private class HttpPacketSender implements Runnable {
        private HttpSession session;

        HttpPacketSender(HttpSession session) {
            this.session = session;
        }

        @Override
        public void run() {
            session.sendPendingPackets();
        }

        private void init() {
            HttpBindManager.getInstance().getSessionManager().execute(this);
        }
    }

    @Override
    public String toString()
    {
        String peerAddress = "(not available)";
        if ( getConnection() != null ) {
            try {
                peerAddress = getConnection().getHostAddress();
            } catch ( UnknownHostException e ) {
                Log.debug( "Unable to determine address for peer of local client session.", e );
            }
        }
        return this.getClass().getSimpleName() +"{" +
            "address=" + getAddress() +
            ", streamID=" + getStreamID() +
            ", status=" + getStatus() +
            (getStatus() == STATUS_AUTHENTICATED ? " (authenticated)" : "" ) +
            (getStatus() == STATUS_CONNECTED ? " (connected)" : "" ) +
            (getStatus() == STATUS_CLOSED ? " (closed)" : "" ) +
            ", isSecure=" + isSecure() +
            ", isDetached=" + isDetached() +
            ", serverName='" + getServerName() + '\'' +
            ", isInitialized=" + isInitialized() +
            ", hasAuthToken=" + (getAuthToken() != null) +
            ", peer address='" + peerAddress +'\'' +
            ", presence='" + getPresence().toString() + '\'' +
            ", hold='" + getHold() + '\'' +
            ", wait='" + getWait() + '\'' +
            ", maxRequests='" + getMaxRequests() + '\'' +
            ", maxPause='" + getMaxPause() + '\'' +
            ", lastActivity='" + getLastActivity() + '\'' +
            ", lastAcknowledged='" + getLastAcknowledged() + '\'' +
            ", inactivityTimeout='" + getInactivityTimeout() + '\'' +
            ", openConnectionCount='" + getOpenConnectionCount() + '\'' +
            '}';
    }
}
