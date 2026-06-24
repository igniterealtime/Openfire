/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
                sendBindError(session, packet, PacketError.Condition.jid_malformed);
                return null;
            }
        }
        // Get the token that was generated during the SASL authentication
        AuthToken authToken = session.getAuthToken();
        if (authToken == null) {
            // User must be authenticated before binding a resource
            sendBindError(session, packet, PacketError.Condition.not_authorized);
            return null;
        }
        if (authToken.isAnonymous()) {
            // ANONYMOUS SASL: resource is server-generated and unique, so there is no conflict to resolve. Initialize the anonymous login and complete the bind synchronously.
            session.setAnonymousAuth();
            sendBindSuccess(session, packet);
            return null;
        }

        final String username = authToken.getUsername().toLowerCase();
        final JID desiredJid = new JID(username, serverName, resource, true);

        // OF-3044: If an existing route is present but its session is already closed, it is a stale / detached session
        // that must be cleaned up cluster-wide before the new session can take over. This is done here (NOT inside
        // SessionManager.bindResource) on purpose: the removeDetached task re-enters the bare-JID client route lock on
        // another thread, and bindResource holds that lock - doing this there would deadlock. This (closed) case is
        // mutually exclusive with the live-conflict case bindResource handles.
        final ClientSession oldSession = routingTable.getClientRoute(desiredJid);
        if (oldSession != null && oldSession.isClosed()) {
            Log.debug("Instructing all cluster nodes to remove any detached session for '{}' as a new session is binding to that resource.", desiredJid);
            CacheFactory.doSynchronousClusterTask(new ClientSessionTask(desiredJid, RemoteSessionTask.Operation.removeDetached), true);
            // The conflicting route was closed/detached; no live conflict remains. Bind directly.
            session.setAuthToken(authToken, resource);
            sendBindSuccess(session, packet);
            return null;
        }

        // Resolve any live conflict and install the route atomically, off this worker thread to prevent thread starvation (OF-3319).
        sessionManager.bindResource(session, authToken, resource)
            .whenComplete((result, throwable) -> {
                try {
                    if (throwable != null)
                    {
                        Log.error("Unexpected error during resource-binding conflict resolution for '{}'", desiredJid, throwable);
                        sendBindError(session, packet, PacketError.Condition.internal_server_error);
                    }
                    else if (result == SessionManager.BindResult.BOUND)
                    {
                        Log.debug("Successful resource bind for '{}'.", desiredJid);
                        sendBindSuccess(session, packet);
                    }
                    else
                    {
                        Log.debug("Rejecting resource bind for '{}' with 'conflict'.", desiredJid);
                        sendBindError(session, packet, PacketError.Condition.conflict);
                    }
                } catch (final Exception e) {
                    Log.error("Failed to deliver resource-bind response for '{}'", desiredJid, e);
                }
            });
        return null; // Response is sent asynchronously from the completion stage above.
    }

    /**
     * Sends a successful resource-binding result to the session and dispatches the resource_bound event.
     */
    private void sendBindSuccess(final LocalClientSession session, final IQ request)
    {
        final IQ reply = IQ.createResultIQ(request);
        reply.setFrom((String) null); // OF-2001
        final Element child = reply.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind");
        child.addElement("jid").setText(session.getAddress().toString());

        // Send the response directly since a route does not exist at this point.
        session.process(reply);

        // After the client has been informed, inform all listeners as well.
        SessionEventDispatcher.dispatchEvent(session, SessionEventDispatcher.EventType.resource_bound);
    }

    /**
     * Sends a resource-binding error to the session.
     */
    private void sendBindError(final LocalClientSession session, final IQ request, final PacketError.Condition condition)
    {
        final IQ reply = IQ.createResultIQ(request);
        reply.setFrom((String) null); // OF-2001
        reply.setChildElement(request.getChildElement().createCopy());
        reply.setError(condition);

        // Sent directly on the session, as a route does not exist for a not-yet-bound session.
        session.process(reply);
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
