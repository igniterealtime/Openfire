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
import java.util.Date;

/**
 * Protection proxy for the Presence class.
 *
 * @author Iain Shigeoka
 */
public class PresenceProxy extends AbstractPacketProxy implements Presence {

    private Presence presence;

    public PresenceProxy(Presence presence, AuthToken authToken, Permissions permissions) {
        super(presence, authToken, permissions);
        this.presence = presence;
    }

    public boolean isAvailable() {
        return presence.isAvailable();
    }

    public void setAvailable(boolean online) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setAvailable(online);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public boolean isVisible() {
        return presence.isVisible();
    }

    public void setVisible(boolean visible) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setVisible(visible);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public String getID() {
        return presence.getID();
    }

    public String getUsername() {
        return presence.getUsername();
    }

    public Date getLoginTime() {
        return presence.getLoginTime();
    }

    public Date getLastUpdateTime() {
        return presence.getLastUpdateTime();
    }

    public void setLastUpdateTime(Date time) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setLastUpdateTime(time);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getShow() {
        return presence.getShow();
    }

    public void setShow(int status) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setShow(status);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public String getStatus() {
        return presence.getStatus();
    }

    public void setStatus(String status) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setStatus(status);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getPriority() {
        return presence.getPriority();
    }

    public void setPriority(int priority) throws UnauthorizedException {
        if (presence.getUsername().equals(authToken.getUsername()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            presence.setPriority(priority);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RoutePriority getRoutePriority() {
        return presence.getRoutePriority();
    }

    public void setRoutePriority(RoutePriority priority) {
        presence.setRoutePriority(priority);
    }

    public XMPPError getError() {
        return presence.getError();
    }

    public Type getType() {
        return presence.getType();
    }

    public XMPPAddress getRecipient() {
        return presence.getRecipient();
    }

    public XMPPAddress getSender() {
        return presence.getSender();
    }

    public Session getOriginatingSession() {
        return presence.getOriginatingSession();
    }

    public XMPPPacket.Type typeFromString(String type) {
        return presence.typeFromString(type);
    }

    public XMPPFragment createDeepCopy() {
        return presence.createDeepCopy();
    }
}
