/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Christian Schudt
 */
public class MucPrivilegesTest {

    @Test
    public void ownerShouldBeAbleToDoAnything() {
        assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToRevokeModeratorPrivilegesFromOtherAdmin() {
        assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToGrantMembership() {
        assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.none, MUCRole.Role.none, MUCRole.Affiliation.member, MUCRole.Role.participant));
    }

    @Test
    public void adminModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromOwner() {
        assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberShouldNotBeAbleToDoAnything() {
        assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.participant, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }
}
