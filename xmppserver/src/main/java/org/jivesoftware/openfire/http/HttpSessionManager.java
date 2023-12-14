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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegate;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegateMBean;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.ObjectName;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages sessions for all users connecting to Openfire using the HTTP binding protocol,
 * <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 */
public class HttpSessionManager {
    
    private static final Logger Log = LoggerFactory.getLogger(HttpSessionManager.class);

    /**
     * Object name used to register delegate MBean (JMX) for the 'httpbind-worker' thread pool executor.
     */
    private ObjectName workerThreadPoolObjectName;

    private SessionManager sessionManager;
    private final Map<String, HttpSession> sessionMap = new ConcurrentHashMap<>();
    private TimerTask inactivityTask;
    private ThreadPoolExecutor stanzaWorkerPool;
    private final SessionListener sessionListener = new SessionListener() {
        @Override
        public void sessionClosed(HttpSession session) {
            sessionMap.remove(session.getStreamID().getID());
        }
    };

    /**
     * Creates a new HttpSessionManager instance.
     */
    public HttpSessionManager() {
    }

    /**
     * Starts the services used by the HttpSessionManager.
     *
     * (Re)creates and configures a pooled executor to handle async routing for stanzas with a configurable
     * (through property "xmpp.httpbind.worker.threads") amount of threads; also uses an unbounded task queue and
     * configurable ("xmpp.httpbind.worker.timeout") keep-alive.
     *
     * Note: Apart from the processing threads configured in this class, the server also uses a thread pool to perform
     * the network IO (as configured in ({@link HttpBindManager#HTTP_BIND_THREADS}). BOSH installations expecting heavy
     * loads may want to allocate additional threads to this worker pool to ensure timely delivery of inbound packets
     */
    public void start() {
        Log.info( "Starting instance" );

        this.sessionManager = SessionManager.getInstance();

        stanzaWorkerPool = new ThreadPoolExecutor(MIN_POOL_SIZE.getValue(), MAX_POOL_SIZE.getValue(), POOL_KEEP_ALIVE.getValue().toSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), // unbounded task queue
                new NamedThreadFactory( "httpbind-worker-", true, null, Thread.currentThread().getThreadGroup(), null )
        );

        if (JMXManager.isEnabled()) {
            final ThreadPoolExecutorDelegateMBean mBean = new ThreadPoolExecutorDelegate(stanzaWorkerPool);
            workerThreadPoolObjectName = JMXManager.tryRegister(mBean, ThreadPoolExecutorDelegateMBean.BASE_OBJECT_NAME + "bosh");
        }
        stanzaWorkerPool.prestartCoreThread();

        // Periodically check for Sessions that need a cleanup.
        inactivityTask = new HttpSessionReaper();
        TaskEngine.getInstance().schedule( inactivityTask, Duration.ofSeconds(30), SESSION_CLEANUP_INTERVAL.getValue() );
    }

    /**`
     * Maximum number of threads used to process incoming BOSH data. Defaults to the same amount of threads as what's
     * used for non-BOSH/TCP-connected XMPP clients.
     */
    public static SystemProperty<Integer> MAX_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.httpbind.worker.threads")
        .setDefaultValue( JiveGlobals.getIntProperty(ConnectionSettings.Client.MAX_THREADS, 8) )
        .setDynamic(false)
        .build();

    /**
     * Minimum amount of threads used to process incoming BOSH data.
     */
    public static final SystemProperty<Integer> MIN_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.httpbind.worker.threads-min")
        .setDynamic(false)
        .setDefaultValue((MAX_POOL_SIZE.getDefaultValue()/4)+1)
        .setMinValue(1)
        .build();

    /**
     * Duration that unused, surplus threads that once processed BOSH data are kept alive.
     */
    public static SystemProperty<Duration> POOL_KEEP_ALIVE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.worker.timeout")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofSeconds(60))
        .setDynamic(false)
        .build();

    /**
     * Interval in which a check is executed that will cleanup unused/inactive BOSH sessions.
     */
    public static SystemProperty<Duration> SESSION_CLEANUP_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.worker.cleanupcheck")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofSeconds(30))
        .setMinValue(Duration.ofSeconds(1))
        .setDynamic(false)
        .build();

    /**
     * Stops any services and cleans up any resources used by the HttpSessionManager.
     */
    public void stop() {
        Log.info( "Stopping instance" );
        if (workerThreadPoolObjectName != null) {
            JMXManager.getInstance().tryUnregister(workerThreadPoolObjectName);
            workerThreadPoolObjectName = null;
        }
        inactivityTask.cancel();
        for (HttpSession session : sessionMap.values()) {
            Log.debug( "Closing as session manager instance is being stopped: {}", session );
            session.close();
        }
        sessionMap.clear();
        stanzaWorkerPool.shutdown();
    }

    /**
     * Returns the session related to a stream id.
     *
     * @param streamID the stream id to retrieve the session.
     * @return the session related to the provided stream id.
     */
    public HttpSession getSession(String streamID) {
        return sessionMap.get(streamID);
    }

    /**
     * Creates an HTTP binding session which will allow a user to exchange packets with Openfire.
     *
     * @param body the body element that was sent containing the request for a new session.
     * @param connection the HTTP connection object which abstracts the individual connections to
     * Openfire over the HTTP binding protocol. The initial session creation response is returned to
     * this connection.
     * @return the created HTTP session.
     *
     * @throws UnauthorizedException if the Openfire server is currently in an uninitialized state.
     * Either shutting down or starting up.
     * @throws HttpBindException when there is an internal server error related to the creation of
     * the initial session creation response.
     * @throws UnknownHostException if no IP address for the peer could be found
     */
    public HttpSession createSession(HttpBindBody body, HttpConnection connection)
        throws UnauthorizedException, HttpBindException, UnknownHostException
    {
        // TODO Check if IP address is allowed to connect to the server
        final Duration wait = body.getWait().compareTo(MAX_WAIT.getValue()) > 0 ? MAX_WAIT.getValue() : body.getWait();
        final Duration defaultInactivityTimeout;
        if (wait.isZero() || body.getHold() == 0) {
            // Session will be polling.
            defaultInactivityTimeout = POLLING_INACTIVITY_TIMEOUT.getValue();
        } else {
            defaultInactivityTimeout = INACTIVITY_TIMEOUT.getValue();
        }
        HttpSession session = createSession(connection, Locale.forLanguageTag(body.getLanguage()), wait, body.getHold(),
            connection.isEncrypted(), POLLING_INTERVAL.getValue(), MAX_REQUESTS.getValue(), MAX_PAUSE.getValue(),
            defaultInactivityTimeout, body.getMajorVersion(), body.getMinorVersion());

        session.resetInactivityTimeout();

        connection.setSession(session);
        try {
            connection.deliverBody(createSessionCreationResponse(session), true);
        }
        catch (HttpConnectionClosedException | DocumentException | IOException e) {
            Log.error("Error creating session.", e);
            throw new HttpBindException("Internal server error", BoshBindingError.internalServerError);
        }
        return session;
    }

    /**
     * The maximum length of a temporary session pause that a BOSH client MAY request.
     */
    public static SystemProperty<Duration> MAX_PAUSE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.client.maxpause")
        .setDefaultValue(Duration.ofSeconds(300))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .setMinValue(Duration.ZERO)
        .build();

    /**
     * Returns the longest time that Openfire is allowed to wait before responding to
     * any request during the session. This enables the client to prevent its TCP connection from
     * expiring due to inactivity, as well as to limit the delay before it discovers any network
     * failure.
     */
    public static SystemProperty<Duration> MAX_WAIT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.client.requests.wait")
        .setDefaultValue(Duration.ofSeconds(Integer.MAX_VALUE))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .setMinValue(Duration.ZERO)
        .build();

    /**
     * Openfire SHOULD include two additional attributes in the session creation response element,
     * specifying the shortest allowable polling interval and the longest allowable inactivity
     * period (both in seconds). Communication of these parameters enables the client to engage in
     * appropriate behavior (e.g., not sending empty request elements more often than desired, and
     * ensuring that the periods with no requests pending are never too long).
     */
    public static SystemProperty<Duration> POLLING_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.client.requests.polling")
        .setDefaultValue(Duration.ofSeconds(5))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .setMinValue(Duration.ZERO)
        .build();

    /**
     * Openfire MAY limit the number of simultaneous requests the client makes with the 'requests'
     * attribute. The RECOMMENDED value is "2". Servers that only support polling behavior MUST
     * prevent clients from making simultaneous requests by setting the 'requests' attribute to a
     * value of "1" (however, polling is NOT RECOMMENDED). In any case, clients MUST NOT make more
     * simultaneous requests than specified by the Openfire.
     */
    public static SystemProperty<Integer> MAX_REQUESTS = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.httpbind.client.requests.max")
        .setDefaultValue(2)
        .setDynamic(true)
        .build();

    /**
     * Period of time a session has to be idle to be closed. Default is 30 seconds. Sending stanzas to the
     * client is not considered as activity. We are only considering the connection active when the
     * client sends some data or heartbeats (i.e. whitespaces) to the server. The reason for this is
     * that sending data will fail if the connection is closed. And if the thread is blocked while
     * sending data (because the socket is closed) then the clean up thread will close the socket
     * anyway.
     */
    public static SystemProperty<Duration> INACTIVITY_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.client.idle")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .setMinValue(Duration.ZERO)
        .build();

    /**
     * Period of time a polling session has to be idle to be closed. Default is 60 seconds. Sending stanzas to the
     * client is not considered as activity. We are only considering the connection active when the
     * client sends some data or heartbeats (i.e. whitespaces) to the server. The reason for this is
     * that sending data will fail if the connection is closed. And if the thread is blocked while
     * sending data (because the socket is closed) then the clean up thread will close the socket
     * anyway.
     */
    public static SystemProperty<Duration> POLLING_INACTIVITY_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.httpbind.client.idle.polling")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .setMinValue(Duration.ZERO)
        .build();

    private HttpSession createSession(HttpConnection connection, Locale language, Duration wait,
                                      int hold, boolean isEncrypted, Duration maxPollingInterval,
                                      int maxRequests, Duration maxPause, Duration defaultInactivityTimeout,
                                      int majorVersion, int minorVersion) throws UnauthorizedException, UnknownHostException
    {
        // Create a ClientSession for this user.
        StreamID streamID = SessionManager.getInstance().nextStreamID();
        // Send to the server that a new client session has been created
        HttpSession session = sessionManager.createClientHttpSession(streamID, connection, language, wait, hold, isEncrypted,
                                                                     maxPollingInterval, maxRequests, maxPause,
                                                                     defaultInactivityTimeout, majorVersion, minorVersion);
        // Register that the new session is associated with the specified stream ID
        sessionMap.put(streamID.getID(), session);
        SessionEventDispatcher.addListener( sessionListener );
        return session;
    }

    private static String createSessionCreationResponse(HttpSession session) throws DocumentException {
        Element response = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        response.addNamespace("stream", "http://etherx.jabber.org/streams");
        response.addAttribute("from", session.getServerName());
        response.addAttribute("authid", session.getStreamID().getID());
        response.addAttribute("sid", session.getStreamID().getID());
        response.addAttribute("secure", Boolean.TRUE.toString());
        response.addAttribute("requests", String.valueOf(session.getMaxRequests()));
        response.addAttribute("inactivity", String.valueOf(session.getInactivityTimeout().toSeconds()));
        response.addAttribute("polling", String.valueOf(session.getMaxPollingInterval().toSeconds()));
        response.addAttribute("wait", String.valueOf(session.getWait().getSeconds()));
        if ((session.getMajorVersion() == 1 && session.getMinorVersion() >= 6) ||
            session.getMajorVersion() > 1) {
            response.addAttribute("hold", String.valueOf(session.getHold()));
            response.addAttribute("ack", String.valueOf(session.getLastAcknowledged()));
            response.addAttribute("maxpause", String.valueOf(session.getMaxPause().toSeconds()));
            response.addAttribute("ver", session.getMajorVersion() + "." + session.getMinorVersion());
        }

        Element features = response.addElement("stream:features");
        for (Element feature : session.getAvailableStreamFeatures()) {
            features.add(feature);
        }

        return response.asXML();
    }

    private class HttpSessionReaper extends TimerTask {

        @Override
        public void run() {
            Instant currentTime = Instant.now();
            for (HttpSession session : sessionMap.values()) {
                try {
                    Duration lastActive = Duration.between(session.getLastActivity(), currentTime);
                    String hostAddress = session.getConnection() != null ? session.getConnection().getHostAddress() : "(not available)";
                    if( !lastActive.isNegative() && !lastActive.isZero() && HttpBindManager.LOG_HTTPBIND_ENABLED.getValue()) {
                        Log.info("Session {} was last active {} ago: {} from IP {} " +
                                " currently on rid {}",
                                session.getStreamID(),
                                lastActive,
                                session.getAddress(), // JID
                                hostAddress,
                                session.getLastAcknowledged()); // RID
                    }
                    if (lastActive.compareTo(session.getInactivityTimeout()) > 0) {
                        Log.info("Closing idle session {}: {} from IP {}",
                                session.getStreamID(),
                                session.getAddress(),
                                hostAddress);
                        session.close();
                    }
                } catch (Exception e) {
                    Log.error("Failed to determine idle state for session: {}", session, e);
                }
            }
        }
    }

    /**
     * Executes a Runnable in the thread pool that is used for processing stanzas received over BOSH.
     *
     * @param runnable The task to run
     */
    public void execute(@Nonnull final Runnable runnable) {
        this.stanzaWorkerPool.execute(runnable);
    }
}
