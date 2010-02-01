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

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.IQ.Type;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ClientConnectionHandler extends ConnectionHandler {

	private static final Logger Log = LoggerFactory.getLogger(ClientConnectionHandler.class);

    public ClientConnectionHandler(String serverName) {
        super(serverName);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, XMPPServer.getInstance().getPacketDeliverer());
    }

    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new ClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.client.idle", 6 * 60 * 1000) / 1000;
    }

	/**
	 * In addition to the functionality provided by the parent class, this
	 * method will send XMPP ping requests to the remote entity on every first
	 * invocation of this method (which will occur after a period of half the
	 * allowed connection idle time has passed, without any IO).
	 * 
	 * XMPP entities must respond with either an IQ result or an IQ error
	 * (feature-unavailable) stanza upon receiving the XMPP ping stanza. Both
	 * responses will be received by Openfire and will cause the connection idle
	 * count to be reset.
	 * 
	 * Entities that do not respond to the IQ Ping stanzas can be considered
	 * dead, and their connection will be closed by the parent class
	 * implementation on the second invocation of this method.
	 * 
	 * Note that whitespace pings that are sent by XMPP entities will also cause
	 * the connection idle count to be reset.
	 * 
	 * @see ConnectionHandler#sessionIdle(IoSession, IdleStatus)
	 */
    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
    	super.sessionIdle(session, status);
    	
    	final boolean doPing = JiveGlobals.getBooleanProperty("xmpp.client.idle.ping", true);
        if (doPing && session.getIdleCount(status) == 1) {
            final ClientStanzaHandler handler = (ClientStanzaHandler) session.getAttribute(HANDLER);
            final JID entity = handler.getAddress();
            
            if (entity != null) {
		    	// Ping the connection to see if it is alive.
				final IQ pingRequest = new IQ(Type.get);
				pingRequest.setChildElement("ping",
						IQPingHandler.NAMESPACE);
				pingRequest.setFrom(serverName);
				pingRequest.setTo(entity); 
				
	            // Get the connection for this session
	            final Connection connection = (Connection) session.getAttribute(CONNECTION);

		        if (Log.isDebugEnabled()) {
		            Log.debug("ConnectionHandler: Pinging connection that has been idle: " + connection);
		        }

		        connection.deliver(pingRequest);
            }
        }
    }
}
