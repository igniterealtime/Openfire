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
package org.jivesoftware.openfire.muc;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests that verify the implementation of {@link MUCUser}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@RunWith(MockitoJUnitRunner.class)
public class MUCUserTest {

    @Mock
    private MultiUserChatService mockService;

    @Before
    public void setup() throws Exception {
        doReturn("conference").when(mockService).getServiceName();
    }

    /**
     * Asserts that when a populated instance of MUCUser is serialized and the resulting data deserialized again, an
     * instance results that is equal to the original input.
     */
    @Test
    public void testExternalizedEquals() throws Exception
    {
        // Setup test fixture.
        final JID userAddress = new JID("unittest@example.org");
        final MUCUser input = new MUCUser(mockService, userAddress); // Set all fields to a non-default value, for a more specific test!
        input.addRoomName("testRoom");
        input.addRoomName("Room-Test");
        final Field lastPacketTimeField = MUCUser.class.getDeclaredField("lastPacketTime");
        lastPacketTimeField.setAccessible(true);
        lastPacketTimeField.set(input, Instant.ofEpochMilli(13151241));
        lastPacketTimeField.setAccessible(false);

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
        assertTrue(result instanceof MUCUser);
        assertEquals(input, result);
        assertEquals(input.getAddress(), ((MUCUser) result).getAddress());
        assertEquals(input.getAddress(), ((MUCUser) result).getAddress());
        assertEquals(input.getLastPacketTime(), ((MUCUser) result).getLastPacketTime());
        // UnmodifiableCollection (As returned by MUCUser#getRoomNames) does not implement #equals. Work-around by using a different collection that does.
        assertEquals(new ArrayList<>(input.getRoomNames()), new ArrayList<>(((MUCUser) result).getRoomNames()));

        assertEquals(input.getCachedSize(), ((MUCUser) result).getCachedSize());
    }
}
