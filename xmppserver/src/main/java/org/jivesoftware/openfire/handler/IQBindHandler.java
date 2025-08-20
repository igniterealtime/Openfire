/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ClientSessionTask;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.RemoteSessionTask;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

/**
 * Binds a resource to the stream so that the client's address becomes a full JID. Once a resource
 * has been bound to the session the entity (i.e. client) is considered a "connected resource".
 * <p>
 * Clients may specify a desired resource but if none was specified then the server will create
 * a random resource for the session. The new resource should be in accordance with ResourcePrep.
 * The server will also verify if there are previous sessions from the same user that are already
 * using the resource specified by the user. Depending on the server configuration the old session
 * may be kicked or the new session may be rejected.</p>
 *
 * @author Gaston Dombiak
 */
public class IQBindHandler extends IQHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQBindHandler.class);

    private IQHandlerInfo info;
    private String serverName;
    private RoutingTable routingTable;

    public IQBindHandler() {
        super("Resource Binding handler");
        info = new IQHandlerInfo("bind", "urn:ietf:params:xml:ns:xmpp-bind");
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        LocalClientSession session = (LocalClientSession) sessionManager.getSession(packet.getFrom());
        // If no session was found then answer an error (if possible)
        if (session == null) {
            Log.warn("Error during resource binding. No session found for '{}'", packet.getFrom());
            // This error packet probably won't make it through
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }

        IQ reply = IQ.createResultIQ(packet);
        reply.setFrom((String)null); // OF-2001: IQ bind requests are made from entities that have no resource yet. Responding with a 'from' value confuses many clients.
        Element child = reply.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind");
        // Check if the client specified a desired resource
        String resource = packet.getChildElement().elementTextTrim("resource");
        if (resource == null || resource.isEmpty()) {
            // None was defined so use the random generated resource
            resource = session.getAddress().getResource();
        }
        else {
            // Check that the desired resource is valid
            try {
                resource = JID.resourceprep(resource);
            }
            catch (IllegalArgumentException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.jid_malformed);
                // Send the error directly since a route does not exist at this point.
                session.process(reply);
                return null;
            }
        }
        // Get the token that was generated during the SASL authentication
        AuthToken authToken = session.getAuthToken();
        if (authToken == null) {
            // User must be authenticated before binding a resource
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_authorized);
            // Send the error directly since a route does not exist at this point.
            session.process(reply);
            return reply;
        }
        if (authToken.isAnonymous()) {
            // User used ANONYMOUS SASL so initialize the session as an anonymous login
            session.setAnonymousAuth();
        }
        else {
            String username = authToken.getUsername().toLowerCase();
            // If a session already exists with the requested JID, then check to see
            // if we should kick it off or refuse the new connection
            final JID desiredJid = new JID(username, serverName, resource, true);
            ClientSession oldSession = routingTable.getClientRoute(desiredJid);
            if (oldSession != null) {
                try {
                    if (oldSession.isClosed()) {
                        // If there's an old session that's already closed, then this could be a detached session. The
                        // new session does not conflict with the old one, but the old one needs to be cleaned up to
                        // prevent data consistency issues (OF-3044).
                        Log.debug("Instructing all cluster nodes to remove any detached session for '{}' as a new session is binding to that resource.", desiredJid);
                        CacheFactory.doSynchronousClusterTask(new ClientSessionTask(desiredJid, RemoteSessionTask.Operation.removeDetached), true);
                    }
                    else
                    {
                        Log.debug("Found a pre-existing, non-closed session for '{}'. Performing resource conflict resolution.", desiredJid);
                        int conflictLimit = sessionManager.getConflictKickLimit();
                        if (conflictLimit == SessionManager.NEVER_KICK) {
                            Log.debug("Conflict resolution configuration is 'NEVER KICK'. Rejecting the bind request with error condition 'conflict'.");
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(PacketError.Condition.conflict);
                            // Send the error directly since a route does not exist at this point.
                            session.process(reply);
                            return null;
                        }

                        int conflictCount = oldSession.incrementConflictCount();
                        if (conflictCount > conflictLimit) {
                            Log.debug("Kick out an old connection that is conflicting with a new one. Old session: {}", oldSession);
                            StreamError error = new StreamError(StreamError.Condition.conflict);
                            oldSession.deliverRawText(error.toXML());
                            oldSession.markNonResumable();

                            // OF-1923: As the session is now replaced, the old session will never be resumed.
                            if (oldSession instanceof LocalClientSession) {
                                // As the new session has already replaced the old session, we're not explicitly closing
                                // the old session again, as that would cause the state of the new session to be affected.
                                sessionManager.removeDetached((LocalClientSession) oldSession);
                            }
                        } else {
                            Log.debug("Conflict resolution configuration does not allow kicking of old session (yet). Conflict count: {}, conflict limit: {}. Rejecting the bind request with error condition 'conflict'.", conflictCount, conflictLimit);
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(PacketError.Condition.conflict);
                            // Send the error directly since a route does not exist at this point.
                            session.process(reply);
                            return null;
                        }
                    }
                }
                catch (Exception e) {
                    Log.error("Error during login", e);
                }
            }
            // If the connection was not refused due to conflict, log the user in
            session.setAuthToken(authToken, resource);
        }

        child.addElement("jid").setText(session.getAddress().toString());
        // Send the response directly since a route does not exist at this point.
        session.process(reply);
        // After the client has been informed, inform all listeners as well.
        SessionEventDispatcher.dispatchEvent(session, SessionEventDispatcher.EventType.resource_bound);
        return null;
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
        serverName = server.getServerInfo().getXMPPDomain();
     }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }
}
