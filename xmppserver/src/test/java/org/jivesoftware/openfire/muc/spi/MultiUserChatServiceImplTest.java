/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.muc.Affiliation;
import org.jivesoftware.openfire.muc.MUCOccupant;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MultiUserChatServiceImplTest
{
    @Mock
    private MUCRoom room;

    @Mock
    private MUCOccupant inviter;

    @Mock
    private MUCOccupant roomSelfOccupant;

    @Test
    public void testAddInviteeAsMemberUsesRoomAffiliationWhenOccupantInvitesAreAllowed() throws Exception
    {
        final JID invitee = new JID("invitee@example.org");
        when(room.canOccupantsInvite()).thenReturn(true);
        when(room.getSelfRepresentation()).thenReturn(roomSelfOccupant);
        when(roomSelfOccupant.getAffiliation()).thenReturn(Affiliation.owner);

        MultiUserChatServiceImpl.addInviteeAsMember(room, invitee, inviter);

        verify(room).addMember(invitee, null, Affiliation.owner);
    }

    @Test
    public void testAddInviteeAsMemberUsesInviterAffiliationWhenOccupantInvitesAreDisallowed() throws Exception
    {
        final JID invitee = new JID("invitee@example.org");
        when(room.canOccupantsInvite()).thenReturn(false);
        when(inviter.getAffiliation()).thenReturn(Affiliation.member);

        MultiUserChatServiceImpl.addInviteeAsMember(room, invitee, inviter);

        verify(room).addMember(invitee, null, Affiliation.member);
    }
}
