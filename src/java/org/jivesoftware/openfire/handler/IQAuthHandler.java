/**
 * $RCSfile$
 * $Revision: 2747 $
 * $Date: 2005-08-31 15:12:28 -0300 (Wed, 31 Aug 2005) $
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

package org.jivesoftware.openfire.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.*;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.stringprep.Stringprep;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the TYPE_IQ jabber:iq:auth protocol (plain only). Clients
 * use this protocol to authenticate with the server. A 'get' query
 * runs an authentication probe with a given user name. Return the
 * authentication form or an error indicating the user is not
 * registered on the server.<p>
 *
 * A 'set' query authenticates with information given in the
 * authentication form. An authenticated session may reset their
 * authentication information using a 'set' query.
 *
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 *
 * @author Iain Shigeoka
 */
public class IQAuthHandler extends IQHandler implements IQAuthInfo {

    private boolean anonymousAllowed;

    private Element probeResponse;
    private IQHandlerInfo info;

    private String serverName;
    private UserManager userManager;
    private RoutingTable routingTable;
    private IQRegisterHandler registerHandler;

    /**
     * Clients are not authenticated when accessing this handler.
     */
    public IQAuthHandler() {
        super("XMPP Authentication handler");
        info = new IQHandlerInfo("query", "jabber:iq:auth");

        probeResponse = DocumentHelper.createElement(QName.get("query", "jabber:iq:auth"));
        probeResponse.addElement("username");
        if (AuthFactory.isPlainSupported()) {
            probeResponse.addElement("password");
        }
        if (AuthFactory.isDigestSupported()) {
            probeResponse.addElement("digest");
        }
        probeResponse.addElement("resource");
        anonymousAllowed = JiveGlobals.getBooleanProperty("xmpp.auth.anonymous");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        JID from = packet.getFrom();
        LocalClientSession session = (LocalClientSession) sessionManager.getSession(from);
        // If no session was found then answer an error (if possible)
        if (session == null) {
            Log.error("Error during authentication. Session not found in " +
                    sessionManager.getPreAuthenticatedKeys() +
                    " for key " +
                    from);
            // This error packet will probably won't make it through
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }
        IQ response;
        boolean resourceBound = false;
        if (JiveGlobals.getBooleanProperty("xmpp.auth.iqauth",true)) {
            try {
                Element iq = packet.getElement();
                Element query = iq.element("query");
                Element queryResponse = probeResponse.createCopy();
                if (IQ.Type.get == packet.getType()) {
                    String username = query.elementText("username");
                    if (username != null) {
                        queryResponse.element("username").setText(username);
                    }
                    response = IQ.createResultIQ(packet);
                    response.setChildElement(queryResponse);
                    // This is a workaround. Since we don't want to have an incorrect TO attribute
                    // value we need to clean up the TO attribute and send directly the response.
                    // The TO attribute will contain an incorrect value since we are setting a fake
                    // JID until the user actually authenticates with the server.
                    if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
                        response.setTo((JID)null);
                    }
                }
                // Otherwise set query
                else {
                    if (query.elements().isEmpty()) {
                        // Anonymous authentication
                        response = anonymousLogin(session, packet);
                        resourceBound = session.getStatus() == Session.STATUS_AUTHENTICATED;
                    }
                    else {
                        String username = query.elementText("username");
                        // Login authentication
                        String password = query.elementText("password");
                        String digest = null;
                        if (query.element("digest") != null) {
                            digest = query.elementText("digest").toLowerCase();
                        }
    
                        // If we're already logged in, this is a password reset
                        if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                            // Check that a new password has been specified
                            if (password == null || password.trim().length() == 0) {
                                response = IQ.createResultIQ(packet);
                                response.setError(PacketError.Condition.not_allowed);
                                response.setType(IQ.Type.error);
                            }
                            else {
                                // Check if a user is trying to change his own password
                                if (session.getUsername().equalsIgnoreCase(username)) {
                                    response = passwordReset(password, packet, username, session);
                                }
                                // Check if an admin is trying to set the password for another user
                                else if (XMPPServer.getInstance().getAdmins()
                                        .contains(new JID(from.getNode(), from.getDomain(), null, true))) {
                                    response = passwordReset(password, packet, username, session);
                                }
                                else {
                                    // User not authorized to change the password of another user
                                    throw new UnauthorizedException();
                                }
                            }
                        }
                        else {
                            // it is an auth attempt
                            response = login(username, query, packet, password, session, digest);
                            resourceBound = session.getStatus() == Session.STATUS_AUTHENTICATED;
                        }
                    }
                }
            }
            catch (UserNotFoundException e) {
                response = IQ.createResultIQ(packet);
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.not_authorized);
            }
            catch (UnauthorizedException e) {
                response = IQ.createResultIQ(packet);
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.not_authorized);
            } catch (ConnectionException e) {
                response = IQ.createResultIQ(packet);
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.internal_server_error);
            } catch (InternalUnauthenticatedException e) {
                response = IQ.createResultIQ(packet);
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.internal_server_error);
            }
        }
        else {
            response = IQ.createResultIQ(packet);
            response.setChildElement(packet.getChildElement().createCopy());
            response.setError(PacketError.Condition.not_authorized);
        }
        // Send the response directly since we want to be sure that we are sending it back
        // to the correct session. Any other session of the same user but with different
        // resource is incorrect.
        session.process(response);
        if (resourceBound) {
            // After the client has been informed, inform all listeners as well.
            SessionEventDispatcher.dispatchEvent(session, SessionEventDispatcher.EventType.resource_bound);
        }
        return null;
    }

    private IQ login(String username, Element iq, IQ packet, String password, LocalClientSession session, String digest)
            throws UnauthorizedException, UserNotFoundException, ConnectionException, InternalUnauthenticatedException {
    	// Verify the validity of the username
    	if (username == null || username.trim().length() == 0) {
    		throw new UnauthorizedException("Invalid username (empty or null).");
    	}
    	try {
    		Stringprep.nodeprep(username);
    	} catch (StringprepException e) {
            throw new UnauthorizedException("Invalid username: " + username, e);
		}
    	
    	// Verify that specified resource is not violating any string prep rule
        String resource = iq.elementText("resource");
        if (resource != null) {
            try {
                resource = JID.resourceprep(resource);
            }
            catch (StringprepException e) {
                throw new UnauthorizedException("Invalid resource: " + resource, e);
            }
        }
        else {
            // Answer a not_acceptable error since a resource was not supplied
            IQ response = IQ.createResultIQ(packet);
            response.setChildElement(packet.getChildElement().createCopy());
            response.setError(PacketError.Condition.not_acceptable);
            return response;
        }
        if (! JiveGlobals.getBooleanProperty("xmpp.auth.iqauth",true)) {
            throw new UnauthorizedException();
        }
        username = username.toLowerCase();
        // Verify that supplied username and password are correct (i.e. user authentication was successful)
        AuthToken token = null;
        if (password != null && AuthFactory.isPlainSupported()) {
            token = AuthFactory.authenticate(username, password);
        }
        else if (digest != null && AuthFactory.isDigestSupported()) {
            token = AuthFactory.authenticate(username, session.getStreamID().toString(),
                    digest);
        }
        if (token == null) {
            throw new UnauthorizedException();
        }
        // Verify if there is a resource conflict between new resource and existing one.
        // Check if a session already exists with the requested full JID and verify if
        // we should kick it off or refuse the new connection
        ClientSession oldSession = routingTable.getClientRoute(new JID(username, serverName, resource, true));
        if (oldSession != null) {
            try {
                int conflictLimit = sessionManager.getConflictKickLimit();
                if (conflictLimit == SessionManager.NEVER_KICK) {
                    IQ response = IQ.createResultIQ(packet);
                    response.setChildElement(packet.getChildElement().createCopy());
                    response.setError(PacketError.Condition.forbidden);
                    return response;
                }

                int conflictCount = oldSession.incrementConflictCount();
                if (conflictCount > conflictLimit) {
                    // Send a stream:error before closing the old connection
                    StreamError error = new StreamError(StreamError.Condition.conflict);
                    oldSession.deliverRawText(error.toXML());
                    oldSession.close();
                }
                else {
                    IQ response = IQ.createResultIQ(packet);
                    response.setChildElement(packet.getChildElement().createCopy());
                    response.setError(PacketError.Condition.forbidden);
                    return response;
                }
            }
            catch (Exception e) {
                Log.error("Error during login", e);
            }
        }
        // Set that the new session has been authenticated successfully
        session.setAuthToken(token, resource);
        packet.setFrom(session.getAddress());
        return IQ.createResultIQ(packet);
    }

    private IQ passwordReset(String password, IQ packet, String username, Session session)
            throws UnauthorizedException
    {
        IQ response;
        // Check if users can change their passwords and a password was specified
        if (!registerHandler.canChangePassword() || password == null || password.length() == 0) {
            throw new UnauthorizedException();
        }
        else {
            try {
                userManager.getUser(username).setPassword(password);
                response = IQ.createResultIQ(packet);
                List<String> params = new ArrayList<String>();
                params.add(username);
                params.add(session.toString());
                Log.info(LocaleUtils.getLocalizedString("admin.password.update", params));
            }
            catch (UserNotFoundException e) {
                throw new UnauthorizedException();
            }
        }
        return response;
    }

    private IQ anonymousLogin(LocalClientSession session, IQ packet) {
        IQ response = IQ.createResultIQ(packet);
        if (anonymousAllowed) {
            // Verify that client can connect from his IP address
            boolean forbidAccess = false;
            try {
                String hostAddress = session.getConnection().getHostAddress();
                if (!LocalClientSession.getAllowedAnonymIPs().isEmpty() &&
                        !LocalClientSession.getAllowedAnonymIPs().containsKey(hostAddress)) {
                    byte[] address = session.getConnection().getAddress();
                    String range1 = (address[0] & 0xff) + "." + (address[1] & 0xff) + "." +
                            (address[2] & 0xff) +
                            ".*";
                    String range2 = (address[0] & 0xff) + "." + (address[1] & 0xff) + ".*.*";
                    String range3 = (address[0] & 0xff) + ".*.*.*";
                    if (!LocalClientSession.getAllowedAnonymIPs().containsKey(range1) &&
                            !LocalClientSession.getAllowedAnonymIPs().containsKey(range2) &&
                            !LocalClientSession.getAllowedAnonymIPs().containsKey(range3)) {
                        forbidAccess = true;
                    }
                }
            } catch (UnknownHostException e) {
                forbidAccess = true;
            }
            if (forbidAccess) {
                // Connection forbidden from that IP address
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.forbidden);
            }
            else {
                // Anonymous authentication allowed
                session.setAnonymousAuth();
                response.setTo(session.getAddress());
                Element auth = response.setChildElement("query", "jabber:iq:auth");
                auth.addElement("resource").setText(session.getAddress().getResource());
            }
        }
        else {
            // Anonymous authentication is not allowed
            response.setChildElement(packet.getChildElement().createCopy());
            response.setError(PacketError.Condition.forbidden);
        }
        return response;
    }

    public boolean isAnonymousAllowed() {
        return anonymousAllowed;
    }

    public void setAllowAnonymous(boolean isAnonymous) throws UnauthorizedException {
        anonymousAllowed = isAnonymous;
        JiveGlobals.setProperty("xmpp.auth.anonymous", Boolean.toString(anonymousAllowed));
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        registerHandler = server.getIQRegisterHandler();
        serverName = server.getServerInfo().getXMPPDomain();
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
