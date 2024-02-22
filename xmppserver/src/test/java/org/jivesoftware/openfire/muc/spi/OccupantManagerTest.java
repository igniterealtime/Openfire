/*
 * Copyright (C) 2022-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedTask;
import org.jivesoftware.openfire.muc.cluster.OccupantRemovedTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class OccupantManagerTest
{
    @Mock(strictness = Mock.Strictness.LENIENT)
    private XMPPServer xmppServer;

    @Mock
    private MultiUserChatService mockService;

    @BeforeEach
    public void setup() throws Exception {
        doReturn("conference").when(mockService).getServiceName();
        doReturn("conference.example.org").when(mockService).getServiceDomain();

        when(xmppServer.getNodeID()).thenReturn(NodeID.getInstance(UUID.randomUUID().toString().getBytes()));
        doAnswer(invocationOnMock -> {
            final JID jid = invocationOnMock.getArgument(0);
            return jid.getDomain().equals("example.org");
        }).when(xmppServer).isLocal(any(JID.class));

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);
    }

    @Test
    public void testEmpty() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);

        // Execute system under test.

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());
        assertEquals(0, occupantManager.getNodeByLocalOccupant().size());
        assertEquals(0, occupantManager.getLocalOccupantsByNode().size());
    }

    @Test
    public void testOneLocalOccupant() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("room", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";

        // Execute system under test.
        occupantManager.occupantJoined(roomJID, userJID, nickname);

        // Verify results.
        assertEquals(1, occupantManager.getLocalOccupants().size());
        assertEquals(roomJID.getNode(), occupantManager.getLocalOccupants().iterator().next().getRoomName());
        assertEquals(userJID, occupantManager.getLocalOccupants().iterator().next().getRealJID());
        assertEquals(nickname, occupantManager.getLocalOccupants().iterator().next().getNickname());

        assertEquals(1, occupantManager.getNodeByLocalOccupant().size());

        assertEquals(xmppServer.getNodeID(), occupantManager.getNodeByLocalOccupant().entrySet().iterator().next().getValue());
        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(1, occupantManager.getLocalOccupantsByNode().get(xmppServer.getNodeID()).size());
    }

    @Test
    public void testLocalOccupantLeft() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("room", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";

        // Execute system under test.
        occupantManager.occupantJoined(roomJID, userJID, nickname);
        occupantManager.occupantLeft(roomJID, userJID, nickname);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());
        assertEquals(0, occupantManager.getNodeByLocalOccupant().size());
        assertEquals(0, occupantManager.getLocalOccupantsByNode().size());
    }

    @Test
    public void testTwoLocalOccupantsSameUserDifferentRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID firstRoomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID secondRoomJID = new JID("roomB", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";

        // Execute system under test.
        occupantManager.occupantJoined(firstRoomJID, userJID, nickname);
        occupantManager.occupantJoined(secondRoomJID, userJID, nickname);

        // Verify results.
        assertEquals(2, occupantManager.getLocalOccupants().size());
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(firstRoomJID.getNode())));
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(secondRoomJID.getNode())));
        assertTrue(occupantManager.getLocalOccupants().stream().allMatch(occupant -> occupant.getRealJID().equals(userJID)));
        assertTrue(occupantManager.getLocalOccupants().stream().allMatch(occupant -> occupant.getNickname().equals(nickname)));

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(xmppServer.getNodeID())));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(xmppServer.getNodeID()).size());
    }

    @Test
    public void testTwoLocalOccupantsDifferentUsersDifferentRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID firstRoomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID secondRoomJID = new JID("roomB", mockService.getServiceDomain(), null);
        final JID firstUserJID = new JID("johndoe", "example.org", null);
        final JID secondUserJID = new JID("janedoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Jane Doe";

        // Execute system under test.
        occupantManager.occupantJoined(firstRoomJID, firstUserJID, firstNickname);
        occupantManager.occupantJoined(secondRoomJID, secondUserJID, secondNickname);

        // Verify results.
        assertEquals(2, occupantManager.getLocalOccupants().size());
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(firstRoomJID.getNode()) && occupant.getRealJID().equals(firstUserJID) && occupant.getNickname().equals(firstNickname)));
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(secondRoomJID.getNode()) && occupant.getRealJID().equals(secondUserJID) && occupant.getNickname().equals(secondNickname)));

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(xmppServer.getNodeID())));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(xmppServer.getNodeID()).size());
    }

    @Test
    public void testTwoLocalOccupantsSameUserSameRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Another John";

        // Execute system under test.
        occupantManager.occupantJoined(roomJID, userJID, firstNickname);
        occupantManager.occupantJoined(roomJID, userJID, secondNickname);

        // Verify results.
        assertEquals(2, occupantManager.getLocalOccupants().size());
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(roomJID.getNode()) && occupant.getRealJID().equals(userJID) && occupant.getNickname().equals(firstNickname)));
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(roomJID.getNode()) && occupant.getRealJID().equals(userJID) && occupant.getNickname().equals(secondNickname)));

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(xmppServer.getNodeID())));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(xmppServer.getNodeID()).size());
    }

    @Test
    public void testTwoLocalOccupantsDifferentUsersSameRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID firstUserJID = new JID("johndoe", "example.org", null);
        final JID secondUserJID = new JID("janedoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Jane Doe";

        // Execute system under test.
        occupantManager.occupantJoined(roomJID, firstUserJID, firstNickname);
        occupantManager.occupantJoined(roomJID, secondUserJID, secondNickname);

        // Verify results.
        assertEquals(2, occupantManager.getLocalOccupants().size());
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(roomJID.getNode()) && occupant.getRealJID().equals(firstUserJID) && occupant.getNickname().equals(firstNickname)));
        assertTrue(occupantManager.getLocalOccupants().stream().anyMatch(occupant -> occupant.getRoomName().equals(roomJID.getNode()) && occupant.getRealJID().equals(secondUserJID) && occupant.getNickname().equals(secondNickname)));

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(xmppServer.getNodeID())));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(xmppServer.getNodeID()).size());
    }

    @Test
    public void testOneRemoteOccupant() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("room", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask addedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), nickname, userJID, remoteNode);

        // Execute system under test.
        occupantManager.process(addedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());

        assertEquals(1, occupantManager.getNodeByLocalOccupant().size());

        assertEquals(remoteNode, occupantManager.getNodeByLocalOccupant().entrySet().iterator().next().getValue());
        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(1, occupantManager.getLocalOccupantsByNode().get(remoteNode).size());
    }

    @Test
    public void testRemoteOccupantLeft() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("room", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask addedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), nickname, userJID, remoteNode);
        final OccupantRemovedTask removedTask = new OccupantRemovedTask(mockService.getServiceName(), roomJID.getNode(), nickname, userJID, remoteNode);

        // Execute system under test.
        occupantManager.process(addedTask);
        occupantManager.process(removedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());
        assertEquals(0, occupantManager.getNodeByLocalOccupant().size());
        assertEquals(0, occupantManager.getLocalOccupantsByNode().size());
    }

    @Test
    public void testTwoRemoteOccupantsSameUserDifferentRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID firstRoomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID secondRoomJID = new JID("roomB", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask firstAddedTask = new OccupantAddedTask(mockService.getServiceName(), firstRoomJID.getNode(), nickname, userJID, remoteNode);
        final OccupantAddedTask secondAddedTask = new OccupantAddedTask(mockService.getServiceName(), secondRoomJID.getNode(), nickname, userJID, remoteNode);

        // Execute system under test.
        occupantManager.process(firstAddedTask);
        occupantManager.process(secondAddedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(remoteNode)));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(remoteNode).size());
    }

    @Test
    public void testTwoRemoteOccupantsDifferentUsersDifferentRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID firstRoomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID secondRoomJID = new JID("roomB", mockService.getServiceDomain(), null);
        final JID firstUserJID = new JID("johndoe", "example.org", null);
        final JID secondUserJID = new JID("janedoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Jane Doe";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask firstAddedTask = new OccupantAddedTask(mockService.getServiceName(), firstRoomJID.getNode(), firstNickname, firstUserJID, remoteNode);
        final OccupantAddedTask secondAddedTask = new OccupantAddedTask(mockService.getServiceName(), secondRoomJID.getNode(), secondNickname, secondUserJID, remoteNode);

        // Execute system under test.
        occupantManager.process(firstAddedTask);
        occupantManager.process(secondAddedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(remoteNode)));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(remoteNode).size());
    }

    @Test
    public void testTwoRemoteOccupantsSameUserSameRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Another John";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask firstAddedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), firstNickname, userJID, remoteNode);
        final OccupantAddedTask secondAddedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), secondNickname, userJID, remoteNode);

        // Execute system under test.
        occupantManager.process(firstAddedTask);
        occupantManager.process(secondAddedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(remoteNode)));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(remoteNode).size());
    }

    @Test
    public void testTwoRemoteOccupantsDifferentUsersSameRoom() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("roomA", mockService.getServiceDomain(), null);
        final JID firstUserJID = new JID("johndoe", "example.org", null);
        final JID secondUserJID = new JID("janedoe", "example.org", null);
        final String firstNickname = "John Doe";
        final String secondNickname = "Jane Doe";
        final NodeID remoteNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final OccupantAddedTask firstAddedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), firstNickname, firstUserJID, remoteNode);
        final OccupantAddedTask secondAddedTask = new OccupantAddedTask(mockService.getServiceName(), roomJID.getNode(), secondNickname, secondUserJID, remoteNode);

        // Execute system under test.
        occupantManager.process(firstAddedTask);
        occupantManager.process(secondAddedTask);

        // Verify results.
        assertEquals(0, occupantManager.getLocalOccupants().size());

        assertEquals(2, occupantManager.getNodeByLocalOccupant().size());
        assertTrue(occupantManager.getNodeByLocalOccupant().entrySet().stream().allMatch(entry -> entry.getValue().equals(remoteNode)));

        assertEquals(1, occupantManager.getLocalOccupantsByNode().size());
        assertEquals(2, occupantManager.getLocalOccupantsByNode().get(remoteNode).size());
    }

    @Test
    public void testRegisterActivity() throws Exception
    {
        // Setup test fixture.
        final OccupantManager occupantManager = new OccupantManager(mockService);
        final JID roomJID = new JID("room", mockService.getServiceDomain(), null);
        final JID userJID = new JID("johndoe", "example.org", null);
        final String nickname = "John Doe";
        final Duration pause = Duration.ofMillis(10);

        // Execute system under test.
        occupantManager.occupantJoined(roomJID, userJID, nickname);
        final Instant start = Instant.now();
        Thread.sleep(pause.toMillis());
        occupantManager.registerActivity(userJID);

        // Verify results.
        final Instant lastActive = occupantManager.getLocalOccupants().iterator().next().getLastActive();
        assertTrue(Duration.between(start, lastActive).compareTo(pause) >= 0);
    }
}
