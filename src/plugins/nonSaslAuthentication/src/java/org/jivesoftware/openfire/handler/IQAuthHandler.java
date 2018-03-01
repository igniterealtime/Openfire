/*
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

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

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
public class IQAuthHandler extends IQHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQAuthHandler.class);

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
        if (AuthFactory.supportsPasswordRetrieval()) {
            probeResponse.addElement("password");
            probeResponse.addElement("digest");
        }
        probeResponse.addElement("resource");
    }

    @Override
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
            catch (UserNotFoundException | UnauthorizedException e) {
                response = IQ.createResultIQ(packet);
                response.setChildElement(packet.getChildElement().createCopy());
                response.setError(PacketError.Condition.not_authorized);
            } catch (ConnectionException | InternalUnauthenticatedException e) {
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
        if ( AuthFactory.supportsPasswordRetrieval() ) {
            if ( password != null) {
                token = AuthFactory.authenticate( username, password );
            } else if ( digest != null) {
                token = authenticate(username, session.getStreamID().toString(), digest );
            }
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
                List<String> params = new ArrayList<>();
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
        if (JiveGlobals.getBooleanProperty("xmpp.auth.anonymous")) {
            // Verify that client can connect from his IP address
            boolean forbidAccess = !LocalClientSession.isAllowedAnonymous( session.getConnection() );
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

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        registerHandler = server.getIQRegisterHandler();
        serverName = server.getServerInfo().getXMPPDomain();
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    /**
     * Authenticates a user with a username, token, and digest and returns an AuthToken.
     * The digest should be generated using the {@link AuthFactory#createDigest(String, String)} method.
     * If the username and digest do not match the record of any user in the system, the
     * method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param token the token that was used with plain-text password to generate the digest.
     * @param digest the digest generated from plain-text password and unique token.
     * @return an AuthToken token if the username and digest are correct for the user's
     *      password and given token.
     * @throws UnauthorizedException if the username and password do not match any
     *      existing user or the account is locked out.
     */
    public static AuthToken authenticate(String username, String token, String digest)
            throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        if ( LockOutManager.getInstance().isAccountDisabled(username)) {
            LockOutManager.getInstance().recordFailedLogin(username);
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals( XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }
        try {
            String password = AuthFactory.getPassword( username );
            String anticipatedDigest = AuthFactory.createDigest(token, password);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
        return new AuthToken(username);
    }

}
