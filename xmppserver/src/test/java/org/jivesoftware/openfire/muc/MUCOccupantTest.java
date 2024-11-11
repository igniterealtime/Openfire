/*
 * Copyright (C) 2021-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests that verify the implementation of {@link MUCOccupant}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@ExtendWith(MockitoExtension.class)
public class MUCOccupantTest
{

    @Mock
    private MUCRoom mockRoom;

    @BeforeEach
    public void setup() throws Exception {
        doReturn(new JID("testroom@conference.example.org")).when(mockRoom).getJID();
    }

    /**
     * Asserts that when a populated instance of MUCOccupant is serialized and the resulting data deserialized again, an
     * instance results that is equal to the original input.
     */
    @Test
    public void testExternalizedEquals() throws Exception
    {
        // Setup test fixture.
        final JID userAddress = new JID("unittest@example.org");
        final String nickname = "nickname for test";
        final Role role = Role.participant;
        final Affiliation affiliation = Affiliation.admin;
        final Presence presence = new Presence();
        presence.setFrom(userAddress);
        presence.setTo(mockRoom.getJID());
        presence.addChildElement("x", "http://jabber.org/protocol/muc");
        presence.addChildElement("x", "http://jivesoftware.org/protocol/muc").addElement("deaf-occupant");
        final MUCOccupant input = new MUCOccupant(mockRoom, nickname, role, affiliation, userAddress, presence); // Set all fields to a non-default value, for a more specific test!

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
        assertTrue(result instanceof MUCOccupant);
        assertEquals(input, result);
        assertEquals(input.getAffiliation(), ((MUCOccupant) result).getAffiliation());
        assertEquals(input.getNickname(), ((MUCOccupant) result).getNickname());
        assertEquals(input.getPresence().toXML(), ((MUCOccupant) result).getPresence().toXML());
        assertEquals(input.getReportedFmucAddress(), ((MUCOccupant) result).getReportedFmucAddress());
        assertEquals(input.getRole(), ((MUCOccupant) result).getRole());
        assertEquals(input.getOccupantJID(), ((MUCOccupant) result).getOccupantJID());
        assertEquals(input.getUserAddress(), ((MUCOccupant) result).getUserAddress());
        assertEquals(input.isRemoteFmuc(), ((MUCOccupant) result).isRemoteFmuc());
        assertEquals(input.isVoiceOnly(), ((MUCOccupant) result).isVoiceOnly());

        final Field extendedInformationField = MUCOccupant.class.getDeclaredField("extendedInformation");
        extendedInformationField.setAccessible(true);
        final Element expectedExtendedInformation = (Element) extendedInformationField.get(input);
        final Element actualExtendedInformation = (Element) extendedInformationField.get(result);
        extendedInformationField.setAccessible(false);

        assertEquals(expectedExtendedInformation.asXML(), actualExtendedInformation.asXML());

        // TODO Worry why the cached sizes differ (it's caused by the presence and extendedInformation element sizes).
        // assertEquals(input.getCachedSize(), ((MUCOccupant) result).getCachedSize());
    }
}
