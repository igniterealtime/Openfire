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
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.RosterItem;
import java.util.List;

/**
 * Standard security proxy
 *
 * @author Iain Shigeoka
 */
public class RosterItemProxy implements RosterItem {

    private RosterItem item;
    private AuthToken authToken;
    private Permissions permissions;

    public RosterItemProxy(RosterItem item, AuthToken authToken, Permissions permissions) {
        this.authToken = authToken;
        this.permissions = permissions;
        this.item = item;
    }

    public SubType getSubStatus() {
        return item.getSubStatus();
    }

    public void setSubStatus(SubType subStatus) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            item.setSubStatus(subStatus);
        }
        else {
            throw new org.jivesoftware.messenger.auth.UnauthorizedException();
        }
    }

    public AskType getAskStatus() {
        return item.getAskStatus();
    }

    public void setAskStatus(AskType askStatus) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            item.setAskStatus(askStatus);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RecvType getRecvStatus() {
        return item.getRecvStatus();
    }

    public void setRecvStatus(RecvType recvStatus) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            item.setRecvStatus(recvStatus);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public XMPPAddress getJid() {
        return item.getJid();
    }

    public String getNickname() {
        return item.getNickname();
    }

    public void setNickname(String nickname) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            item.setNickname(nickname);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public List getGroups() {
        return item.getGroups();
    }

    public void setGroups(List groups) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            item.setGroups(groups);
        }
        else {
            throw new UnauthorizedException();
        }
    }
}
