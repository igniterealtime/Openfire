/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

/**
 * Binds a resource to the stream so that the client's address becomes a full JID. Once a resource
 * has been binded to the session the entity (i.e. client) is considered a "connected resource".<p>
 * <p/>
 * Clients may specify a desired resource but if none was specified then the server will create
 * a random resource for the session. The new resource should be in accordance with ResourcePrep.
 * The server will also verify if there are previous sessions from the same user that are already
 * using the resource specified by the user. Depending on the server configuration the old session
 * may be kicked or the new session may be rejected.
 *
 * @author Gaston Dombiak
 */
public class IQBindHandler extends IQHandler {

    private IQHandlerInfo info;
    private XMPPServer localServer;

    public IQBindHandler() {
        super("Resource Binding handler");
        info = new IQHandlerInfo("bind", "urn:ietf:params:xml:ns:xmpp-bind");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        ClientSession session = sessionManager.getSession(packet.getFrom());
        // If no session was found then answer an error (if possible)
        if (session == null) {
            Log.error("Error during resource binding. Session not found in " +
                    sessionManager.getPreAuthenticatedKeys() +
                    " for key " +
                    packet.getFrom());
            // This error packet will probably won't make it through
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }

        IQ reply = IQ.createResultIQ(packet);
        Element child = reply.setChildElement("bind", "urn:ietf:params:xml:ns:xmpp-bind");
        // Check if the client specified a desired resource
        String resource = packet.getChildElement().elementTextTrim("resource");
        if (resource == null || resource.length() == 0) {
            // None was defined so use the random generated resource
            resource = session.getAddress().getResource();
        }
        else {
            // Check that the desired resource is valid
            try {
                resource = JID.resourceprep(resource);
            }
            catch (StringprepException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.jid_malformed);
                // Send the error directly since a route does not exist at this point.
                session.process(reply);
                return null;
            }
        }
        // Get the token that was generated during the SASL authentication
        AuthToken authToken = session.getAuthToken();
        if (authToken.isAnonymous()) {
            // User used ANONYMOUS SASL so initialize the session as an anonymous login
            session.setAnonymousAuth();
        }
        else {
            String username = authToken.getUsername().toLowerCase();
            // If a session already exists with the requested JID, then check to see
            // if we should kick it off or refuse the new connection
            if (sessionManager.isActiveRoute(username, resource)) {
                ClientSession oldSession;
                try {
                    String domain = localServer.getServerInfo().getName();
                    oldSession = sessionManager.getSession(username, domain, resource);
                    oldSession.incrementConflictCount();
                    int conflictLimit = sessionManager.getConflictKickLimit();
                    if (conflictLimit != SessionManager.NEVER_KICK &&
                            oldSession.getConflictCount() > conflictLimit) {
                        Connection conn = oldSession.getConnection();
                        if (conn != null) {
                            // Kick out the old connection that is conflicting with the new one
                            StreamError error = new StreamError(StreamError.Condition.conflict);
                            conn.deliverRawText(error.toXML());
                            conn.close();
                        }
                    }
                    else {
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(PacketError.Condition.conflict);
                        // Send the error directly since a route does not exist at this point.
                        session.process(reply);
                        return null;
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
        return null;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
