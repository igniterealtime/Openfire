/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.ConcurrentGroupList;
import org.jivesoftware.openfire.group.ConcurrentGroupMap;
import org.jivesoftware.openfire.muc.spi.FMUCMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit tests that verify the implementation of {@link MUCRoom}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@ExtendWith(MockitoExtension.class)
public class MUCRoomTest {

    @Mock
    private XMPPServer xmppServer;

    @Mock
    private MultiUserChatManager mockMUCManager;

    @Mock
    private MultiUserChatService mockService;

    @BeforeEach
    public void setup() throws Exception {
        doReturn("conference").when(mockService).getServiceName();
        doReturn("conference.example.org").when(mockService).getServiceDomain();

        when(mockMUCManager.getMultiUserChatService(anyString())).thenReturn(mockService);
        //when(mockMUCManager.getMultiUserChatService((JID)any())).thenReturn(mockService);
        when(xmppServer.getMultiUserChatManager()).thenReturn(mockMUCManager);

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);
    }

    /**
     * Asserts that when a populated instance of MUCRoom is serialized and the resulting data deserialized again, an
     * instance results that is equal to the original input.
     */
    @Test
    public void testExternalizedEquals() throws Exception
    {
        // Setup test fixture.

        final MUCRole roomRole = new MUCRole();
        populateField(roomRole, "roomJid", new JID("room-test-role-jid@example.org"));
        populateField(roomRole, "role", MUCRole.Role.visitor);
        populateField(roomRole, "affiliation", MUCRole.Affiliation.member);
        populateField(roomRole, "rJID", new JID("room-test-jid@conference.example.org"));

        final List<MUCRole> occupants = new ArrayList<>();
        final MUCRole occupantA = new MUCRole();
        populateField(occupantA, "roomJid", new JID("occupantA@example.org"));
        populateField(occupantA, "role", MUCRole.Role.participant);
        populateField(occupantA, "affiliation", MUCRole.Affiliation.member);
        populateField(occupantA, "rJID", new JID("room-test-jid@conference.example.org/occupantA"));
        occupants.add(occupantA);

        final MUCRole occupantB = new MUCRole();
        populateField(occupantB, "roomJid", new JID("occupantBA@example.org"));
        populateField(occupantB, "role", MUCRole.Role.none);
        populateField(occupantB, "affiliation", MUCRole.Affiliation.member);
        populateField(occupantB, "rJID", new JID("room-test-jid@conference.example.org/occupantB"));
        occupants.add(occupantB);

        final ConcurrentGroupList<JID> owners = new ConcurrentGroupList<>();
        owners.add(new JID("unit-test-owner-1@example.com"));
        owners.add(new JID("unit-test-owner-2@example.org"));

        final ConcurrentGroupList<JID> admins = new ConcurrentGroupList<>();
        admins.add(new JID("unit-test-admin-1@example.com"));
        admins.add(new JID("unit-test-admin-2@example.org"));

        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(new JID("unit-test-member-1@example.com"), "test nick");
        members.put(new JID("unit-test-member-2@example.org"), "nick unit test");

        final ConcurrentGroupList<JID> outcasts = new ConcurrentGroupList<>();
        outcasts.add(new JID("unit-test-outcast-1@example.com"));
        outcasts.add(new JID("unit-test-outcast-2@example.org"));

        final List<MUCRole.Role> rolesToBroadcastPresence = new ArrayList<>();
        rolesToBroadcastPresence.add(MUCRole.Role.visitor);
        rolesToBroadcastPresence.add(MUCRole.Role.none);
        rolesToBroadcastPresence.add(MUCRole.Role.moderator);

        final MUCRoom input = new MUCRoom(); // Set all fields to a non-default value, for a more specific test!
        populateField(input, "mucService", mockService);
        populateField(input, "name", "test-room-name");
        populateField(input, "occupants", occupants);
        populateField(input, "role", roomRole);
        populateField(input, "startTime", Instant.now().minus(Duration.ofDays(4)).toEpochMilli());
        populateField(input, "endTime", Instant.now().minus(Duration.ofHours(23)).toEpochMilli());
        populateField(input, "isDestroyed", true);
        populateField(input, "roomHistory", new MUCRoomHistory(input, new HistoryStrategy(new JID("test-room-name", mockService.getServiceDomain(),null), null)));
        populateField(input, "lockedTime", Instant.now().minus(Duration.ofMinutes(141)).toEpochMilli());
        populateField(input, "owners", owners);
        populateField(input, "admins", admins);
        populateField(input, "members", members);
        populateField(input, "outcasts", outcasts);
        populateField(input, "naturalLanguageName", "Test natural language value");
        populateField(input, "description", "test description");
        populateField(input, "canOccupantsChangeSubject", true);
        populateField(input, "maxUsers", 25);
        populateField(input, "rolesToBroadcastPresence", rolesToBroadcastPresence);
        populateField(input, "publicRoom", true);
        populateField(input, "persistent", true);
        populateField(input, "moderated", true);
        populateField(input, "membersOnly", true);
        populateField(input, "canOccupantsInvite", true);
        populateField(input, "password", "a unit test password");
        populateField(input, "canAnyoneDiscoverJID", true);
        populateField(input, "canSendPrivateMessage", "a test minimal role");
        populateField(input, "logEnabled", true);
        populateField(input, "loginRestrictedToNickname", true);
        populateField(input, "canChangeNickname", true);
        populateField(input, "registrationEnabled", true);
        populateField(input, "fmucEnabled", true);
        populateField(input, "fmucOutboundNode", new JID("fmuc-unit-test-node@example.com"));
        populateField(input, "fmucOutboundMode", FMUCMode.MasterMaster);
        populateField(input, "fmucInboundNodes", new HashSet<>(Arrays.asList(new JID("fmuc-inbound-z@example.org"), new JID("fmuc-inbound-y@example.org"))));
        populateField(input, "subject", "test subject");
        populateField(input, "roomID", 325);
        populateField(input, "creationDate", Date.from(Instant.now().minus(Duration.ofHours(3252))));
        populateField(input, "modificationDate", Date.from(Instant.now().minus(Duration.ofSeconds(91))));
        populateField(input, "emptyDate", Date.from(Instant.now().minus(Duration.ofMinutes(825))));
        populateField(input, "savedToDB", true);

        // Return our newly created room when it's being requested from the service (used by serialization).
        //when(mockService.getChatRoom(input.getJID().getNode())).thenReturn(input);

        // Execute system under test.
        final byte[] serialized;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
            oos.writeObject(input);
            serialized = baos.toByteArray();
        }

        final Object result;
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
             final ObjectInputStream ois = new ObjectInputStream(bais)) {
            result = ois.readObject();
        }

        // Verify results.
        assertNotNull(result);
        assertTrue(result instanceof MUCRoom);
        assertEquals(input, result);
        assertEquals(2, ((MUCRoom) result).getOccupantsCount());
        assertEquals(new ArrayList<>(input.getOccupants()), new ArrayList<>(((MUCRoom) result).getOccupants()));
        assertEquals(input.getName(), ((MUCRoom) result).getName());
        assertEquals(input.getID(), ((MUCRoom) result).getID());
        assertEquals(input.getCreationDate(), ((MUCRoom) result).getCreationDate());
        assertEquals(input.getModificationDate(), ((MUCRoom) result).getModificationDate());
        assertEquals(input.getEmptyDate(), ((MUCRoom) result).getEmptyDate());
        assertEquals(input.getRole(), ((MUCRoom) result).getRole());
        assertEquals(input.getChatLength(), ((MUCRoom) result).getChatLength());
        assertEquals(input.isLocked(), ((MUCRoom) result).isLocked());
        assertEquals(input.isManuallyLocked(), ((MUCRoom) result).isManuallyLocked());
        assertEquals(input.getSubject(), ((MUCRoom) result).getSubject());
        assertEquals(input.getRoomHistory(), ((MUCRoom) result).getRoomHistory());
        assertEquals(input.getOwners(), ((MUCRoom) result).getOwners());
        assertEquals(input.getAdmins(), ((MUCRoom) result).getAdmins());
        assertEquals(input.getMembers(), ((MUCRoom) result).getMembers());
        assertEquals(input.getOutcasts(), ((MUCRoom) result).getOutcasts());
        assertEquals(input.getModerators(), ((MUCRoom) result).getModerators());
        assertEquals(input.getParticipants(), ((MUCRoom) result).getParticipants());
        assertEquals(input.canAnyoneDiscoverJID(), ((MUCRoom) result).canAnyoneDiscoverJID());
        assertEquals(input.canSendPrivateMessage(), ((MUCRoom) result).canSendPrivateMessage());

        //assertEquals(input.getCachedSize(), ((MUCRole) result).getCachedSize());
    }

    public static <E> void populateField(final E object, final String fieldName, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        try {
            field.set(object, value);
        } finally {
            field.setAccessible(false);
        }
    }
}
