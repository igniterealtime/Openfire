/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * Implements the TYPE_IQ jabber:iq:auth protocol (plain only). Clients
 * use this protocol to authenticate with the server.
 * A 'get' query runs an authentication probe with a given user name.
 * Return the authentication form or an error indicating the user
 * is not registered on the server.
 * A 'set' query authenticates with information given in the
 * authentication form. An authenticated session may reset their
 * authentication information using a 'set' query.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQAuthHandler extends IQHandler implements IQAuthInfo {

    private Element probeResponse;
    // TODO: this won't work with independent servers in the same JVM
    private static boolean anonymousAllowed;
    private IQHandlerInfo info;

    /**
     * Clients are not authenticated when accessing this handler.
     */
    public IQAuthHandler() {
        super("XMPP Authentication handler");
        info = new IQHandlerInfo("query", "jabber:iq:auth");

        probeResponse = DocumentHelper.createElement(QName.get("query", "jabber:iq:auth"));
        probeResponse.add(DocumentHelper.createElement("username"));
        if (AuthFactory.isPlainSupported()) {
            probeResponse.add(DocumentHelper.createElement("password"));
        }
        if (AuthFactory.isDigestSupported()) {
            probeResponse.add(DocumentHelper.createElement("digest"));
        }
        probeResponse.add(DocumentHelper.createElement("resource"));
        anonymousAllowed = "true".equals(JiveGlobals.getProperty("xmpp.auth.anonymous"));
    }

    public synchronized IQ handleIQ(IQ packet) throws
            UnauthorizedException, PacketException {
        try {
            Session session = packet.getOriginatingSession();
            IQ response = null;
            try {
                Element iq = ((XMPPDOMFragment)packet.getChildFragment()).getRootElement();

                if (IQ.GET == packet.getType()) {
                    String username = iq.element("username").getTextTrim();
                    probeResponse.element("username").setText(username);
                    response = packet.createResult(probeResponse);
                }
                else { // set query

                    if (iq.elements().isEmpty()) {
                        // Anonymous authentication
                        response = anonymousLogin(session, packet);
                    }
                    else {
                        String username = iq.element("username").getTextTrim();

                        // login authentication
                        String password = null;
                        if (iq.element("password") != null) {
                            password = iq.element("password").getTextTrim();
                        }
                        String digest = null;
                        if (iq.element("digest") != null) {
                            digest = iq.element("digest").getTextTrim().toLowerCase();
                        }

                        // If we're already logged in, this is a password reset
                        if (session.getStatus() == Session.STATUS_AUTHENTICATED) {
                            response = passwordReset(password, packet, username, session);
                        }
                        else {
                            // it is an auth attempt
                            response = login(username, iq, packet, response, password, session, digest);
                        }
                    }
                }
            }
            catch (UserNotFoundException e) {
                response = packet.createResult();
                response.setError(XMPPError.Code.UNAUTHORIZED);
            }
            catch (UnauthorizedException e) {
                response = packet.createResult();
                response.setError(XMPPError.Code.UNAUTHORIZED);
            }
            deliverer.deliver(response);
        }
        catch (Exception e) {
            Log.error("Error handling authentication IQ packet", e);
        }
        return null;
    }

    private IQ login(String username,
                     Element iq,
                     IQ packet,
                     IQ response,
                     String password,
                     Session session,
                     String digest)
            throws UnauthorizedException, UserNotFoundException {
        XMPPAddress jid = localServer.createAddress(username, iq.element("resource").getTextTrim());


        // If a session already exists with the requested JID, then check to see
        // if we should kick it off or refuse the new connection
        if (sessionManager.isActiveRoute(jid)) {
            Session oldSession = null;
            try {
                oldSession = sessionManager.getSession(jid);
                oldSession.incrementConflictCount();
                int conflictLimit = sessionManager.getConflictKickLimit();
                if (conflictLimit != SessionManager.NEVER_KICK && oldSession.getConflictCount() > conflictLimit) {
                    Connection conn = oldSession.getConnection();
                    if (conn != null) {
                        conn.close();
                    }
                }
                else {
                    response = packet.createResult();
                    response.setError(XMPPError.Code.FORBIDDEN);
                }
            }
            catch (Exception e) {
                Log.error("Error during login", e);
            }
        }
        // If the connection was not refused due to conflict, log the user in
        if (response == null) {
            AuthToken token = null;
            if (password != null && AuthFactory.isPlainSupported()) {
                token = AuthFactory.getAuthToken(username, password);
            }
            else if (digest != null && AuthFactory.isDigestSupported()) {
                token = AuthFactory.getAuthToken(username, session.getStreamID().toString(), digest);
            }
            if (token == null) {
                throw new UnauthorizedException();
            }
            else {
                session.setAuthToken(token, userManager, jid.getResource());
                packet.setSender(session.getAddress());
                response = packet.createResult();
            }
        }
        return response;
    }

    private IQ passwordReset(String password,
                             IQ packet,
                             String username,
                             Session session)
            throws UnauthorizedException {
        IQ response;
        if (password == null || password.length() == 0) {
            throw new UnauthorizedException();
        }
        else {
            try {
                userManager.getUser(username).setPassword(password);
                response = packet.createResult();
                List params = new ArrayList();
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

    private IQ anonymousLogin(Session session,
                              IQ packet)
            throws UnauthorizedException {
        IQ response;
        if (anonymousAllowed) {
            session.setAnonymousAuth();
            MetaDataFragment meta = new MetaDataFragment("jabber:iq:auth", "query");
            meta.setProperty("query.resource", session.getAddress().getResource());
            response = packet.createResult();
            response.addFragment(meta);
        }
        else {
            response = packet.createResult();
            response.setError(XMPPError.Code.FORBIDDEN);
        }
        return response;
    }

    public boolean isAllowAnonymous() {
        return anonymousAllowed;
    }

    public void setAllowAnonymous(boolean isAnonymous) throws UnauthorizedException {
        anonymousAllowed = isAnonymous;
        JiveGlobals.setProperty("xmpp.auth.anonymous", anonymousAllowed ? "true" : "false");
    }

    public UserManager userManager;
    public XMPPServer localServer;
    public SessionManager sessionManager;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
