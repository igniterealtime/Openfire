/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.muc.spi;


import org.jivesoftware.openfire.muc.MUCRoom;
import org.xmpp.packet.JID;
import org.xmpp.resultsetmanagement.Result;

public class MUCRoomSearchInfo implements Result {

    private final String serviceName;
    private final JID jid;
    private final String name;
    private final String subject;
    private final String naturalLanguageName;
    private final String description;
    private final boolean isLocked;
    private final boolean isPublicRoom;
    private final int occupantsCount;
    private final int participantCount;
    private final int maxUsers;
    private final boolean isMembersOnly;
    private final boolean isPasswordProtected;
    private final boolean canAnyoneDiscoverJID;


    public MUCRoomSearchInfo(final MUCRoom room) {
        this.serviceName = room.getMUCService().getServiceName();
        this.jid = room.getJID();
        this.name = room.getName();
        this.subject = room.getSubject();
        this.naturalLanguageName = room.getNaturalLanguageName();
        this.description = room.getDescription();
        this.isLocked = room.isLocked();
        this.isPublicRoom = room.isPublicRoom();
        this.occupantsCount = room.getOccupantsCount();
        this.participantCount = room.getParticipants().size();
        this.maxUsers = room.getMaxUsers();
        this.isMembersOnly = room.isMembersOnly();
        this.isPasswordProtected = room.isPasswordProtected();
        this.canAnyoneDiscoverJID = room.canAnyoneDiscoverJID();
    }

    public String getServiceName() {
        return serviceName;
    }

    public JID getJID() {
        return jid;
    }

    public String getSubject() {
        return subject;
    }

    public String getName() {
        return name;
    }

    public String getNaturalLanguageName() {
        return naturalLanguageName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public boolean isPublicRoom() {
        return isPublicRoom;
    }

    public int getOccupantsCount() {
        return occupantsCount;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public boolean isMembersOnly() {
        return isMembersOnly;
    }

    public boolean isPasswordProtected() {
        return isPasswordProtected;
    }

    public boolean canAnyoneDiscoverJID() {
        return canAnyoneDiscoverJID;
    }

    @Override
    public String getUID() {
        return getJID().toString();
    }
}
