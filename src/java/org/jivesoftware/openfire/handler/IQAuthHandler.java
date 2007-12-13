/**
 * $RCSfile$
 * $Revision: 2747 $
 * $Date: 2005-08-31 15:12:28 -0300 (Wed, 31 Aug 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.handler;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

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
    private boolean iqAuthAllowed;

    private Element probeResponse;
    private IQHandlerInfo info;

    private String serverName;
    private UserManager userManager;
    private SessionManager sessionManager;
    private RoutingTable routingTable;

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
        LocalClientSession session = (LocalClientSession) sessionManager.getSession(packet.getFrom());
        // If no session was found then answer an error (if possible)
        if (session == null) {
            Log.error("Error during authentication. Session not found in " +
                    sessionManager.getPreAuthenticatedKeys() +
                    " for key " +
                    packet.getFrom());
            // This error packet will probably won't make it through
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }
        IQ response;
        if (JiveGlobals.getBooleanProperty("xmpp.auth.iqauth",true)) {
            try {
                Element iq = packet.getElement();
                Element query = iq.element("query");
                Element queryResponse = probeResponse.createCopy();
                if (IQ.Type.get == packet.getType()) {
                    String username = query.elementTextTrim("username");
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
                    }
                    else {
                        String username = query.elementTextTrim("username");
                        // Login authentication
                        String password = query.elementTextTrim("password");
                        String digest = null;
                        if (query.element("digest") != null) {
                            digest = query.elementTextTrim("digest").toLowerCase();
                        }
    
                        // If we're already logged in, this is a password reset
                        if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                            response = passwordReset(password, packet, username, session);
                        }
                        else {
                            // it is an auth attempt
                            response = login(username, query, packet, password, session, digest);
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
        return null;
    }

    private IQ login(String username, Element iq, IQ packet, String password, LocalClientSession session, String digest)
            throws UnauthorizedException, UserNotFoundException {
        // Verify that specified resource is not violating any string prep rule
        String resource = iq.elementTextTrim("resource");
        if (resource != null) {
            try {
                resource = JID.resourceprep(resource);
            }
            catch (StringprepException e) {
                throw new IllegalArgumentException("Invalid resource: " + resource);
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
                oldSession.incrementConflictCount();
                int conflictLimit = sessionManager.getConflictKickLimit();
                if (conflictLimit != SessionManager.NEVER_KICK && oldSession.getConflictCount() > conflictLimit) {
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
        if (password == null || password.length() == 0) {
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
            session.setAnonymousAuth();
            response.setTo(session.getAddress());
            Element auth = response.setChildElement("query", "jabber:iq:auth");
            auth.addElement("resource").setText(session.getAddress().getResource());
        }
        else {
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
        sessionManager = server.getSessionManager();
        routingTable = server.getRoutingTable();
        serverName = server.getServerInfo().getName();
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}