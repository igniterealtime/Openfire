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

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Decorates a connection object with permission checks.
 *
 * @author Iain Shigeoka
 */
public class ConnectionProxy implements Connection {

    /**
     * Permissions associated with this connection
     */
    private Permissions permissions;
    /**
     * The connection this proxy is protecting
     */
    private Connection conn;
    /**
     * Authentication token for this proxy
     */
    private AuthToken auth;

    public ConnectionProxy(Connection connection, AuthToken auth, Permissions perm) {
        conn = connection;
        permissions = perm;
        this.auth = auth;
    }

    public boolean validate() {
        return conn.validate();
    }

    public void init(Session session) {
        conn.init(session);
    }

    public InetAddress getInetAddress()
            throws UnauthorizedException, UnknownHostException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.USER_ADMIN)) {
            return conn.getInetAddress();
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public XMLStreamWriter getSerializer() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.USER_ADMIN)) {
            return conn.getSerializer();
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public void close() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.USER_ADMIN)) {
            conn.close();
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public boolean isClosed() {
        return conn.isClosed();
    }

    public boolean isSecure() {
        return conn.isSecure();
    }

    public Object registerCloseListener(ConnectionCloseListener listener,
                                        Object handbackMessage)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.USER_ADMIN)) {
            return conn.registerCloseListener(listener, handbackMessage);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Object removeCloseListener(ConnectionCloseListener listener)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.USER_ADMIN)) {
            return conn.removeCloseListener(listener);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void deliver(XMPPPacket packet) throws UnauthorizedException,
            PacketException, XMLStreamException {
        //if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
        // | Permissions.USER_ADMIN)) {
        // Look into doing something about limiting delivery
        // of packets until properly authenticated
        if (true) {
            conn.deliver(packet);
        }
        else {
            throw new UnauthorizedException();
        }
    }

}
