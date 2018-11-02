package org.jivesoftware.openfire.plugin.rest.controller;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.plugin.rest.entity.SessionEntities;
import org.jivesoftware.openfire.plugin.rest.entity.SessionEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

/**
 * The Class SessionController.
 */
public class SessionController {
    /** The Constant INSTANCE. */
    public static final SessionController INSTANCE = new SessionController();

    /** The log. */
    private static Logger LOG = LoggerFactory.getLogger(SessionController.class);

    /**
     * Gets the single instance of SessionController.
     *
     * @return single instance of SessionController
     */
    public static SessionController getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the user sessions.
     *
     * @param username the username
     * @return the user sessions
     * @throws ServiceException the service exception
     */
    public SessionEntities getUserSessions(String username) throws ServiceException {
        Collection<ClientSession> clientSessions = SessionManager.getInstance().getSessions(username);
        SessionEntities sessionEntities = convertToSessionEntities(clientSessions);
        return sessionEntities;
    }
    
    /**
     * Gets the all sessions.
     *
     * @return the all sessions
     * @throws ServiceException the service exception
     */
    public SessionEntities getAllSessions() throws ServiceException {
        Collection<ClientSession> clientSessions = SessionManager.getInstance().getSessions();
        SessionEntities sessionEntities = convertToSessionEntities(clientSessions);
        return sessionEntities;
    }
    
    /**
     * Removes the user sessions.
     *
     * @param username the username
     * @throws ServiceException the service exception
     */
    public void removeUserSessions(String username) throws ServiceException {
        final StreamError error = new StreamError(StreamError.Condition.not_authorized);
        for (ClientSession session : SessionManager.getInstance().getSessions(username)) {
            session.deliverRawText(error.toXML());
            session.close();
        }
    }

    /**
     * Convert to session entities.
     *
     * @param clientSessions the client sessions
     * @return the session entities
     * @throws ServiceException the service exception
     */
    private SessionEntities convertToSessionEntities(Collection<ClientSession> clientSessions) throws ServiceException {
        List<SessionEntity> sessions = new ArrayList<SessionEntity>();
        SessionEntities sessionEntities = new SessionEntities(sessions);

        for (ClientSession clientSession : clientSessions) {
            SessionEntity session = new SessionEntity();
            session.setSessionId(clientSession.getAddress().toString());

            if (!clientSession.isAnonymousUser()) {
                try {
                    session.setUsername(clientSession.getUsername());
                } catch (UserNotFoundException e) {
                    throw new ServiceException("Could not get user", "", ExceptionType.USER_NOT_FOUND_EXCEPTION,
                            Response.Status.NOT_FOUND, e);
                }
            } else {
                session.setUsername("Anonymous");
            }

            session.setResource(clientSession.getAddress().getResource());
            
            if (clientSession instanceof LocalClientSession) {
                  session.setNode("Local");
            } else {
                session.setNode("Remote");
            }

            String status = "";
            if (clientSession.getStatus() == Session.STATUS_CLOSED) {
                status = "Closed";
            } else if (clientSession.getStatus() == Session.STATUS_CONNECTED) {
                status = "Connected";
            } else if (clientSession.getStatus() == Session.STATUS_AUTHENTICATED) {
                status = "Authenticated";
            } else {
                status = "Unkown";
            }
            session.setSessionStatus(status);

            if (clientSession.getPresence() != null) {
                session.setPresenceMessage(clientSession.getPresence().getStatus());

                Presence.Show show = clientSession.getPresence().getShow();
                if(show == Presence.Show.away) {
                    session.setPresenceStatus("Away");
                } else if(show == Presence.Show.chat) {
                    session.setPresenceStatus("Available to Chat");
                } else if(show == Presence.Show.dnd) {
                    session.setPresenceStatus("Do Not Disturb");
                } else if(show == Presence.Show.xa) {
                    session.setPresenceStatus("Extended Away");
                } else if(show == null) {
                    session.setPresenceStatus("Online");
                } else {
                    session.setPresenceStatus("Unknown/Not Recognized");
                }
                session.setPriority(clientSession.getPresence().getPriority());
            }
            
            try {
                session.setHostAddress(clientSession.getHostAddress());
                session.setHostName(clientSession.getHostName());
            } catch (UnknownHostException e) {
                LOG.error("UnknownHostException", e);
            }

            session.setCreationDate(clientSession.getCreationDate());
            session.setLastActionDate(clientSession.getLastActiveDate());
            session.setSecure(clientSession.isSecure());

            sessions.add(session);
        }
        return sessionEntities;
    }
}
