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
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Packet;
import java.util.Date;

/**
 * In-memory implementation of a session.
 *
 * @author Iain Shigeoka
 */
public class SessionImpl implements Session {

    /**
     * The Address this session is authenticated as.
     */
    private JID address;

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
    private SessionManager sessionManager;

    private String serverName;

    private Date startDate = new Date();

    private int conflictCount = 0;

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param connection The connection we are proxying
     */
    public SessionImpl(String serverName, Connection connection, StreamID streamID) throws UnauthorizedException {
        conn = connection;
        this.streamID = streamID;
        this.serverName = serverName;
        String id = streamID.getID();
        this.address = new JID(null, serverName, id);
        // Set an unavailable initial presence
        presence = new Presence();
        presence.setType(Presence.Type.unavailable);

        this.sessionManager = SessionManager.getInstance();

        if (sessionManager == null) {
            throw new UnauthorizedException("Required services not available");
        }
    }

    public String getUsername() throws UserNotFoundException {
        if (authToken == null) {
            throw new UserNotFoundException();
        }
        return authToken.getUsername();
    }

    public String getServerName() {
        return serverName;
    }

    public void setAuthToken(AuthToken auth, UserManager userManager, String resource) throws UserNotFoundException {
        User user = userManager.getUser(auth.getUsername());
        address = new JID(user.getUsername(), serverName, resource);
        authToken = auth;

        sessionManager.addSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);
    }

    public void setAnonymousAuth() {
        sessionManager.addAnonymousSession(this);
        setStatus(Session.STATUS_AUTHENTICATED);
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

    public JID getAddress() {
        return address;
    }

    public void setAddress(JID address){
        this.address = address;
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
        if (oldPresence.isAvailable() && !this.presence.isAvailable()) {
            // The client is no longer available
            sessionManager.sessionUnavailable(this);
            // Mark that the session is no longer initialized. This means that if the user sends
            // an available presence again the session will be initialized again thus receiving
            // offline messages and offline presence subscription requests
            setInitialized(false);
        }
        else if (!oldPresence.isAvailable() && this.presence.isAvailable()) {
            // The client is available
            sessionManager.sessionAvailable(this);
        }
        else if (oldPresence.getPriority() != this.presence.getPriority()) {
            // The client has changed the priority of his presence
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

    public void process(Packet packet) {
        deliver(packet);
    }

    private void deliver(Packet packet) {
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
