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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * In-memory implementation of a session.
 *
 * @author Iain Shigeoka
 */
public class SessionImpl implements Session {

    /**
     * The XMPPAddress this session is authenticated as.
     */
    private XMPPAddress jid;

    /**
     * The stream id for this session (random and unique).
     */
    private StreamID streamID;

    /**
     * The current session status.
     */
    protected int status = STATUS_CONNECTED;

    /**
     * The connection that this session represents.
     */
    protected Connection conn;

    /**
     * The authentication token for this session.
     */
    protected AuthToken authToken;

    /**
     * Flag indicating if this session has been initialized yet (upon first available transition).
     */
    private boolean initialized;

    private Presence presence = null;
    private SessionManagerImpl sessionManager;

    private String serverName;

    private Date startDate = new Date();

    private int conflictCount = 0;

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param connection The connection we are proxying
     */
    public SessionImpl(SessionManagerImpl sessionManager,
                       String serverName,
                       Connection connection,
                       StreamID streamID)
            throws UnauthorizedException {
        conn = connection;
        this.streamID = streamID;
        this.serverName = serverName;
        this.jid = new XMPPAddress(null, null, null);
        presence = new PresenceImpl();

        this.sessionManager = sessionManager;

        if (sessionManager == null) {
            throw new UnauthorizedException("Required services not available");
        }
    }

    public long getUserID() throws UserNotFoundException {
        if (authToken == null) {
            throw new UserNotFoundException();
        }
        return authToken.getUserID();
    }

    public String getServerName() {
        return serverName;
    }

    public void setAuthToken(AuthToken auth, UserManager userManager, String resource) throws UserNotFoundException {
        User user = userManager.getUser(auth.getUserID());
        jid = new XMPPAddress(user.getUsername(), serverName, resource);
        authToken = auth;

        List params = new ArrayList();
        params.add(jid.toString());
        params.add(getConnection().toString());
        // Log.info(LocaleUtils.getLocalizedString("admin.authenticated",params));

        sessionManager.addSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);
    }

    public void setAnonymousAuth() {
        jid = new XMPPAddress("", serverName, "");
        // Registering with the session manager assigns the resource
        sessionManager.addAnonymousSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);

        List params = new ArrayList();
        params.add(jid.toString());
        params.add(getConnection().toString());
        //   Log.info(LocaleUtils.getLocalizedString("admin.authenticated",params));
    }

    public AuthToken getAuthToken() {
        return authToken;
    }

    public int getStatus() {
        return status;
    }

    public Connection getConnection() {
        return conn;
    }

    public XMPPAddress getAddress() {
        return jid;
    }

    public StreamID getStreamID() {
        return streamID;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean isInit) {
        initialized = isInit;
    }

    public Presence getPresence() {
        return presence;
    }

    public Presence setPresence(Presence presence) {
        Presence oldPresence = this.presence;
        this.presence = presence;
        if (oldPresence.getPriority() != this.presence.getPriority()) {
            sessionManager.changePriority(getAddress(), this.presence.getPriority());
        }
        return oldPresence;
    }

    public Date getCreationDate() {
        return startDate;
    }

    private long lastActiveDate;

    public Date getLastActiveDate() {
        return new Date(lastActiveDate);
    }

    private long clientPacketCount = 0;
    private long serverPacketCount = 0;

    /**
     * <p>Increments the count of client to server packets by one.</p>
     */
    public void incrementClientPacketCount() {
        clientPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * <p>Increments the count of server to client packets by one.</p>
     */
    public void incrementServerPacketCount() {
        serverPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    public long getNumClientPackets() {
        return clientPacketCount;
    }

    public long getNumServerPackets() {
        return serverPacketCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void incrementConflictCount() throws UnauthorizedException {
        conflictCount++;
    }

    public void process(XMPPPacket packet) {
        deliver(packet);
    }

    private void deliver(XMPPPacket packet) {
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(packet);
            }
            catch (Exception e) {
                // TODO: Should attempt to do something with the packet
                try {
                    conn.close();
                }
                catch (UnauthorizedException e1) {
                    // TODO: something more intelligent, if the connection is
                    // already closed this will throw an exception but it is not a
                    // logged error
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e1);
                }
            }
        }
    }
}
