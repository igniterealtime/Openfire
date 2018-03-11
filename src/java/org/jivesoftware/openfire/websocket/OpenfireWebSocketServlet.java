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

import java.text.MessageFormat;

import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
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

    private static final long serialVersionUID = 7281841492829464605L;
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
    public void configure(WebSocketServletFactory factory)
    {
        if (XmppWebSocket.isCompressionEnabled()) {
            factory.getExtensionFactory().register("permessage-deflate", PerMessageDeflateExtension.class);
        }
        final int messageSize = JiveGlobals.getIntProperty("xmpp.parser.buffer.size", 1048576);
        factory.getPolicy().setMaxTextMessageBufferSize(messageSize * 5);
        factory.getPolicy().setMaxTextMessageSize(messageSize);
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
            {
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
                    Log.warn(MessageFormat.format("Unable to load websocket factory: {0} ({1})", e.getClass().getName(), e.getMessage()));
                }
                Log.warn("Failed to create websocket for {}:{} make a request at {}", req.getRemoteAddress(), req.getRemotePort(), req.getRequestPath() );
                return null;
            }
        });
    }
}
