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
import java.util.Iterator;
import java.util.Collection;
import javax.xml.stream.XMLStreamException;

/**
 * Standard security proxy
 *
 * @author Iain Shigeoka
 */
public class SessionManagerProxy implements SessionManager {

    private SessionManager sessionManager;
    private Permissions permissions;

    public SessionManagerProxy(SessionManager sessionManager, AuthToken auth, Permissions permission) {
        this.sessionManager = sessionManager;
        this.permissions = permission;
    }

    public Session createSession(Connection conn) throws UnauthorizedException {
        return sessionManager.createSession(conn);
    }

    public void changePriority(XMPPAddress sender, int priority) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.changePriority(sender, priority);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Session getBestRoute(XMPPAddress recipient) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getBestRoute(recipient);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public boolean isActiveRoute(XMPPAddress route) {
        return sessionManager.isActiveRoute(route);
    }

    public Session getSession(XMPPAddress address)
            throws UnauthorizedException, SessionNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getSession(address);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Collection<Session> getSessions() {
        return sessionManager.getSessions();
    }

    public Collection<Session> getSessions(SessionResultFilter filter) {
        return sessionManager.getSessions(filter);
    }

    public Iterator getAnonymousSessions() {
        return sessionManager.getAnonymousSessions();
    }

    public Collection<Session> getSessions(String username) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getSessions(username);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getTotalSessionCount() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getTotalSessionCount();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getSessionCount() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getSessionCount();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getAnonymousSessionCount() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getAnonymousSessionCount();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getSessionCount(String username) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getSessionCount(username);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Iterator getSessionUsers() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return sessionManager.getSessionUsers();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void sendServerMessage(String subject, String body)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.sendServerMessage(subject, body);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void sendServerMessage(XMPPAddress address, String subject, String body)
            throws UnauthorizedException, SessionNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.sendServerMessage(address, subject, body);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void broadcast(XMPPPacket packet) throws
            UnauthorizedException, PacketException, XMLStreamException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.broadcast(packet);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void userBroadcast(String username, XMPPPacket packet) throws
            UnauthorizedException, PacketException, XMLStreamException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.userBroadcast(username, packet);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void addAnonymousSession(Session session) {
        sessionManager.addAnonymousSession(session);
    }

    public int getConflictKickLimit() {
        return sessionManager.getConflictKickLimit();
    }

    public void setConflictKickLimit(int limit) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            sessionManager.setConflictKickLimit(limit);
        }
        else {
            throw new UnauthorizedException();
        }
    }
}
