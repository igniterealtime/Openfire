/*
 * Copyright (C) 2015 Tom Evans, 2023-2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.dom4j.io.XMPPPacketReader;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.nio.NettyClientConnectionHandler;
import org.jivesoftware.openfire.nio.OfflinePacketDeliverer;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

/**
 * This class handles all WebSocket events for the corresponding connection with a remote peer, analogous to the
 * function of {@link NettyClientConnectionHandler} for TCP connections.
 *
 * Specifically the XMPP session is managed concurrently with the WebSocket session, including all
 * framing and authentication requirements. Packets received from the remote peer are forwarded as
 * needed via a {@link SessionPacketRouter}, and packets destined for the remote peer are forwarded
 * via the corresponding {@link RemoteEndpoint}.
 */
@WebSocket
public class WebSocketClientConnectionHandler
{
    /**
     * Controls if clients that do websockets without the required XMPP framing will get their 'stream' element names
     * replaced, so that they are able to connect.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2479">OF-2479: Allow Tsung to test with websockets</a>
     */
    public static final SystemProperty<Boolean> STREAM_SUBSTITUTION_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.websocket.stream-substitution-enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    public static final SystemProperty<Boolean> KEEP_ALIVE_FRAME_PING_ENABLED_PROPERTY = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.websocket.frame.ping.enabled")
        .setDefaultValue(Boolean.TRUE)
        .setDynamic(Boolean.TRUE)
        .build();

    public static final SystemProperty<Duration> KEEP_ALIVE_FRAME_PING_INTERVAL_PROPERTY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.websocket.frame.ping.interval")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(Boolean.TRUE)
        .build();

    private static final Logger Log = LoggerFactory.getLogger( WebSocketClientConnectionHandler.class );
    private static GenericObjectPool<XMPPPacketReader> readerPool;
    private Session wsSession;
    private WebSocketConnection wsConnection;
    private TimerTask websocketFramePingTask;
    private TimerTask xmppSessionIdleTask;
    private Instant lastReceived = Instant.now();
    private Instant lastWebsocketPing = Instant.now();

    public WebSocketClientConnectionHandler() {
        if (readerPool == null) {
            initializePool();
        }
    }

    // WebSocket event handlers

    @OnWebSocketConnect
    public void onConnect(Session session)
    {
        lastReceived = Instant.now();
        wsSession = session;
        final PacketDeliverer backupDeliverer = NettyClientConnectionHandler.BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? new OfflinePacketDeliverer() : null;
        wsConnection = new WebSocketConnection(this, backupDeliverer, (InetSocketAddress) session.getRemoteAddress()); // TODO can't assume InetSocketAddress
        websocketFramePingTask = new WebsocketFramePingTask();
        if (KEEP_ALIVE_FRAME_PING_ENABLED_PROPERTY.getValue()) {
            // Run the task every 10% of the interval, to get the timing roughly in-line with the configured interval.
            final Duration taskInterval = KEEP_ALIVE_FRAME_PING_INTERVAL_PROPERTY.getValue().dividedBy(10);
            TaskEngine.getInstance().schedule(websocketFramePingTask, taskInterval, taskInterval);
        }

        final Duration maxIdleTime = getMaxIdleTime();
        xmppSessionIdleTask = new XmppSessionIdleTask();
        if (!maxIdleTime.isNegative() && !maxIdleTime.isZero()) {
            TaskEngine.getInstance().schedule(xmppSessionIdleTask, maxIdleTime.dividedBy(10), maxIdleTime.dividedBy(10));
        }

        wsConnection.setStanzaHandler(new WebSocketClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), wsConnection));
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        wsConnection.close(); // or: wsConnection.onVirtualUnexpectedDisconnect(); ??
    }

    @OnWebSocketMessage
    public void onTextMethod(String stanza)
    {
        lastReceived = Instant.now();

        XMPPPacketReader reader = null;
        try {
            reader = readerPool.borrowObject();

            if (STREAM_SUBSTITUTION_ENABLED.getValue()) {
                // Allow clients that do websockets without the required XMPP framing to connect. See https://igniterealtime.atlassian.net/browse/OF-2479
                if (stanza.startsWith("<?xml version='1.0'?><stream:stream ")) {
                    stanza = stanza.replace("<?xml version='1.0'?><stream:stream ", "<open ");
                    stanza = stanza.replace("jabber:client", "urn:ietf:params:xml:ns:xmpp-framing");
                    stanza += "</open>";
                }
                if (stanza.startsWith("</stream:stream>")) {
                    stanza = stanza.replace("</stream:stream>", "<close xmlns='urn:ietf:params:xml:ns:xmpp-framing' />");
                }
            }

            try {
                final StanzaHandler handler = wsConnection.getStanzaHandler();
                handler.process(stanza, reader);
            } catch (Throwable e) { // Make sure to catch Throwable, not (only) Exception! See OF-2367
                Log.error("Closing connection due to error while processing stanza: {}", stanza, e);
                if ( wsConnection != null ) {
                    wsConnection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while processing data raw inbound data."));
                }
            }
        } catch (Exception ex) {
            Log.error("Failed to process XMPP stanza", ex);
        } finally {
            if (reader != null) {
                readerPool.returnObject(reader);
            }
        }
    }

    @OnWebSocketError
    public void onError(Throwable error)
    {
        Log.debug("Error detected; connection: {}, session: {}", wsConnection, wsSession, error);
        synchronized (this) {
            try {
                if (isWebSocketOpen()) {
                    Log.warn("Attempting to close connection on which an error occurred: {}", wsConnection, error);
                    wsConnection.close(new StreamError(StreamError.Condition.internal_server_error), !isWebSocketOpen());
                } else {
                    Log.debug("Error detected on websocket that isn't open (any more):", error);
                    wsConnection.close(null, !isWebSocketOpen());
                }
            } catch (Exception e) {
                Log.error("Error disconnecting websocket", e);
            } finally {
                wsSession = null;
            }
        }
    }

    /**
     * Returns the max duration that a connection can be idle before being closed.
     *
     * @return the max duration that a connection can be idle.
     */
    public Duration getMaxIdleTime() {
        return ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getValue();
    }

    // local (package) visibility

    synchronized boolean isWebSocketOpen() {
        return wsSession != null && wsSession.isOpen();
    }

    @Deprecated // Remove in Openfire 4.9 or later.
    synchronized boolean isWebSocketSecure() {
        return isWebSocketEncrypted();
    }

    synchronized boolean isWebSocketEncrypted() {
        return wsSession != null && wsSession.isSecure();
    }

    synchronized String getTLSProtocolName() {
        return wsSession == null ? null : wsSession.getProtocolVersion();
    }

    synchronized String getCipherSuiteName() {
        return wsSession == null ? null : "unknown";
    }

    static boolean isCompressionEnabled() {
        return JiveGlobals.getProperty(
                ConnectionSettings.Client.COMPRESSION_SETTINGS, Connection.CompressionPolicy.optional.toString())
                .equalsIgnoreCase(Connection.CompressionPolicy.optional.toString());
    }

    private synchronized void initializePool() {
        if (readerPool == null) {
            readerPool = new GenericObjectPool<>(new XMPPPPacketReaderFactory());
            readerPool.setMaxTotal(-1);
            readerPool.setBlockWhenExhausted(false);
            readerPool.setTestOnReturn(true);
            readerPool.setTimeBetweenEvictionRunsMillis(Duration.ofMinutes(1).toMillis());
        }
    }

    /**
     * Returns the Jetty WebSocket session.
     * @return the websocket session.
     */
    Session getWsSession()
    {
        return wsSession;
    }

    //-- Keep-alive ping for idle peers

    /**
     * Task that periodically sends websocket pings, to prevent the websocket transport from being closed.
     */
    private final class WebsocketFramePingTask extends TimerTask
    {
        @Override
        public void run() {
            if (!isWebSocketOpen()) {
                TaskEngine.getInstance().cancelScheduledTask(websocketFramePingTask);
                TaskEngine.getInstance().cancelScheduledTask(xmppSessionIdleTask);
            } else if (KEEP_ALIVE_FRAME_PING_ENABLED_PROPERTY.getValue()) {
                // Send a ping when the line has been inactive for more than 90% of the maximum allowable duration.
                final Duration inactive = Duration.between(lastReceived, Instant.now());
                final Duration sinceLastPing = Duration.between(lastWebsocketPing, Instant.now());
                final Duration shortest = inactive.compareTo(sinceLastPing) > 0 ? sinceLastPing : inactive;
                final Duration maxIdleTime = KEEP_ALIVE_FRAME_PING_INTERVAL_PROPERTY.getValue().dividedBy(10).multipliedBy(9);
                if (shortest.compareTo(maxIdleTime) > 0) {
                    try {
                        Log.trace("Remote peer was inactive for {}. Sending websocket ping to: {}", shortest, wsConnection);
                        // see https://tools.ietf.org/html/rfc6455#section-5.5.2
                        wsSession.getRemote().sendPing(null);
                        lastWebsocketPing = Instant.now();
                    } catch (IOException ioe) {
                        // Log the issue, but no need to act: IdleTask will eventually clean up this websocket.
                        Log.warn("Unable to send websocket ping to remote peer: {}", wsConnection, ioe);
                    }
                }
            }
        }
    }

    /**
     * Task that, on prolonged inactivity, sends an XMPP ping, to ensure that the remote entity is still responsive.
     */
    private final class XmppSessionIdleTask extends TimerTask {
        private Instant pendingPingSentAt = null;

        @Override
        public void run()
        {
            if (!isWebSocketOpen() || getMaxIdleTime().isNegative() || getMaxIdleTime().isZero()) {
                TaskEngine.getInstance().cancelScheduledTask(websocketFramePingTask);
                TaskEngine.getInstance().cancelScheduledTask(xmppSessionIdleTask);
                return;
            }

            // Was there activity since we've pinged, then reset the 'ping' status.
            if (pendingPingSentAt != null && lastReceived.isAfter(pendingPingSentAt)) {
                pendingPingSentAt = null;
            }

            final Duration inactive = Duration.between(lastReceived, Instant.now());
            if (inactive.compareTo(getMaxIdleTime()) > 0) {
                // Inactive session, that didn't respond to a ping that should've been sent earlier. Kill.
                Log.debug("Closing connection that has been idle: {}", wsConnection);
                wsConnection.close(new StreamError(StreamError.Condition.connection_timeout, "Closing connection due to inactivity."));
                return;
            }

            final boolean doPing = ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getValue();
            if (doPing) {
                // Probe the peer once, when it's inactive for 50% of the maximum idle time (this mimics the behavior for TCP connections).
                final boolean alreadyPinged = pendingPingSentAt != null;
                if (!alreadyPinged) {
                    if (inactive.compareTo(getMaxIdleTime().dividedBy(2)) > 0) {
                        // Client has been inactive for more than 50% of the max idle time, and has not been pinged yet.
                        sendPing();
                    }
                }
            }
        }

        private void sendPing()
        {
            // Ping the connection to see if it is alive.
            final JID entity = wsConnection.getStanzaHandler().getAddress();
            final IQ pingRequest = new IQ(IQ.Type.get);
            pingRequest.setChildElement("ping", IQPingHandler.NAMESPACE);
            pingRequest.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
            pingRequest.setTo(entity);

            Log.debug("Pinging websocket connection (XMPP address: '{}') that has been idle: {}", entity, wsConnection);

            // OF-1497: Ensure that data sent to the client is processed through LocalClientSession, to avoid
            // synchronisation issues with stanza counts related to Stream Management (XEP-0198)!
            final LocalClientSession ofSession = (LocalClientSession) SessionManager.getInstance().getSession(entity);
            if (ofSession == null) {
                Log.warn( "Trying to ping a websocket connection (XMPP address: '{}') that's idle, but has no corresponding Openfire session. Websocket connection: {}", entity, wsConnection );
            } else {
                try {
                    ofSession.deliver( pingRequest );
                    pendingPingSentAt = Instant.now();
                } catch (UnauthorizedException e) {
                    Log.warn("An unexpected exception occurred while trying to ping a websocket connection (XMPP address: '{}') that's idle. Websocket connection: {}", entity, wsConnection, e);
                }
            }
        }
    }
}
