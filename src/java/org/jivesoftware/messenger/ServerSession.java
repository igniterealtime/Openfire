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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import javax.xml.stream.XMLStreamWriter;

public class ServerSession implements Session {

    private StreamID streamID;
    private XMPPAddress address;
    private Date creationDate;
    private Connection connection = new ServerConnection();

    public ServerSession(XMPPAddress address, StreamID streamID) {
        this.address = address;
        this.streamID = streamID;
        creationDate = new Date();
    }

    public Connection getConnection() {
        return connection;
    }

    public int getStatus() {
        return Session.STATUS_AUTHENTICATED;
    }

    public void setStatus(int status) {
    }

    public boolean isInitialized() {
        return true;
    }

    public void setInitialized(boolean isInit) throws UnauthorizedException {
    }

    public Presence getPresence() {
        return null;
    }

    public Presence setPresence(Presence presence) {
        return null;
    }

    public void setAuthToken(AuthToken auth,
                             UserManager userManager,
                             String resource) {
    }

    public void setAnonymousAuth() throws UnauthorizedException {
    }

    // TODO: we need a server auth token for priviledged services to use
    public AuthToken getAuthToken() {
        return null;
    }

    public StreamID getStreamID() {
        return streamID;
    }

    public long getUserID() throws UserNotFoundException, UnauthorizedException {
        return 0;
    }

    public String getServerName() {
        return address.toString();
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public Date getLastActiveDate() {
        return new Date();
    }

    public void incrementClientPacketCount() {
    }

    public void incrementServerPacketCount() {
    }

    public long getNumClientPackets() {
        return 0;
    }

    public long getNumServerPackets() {
        return 0;
    }

    public int getConflictCount() {
        return 0;
    }

    public void incrementConflictCount() {
    }

    public XMPPAddress getAddress() {
        return address;
    }

    public void process(XMPPPacket packet) {
    }

    private class ServerConnection implements Connection {
        public boolean validate() {
            return true;
        }

        public void init(Session session) {
        }

        public InetAddress getInetAddress()
                throws UnauthorizedException, UnknownHostException {
            return InetAddress.getLocalHost();
        }

        public XMLStreamWriter getSerializer() throws UnauthorizedException {
            // todo: implement so this loops back
            return null;
        }

        public void close() throws UnauthorizedException {
        }

        public boolean isClosed() {
            return false;
        }

        public boolean isSecure() {
            return true;
        }

        public Object registerCloseListener(ConnectionCloseListener listener,
                                            Object handbackMessage)
                throws UnauthorizedException {
            return null;
        }

        public Object removeCloseListener(ConnectionCloseListener listener)
                throws UnauthorizedException {
            return null;
        }

        public void deliver(XMPPPacket packet)
                throws UnauthorizedException {

        }
    }
}