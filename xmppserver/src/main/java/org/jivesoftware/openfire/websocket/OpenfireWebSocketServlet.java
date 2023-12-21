/*
 * Copyright (C) 2015 Tom Evans, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * This Servlet enables XMPP over WebSocket (RFC 7395) for Openfire.
 * 
 * The Jetty WebSocketServlet serves as a base class and enables easy integration into the
 * BOSH (http-bind) web context. Each WebSocket request received at the "/ws/" URI will be
 * forwarded to this plugin/servlet, which will in turn create a new {@link WebSocketClientConnectionHandler}
 * for each new connection. 
 */
public class OpenfireWebSocketServlet extends JettyWebSocketServlet {

    private static final long serialVersionUID = 1074354600476010708L;
    private static final Logger Log = LoggerFactory.getLogger(OpenfireWebSocketServlet.class);

    @Override
    public void destroy()
    {
        // terminate any active websocket sessions
        SessionManager sm = XMPPServer.getInstance().getSessionManager();
        for (ClientSession session : sm.getSessions()) {
            if (session instanceof LocalSession) {
                Object ws = ((LocalSession) session).getSessionData("ws");
                if (ws != null && (Boolean) ws) {
                    Log.debug( "Closing session as websocket servlet is being destroyed: {}", session );
                    session.close();
                }
            }
        }

        super.destroy();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        // add CORS headers for all HTTP responses (errors, etc.)
        if (HttpBindManager.HTTP_BIND_CORS_ENABLED.getValue())
        {
            final HttpBindManager boshManager = HttpBindManager.getInstance();
            if (boshManager.isAllOriginsAllowed()) {
                // Set the Access-Control-Allow-Origin header to * to allow all Origin to do the CORS
                response.setHeader("Access-Control-Allow-Origin", HttpBindManager.HTTP_BIND_CORS_ALLOW_ORIGIN_ALL);
            } else {
                // Get the Origin header from the request and check if it is in the allowed Origin Map.
                // If it is allowed write it back to the Access-Control-Allow-Origin header of the respond.
                final String origin = request.getHeader("Origin");
                if (boshManager.isThisOriginAllowed(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                }
            }
            response.setHeader("Access-Control-Allow-Methods", String.join(",", HttpBindManager.HTTP_BIND_CORS_ALLOW_METHODS.getValue()));
            response.setHeader("Access-Control-Allow-Headers", String.join(",", HttpBindManager.HTTP_BIND_CORS_ALLOW_HEADERS.getValue()));
            response.setHeader("Access-Control-Max-Age", String.valueOf(HttpBindManager.HTTP_BIND_CORS_MAX_AGE.getValue().toSeconds()));
        }
        super.service(request, response);
    }

    @Override
    public void configure(JettyWebSocketServletFactory factory)
    {
        if (!WebSocketClientConnectionHandler.isCompressionEnabled()) {
            factory.getAvailableExtensionNames().remove("permessage-deflate");
        }
        final int messageSize = JiveGlobals.getIntProperty("xmpp.parser.buffer.size", 1048576);
        factory.setInputBufferSize(messageSize * 5);
        factory.setOutputBufferSize(messageSize * 5);
        factory.setMaxTextMessageSize(messageSize);

        // Jetty's idle policy cannot be modified - it will bluntly kill the connection. Ensure that it's longer than
        // the maximum amount of idle-time that Openfire allows for its client connections!
        final Duration propValue = ConnectionSettings.Client.IDLE_TIMEOUT_PROPERTY.getValue();
        final long maxJettyIdleMs;
        if (propValue.isNegative() || propValue.isZero()) {
            maxJettyIdleMs = Long.MAX_VALUE;
        } else {
            maxJettyIdleMs = propValue.plus(Duration.of(30, ChronoUnit.SECONDS)).toMillis();
        }
        factory.setIdleTimeout(Duration.ofMillis(maxJettyIdleMs));

        factory.setCreator((req, resp) -> {
            try {
                for (String subprotocol : req.getSubProtocols())
                {
                    if ("xmpp".equals(subprotocol))
                    {
                        resp.setAcceptedSubProtocol(subprotocol);
                        return new WebSocketClientConnectionHandler();
                    }
                }
            } catch (Exception e) {
                Log.warn("Unable to load websocket factory", e);
            }
            Log.warn("Failed to create websocket for {}:{} make a request at {}", req.getHttpServletRequest().getRemoteAddr(), req.getHttpServletRequest().getRemotePort(), req.getRequestPath() );
            return null;
        });
    }
}
