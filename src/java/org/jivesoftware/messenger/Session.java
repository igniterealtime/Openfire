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

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Date;

/**
 * The session is the primary interface to the entire chat server.
 * Use the session to obtain references to all system managers, permissions,
 * authentication, and other resources.<p>
 *
 * The session represents a connection between the server and a client (c2s) or
 * another server (s2s). Authentication and user accounts are associated with
 * c2s connections while s2s has an optional authentication association but no
 * single user user.<p>
 *
 * Obtain object managers from the session in order to access server resources.
 *
 * @author Iain Shigeoka
 */
public interface Session extends RoutableChannelHandler {

    public static final int STATUS_CLOSED = -1;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_STREAMING = 2;
    public static final int STATUS_AUTHENTICATED = 3;

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
     public XMPPAddress getAddress();

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    public Connection getConnection();

    /**
     * Obtain the current status of this session.
     *
     * @return The status code for this session
     */
    public int getStatus();

    /**
     * Set the new status of this session. Setting a status may trigger
     * certain events to occur (setting a closed status will close this
     * session).
     *
     * @param status The new status code for this session
     */
    public void setStatus(int status) throws UnauthorizedException;

    /**
     * Flag indicating if this session has been initialized once coming
     * online. Session initialization occurs after the session receives
     * the first "available" presence update from the client. Initialization
     * actions include pushing offline messages, presence subscription requests,
     * and presence statuses to the client. Initialization occurs only once
     * following the first available presence transition.
     *
     * @return True if the session has already been initializsed
     */
    public boolean isInitialized();

    /**
     * Sets the initialization state of the session.
     *
     * @param isInit True if the session has been initialized
     * @throws UnauthorizedException If the caller does not have permission to make this change
     * @see #isInitialized
     */
    public void setInitialized(boolean isInit) throws UnauthorizedException;

    /**
     * Obtain the presence of this session.
     *
     * @return The presence of this session or null if not authenticated
     */
    public Presence getPresence();

    /**
     * Set the presence of this session
     *
     * @param presence The presence for the session
     * @return The old priority of the session or null if not authenticated
     */
    public Presence setPresence(Presence presence) throws UnauthorizedException;

    /**
     * Initialize the session with a valid authentication token and
     * resource name. This automatically upgrades the session's
     * status to authenticated and enables many features that are not
     * available until authenticated (obtaining managers for example).
     *
     * @param auth        The authentication token obtained from the AuthFactory
     * @param resource    The resource this session authenticated under
     * @param userManager The user manager this authentication occured under
     */
    public void setAuthToken(AuthToken auth, UserManager userManager, String resource)
            throws UserNotFoundException, UnauthorizedException;

    /**
     * <p>Initialize the session as an anonymous login.</p>
     * <p>This automatically upgrades the session's
     * status to authenticated and enables many features that are not
     * available until authenticated (obtaining managers for example).</p>
     */
    public void setAnonymousAuth() throws UnauthorizedException;

    /**
     * <p>Obtain the authentication token associated with this session.</p>
     *
     * @return The authentication token associated with this session (can be null)
     */
    public AuthToken getAuthToken();

    /**
     * Obtain the stream ID associated with this sesison. Stream ID's are generated by the server
     * and should be unique and random.
     *
     * @return This session's assigned stream ID
     */
    public StreamID getStreamID();

    /**
     * <p>Obtain the user ID associated with this session.</p>
     * <p>Use this information with the user manager to obtain the
     * user based on ID.</p>
     *
     * @return The user ID associated with this session
     * @throws UserNotFoundException if a user is not associated with a session (the session has not authenticated yet)
     * @throws UnauthorizedException If caller doesn't have permission to access this information
     */
    public long getUserID() throws UserNotFoundException, UnauthorizedException;

    /**
     * Obtain the name of the server this session belongs to.
     *
     * @return the server name.
     */
    public String getServerName();

    /**
     * Obtain the date the session was created.
     *
     * @return the session's creation date.
     */
    public Date getCreationDate();

    /**
     * Obtain the time the session last had activity.
     *
     * @return The last time the session received activity.
     */
    public Date getLastActiveDate();

    /**
     * Obtain the number of packets sent from the client to the server.
     *
     * @throws UnauthorizedException If caller doesn't have permission to access this information
     */
    public void incrementClientPacketCount() throws UnauthorizedException;

    /**
     * Obtain the number of packets sent from the server to the client.
     *
     * @throws UnauthorizedException If caller doesn't have permission to access this information
     */
    public void incrementServerPacketCount() throws UnauthorizedException;

    /**
     * Obtain the number of packets sent from the client to the server.
     *
     * @return The number of packets sent from the client to the server.
     */
    public long getNumClientPackets();

    /**
     * Obtain the number of packets sent from the server to the client.
     *
     * @return The number of packets sent from the server to the client.
     */
    public long getNumServerPackets();

    /**
     * Returns the number of conflicts detected on this session.
     * Conflicts typically occur when another session authenticates properly
     * to the user account and requests to use a resource matching the one
     * in use by this session. Administrators may configure the server to automatically
     * kick off existing sessions when their conflict count exceeds some limit including
     * 0 (old sessions are kicked off immediately to accommodate new sessions). Conflicts
     * typically signify the existing (old) session is broken/hung.
     *
     * @return The number of conflicts detected for this session
     */
    public int getConflictCount();

    /**
     * Increments the conflict by one.
     *
     * @throws UnauthorizedException If caller doesn't have permission to access this information
     */
    public void incrementConflictCount() throws UnauthorizedException;
}