/*
 * Copyright (C) 2015 Tom Evans. All rights reserved.
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

import java.io.IOException;
import java.text.MessageFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Servlet enables XMPP over WebSocket (RFC 7395) for Openfire.
 * 
 * The Jetty WebSocketServlet serves as a base class and enables easy integration into the
 * BOSH (http-bind) web context. Each WebSocket request received at the "/ws/" URI will be
 * forwarded to this plugin/servlet, which will in turn create a new {@link XmppWebSocket}
 * for each new connection. 
 */
public class OpenfireWebSocketServlet extends WebSocketServlet {

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
            response.setHeader("Access-Control-Max-Age", String.valueOf(HttpBindManager.HTTP_BIND_CORS_MAX_AGE.getValue().getSeconds())); // TODO replace with 'toSeconds()' after dropping support for Java 8
        }
        super.service(request, response);
    }

    @Override
    public void configure(WebSocketServletFactory factory)
    {
        if (XmppWebSocket.isCompressionEnabled()) {
            factory.getExtensionFactory().register("permessage-deflate", PerMessageDeflateExtension.class);
        }
        final int messageSize = JiveGlobals.getIntProperty("xmpp.parser.buffer.size", 1048576);
        factory.getPolicy().setMaxTextMessageBufferSize(messageSize * 5);
        factory.getPolicy().setMaxTextMessageSize(messageSize);
        factory.setCreator((req, resp) -> {
            try {
                for (String subprotocol : req.getSubProtocols())
                {
                    if ("xmpp".equals(subprotocol))
                    {
                        resp.setAcceptedSubProtocol(subprotocol);
                        return new XmppWebSocket();
                    }
                }
            } catch (Exception e) {
                Log.warn("Unable to load websocket factory", e);
            }
            Log.warn("Failed to create websocket for {}:{} make a request at {}", req.getRemoteAddress(), req.getRemotePort(), req.getRequestPath() );
            return null;
        });
    }
}
