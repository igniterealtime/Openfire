/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;

/**
 * Manages the sessions associated with an account. The information
 * maintained by the Session manager is entirely transient and does
 * not need to be preserved between server restarts.
 *
 * @author Iain Shigeoka
 */
public interface SessionManager {

    final int NEVER_KICK = -1;

    /**
     * Creates a new session for the given connection. Session managers will provide
     * their own sessions through this factory.
     *
     * @param conn The connection to wrap a session around
     * @return The session wrapped around the given connection
     * @throws UnauthorizedException if the caller doesn't have permission to access this resource
     */
    public Session createSession(Connection conn) throws UnauthorizedException;


    /**
     * Change the priority of a session associated with the sender.
     *
     * @param sender   The sender who's session just changed priority
     * @param priority The new priority for the session
     */
    public void changePriority(XMPPAddress sender, int priority) throws UnauthorizedException;

    /**
     * Retrieve the best route to deliver packets to this session
     * given the recipient jid. If no active routes exist, this method
     * returns a reference to itself (the account can store the packet).
     * A null recipient chooses the default active route for this account
     * if one exists. If the recipient can't be reached by this account
     * (wrong account) an exception is thrown.
     *
     * @param recipient The recipient ID to send to or null to select the default route
     * @return The XMPPAddress best suited to use for delivery to the recipient
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public Session getBestRoute(XMPPAddress recipient) throws UnauthorizedException;

    /**
     * Determines if there is an active (reachable session) with the given address.
     *
     * @param route The address being checked for an active session
     * @return True if there is a session with this address
     */
    public boolean isActiveRoute(XMPPAddress route);

    /**
     * <p>Obtain the session associated with the XMPPAddress.</p>
     * <p>All sessions will have a resource so addresses without
     * resources are sure to throw a not found exception.</p>
     *
     * @param address The address of the session you'd like to receive
     * @return The session corresponding to the given address
     * @throws UnauthorizedException    If caller doesn't have permission to access this method
     * @throws SessionNotFoundException If there is no session matching the given address
     */
    public Session getSession(XMPPAddress address)
            throws UnauthorizedException, SessionNotFoundException;

    /**
     * <p>Obtain an iterator of all sessions on the server.</p>
     *
     * @return An iterator over the sessions (never null)
     */
    public Iterator getSessions() throws UnauthorizedException;

    /**
     * <p>Obtain an iterator of all sessions on the server.</p>
     *
     * @param filter The result filter to apply to the search
     * @return An iterator over the sessions (never null)
     */
    public Iterator getSessions(SessionResultFilter filter) throws UnauthorizedException;

    /**
     * <p>Obtain an iterator of all anonymous sessions on the server.</p>
     *
     * @return An iterator over the anonynmous sessions (never null)
     * @throws UnauthorizedException
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public Iterator getAnonymousSessions() throws UnauthorizedException;

    /**
     * <p>Obtain an iterator of all sessions for a given user on the server.</p>
     *
     * @param username The name of the user that owns the sessions
     * @return An iterator over the sessions (never null)
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public Iterator getSessions(String username) throws UnauthorizedException;

    /**
     * <p>Obtain a count of the number of sessions on the server, including unauthenticated
     * sessions (raw connections).</p>
     *
     * @return The total number of sessions on the server including unauthenticated sessions
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public int getTotalSessionCount() throws UnauthorizedException;

    /**
     * <p>Obtain a count of the number of authenticated sessions on the server.</p>
     *
     * @return The total number of active sessions on the server
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public int getSessionCount() throws UnauthorizedException;

    /**
     * <p>Obtain a count of the number of anonymous sessions on the server.</p>
     *
     * @return The total number of anonymous sessions on the server
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public int getAnonymousSessionCount() throws UnauthorizedException;

    /**
     * <p>Obtain a count of the number of active sessions for an authenticated user on the server.</p>
     *
     * @param username The name of the user who owns the sessions to be counted
     * @return The total number of active sessions on the server for a particular user
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public int getSessionCount(String username) throws UnauthorizedException;

    /**
     * Obtain an iterator of all user names for logged in (active) users.
     * <pre><code>
     * Iterator itr = sessionManager.getSessionUsers();
     * while (itr.hasNext()){
     *   String name = (String)itr.next();
     *   Iterator sessItr = sessionManager.getSessions(name);
     *   while (sessItr.hasNext()){
     *     //..
     *   }
     * }
     * </code></pre>
     *
     * @return An iterator over the sessions (never null)
     */
    public Iterator getSessionUsers() throws UnauthorizedException;

    /**
     * <p>Sends a server message to all connected users.</p>
     * <p>This is very useful for making server-wide announcements such as
     * shutdown alerts.</p>
     *
     * @param subject The subject for the message or null for no subject
     * @param body    The body of the message (required)
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public void sendServerMessage(String subject, String body)
            throws UnauthorizedException;

    /**
     * <p>Sends a server message to a user (all sessions) or resource (one session).</p>
     * <p>This is very useful for announcing administration changes that will affect
     * a particular session or user. If the address is to user@server.com then the message
     * is sent to all connected session for the specified user. If the address includes the
     * resource name (e.g. user@server.com/resource) the message is only sent to a session
     * logged into that resource.</p>
     *
     * @param address The address of the user or session to receive the message, or null to send to all users
     * @param subject The subject for the message or null for no subject
     * @param body    The body of the message
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public void sendServerMessage(XMPPAddress address, String subject, String body)
            throws UnauthorizedException, SessionNotFoundException;

    /**
     * Broadcasts the given data to all connected sessions. Excellent
     * for server administration messages.
     *
     * @param packet The packet to be broadcast
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public void broadcast(XMPPPacket packet) throws UnauthorizedException,
            PacketException, XMLStreamException;

    /**
     * Broadcasts the given data to all connected sessions for a particular
     * user. Excellent for updating all connected resources for users such as
     * roster pushes.
     *
     * @param packet The packet to be broadcast
     * @throws UnauthorizedException If caller doesn't have permission to access this method
     */
    public void userBroadcast(String username, XMPPPacket packet) throws
            UnauthorizedException, PacketException, XMLStreamException;

    /**
     * <p>Obtain the number of conflicts a session can conflict with new sessions before
     * being kicked off.</p>
     * <p>A kick limit of 0 means old sessions will be kicked immediately when new,
     * authenticated sessions want it's resource. Conversely, a kick limit of
     * SessionManager.NEVER_KICK will cause the server to never kick off an
     * existing resource.</p>
     *
     * @return The kick limit for the server
     */
    public int getConflictKickLimit();

    /**
     * <p>Set the number of conflicts a session can conflict with new sessions before
     * being kicked off.</p>
     * <p>A kick limit of 0 means old sessions will be kicked immediately when new,
     * authenticated sessions want it's resource. Conversely, a kick limit of
     * SessionManager.NEVER_KICK will cause the server to never kick off an
     * existing resource.</p>
     *
     * @param limit The new kick limit for the server
     */
    public void setConflictKickLimit(int limit) throws UnauthorizedException;

    /**
     * Add a new session with an anonymous login (no user account)
     *
     * @param session The session to add
     */
    public void addAnonymousSession(Session session);
}
