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

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages sessions for all users connecting to Openfire using the HTTP binding protocal,
 * <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 */
public class HttpSessionManager {
	
	private static final Logger Log = LoggerFactory.getLogger(HttpSessionManager.class);

    private SessionManager sessionManager;
    private Map<String, HttpSession> sessionMap = new ConcurrentHashMap<String, HttpSession>();
    private TimerTask inactivityTask;
    private Executor sendPacketPool;
    private SessionListener sessionListener = new SessionListener() {
        public void connectionOpened(HttpSession session, HttpConnection connection) {
        }

        public void connectionClosed(HttpSession session, HttpConnection connection) {
        }

        public void sessionClosed(HttpSession session) {
            sessionMap.remove(session.getStreamID().getID());
        }
    };

    /**
     * Creates a new HttpSessionManager instance.
     */
    public HttpSessionManager() {
        this.sessionManager = SessionManager.getInstance();

        // Set the executor to use for processing http requests
        int eventThreads = JiveGlobals.getIntProperty("xmpp.client.processing.threads", 16);
        sendPacketPool = new ThreadPoolExecutor(
            eventThreads + 1, eventThreads + 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>() );
    }

    /**
     * Starts the services used by the HttpSessionManager.
     */
    public void start() {
        inactivityTask = new HttpSessionReaper();
        TaskEngine.getInstance().schedule(inactivityTask, 30 * JiveConstants.SECOND,
                30 * JiveConstants.SECOND);
    }

    /**
     * Stops any services and cleans up any resources used by the HttpSessionManager.
     */
    public void stop() {
        inactivityTask.cancel();
        for (HttpSession session : sessionMap.values()) {
            session.close();
        }
        sessionMap.clear();
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
     * @param address the internet address that was used to bind to Wildfie.
     * @param rootNode the body element that was sent containing the request for a new session.
     * @param connection the HTTP connection object which abstracts the individual connections to
     * Openfire over the HTTP binding protocol. The initial session creation response is returned to
     * this connection.
     * @return the created HTTP session.
     *
     * @throws UnauthorizedException if the Openfire server is currently in an uninitialized state.
     * Either shutting down or starting up.
     * @throws HttpBindException when there is an internal server error related to the creation of
     * the initial session creation response.
     */
    public HttpSession createSession(InetAddress address, Element rootNode,
                                     HttpConnection connection)
            throws UnauthorizedException, HttpBindException {
        // TODO Check if IP address is allowed to connect to the server

        // Default language is English ("en").
        String language = rootNode.attributeValue("xml:lang");
        if (language == null || "".equals(language)) {
            language = "en";
        }

        int wait = getIntAttribute(rootNode.attributeValue("wait"), 60);
        int hold = getIntAttribute(rootNode.attributeValue("hold"), 1);
        
        String version = rootNode.attributeValue("ver");
        if (version == null || "".equals(version)) {
        	version = "1.5";
        }

        HttpSession session = createSession(connection.getRequestId(), address, connection);
        session.setWait(Math.min(wait, getMaxWait()));
        session.setHold(hold);
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
        
        // Store language and version information in the connection.
        session.setLanguage(language);
        
        String [] versionString = version.split("\\.");
        session.setMajorVersion(Integer.parseInt(versionString[0]));
        session.setMinorVersion(Integer.parseInt(versionString[1]));
        
        try {
            connection.deliverBody(createSessionCreationResponse(session));
        }
        catch (HttpConnectionClosedException e) {
            /* This won't happen here. */
        }
        catch (DocumentException e) {
            Log.error("Error creating document", e);
            throw new HttpBindException("Internal server error",
                    BoshBindingError.internalServerError);
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

    /**
     * Forwards a client request, which is related to a session, to the server. A connection is
     * created and queued up in the provided session. When a connection reaches the top of a queue
     * any pending packets bound for the client will be forwarded to the client through the
     * connection.
     *
     * @param rid the unique, sequential, requestID sent from the client.
     * @param session the HTTP session of the client that made the request.
     * @param isSecure true if the request was made over a secure channel, HTTPS, and false if it
     * was not.
     * @param rootNode the XML body of the request.
     * @return the created HTTP connection.
     *
     * @throws HttpBindException for several reasons: if the encoding inside of an auth packet is
     * not recognized by the server, or if the packet type is not recognized.
     * @throws HttpConnectionClosedException if the session is no longer available.
     */
    public HttpConnection forwardRequest(long rid, HttpSession session, boolean isSecure,
                                         Element rootNode) throws HttpBindException,
            HttpConnectionClosedException
    {
        //noinspection unchecked
        List<Element> elements = rootNode.elements();
    	boolean isPoll = (elements.size() == 0);
    	if ("terminate".equals(rootNode.attributeValue("type")))
    		isPoll = false;
    	else if ("true".equals(rootNode.attributeValue(new QName("restart", rootNode.getNamespaceForPrefix("xmpp")))))
    		isPoll = false;
    	else if (rootNode.attributeValue("pause") != null)
    		isPoll = false;
        HttpConnection connection = session.createConnection(rid, elements, isSecure, isPoll);
        if (elements.size() > 0) {
            // creates the runnable to forward the packets
            new HttpPacketSender(session).init();
        }
        return connection;
    }

    private HttpSession createSession(long rid, InetAddress address, HttpConnection connection) throws UnauthorizedException {
        // Create a ClientSession for this user.
        StreamID streamID = SessionManager.getInstance().nextStreamID();
        // Send to the server that a new client session has been created
        HttpSession session = sessionManager.createClientHttpSession(rid, address, streamID, connection);
        // Register that the new session is associated with the specified stream ID
        sessionMap.put(streamID.getID(), session);
        session.addSessionCloseListener(sessionListener);
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

    private double getDoubleAttribute(String doubleValue, double defaultValue) {
        if (doubleValue == null || "".equals(doubleValue.trim())) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(doubleValue);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private String createSessionCreationResponse(HttpSession session) throws DocumentException {
        Element response = DocumentHelper.createElement("body");
        response.addNamespace("", "http://jabber.org/protocol/httpbind");
        response.addNamespace("stream", "http://etherx.jabber.org/streams");
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
            long currentTime = System.currentTimeMillis();
            for (HttpSession session : sessionMap.values()) {
                long lastActive = currentTime - session.getLastActivity();
                if (lastActive > session.getInactivityTimeout() * JiveConstants.SECOND) {
                    session.close();
                }
            }
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

        public void run() {
            session.sendPendingPackets();
        }

        private void init() {
            sendPacketPool.execute(this);
        }
    }
}
