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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
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

    private SessionManager sessionManager;
    private Map<String, HttpSession> sessionMap = new ConcurrentHashMap<>(
            JiveGlobals.getIntProperty("xmpp.httpbind.session.initial.count", 16));
    private TimerTask inactivityTask;
    private ThreadPoolExecutor sendPacketPool;
    private SessionListener sessionListener = new SessionListener() {
        @Override
        public void sessionClosed(HttpSession session) {
            sessionMap.remove(session.getStreamID().getID());
        }
    };

    /**
     * Creates a new HttpSessionManager instance.
     */
    public HttpSessionManager() {
        
        JiveGlobals.migrateProperty("xmpp.httpbind.worker.threads");
        JiveGlobals.migrateProperty("xmpp.httpbind.worker.timeout");
    }

    /**
     * @deprecated As of Openfire 4.0.0, the functionality of this method was added to the implementation of #start().
     */
    @Deprecated
    public void init() {}

    private int getCorePoolSize(int maxPoolSize) {
        return (maxPoolSize/4)+1;
    }

    /**
     * Starts the services used by the HttpSessionManager.
     *
     * (Re)creates and configures a pooled executor to handle async routing for incoming packets with a configurable
     * (through property "xmpp.httpbind.worker.threads") amount of threads; also uses an unbounded task queue and
     * configurable ("xmpp.httpbind.worker.timeout") keep-alive.
     *
     * Note: Apart from the processing threads configured in this class, the server also uses a threadpool to perform
     * the network IO (as configured in ({@link HttpBindManager}). BOSH installations expecting heavy loads may want to
     * allocate additional threads to this worker pool to ensure timely delivery of inbound packets
     */
    public void start() {
        Log.info( "Starting instance" );

        this.sessionManager = SessionManager.getInstance();

        final int maxClientPoolSize = JiveGlobals.getIntProperty( "xmpp.client.processing.threads", 8 );
        final int maxPoolSize = JiveGlobals.getIntProperty("xmpp.httpbind.worker.threads", maxClientPoolSize );
        final int keepAlive = JiveGlobals.getIntProperty( "xmpp.httpbind.worker.timeout", 60 );
        final int sessionCleanupCheck = JiveGlobals.getIntProperty("xmpp.httpbind.worker.cleanupcheck", 30);

        sendPacketPool = new ThreadPoolExecutor(getCorePoolSize(maxPoolSize), maxPoolSize, keepAlive, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), // unbounded task queue
                new NamedThreadFactory( "httpbind-worker-", true, null, Thread.currentThread().getThreadGroup(), null )
        );

        sendPacketPool.prestartCoreThread();

        // Periodically check for Sessions that need a cleanup.
        inactivityTask = new HttpSessionReaper();
        TaskEngine.getInstance().schedule( inactivityTask, 30 * JiveConstants.SECOND, sessionCleanupCheck * JiveConstants.SECOND);
    }

    /**
     * Stops any services and cleans up any resources used by the HttpSessionManager.
     */
    public void stop() {
        Log.info( "Stopping instance" );
        inactivityTask.cancel();
        for (HttpSession session : sessionMap.values()) {
            Log.debug( "Closing as session manager instance is being stopped: {}", session );
            session.close();
        }
        sessionMap.clear();
        sendPacketPool.shutdown();
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
        HttpSession session = createSession(connection, Locale.forLanguageTag(body.getLanguage()));
        session.setWait(Math.min(body.getWait(), getMaxWait()));
        session.setHold(body.getHold());
        session.setSecure(connection.isSecure());
        session.setMaxPollingInterval(getPollingInterval());
        session.setMaxRequests(getMaxRequests());
        session.setMaxPause(getMaxPause());
        
        if(session.isPollingSession()) {
            session.setDefaultInactivityTimeout(getPollingInactivityTimeout());
        }
        else {
            session.setDefaultInactivityTimeout(getInactivityTimeout());
        }
        session.resetInactivityTimeout();
        
        session.setMajorVersion(body.getMajorVersion());
        session.setMinorVersion(body.getMinorVersion());

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
     * Returns the maximum length of a temporary session pause (in seconds) that the client MAY 
     * request.
     *
     * @return the maximum length of a temporary session pause (in seconds) that the client MAY 
     *         request.
     */
    public int getMaxPause() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.maxpause", 300);
    }

    /**
     * Returns the longest time (in seconds) that Openfire is allowed to wait before responding to
     * any request during the session. This enables the client to prevent its TCP connection from
     * expiring due to inactivity, as well as to limit the delay before it discovers any network
     * failure.
     *
     * @return the longest time (in seconds) that Openfire is allowed to wait before responding to
     *         any request during the session.
     */
    public int getMaxWait() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.requests.wait",
                Integer.MAX_VALUE);
    }

    /**
     * Openfire SHOULD include two additional attributes in the session creation response element,
     * specifying the shortest allowable polling interval and the longest allowable inactivity
     * period (both in seconds). Communication of these parameters enables the client to engage in
     * appropriate behavior (e.g., not sending empty request elements more often than desired, and
     * ensuring that the periods with no requests pending are never too long).
     *
     * @return the maximum allowable period over which a client can send empty requests to the
     *         server.
     */
    public int getPollingInterval() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.requests.polling", 5);
    }

    /**
     * Openfire MAY limit the number of simultaneous requests the client makes with the 'requests'
     * attribute. The RECOMMENDED value is "2". Servers that only support polling behavior MUST
     * prevent clients from making simultaneous requests by setting the 'requests' attribute to a
     * value of "1" (however, polling is NOT RECOMMENDED). In any case, clients MUST NOT make more
     * simultaneous requests than specified by the Openfire.
     *
     * @return the number of simultaneous requests allowable.
     */
    public int getMaxRequests() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.requests.max", 2);
    }

    /**
     * Seconds a session has to be idle to be closed. Default is 30. Sending stanzas to the
     * client is not considered as activity. We are only considering the connection active when the
     * client sends some data or hearbeats (i.e. whitespaces) to the server. The reason for this is
     * that sending data will fail if the connection is closed. And if the thread is blocked while
     * sending data (because the socket is closed) then the clean up thread will close the socket
     * anyway.
     *
     * @return Seconds a session has to be idle to be closed.
     */
    public int getInactivityTimeout() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.idle", 30);
    }

    /**
     * Seconds a polling session has to be idle to be closed. Default is 60. Sending stanzas to the
     * client is not considered as activity. We are only considering the connection active when the
     * client sends some data or hearbeats (i.e. whitespaces) to the server. The reason for this is
     * that sending data will fail if the connection is closed. And if the thread is blocked while
     * sending data (because the socket is closed) then the clean up thread will close the socket
     * anyway.
     *
     * @return Seconds a polling session has to be idle to be closed.
     */
    public int getPollingInactivityTimeout() {
        return JiveGlobals.getIntProperty("xmpp.httpbind.client.idle.polling", 60);
    }

    private HttpSession createSession(HttpConnection connection, Locale language) throws UnauthorizedException, UnknownHostException
    {
        // Create a ClientSession for this user.
        StreamID streamID = SessionManager.getInstance().nextStreamID();
        // Send to the server that a new client session has been created
        HttpSession session = sessionManager.createClientHttpSession(streamID, connection, language);
        // Register that the new session is associated with the specified stream ID
        sessionMap.put(streamID.getID(), session);
        SessionEventDispatcher.addListener( sessionListener );
        return session;
    }

    private static int getIntAttribute(String value, int defaultValue) {
        if (value == null || "".equals(value.trim())) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String createSessionCreationResponse(HttpSession session) throws DocumentException {
        Element response = DocumentHelper.createElement( QName.get( "body", "http://jabber.org/protocol/httpbind" ) );
        response.addNamespace("stream", "http://etherx.jabber.org/streams");
        response.addAttribute("from", session.getServerName());
        response.addAttribute("authid", session.getStreamID().getID());
        response.addAttribute("sid", session.getStreamID().getID());
        response.addAttribute("secure", Boolean.TRUE.toString());
        response.addAttribute("requests", String.valueOf(session.getMaxRequests()));
        response.addAttribute("inactivity", String.valueOf(session.getInactivityTimeout()));
        response.addAttribute("polling", String.valueOf(session.getMaxPollingInterval()));
        response.addAttribute("wait", String.valueOf(session.getWait()));
        if ((session.getMajorVersion() == 1 && session.getMinorVersion() >= 6) ||
            session.getMajorVersion() > 1) {
            response.addAttribute("hold", String.valueOf(session.getHold()));
            response.addAttribute("ack", String.valueOf(session.getLastAcknowledged()));
            response.addAttribute("maxpause", String.valueOf(session.getMaxPause()));
            response.addAttribute("ver", String.valueOf(session.getMajorVersion())
                    + "." + String.valueOf(session.getMinorVersion()));
        }

        Element features = response.addElement("stream:features");
        for (Element feature : session.getAvailableStreamFeaturesElements()) {
            features.add(feature);
        }

        return response.asXML();
    }

    private class HttpSessionReaper extends TimerTask {

        @Override
        public void run() {
            boolean logHttpbindEnabled = JiveGlobals.getBooleanProperty("log.httpbind.enabled", false);
            long currentTime = System.currentTimeMillis();
            for (HttpSession session : sessionMap.values()) {
                try {
                    long lastActive = currentTime - session.getLastActivity();
                    if( lastActive >= 1 && logHttpbindEnabled && Log.isInfoEnabled()) {
                        Log.info("Session {} was last active {} ms ago: {} from IP {} " +
                                " currently on rid {}",
                                session.getStreamID(),
                                lastActive,
                                session.getAddress(), // JID
                                session.getConnection().getHostAddress(),
                                session.getLastAcknowledged()); // RID
                    }
                    if (lastActive > session.getInactivityTimeout() * JiveConstants.SECOND) {
                        Log.info("Closing idle session {}: {} from IP {}",
                                session.getStreamID(),
                                session.getAddress(),
                                session.getConnection().getHostAddress());
                        session.close();
                    }
                } catch (Exception e) {
                    Log.error("Failed to determine idle state for session: {}", session, e);
                }
            }
        }
    }

    protected void execute(Runnable runnable) {
        this.sendPacketPool.execute(runnable);
    }
}
