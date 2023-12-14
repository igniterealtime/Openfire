/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.multiplex.UnknownStanzaException;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A session represents a series of interactions with an XMPP client sending packets using the HTTP
 * Binding protocol specified in <a href="https://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 * A session can have several client connections open simultaneously while awaiting packets bound
 * for the client from the server.
 *
 * @author Alexander Wenckus
 */
public class HttpSession extends LocalClientSession {

    private static final Logger Log = LoggerFactory.getLogger(HttpSession.class);

    /**`
     * Controls if client-provided 'pause' values that are invalid (higher than 'maxpause') are ignored or will cause the client to be disconnected.
     */
    public static SystemProperty<Boolean> IGNORE_INVALID_PAUSE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.httpbind.client.maxpause.ignore-invalid")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    private static XmlPullParserFactory factory = null;
    private static final ThreadLocal<XMPPPacketReader> localParser;
    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        // Create xmpp parser to keep in each thread
        localParser = ThreadLocal.withInitial(() -> {
            XMPPPacketReader parser = new XMPPPacketReader();
            factory.setNamespaceAware(true);
            parser.setXPPFactory(factory);
            return parser;
        });
    }

    /**
     * Specifies the longest time that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     */
    private final Duration wait;

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     */
    private final int hold;

    /**
     * Sets whether the initial request on the session was encrypted (eg: using HTTPS instead of HTTP).
     */
    private final boolean isEncrypted;

    /**
     * Sets the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     */
    private final Duration maxPollingInterval;

    /**
     * The max number of requests it is permissible for this session to have open at any one time.
     */
    private final int maxRequests;

    /**
     * Sets the maximum length of a temporary session pause that the client MAY request.
     */
    private final Duration maxPause;

    /**
     * Sets the default inactivity timeout of this session. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     */
    private final Duration defaultInactivityTimeout;

    /**
     * Returns the major version of BOSH which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements.
     */
    private final int majorVersion;

    /**
     * Sets the minor version of BOSH which the client implements.
     */
    private final int minorVersion;

    /**
     * The X509Certificates associated with this session.
     */
    private final X509Certificate[] sslCertificates;

    /**
     * Collection of client connections on which a BOSH request has been made, but have not been responded to.
     *
     * The connections in the queue will be ordered by their requestId value.
     */
    @GuardedBy("itself")
    private final PriorityQueue<HttpConnection> connectionQueue = new PriorityQueue<>((o1, o2) -> (int) (o1.getRequestId() - o2.getRequestId()));

    /**
     * A thread-safe collection (using a weakly consistent iterator) that contains stanzas that could not immediately be
     * delivered to the peer.
     */
    private final ConcurrentLinkedQueue<Deliverable> pendingElements = new ConcurrentLinkedQueue<>();

    /**
     * A list of data that has been delivered, for potential future retransmission.
     *
     * The size of this collection is limited. It will contain only the last few transmitted elements.
     */
    @GuardedBy("itself")
    private final LinkedList<Delivered> sentElements = new LinkedList<>();

    private Instant lastPoll = Instant.EPOCH;
    private Duration inactivityTimeout;

    private Instant lastActivity;

    @GuardedBy("connectionQueue")
    private long lastSequentialRequestID; // received

    @GuardedBy("connectionQueue")
    private long lastAnsweredRequestID; // sent

    private boolean lastResponseEmpty;

    private final SessionPacketRouter router = new SessionPacketRouter(this);

    public HttpSession(HttpVirtualConnection vConnection, String serverName,
                       StreamID streamID, long requestId, X509Certificate[] sslCertificates, Locale language,
                       Duration wait, int hold, boolean isEncrypted, Duration maxPollingInterval,
                       int maxRequests, Duration maxPause, Duration defaultInactivityTimeout,
                       int majorVersion, int minorVersion) throws UnknownHostException
    {
        super(serverName, vConnection, streamID, language);
        this.lastActivity = Instant.now();
        this.lastSequentialRequestID = requestId;
        this.sslCertificates = sslCertificates;
        this.wait = wait;
        this.hold = hold;
        this.isEncrypted = isEncrypted;
        this.maxPollingInterval = maxPollingInterval;
        this.maxRequests = maxRequests;
        this.maxPause = maxPause;
        this.defaultInactivityTimeout = defaultInactivityTimeout;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;

        if (Log.isDebugEnabled()) {
            Log.debug("Session {} being opened with initial connection {}", getStreamID(), vConnection.toString());
        }
    }

    /**
     * Returns the stream features which are available for this session.
     *
     * @return the stream features which are available for this session.
     */
    @Override
    public List<Element> getAvailableStreamFeatures() {
        final List<Element> elements = new ArrayList<>();

        // If authentication has not happened yet, include available authentication mechanisms.
        if (getAuthToken() == null) {
            final Element sasl = SASLAuthentication.getSASLMechanismsElement(this);
            if (sasl != null) {
                elements.add(sasl);
            }
        }

        if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
            elements.add(DocumentHelper.createElement(new QName("register",new Namespace("", "http://jabber.org/features/iq-register"))));
        }

        elements.add(DocumentHelper.createElement(new QName("bind", new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind"))));

        final Element session = DocumentHelper.createElement(new QName("session", new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
        session.addElement("optional");
        elements.add(session);

        return elements;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @return the longest time it is permissible to wait for a response.
     */
    public Duration getWait() {
        return wait;
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
     * Returns the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @return the max interval within which a client can send polling requests. If more than one
     *         request occurs in the interval the session will be terminated.
     */
    public Duration getMaxPollingInterval() {
        return this.maxPollingInterval;
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
     * Returns the maximum length of a temporary session pause that the client MAY request.
     *
     * @return the maximum length of a temporary session pause that the client MAY request.
     */
    public Duration getMaxPause() {
        return this.maxPause;
    }

    /**
     * Returns true if all connections on this session should be encrypted, and false if they should
     * not.
     *
     * @return true if all connections on this session should be encrypted, and false if they should
     *         not.
     */
    @Override
    public boolean isEncrypted() {
        return isEncrypted;
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
        return (this.wait.isZero() || this.hold == 0);
    }

    /**
     * Sets the time, in seconds, after which this session will be considered inactive and be terminated.
     *
     * @param inactivityTimeout the time, in seconds, after which this session will be considered
     * inactive and be terminated.
     */
    public void setInactivityTimeout(Duration inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    /**
     * Resets the inactivity timeout of this session to default. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     *
     * @see #pause(Duration)
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
    public Duration getInactivityTimeout() {
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
    public void pause(Duration duration) {
        // Respond immediately to all pending requests
        synchronized (connectionQueue) {
            final Iterator<HttpConnection> iter = connectionQueue.iterator();
            while (iter.hasNext()) {
                final HttpConnection toClose = iter.next();
                toClose.close();
                iter.remove();
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
    public Instant getLastActivity() {
        synchronized (connectionQueue) {
            if (!connectionQueue.isEmpty()) {
                // The session is currently active, set the last activity to the current time.
                lastActivity = Instant.now();
            }
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
        synchronized (connectionQueue) {
            return lastSequentialRequestID;
        }
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
     * Forwards a client request, which is related to a session, to the server. A connection is
     * created and queued up in the provided session. When a connection reaches the top of a queue
     * any pending packets bound for the client will be forwarded to the client through the
     * connection.
     *
     * @param body the body element that was sent containing the request for a new session.
     * @param context the context of the asynchronous servlet call leading up to this method call.
     *
     * @throws HttpBindException for several reasons: if the encoding inside an auth packet is not recognized by the
     *                           server, or if the packet type is not recognized.
     * @throws HttpConnectionClosedException if the session is no longer available.
     * @throws IOException if an input or output exception occurred
     */
    public void forwardRequest(HttpBindBody body, AsyncContext context)
            throws HttpBindException, HttpConnectionClosedException, IOException
    {
        final HttpConnection connection = this.createConnection(body, context);
        final long rid = body.getRid();
        final StreamID streamid = getStreamID();

        // Check if security restraints are observed.
        if (isEncrypted && !connection.isEncrypted()) {
            throw new HttpBindException("Session was started from encrypted connection, all " +
                "connections on this session must be encrypted.", BoshBindingError.badRequest);
        }

        synchronized (connectionQueue)
        {
            // Check if the provided RID is in the to-be-expected window.
            if (rid > (lastSequentialRequestID + maxRequests)) {
                Log.warn("Request {} > {}, ending session {}", body.getRid(), (lastSequentialRequestID + maxRequests), getStreamID());
                throw new HttpBindException("Unexpected RID error.", BoshBindingError.itemNotFound);
            }

            // Check for retransmission.
            if (rid <= lastAnsweredRequestID) {
                Log.debug("Request {} on session {} appears to be a request for redelivery, as the last answered RID is {}", rid, getStreamID(), lastAnsweredRequestID);
                redeliver(connection);
                return;
            }

            /*
             * Search through the connection queue to see if this rid already exists on it. If it does then we
             * will close and deliver the existing connection (if appropriate), and close and deliver the same
             * deliverable on the new connection. This is under the assumption that a connection has been dropped,
             * and re-requested before jetty has realised.
             */
            final Iterator<HttpConnection> iter = connectionQueue.iterator();
            while (iter.hasNext()) {
                final HttpConnection queuedConnection = iter.next();
                if (queuedConnection.getRequestId() == rid) {
                    Log.debug("Found previous connection in queue with rid {}", rid);

                    // Note that the old connection is removed here, but the new connection will not be added back before the mutex is released. This leaves
                    // room for another thread to try and consume a connection before this connection has been restored. This should be safe, as the consumer
                    // should not be allowed to consume a connection with a RID that, compared to the previously consumed RID, leaves a 'gap'. In that case,
                    // the to-be-delivered data is expected to be queued, which will be picked up as soon as this connection (for which the RID 'fills the gap',
                    // is being processed by org.jivesoftware.openfire.http.HttpSession.processConnection.
                    iter.remove();

                    assert !queuedConnection.isClosed();
                    Log.debug("For session {} queued connection is still open - calling close() on the old connection (as the new connection will replace it).", streamid);
                    // TODO: OF-2447: implement section 14.3 of XEP 0124 instead of this!
                    deliver(queuedConnection, Collections.singletonList(new Deliverable("")), true);
                    queuedConnection.close();
                    break;
                }
            }
        }

        checkOveractivity(connection);

        // Schedule the connection for consumption.
        processConnection(connection, context);
        resetInactivityTimeout();
    }

    /**
     * This method sends any pending packets in the session. If no packets are
     * pending, this method simply returns. The method is internally synchronized
     * to avoid simultaneous sending operations on this Session. If two
     * threads try to run this method simultaneously, the first one will trigger
     * the pending packets to be sent, while the second one will simply return
     * (as there are no packets left to send).
     */
    protected void sendPendingPackets(final List<Element> packetsToSend) {
        if (packetsToSend == null || packetsToSend.isEmpty()) {
            return;
        }

        // Schedule in-order.
        synchronized (router) {
            HttpBindManager.getInstance().getSessionManager().execute(() -> {
                for (Element packet : packetsToSend) {
                    try {
                        router.route(packet);
                    } catch (UnknownStanzaException e) {
                        Log.error("On session " + getStreamID() + " client provided unknown packet type", e);
                    }
                }
            });
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
     * Creates a new connection on this session.
     *
     * @param body the body element that was sent containing the request for a new session.
     * @param context the context of the asynchronous servlet call leading up to this method call.
     * @return the created {@link HttpConnection} which represents the connection.
     */
    @Nonnull
    synchronized HttpConnection createConnection(@Nonnull final HttpBindBody body, @Nonnull final AsyncContext context)
    {
        final HttpConnection connection = new HttpConnection(body, context);
        final StreamID streamID = getStreamID();
        final long rid = body.getRid();
        if (Log.isDebugEnabled()) {
            Log.debug( "Creating connection for rid: {} in session {}", rid, streamID );
        }
        connection.setSession(this);
        context.setTimeout(getWait().toMillis());
        context.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent asyncEvent) {
                if (Log.isTraceEnabled()) {
                    Log.trace("Session {} Request ID {}, event complete: {}", streamID, rid, asyncEvent);
                }
                synchronized (connectionQueue) {
                    if (connectionQueue.remove(connection) || !connection.isClosed()) {
                        Log.warn("Discovered a 'complete' event for a BOSH connection that has not been consumed (for session {} with Request ID {}, was closed: {}). This likely is a bug in Openfire.", streamID, rid, connection.isClosed());
                    }
                }
                lastActivity = Instant.now();
                SessionEventDispatcher.dispatchEvent( HttpSession.this, SessionEventDispatcher.EventType.connection_closed, connection, context );
            }

            @Override
            public void onTimeout(AsyncEvent asyncEvent) throws IOException {
                if (Log.isTraceEnabled()) {
                    Log.trace("Session {} Request ID {}, event timeout: {}. Returning an empty response.", streamID, rid, asyncEvent);
                }

                try {
                    // If onTimeout does not result in a complete(), the container falls back to default behavior.
                    // This is why this body is to be delivered in a non-async fashion.
                    synchronized (connectionQueue) {
                        // Consume the connection that is timing out, by removing it from the queue and sending data to it.
                        connectionQueue.remove(connection);
                        deliver(connection, Collections.singletonList(new Deliverable("")), false);
                        setLastResponseEmpty(true);
                    }
                } catch (HttpConnectionClosedException e) {
                    Log.warn("Unexpected exception while processing connection timeout.", e);
                }

                // Note that 'onComplete' will be invoked.
            }

            @Override
            public void onError(AsyncEvent asyncEvent) {
                if (Log.isTraceEnabled()) {
                    Log.trace("Session {} Request ID {}, event error: {}", streamID, rid, asyncEvent);
                }
                Log.warn("For session {} an unhandled AsyncListener error occurred: ", streamID, asyncEvent.getThrowable());
                synchronized (connectionQueue) {
                    // There was an error with a connection. Make sure it cannot be consumed again.
                    connectionQueue.remove(connection);
                }
                SessionEventDispatcher.dispatchEvent( HttpSession.this, SessionEventDispatcher.EventType.connection_closed, connection, context );
            }

            @Override
            public void onStartAsync(AsyncEvent asyncEvent) {
                if (Log.isTraceEnabled()) {
                    Log.trace("Session {} Request ID {}, event start: {}", streamID, rid, asyncEvent);
                }
                lastActivity = Instant.now();
            }
        });

        return connection;
    }

    /**
     * Attempts to find data that was previously sent back to the client, using a particular request ID. This is
     * expected to be used to process requests for retransmission.
     *
     * The implementation only holds a limited amount of data. It will return an empty Optional when no data that
     * matches the ID can be found.
     *
     * @param rid The request ID for which to find previously delivered data.
     * @return previously delivered data when found.
     */
    @Nonnull
    private Optional<Delivered> retrieveDeliverable(final long rid) {
        synchronized (sentElements) {
            for (Delivered delivered : sentElements) {
                if (delivered.getRequestID() == rid) {
                    return Optional.of(delivered);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Schedules the connection for processing, ensuring that connections are processed in the order of their request ID
     * values. Processing causes data delivered by the client to be routed in the server to their intended recipients,
     * and allow the connection to be used to send back data to the client.
     *
     * @param connection The connection ready to be processed.
     * @param context the context of the asynchronous servlet call leading up to this method call.
     */
    private void processConnection(@Nonnull final HttpConnection connection, @Nonnull final AsyncContext context) throws HttpConnectionClosedException, IOException
    {
        final long rid = connection.getRequestId();
        final StreamID streamid = getStreamID();
        if (Log.isDebugEnabled()) {
            Log.debug( "Adding connection to stream {} with rid {}", streamid, rid );
        }

        // Note that connections can be expected to arrive 'out of order'. The implementation should only use a connection
        // that has a request ID value that's exactly one higher than the last request ID value in the 'gap-less' sequence
        // of request IDs. When a connection is being processed that has a higher value, it should go unused until another
        // connection arrives that 'fills the gap' (and be used only _after_ that connection gets used).
        boolean aConnectionAvailableForDelivery = false;
        boolean mustClose = false;
        synchronized (connectionQueue) {
            // Note that this queue will automatically order its entities.
            connectionQueue.add(connection);

            lastActivity = Instant.now();

            final Iterator<HttpConnection> iter = connectionQueue.iterator();
            while (iter.hasNext()) {
                final HttpConnection queuedConnection = iter.next();
                assert !queuedConnection.isClosed();

                final long queuedRequestID = queuedConnection.getRequestId();
                if (queuedRequestID <= lastSequentialRequestID)
                {
                    // The request body (inbound data) for this request will already have been processed, but the connection remains queued, waiting to be used to send outbound data.
                    Log.trace("Detected a queued connection (for session {}) with a request ID ({}) that is not higher than the last sequential request ID ({}). This connection is waiting to be used to deliver outbound data back to the client.", streamid, queuedRequestID, lastSequentialRequestID);
                    continue;
                }
                if (queuedRequestID == lastSequentialRequestID + 1)
                {
                    Log.debug("Detected a queued connection (for session {}) with request ID ({}) that is exactly one higher than the last sequential request ID ({}). This inbound data on this connection will now be processed, after which the connection can be used to send outbound data back to the client.", streamid, queuedRequestID, lastSequentialRequestID);
                    // The data that was provided by the client can now be processed by the server.
                    // The below sends this data asynchronously.
                    sendPendingPackets(queuedConnection.getInboundDataQueue());

                    // Evaluate edge-cases.
                    if (queuedConnection.isTerminate()) {
                        Log.debug("Connection (for session {}) with request ID ({}) is a request to terminate.", getStreamID(), queuedRequestID);
                        iter.remove(); // This connection will be consumed here.
                        queuedConnection.deliverBody(createEmptyBody(true), true);
                        mustClose = true;
                    } else if (queuedConnection.isRestart()) {
                        Log.debug("Connection (for session {}) with request ID ({}) is a request to restart.", getStreamID(), queuedRequestID);
                        iter.remove(); // This connection has now been fully consumed.
                        queuedConnection.deliverBody(createSessionRestartResponse(), true);
                    } else if (queuedConnection.getPause() != null) {
                        // OF-2449: Error when the requested pause is higher than the allowed maximum.
                        if (!IGNORE_INVALID_PAUSE.getValue() && (queuedConnection.getPause().compareTo(getMaxPause()) > 0 || queuedConnection.getPause().isNegative())) {
                            Log.info("Connection (for session {}) with request ID ({}) is a request to pause (for {}) that is outside of the permissible range of 0 to {}", getStreamID(), queuedRequestID, queuedConnection.getPause(), getMaxPause());
                            queuedConnection.deliverBody(createTerminalBindingBody("policy-violation"), true);
                            mustClose = true;
                        } else {
                            Log.debug("Connection (for session {}) with request ID ({}) is a request to pause (for {}).", getStreamID(), queuedRequestID, queuedConnection.getPause());
                            pause(queuedConnection.getPause());
                            queuedConnection.deliverBody(createEmptyBody(false), true);
                            setLastResponseEmpty(true);
                        }
                        iter.remove(); // This connection will be consumed by this block. It should not be processed by the 'pause' method.
                    } else {
                        // At least one new connection has become available, and can be used to return data to the client.
                        aConnectionAvailableForDelivery = true;
                    }

                    // There is a new connection available for consumption.
                    lastSequentialRequestID = queuedRequestID;

                    // Note that when this connection fills a gap in the sequence, other connections might already be
                    // available that have the 'next' Request ID value. We need to keep iterating.
                } else {
                    // As we're iterating over the collection that is ordered by Request ID, the iteration can stop
                    // when a gap is detected (subsequent connections will only have higher Request ID values).
                    break;
                }
            }

            if (!mustClose) {
                // If a connection became available for delivery and there's pending data to be delivered, deliver immediately.
                // Request ID of the new connection 'fits in the window'

                if (isPollingSession()) {
                    // Note that the code leading up to here checks if the Request ID of the new connection 'fits in the window',
                    // which means that for polling sessions, the request ID must have been a sequential one, which in turn should
                    // guarantee that 'a new connection is now available for delivery').
                    assert aConnectionAvailableForDelivery; // FIXME: OF-2451: the edge-cases evaluated above make this assertion not necessarily true.
                }

                if (isPollingSession() || aConnectionAvailableForDelivery) {
                    SessionEventDispatcher.dispatchEvent(this, SessionEventDispatcher.EventType.connection_opened, connection, context); // TODO is this the right place to dispatch this event?
                    tryImmediateDelivery();
                }

                // When a new connection has become available, older connections need to be released (allowing the client to
                // send more data if it needs to).
                while (!connectionQueue.isEmpty() && connectionQueue.size() > hold) {
                    if (Log.isTraceEnabled()) {
                        Log.trace("Stream {}: releasing oldest connection (rid {}), as the amount of open connections ({}) is higher than the requested amount to hold ({}).", streamid, rid, connectionQueue.size(), hold);
                    }
                    final HttpConnection openConnection = connectionQueue.peek();
                    assert openConnection != null;
                    if (openConnection.getRequestId() > lastSequentialRequestID) {
                        break; // There's a gap. As described above, connections must be used in sequence, without jumping the queue.
                    }

                    // Consume this connection.
                    connectionQueue.poll();
                    openConnection.deliverBody(createEmptyBody(false), true);
                }
            }
        }

        // OF-2444: Call 'close()' outside of the connectionQueue mutex, to avoid deadlocks.
        if (mustClose) {
            close();
        }
    }

    /**
     * Attempts to process a request for redelivery of data that was sent earlier.
     *
     * This method is expected to be called with a connection that has a request ID that was already processed before.
     *
     * If the data that was delivered in the earlier connection is still available, this will be delivered on this new
     * connection again. When the data is no longer available, an item-not-found error is generated.
     *
     * @param connection The connection requesting a retransmission
     * @throws HttpConnectionClosedException if the connection was closed before a response could be delivered.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding protocol.
     */
    @GuardedBy("connectionQueue")
    private void redeliver(@Nonnull final HttpConnection connection) throws HttpBindException, IOException, HttpConnectionClosedException
    {
        Log.debug("Session {} requesting a retransmission for rid {}", getStreamID(), connection.getRequestId());
        final Optional<Delivered> deliverable = retrieveDeliverable(connection.getRequestId());
        if (!deliverable.isPresent()) {
            Log.warn("Deliverable unavailable for " + connection.getRequestId() + " in session " + getStreamID());
            throw new HttpBindException("Unexpected RID error.", BoshBindingError.itemNotFound);
        }
        connection.deliverBody(asBodyText(deliverable.get().deliverables), true);
    }

    private enum OveractivityType {
        NONE,
        TOO_MANY_SIM_REQS,
        POLLING_TOO_QUICK
    }

    /**
     * Check that the client SHOULD NOT make more simultaneous requests than specified
     * by the 'requests' attribute in the connection manager's Session Creation Response.
     * However, the client MAY make one additional request if it is to pause or terminate a session.
     *
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#overactive">overactive</a>
     * @param connection the new connection.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     *         protocol.
     */
    private void checkOveractivity(HttpConnection connection) throws HttpBindException {
        int pendingConnections;
        OveractivityType overactivity = OveractivityType.NONE;

        synchronized (connectionQueue) {
            pendingConnections = connectionQueue.size();
        }

        Instant time = Instant.now();
        Duration deltaFromLastPoll = Duration.between(lastPoll, time).abs();
        if(pendingConnections >= maxRequests) {
            overactivity = OveractivityType.TOO_MANY_SIM_REQS;
        }
        else if(connection.isPoll()) {
            boolean localIsPollingSession = isPollingSession();
            if (deltaFromLastPoll.compareTo(maxPollingInterval) < 0) {
                if (localIsPollingSession) {
                    overactivity = lastResponseEmpty ? OveractivityType.POLLING_TOO_QUICK : OveractivityType.NONE;
                } else {
                    overactivity = pendingConnections >= maxRequests ? OveractivityType.POLLING_TOO_QUICK : OveractivityType.NONE;
                }
            }
            lastPoll = time;
            if (Log.isDebugEnabled()) {
                Log.debug("Updated session " + getStreamID() +
                        " lastPoll to " + lastPoll +
                        " with rid " + connection.getRequestId() +
                        " lastResponseEmpty = " + lastResponseEmpty  +
                        " overactivity = " + overactivity +
                        " deltaFromLastPoll = " + deltaFromLastPoll +
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
                    errorMessage.append(deltaFromLastPoll);
                    break;
                }
                default: {
                    throw new HttpBindException("Unhandled overactivity type: " + overactivity, BoshBindingError.internalServerError);
                }
            }
            String errorMessageStr = errorMessage.toString();
            if (Log.isDebugEnabled()) {
                Log.debug(errorMessageStr);
            }
            if (!JiveGlobals.getBooleanProperty("xmpp.httpbind.client.requests.ignoreOveractivity", false)) {
                throw new HttpBindException(errorMessageStr, BoshBindingError.policyViolation);
            }
        }
    }

    @Override
    public void deliver(@Nonnull final Packet stanza) {
        deliver(new Deliverable(Collections.singletonList(stanza)));
    }

    /**
     * Delivers the provided data to the client, using the first available
     * connection, queuing the data of no connection is presently available.
     *
     * The data is expected to be XMPP-valid.
     *
     * @param stanza data to be delivered.
     */
    private void deliver(@Nonnull final Deliverable stanza) {
        deliver(Collections.singletonList(stanza));
    }

    /**
     * Delivers the provided data to the client, using the first available
     * connection, queuing the data of no connection is presently available.
     *
     * The data is expected to be XMPP-valid.
     *
     * @param deliverable data to be delivered.
     */
    private void deliver(@Nonnull final List<Deliverable> deliverable)
    {
        pendingElements.addAll(deliverable); // ConcurrentLinkedQueue.addAll is not guaranteed to be atomic. We don't need that, as long as the insertion/iteration order is guaranteed. I'm not sure if it is (but I assume so).
        tryImmediateDelivery();
    }

    /**
     * Tries to deliver all data that is queued to be sent to the client, if data has been queued and a connection is
     * available for delivery.
     *
     * When no data is queued for delivery, or when no connection is available, an invocation does nothing.
     */
    private void tryImmediateDelivery()
    {
        final List<Deliverable> deliverables = drainPendingElements();
        if (deliverables.isEmpty()) {
            Log.trace("Immediate delivery of pending data to the client on session {} was requested, but no data is pending.", getStreamID());
            return;
        }

        // OF-2445: stanzas should not linger forever in a queue of a session that is already closed.
        if (isClosed()) {
            deliverables.forEach(deliverable -> failDelivery(deliverable.getPackets()));
            return;
        }

        final Optional<HttpConnection> connection = getConnectionReadyForOutboundDelivery();

        if (!connection.isPresent()) {
            Log.trace("Immediate delivery of pending data to the client on session {} was requested, but no connection is available. The data ({} deliverables) will be re-queued.", getStreamID(), deliverables.size());
            // place pending deliverables back on queue. // FIXME: if other threads have placed pending elements, this will cause a re-order, which might be undesirable.
            pendingElements.addAll(deliverables);
            return;
        }

        // OF-2444: deliver asynchronously, to avoid deadlocking issues.
        HttpBindManager.getInstance().getSessionManager().execute(() -> {
            try {
                deliver(connection.get(), deliverables, true);
            } catch (HttpConnectionClosedException e) {
                /* Connection was closed, try the next one. Indicates a (concurrency?) bug. */
                Log.warn("Iterating over a connection that was closed for session {}. Openfire will recover from this problem, but it should not occur in the first place.", getStreamID(), e);
                // place pending deliverables back on queue. // FIXME: if other threads have placed pending elements, this will cause a re-order, which might be undesirable.
                pendingElements.addAll(deliverables);
            } catch (IOException e) {
                Log.warn("An unexpected exception occurred while iterating over connections for session {}. Openfire will attempt to recover by ignoring this connection.", getStreamID(), e);
                // place pending deliverables back on queue. // FIXME: if other threads have placed pending elements, this will cause a re-order, which might be undesirable.
                pendingElements.addAll(deliverables);
            }
        });
    }

    /**
     * Returns a connection that can be used to deliver data to the client, if such a connection is currently available.
     *
     * @return A connection that is ready to be responded to.
     */
    @Nonnull
    private Optional<HttpConnection> getConnectionReadyForOutboundDelivery()
    {
        synchronized (connectionQueue) {
            // The connection queue is ordered. No need to iterate further than the first element.
            if (!connectionQueue.isEmpty()) {
                final HttpConnection connection = connectionQueue.peek();
                assert !connection.isClosed();

                // We can only use connections that have a sequential request ID.
                if (connection.getRequestId() <= lastSequentialRequestID) {
                    Log.trace("Got a connection that is ready for outbound delivery of session {}. The connection's RID is {}. The last sequential RID was: {}", getStreamID(), connection.getRequestId(), lastSequentialRequestID);
                    connectionQueue.poll(); // remove it.
                    return Optional.of(connection);
                } else {
                    Log.trace("Trying to get a connection that is ready for outbound delivery of session {}, but the first connection in the connection queue isn't the next connection that needs to be responded to. It's RID is {}, while the last sequential RID was {}.", getStreamID(), connection.getRequestId(), lastSequentialRequestID);
                }
            } else {
                Log.trace("Trying to get a connection that is ready for outbound delivery of session {}, but the connection queue is currently empty. The last sequential RID was {}", getStreamID(), lastSequentialRequestID);
            }
        }
        return Optional.empty();
    }

    /**
     * Uses a connection to return data to the client.
     *
     * @param connection The connection to use return data.
     * @param deliverables The data to be delivered.
     * @param async should the invocation block until the data has been delivered?
     */
    private void deliver(@Nonnull final HttpConnection connection, @Nonnull final List<Deliverable> deliverables, final boolean async)
        throws HttpConnectionClosedException, IOException
    {
        Log.trace("Delivering {} deliverables to the client on session {}, using connection with RID {}", deliverables.size(), getStreamID(), connection.getRequestId());
        connection.deliverBody(asBodyText(deliverables), async);
        lastAnsweredRequestID = connection.getRequestId();

        lastActivity = Instant.now();

        for (final Deliverable deliverable : deliverables) {
            for (int i=0; i<deliverable.stanzaCount(); i++) {
                incrementServerPacketCount();
            }
        }

        // Keep track of data that has been delivered, for potential future retransmission.
        final Delivered delivered = new Delivered(deliverables, connection.getRequestId());
        synchronized (sentElements) {
            while (sentElements.size() > maxRequests) {
                sentElements.poll();
            }

            sentElements.add(delivered);
        }
    }

    private void closeSession(@Nullable final StreamError error, boolean allowWrite) {
        try {
            if (allowWrite) {
                // There generally should not be a scenario where there are pending connections, as well as pending elements
                // to deliver, as when a new connection becomes available while there are pending elements, those will be
                // delivered on that connection immediately. There's an edge-case where there are pending connections which
                // do not have the correct, expected Request ID (+1 in the sequence). In that case, those connections are
                // not eligible to receive data. These facts combines should rule out the need to flush pending elements to
                // open connections in this method.
                synchronized (connectionQueue) {
                    boolean isFirst = true;
                    while (!connectionQueue.isEmpty()) {
                        final HttpConnection toClose = connectionQueue.poll();
                        assert !toClose.isClosed();
                        try {
                            // XEP-0124, section 13: "The connection manager SHOULD acknowledge the session termination on
                            // the oldest connection with an HTTP 200 OK containing a <body/> element of the type
                            // 'terminate'. On all other open connections, the connection manager SHOULD respond with an
                            // HTTP 200 OK containing an empty <body/> element.
                            final String body;
                            if (isFirst) {
                                isFirst = false;
                                body = this.createEmptyBody(true);
                            } else {
                                body = null;
                            }
                            toClose.deliverBody(body, true);
                        } catch (HttpConnectionClosedException e) {
                            // Probably benign.
                            Log.debug("Closing an already closed connection.", e);
                        } catch (IOException e) {
                            // Likely caused by closing a stale session / connection.
                            Log.debug("An unexpected exception occurred while closing a session.", e);
                        }
                    }
                }
            }

            final List<Deliverable> toProcessElements = drainPendingElements();
            for (Deliverable deliverable : toProcessElements) {
                failDelivery(deliverable.getPackets());
            }
        } finally { // ensure the session is removed from the session map
            SessionEventDispatcher.dispatchEvent( this, SessionEventDispatcher.EventType.session_closed, null, null );
        }
    }

    private void failDelivery(final List<Packet> packets) {
        if (packets == null) {
            // Do nothing if someone asked to deliver nothing :)
            return;
        }

        if (conn.getPacketDeliverer() == null) {
            Log.trace("Discarding packet that failed to be delivered to connection {}, for which no backup deliverer was configured.", this);
            return;
        }

        // use a separate thread to schedule backup delivery
        TaskEngine.getInstance().submit(() -> {
            for (final Packet packet : packets) {
                try {
                    conn.getPacketDeliverer().deliver(packet);
                }
                catch (UnauthorizedException e) {
                    Log.error("On session " + getStreamID() + " unable to deliver message to backup deliverer", e);
                }
            }
        });
    }

    /**
     * Returns the textual representation of the provided element, wrapped in a httpbind 'body' element. The intended
     * usage of this method is to generate data that can be included in HTTP responses returned to the client.
     *
     * @param elements The data to be transformed (can be empty).
     * @return The text representation (wrapped in a body element) of the provided elements.
     */
    @Nonnull
    private String asBodyText(@Nonnull final List<Deliverable> elements) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body xmlns='http://jabber.org/protocol/httpbind' ack='")
            .append(getLastAcknowledged()).append("'>");

        setLastResponseEmpty(elements.isEmpty());
        for (Deliverable child : elements) {
            builder.append(child.getDeliverable());
        }
        builder.append("</body>");
        return builder.toString();
    }

    /**
     * Creates an empty BOSH 'body' element, optionally including a 'terminate' type attribute (that, in BOSH,
     * signifies the end of a session). The element will include an 'ack' attribute, of which the value is the highest
     * request ID (rid) the server has received where it has also received all requests with lower request ID values.
     *
     * @param terminate Whether to include a type attribute with value 'terminate'.
     * @return The string representation of an empty BOSH 'body' element.
     */
    @Nonnull
    protected String createEmptyBody(final boolean terminate)
    {
        final Element body = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        if (terminate) { body.addAttribute("type", "terminate"); }
        body.addAttribute("ack", String.valueOf(getLastAcknowledged()));
        return body.asXML();
    }

    /**
     * Creates an empty BOSH 'body' element that including a 'terminate' type attribute (that, in BOSH, signifies the
     * end of a session), and sets the 'condition' attribute to a specified value.
     *
     * @param condition The terminate condition to set.
     * @return The string representation of a BOSH 'body' element.
     */
    @Nonnull
    protected String createTerminalBindingBody(@Nonnull final String condition)
    {
        final Element body = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        body.addAttribute("type", "terminate");
        body.addAttribute("condition", condition);
        return body.asXML();
    }

    /**
     * Creates an empty BOSH 'body' element that including a 'terminate' type attribute (that, in BOSH, signifies the
     * end of a session), sets the 'condition' attribute to 'remote-stream-error' (to signal that an XMPP error is
     * transported), and includes the error in the body.
     *
     * @param error The error to be included in the body.
     * @return The string representation of a BOSH 'body' element.
     */
    @Nonnull
    protected String createRemoteStreamErrorBody(@Nonnull final StreamError error)
    {
        final Element body = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        body.addAttribute("type", "terminate");
        body.addAttribute("condition", "remote-stream-error");
        body.add(error.getElement());
        return body.asXML();
    }

    /**
     * Creates a BOSH 'body' element that represents a 'session restart' event, including the stream features that are
     * available to this session.
     *
     * @return The string representation of a BOSH 'body' element for a 'session restart'.
     * @see <a href="https://xmpp.org/extensions/xep-0206.html#preconditions-sasl">XEP-0206, section 5</a>
     */
    @Nonnull
    private String createSessionRestartResponse()
    {
        final Element response = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        response.addNamespace("stream", "http://etherx.jabber.org/streams");

        final Element features = response.addElement("stream:features");
        for (Element feature : getAvailableStreamFeatures()) {
            features.add(feature);
        }

        return response.asXML();
    }

    /**
     * Removes all elements that are queued to be delivered to the peer from the queue and returns them as an
     * unmodifiable collection.
     *
     * @return An unmodifiable list of elements that are to be sent to the peer.
     */
    @Nonnull
    private List<Deliverable> drainPendingElements()
    {
        final List<Deliverable> result = new LinkedList<>();
        final Iterator<Deliverable> iter = pendingElements.iterator(); // Weakly consistent iterator.
        while (iter.hasNext()) {
            result.add(iter.next());
            iter.remove();
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * A virtual server connection relates to a http session which its self can relate to many http
     * connections.
     */
    public static class HttpVirtualConnection extends VirtualConnection
    {
        private final InetAddress address;
        private ConnectionConfiguration configuration;
        private final ConnectionType connectionType;
        private final PacketDeliverer backupDeliverer;

        public HttpVirtualConnection(@Nonnull final InetAddress address, @Nullable final PacketDeliverer backupDeliverer, @Nonnull final ConnectionType connectionType) {
            this.address = address;
            this.backupDeliverer = backupDeliverer;
            this.connectionType = connectionType;
        }

        @Override
        public void closeVirtualConnection(@Nullable final StreamError error) {
            ((HttpSession) session).closeSession(error, true);
        }

        @Override
        public byte[] getAddress() {
            return address.getAddress();
        }

        @Override
        public String getHostAddress() {
            return address.getHostAddress();
        }

        @Override
        public String getHostName() {
            return address.getHostName();
        }

        @Override
        public void systemShutdown() {
            close(new StreamError(StreamError.Condition.system_shutdown));
        }

        @Override
        public void deliver(Packet packet) {
            ((HttpSession) session).deliver(packet);
        }

        @Override
        public void deliverRawText(@Nullable final String text) {
            if (text == null) {
                // Do nothing if someone asked to send nothing :)
                return;
            }
            ((HttpSession) session).deliver(new Deliverable(text));
        }

        @Override
        public Optional<String> getTLSProtocolName() {
            return Optional.of("unknown"); // FIXME: determine the correct value.
        }

        @Override
        public Optional<String> getCipherSuiteName() {
            return Optional.of("unknown"); // FIXME: determine the correct value.
        }

        @Override
        public ConnectionConfiguration getConfiguration() {
            if (configuration == null) {
                final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
            }
            return configuration;
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return ((HttpSession) session).getPeerCertificates();
        }

        @Override
        @Nullable
        public PacketDeliverer getPacketDeliverer() {
            return backupDeliverer;
        }
    }

    /**
     * Representation of data to be delivered to a client.
     */
    @Immutable
    static class Deliverable
    {
        @Nullable
        private final String text;

        @Nullable
        private final List<String> packets;

        private final int stanzaCount;

        public Deliverable(@Nonnull final String text) {
            this.text = text;
            this.packets = null;
            stanzaCount = StringUtils.countMatches(text, "<presence ") + StringUtils.countMatches(text, "<iq ") + StringUtils.countMatches(text, "<message ");
        }

        public Deliverable(@Nonnull final List<Packet> elements) {
            this.text = null;
            final List<String> stanzas = new ArrayList<>();
            for (final Packet packet : elements) {
                // Append packet namespace according XEP-0206 if needed
                if (Namespace.NO_NAMESPACE.equals(packet.getElement().getNamespace())) {
                    // use string-based operation here to avoid cascading xmlns wonkery
                    StringBuilder packetXml = new StringBuilder(packet.toXML());
                    final int noSlash = packetXml.indexOf( ">" );
                    final int slash = packetXml.indexOf( "/>" );
                    final int insertAt = ( noSlash - 1 == slash ? slash : noSlash );
                    packetXml.insert( insertAt, " xmlns=\"jabber:client\"");
                    stanzas.add(packetXml.toString());
                } else {
                    stanzas.add(packet.toXML());
                }
            }

            this.packets = Collections.unmodifiableList(stanzas);
            this.stanzaCount = stanzas.size();
        }

        @Nonnull
        public String getDeliverable() {
            if (text == null) {
                final StringBuilder builder = new StringBuilder();
                if (packets != null) {
                    for (final String packet : packets) {
                        builder.append(packet);
                    }
                }
                return builder.toString();
            }
            else {
                return text;
            }
        }

        @Nullable
        public List<Packet> getPackets() {
            // Check if the Deliverable is about Packets or raw XML
            if (packets == null) {
                // No packets here (should be just raw XML like <stream> so return nothing
                return null;
            }
            final List<Packet> answer = new ArrayList<>();
            for (final String packetXML : packets) {
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
                    Log.error("Error while parsing text as stanza: {}", packetXML, e);
                }
            }
            return answer;
        }

        public int stanzaCount() {
            return stanzaCount;
        }
    }

    /**
     * A representation of data that have previously been delivered back to the client.
     */
    @Immutable
    static class Delivered
    {
        private final long requestID;

        @Nonnull
        private final List<Deliverable> deliverables;

        public Delivered(@Nonnull final List<Deliverable> deliverables, final long requestID) {
            this.deliverables = Collections.unmodifiableList(deliverables);
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        @Nonnull
        public Collection<Packet> getPackets() {
            final List<Packet> packets = new ArrayList<>();
            for (final Deliverable deliverable : deliverables) {
                // TODO this ignores the 'text' element in deliverable. Is that something that we need to worry about? It is generally used to transmit stream-level data, which might not be applicable here.
                final List<Packet> toDeliver = deliverable.getPackets();
                if (toDeliver != null) {
                    packets.addAll(toDeliver);
                }
            }
            return packets;
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
            ", isEncrypted=" + isEncrypted() +
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
            '}';
    }
}
