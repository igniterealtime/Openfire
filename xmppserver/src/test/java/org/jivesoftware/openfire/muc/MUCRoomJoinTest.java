/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.ConcurrentGroupList;
import org.jivesoftware.openfire.group.ConcurrentGroupMap;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the XEP-0045 compliance of {@link MUCRoom}'s join precondition checks
 * and self-presence status code generation.
 *
 * References:
 * <ul>
 *   <li>XEP-0045 §7.2.5  Password-Protected Rooms</li>
 *   <li>XEP-0045 §7.2.6  Members-Only Rooms</li>
 *   <li>XEP-0045 §7.2.7  Banned Users</li>
 *   <li>XEP-0045 §7.2.8  Nickname Conflict</li>
 *   <li>XEP-0045 §7.2.9  Max Users</li>
 *   <li>XEP-0045 §7.2.10 Locked Room</li>
 *   <li>XEP-0045 §7.2.2  Presence Broadcast (self-presence status codes)</li>
 *   <li>XEP-0045 §7.2.3  Non-Anonymous Rooms (status code 100)</li>
 *   <li>XEP-0045 §7.2.12 Room Logging (status code 170)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
public class MUCRoomJoinTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private MultiUserChatService mockService;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private XMPPServer xmppServer;

    private MUCRoom room;

    private static final JID ROOM_JID = new JID("testroom", "conference.example.org", null);
    private static final JID USER_A = new JID("usera@example.org/res1");
    private static final JID USER_B = new JID("userb@example.org/res1");

    @BeforeEach
    public void setup() throws Exception {
        doReturn("conference").when(mockService).getServiceName();
        doReturn("conference.example.org").when(mockService).getServiceDomain();
        doReturn(null).when(mockService).getMUCDelegate();
        doReturn(false).when(mockService).isSysadmin(any(JID.class));
        doReturn(true).when(mockService).isPasswordRequiredForSysadminsToJoinRoom();

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        room = new MUCRoom();
        setField(room, "mucService", mockService);
        setField(room, "name", "testroom");
        setField(room, "owners", new ConcurrentGroupList<>());
        setField(room, "admins", new ConcurrentGroupList<>());
        setField(room, "members", new ConcurrentGroupMap<>());
        setField(room, "outcasts", new ConcurrentGroupList<>());
        setField(room, "occupants", new CopyOnWriteArrayList<>());
        setField(room, "lockedTime", 0L);
        setField(room, "maxUsers", 0); // 0 = unlimited
        setField(room, "membersOnly", false);
        setField(room, "password", null);
        setField(room, "loginRestrictedToNickname", false);
        setField(room, "canAnyoneDiscoverJID", true);
        setField(room, "logEnabled", false);
        setField(room, "isDestroyed", false);
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.7: Banned Users
    // "If the user has been banned from the room (i.e., has an affiliation of
    //  'outcast'), the service MUST deny access to the room and inform the user
    //  of the fact that they are banned; this is done by returning a presence
    //  stanza of type 'error' specifying a <forbidden/> error condition"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_OutcastCannotJoin() throws Exception {
        // XEP-0045 §7.2.7: A banned user (outcast) MUST be denied access.
        assertThrowsPrecondition(
            "checkJoinRoomPreconditionIsOutcast",
            ForbiddenException.class,
            USER_A, Affiliation.outcast
        );
    }

    @Test
    public void testJoinPrecondition_NonOutcastCanJoin() throws Exception {
        // Non-outcasts MUST be allowed to pass the outcast check.
        assertNoPreconditionException(
            "checkJoinRoomPreconditionIsOutcast",
            USER_A, Affiliation.none
        );
        assertNoPreconditionException(
            "checkJoinRoomPreconditionIsOutcast",
            USER_A, Affiliation.member
        );
        assertNoPreconditionException(
            "checkJoinRoomPreconditionIsOutcast",
            USER_A, Affiliation.admin
        );
        assertNoPreconditionException(
            "checkJoinRoomPreconditionIsOutcast",
            USER_A, Affiliation.owner
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.6: Members-Only Rooms
    // "If the room is members-only but the user is not on the member list, the
    //  service MUST deny access to the room and inform the user that they are
    //  not allowed to enter the room; this is done by returning a presence
    //  stanza of type 'error' specifying a <registration-required/> error"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_MembersOnlyRoom_NonMember_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.6: Unaffiliated user MUST NOT enter a members-only room.
        setField(room, "membersOnly", true);

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionMemberOnly",
            RegistrationRequiredException.class,
            USER_A, Affiliation.none
        );
    }

    @Test
    public void testJoinPrecondition_MembersOnlyRoom_Member_CanJoin() throws Exception {
        // XEP-0045 §7.2.6: A member MUST be allowed to enter a members-only room.
        setField(room, "membersOnly", true);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMemberOnly",
            USER_A, Affiliation.member
        );
    }

    @Test
    public void testJoinPrecondition_MembersOnlyRoom_Admin_CanJoin() throws Exception {
        // XEP-0045 §7.2.6: Admins MUST be allowed to enter a members-only room.
        setField(room, "membersOnly", true);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMemberOnly",
            USER_A, Affiliation.admin
        );
    }

    @Test
    public void testJoinPrecondition_MembersOnlyRoom_Owner_CanJoin() throws Exception {
        // XEP-0045 §7.2.6: Owners MUST be allowed to enter a members-only room.
        setField(room, "membersOnly", true);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMemberOnly",
            USER_A, Affiliation.owner
        );
    }

    @Test
    public void testJoinPrecondition_OpenRoom_NonMember_CanJoin() throws Exception {
        // An open room allows unaffiliated users.
        setField(room, "membersOnly", false);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMemberOnly",
            USER_A, Affiliation.none
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.9: Max Users
    // "If the room has reached its maximum number of occupants, the service
    //  SHOULD deny access to the room and inform the user of the restriction;
    //  this is done by returning a presence stanza of type 'error' specifying
    //  a <service-unavailable/> error condition"
    // "if the room has reached its maximum number of occupants and a room admin
    //  or owner attempts to join, the room MUST allow the admin or owner to join"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_MaxUsersReached_ShouldBeDenied() throws Exception {
        // XEP-0045 §7.2.9: When max occupants is reached, new users SHOULD be denied.
        setField(room, "maxUsers", 1);
        room.occupants.add(createDummyOccupant("existing-user", new JID("existing@example.org")));

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionMaxOccupants",
            ServiceUnavailableException.class,
            USER_A
        );
    }

    @Test
    public void testJoinPrecondition_MaxUsersReached_AdminCanJoin() throws Exception {
        // XEP-0045 §7.2.9: Admins MUST be allowed to join even when the room is full.
        setField(room, "maxUsers", 1);
        room.occupants.add(createDummyOccupant("existing-user", new JID("existing@example.org")));

        final ConcurrentGroupList<JID> admins = new ConcurrentGroupList<>();
        admins.add(USER_A.asBareJID());
        setField(room, "admins", admins);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMaxOccupants",
            USER_A
        );
    }

    @Test
    public void testJoinPrecondition_MaxUsersReached_OwnerCanJoin() throws Exception {
        // XEP-0045 §7.2.9: Owners MUST be allowed to join even when the room is full.
        setField(room, "maxUsers", 1);
        room.occupants.add(createDummyOccupant("existing-user", new JID("existing@example.org")));

        final ConcurrentGroupList<JID> owners = new ConcurrentGroupList<>();
        owners.add(USER_A.asBareJID());
        setField(room, "owners", owners);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMaxOccupants",
            USER_A
        );
    }

    @Test
    public void testJoinPrecondition_UnlimitedUsers_CanJoin() throws Exception {
        // maxUsers == 0 means no limit.
        setField(room, "maxUsers", 0);
        room.occupants.add(createDummyOccupant("user1", new JID("u1@example.org")));
        room.occupants.add(createDummyOccupant("user2", new JID("u2@example.org")));

        assertNoPreconditionException(
            "checkJoinRoomPreconditionMaxOccupants",
            USER_A
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.10: Locked Room
    // "If a user attempts to enter a room while it is 'locked' (i.e., before
    //  the room creator provides an initial configuration), the service MUST
    //  refuse entry and return an <item-not-found/> error to the user"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_LockedRoom_NonOwner_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.10: Non-owners MUST NOT enter a locked room.
        setField(room, "lockedTime", System.currentTimeMillis());

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionLocked",
            RoomLockedException.class,
            USER_A
        );
    }

    @Test
    public void testJoinPrecondition_LockedRoom_Owner_CanJoin() throws Exception {
        // XEP-0045 §7.2.10: The room owner is allowed to enter a locked room.
        setField(room, "lockedTime", System.currentTimeMillis());
        final ConcurrentGroupList<JID> owners = new ConcurrentGroupList<>();
        owners.add(USER_A.asBareJID());
        setField(room, "owners", owners);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionLocked",
            USER_A
        );
    }

    @Test
    public void testJoinPrecondition_UnlockedRoom_CanJoin() throws Exception {
        // When the room is not locked, any user passes this check.
        setField(room, "lockedTime", 0L);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionLocked",
            USER_A
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.8: Nickname Conflict
    // "If the room already contains another user with the nickname desired by
    //  the user seeking to enter the room (or if the nickname is reserved by
    //  another user), the service MUST deny access to the room and inform the
    //  user of the conflict; this is done by returning a presence stanza of
    //  type 'error' specifying a <conflict/> error condition"
    // "if the bare JID of the present occupant matches the bare JID of the user
    //  seeking to enter the room, then the service SHOULD allow entry"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_NicknameInUse_DifferentUser_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.8: A different user already has this nickname → conflict.
        room.occupants.add(createDummyOccupant("witch", new JID("other@example.org")));

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionNicknameInUse",
            UserAlreadyExistsException.class,
            USER_A, "witch"
        );
    }

    @Test
    public void testJoinPrecondition_NicknameInUse_SameBareJID_CanJoin() throws Exception {
        // XEP-0045 §7.2.8: Same bare JID, different resource → multi-session join, allowed.
        room.occupants.add(createDummyOccupant("witch", new JID("usera@example.org/res2")));

        assertNoPreconditionException(
            "checkJoinRoomPreconditionNicknameInUse",
            USER_A, "witch"
        );
    }

    @Test
    public void testJoinPrecondition_NicknameNotInUse_CanJoin() throws Exception {
        // No occupant has this nickname.
        room.occupants.add(createDummyOccupant("otherwitch", new JID("other@example.org")));

        assertNoPreconditionException(
            "checkJoinRoomPreconditionNicknameInUse",
            USER_A, "witch"
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.5: Password-Protected Rooms
    // "If the room requires a password and the user did not supply one (or the
    //  password provided is incorrect), the service MUST deny access to the
    //  room and inform the user that they are unauthorized; this is done by
    //  returning a presence stanza of type 'error' specifying a
    //  <not-authorized/> error"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_WrongPassword_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.5: Wrong password MUST be rejected.
        setField(room, "password", "secret");

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionPasswordProtection",
            org.jivesoftware.openfire.auth.UnauthorizedException.class,
            USER_A, "wrongpassword"
        );
    }

    @Test
    public void testJoinPrecondition_NoPasswordProvided_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.5: Missing password MUST be rejected.
        setField(room, "password", "secret");

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionPasswordProtection",
            org.jivesoftware.openfire.auth.UnauthorizedException.class,
            USER_A, (Object) null
        );
    }

    @Test
    public void testJoinPrecondition_CorrectPassword_CanJoin() throws Exception {
        // Correct password passes the check.
        setField(room, "password", "secret");

        assertNoPreconditionException(
            "checkJoinRoomPreconditionPasswordProtection",
            USER_A, "secret"
        );
    }

    @Test
    public void testJoinPrecondition_NoPasswordRequired_CanJoin() throws Exception {
        // Rooms without a password always pass the password check.
        setField(room, "password", null);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionPasswordProtection",
            USER_A, (Object) null
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045 §7.2.8 (nickname reserved by member)
    // "if the nickname is reserved by another user on the member list, the
    //  service MUST deny access ... specifying a <conflict/> error"
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_NicknameReservedByOtherMember_MustBeRejected() throws Exception {
        // XEP-0045 §7.2.8: A nickname reserved by another member causes a conflict.
        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(USER_B.asBareJID(), "witch");
        setField(room, "members", members);

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionNicknameReserved",
            ConflictException.class,
            USER_A, "witch"
        );
    }

    @Test
    public void testJoinPrecondition_NicknameReservedBySameUser_CanJoin() throws Exception {
        // The user whose nickname it is MUST be allowed to use it.
        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(USER_A.asBareJID(), "witch");
        setField(room, "members", members);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionNicknameReserved",
            USER_A, "witch"
        );
    }

    @Test
    public void testJoinPrecondition_NicknameNotReserved_CanJoin() throws Exception {
        // When no member has reserved this nickname, anyone can use it.
        assertNoPreconditionException(
            "checkJoinRoomPreconditionNicknameReserved",
            USER_A, "witch"
        );
    }

    // -------------------------------------------------------------------------
    // XEP-0045: Login Restricted To Reserved Nickname
    // "not-acceptable" when the user attempts to join with a nickname other
    // than their reserved one.
    // -------------------------------------------------------------------------

    @Test
    public void testJoinPrecondition_RestrictedNickname_WrongNick_MustBeRejected() throws Exception {
        // When loginRestrictedToNickname is enabled, the user MUST use their reserved nick.
        setField(room, "loginRestrictedToNickname", true);
        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(USER_A.asBareJID(), "thirdwitch");
        setField(room, "members", members);

        assertThrowsPrecondition(
            "checkJoinRoomPreconditionRestrictedToNickname",
            NotAcceptableException.class,
            USER_A, "otherwitch"
        );
    }

    @Test
    public void testJoinPrecondition_RestrictedNickname_CorrectNick_CanJoin() throws Exception {
        // When loginRestrictedToNickname is enabled, the reserved nickname is accepted.
        setField(room, "loginRestrictedToNickname", true);
        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(USER_A.asBareJID(), "thirdwitch");
        setField(room, "members", members);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionRestrictedToNickname",
            USER_A, "thirdwitch"
        );
    }

    @Test
    public void testJoinPrecondition_RestrictedNicknameDisabled_AnyNickAccepted() throws Exception {
        // When loginRestrictedToNickname is disabled, any nickname is accepted.
        setField(room, "loginRestrictedToNickname", false);
        final ConcurrentGroupMap<JID, String> members = new ConcurrentGroupMap<>();
        members.put(USER_A.asBareJID(), "thirdwitch");
        setField(room, "members", members);

        assertNoPreconditionException(
            "checkJoinRoomPreconditionRestrictedToNickname",
            USER_A, "completelydifferentnick"
        );
    }

    // =========================================================================
    // Self-presence status codes (XEP-0045 §7.2.2, §7.2.3, §7.2.12)
    // =========================================================================

    /**
     * XEP-0045 §7.2.2: "the 'self-presence' sent by the room to the new user
     * MUST include a status code of 110 so that the user knows this presence
     * refers to itself as an occupant."
     */
    @Test
    public void testSelfPresence_AlwaysContainsStatusCode110() throws Exception {
        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ false);

        assertTrue(hasStatusCode(selfPresence, 110),
            "Self-presence MUST always carry status code 110 (XEP-0045 §7.2.2)");
    }

    @Test
    public void testSelfPresence_OnJoin_AlwaysContainsStatusCode110() throws Exception {
        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertTrue(hasStatusCode(selfPresence, 110),
            "Self-presence MUST always carry status code 110 on join (XEP-0045 §7.2.2)");
    }

    /**
     * XEP-0045 §7.2.3: "If the user is entering a room that is non-anonymous
     * (i.e., which informs all occupants of each occupant's full JID), the
     * service MUST warn the user by including a status code of '100' in the
     * initial presence that the room sends to the new occupant."
     */
    @Test
    public void testSelfPresence_NonAnonymousRoom_ContainsStatusCode100() throws Exception {
        setField(room, "canAnyoneDiscoverJID", true); // non-anonymous

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertTrue(hasStatusCode(selfPresence, 100),
            "Non-anonymous room join presence MUST carry status code 100 (XEP-0045 §7.2.3)");
    }

    @Test
    public void testSelfPresence_SemiAnonymousRoom_DoesNotContainStatusCode100() throws Exception {
        setField(room, "canAnyoneDiscoverJID", false); // semi-anonymous

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertFalse(hasStatusCode(selfPresence, 100),
            "Semi-anonymous room join presence MUST NOT carry status code 100");
    }

    @Test
    public void testSelfPresence_StatusCode100_NotSentOnPresenceUpdate() throws Exception {
        // Status code 100 is only sent in the context of joining, not for regular presence updates.
        setField(room, "canAnyoneDiscoverJID", true); // non-anonymous

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ false);

        assertFalse(hasStatusCode(selfPresence, 100),
            "Status code 100 MUST NOT be sent for non-join presence updates");
    }

    /**
     * XEP-0045 §7.2.12: "If the user is entering a room in which the
     * discussions are logged to a public archive, the service SHOULD allow
     * the user to enter the room but MUST also warn the user that the
     * discussions are logged. This is done by including a status code of
     * '170' in the initial presence that the room sends to the new occupant."
     */
    @Test
    public void testSelfPresence_LoggedRoom_ContainsStatusCode170() throws Exception {
        setField(room, "logEnabled", true);

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertTrue(hasStatusCode(selfPresence, 170),
            "Logged room join presence MUST carry status code 170 (XEP-0045 §7.2.12)");
    }

    @Test
    public void testSelfPresence_UnloggedRoom_DoesNotContainStatusCode170() throws Exception {
        setField(room, "logEnabled", false);

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertFalse(hasStatusCode(selfPresence, 170),
            "Non-logged room join presence MUST NOT carry status code 170");
    }

    @Test
    public void testSelfPresence_StatusCode170_NotSentOnPresenceUpdate() throws Exception {
        // Status code 170 is only sent in the context of joining.
        setField(room, "logEnabled", true);

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ false);

        assertFalse(hasStatusCode(selfPresence, 170),
            "Status code 170 MUST NOT be sent for non-join presence updates");
    }

    /**
     * XEP-0045: New room created — status code 201 informs the user that the
     * room was just created and is locked pending configuration.
     */
    @Test
    public void testSelfPresence_NewRoom_ContainsStatusCode201() throws Exception {
        // A 'new' room has lockedTime == creationDate (both non-zero).
        final long now = System.currentTimeMillis();
        setField(room, "lockedTime", now);
        setField(room, "creationDate", new java.util.Date(now));

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertTrue(hasStatusCode(selfPresence, 201),
            "Joining a newly created room MUST result in status code 201");
    }

    @Test
    public void testSelfPresence_ExistingRoom_DoesNotContainStatusCode201() throws Exception {
        // An existing (already configured) room: lockedTime == 0.
        setField(room, "lockedTime", 0L);

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ true);

        assertFalse(hasStatusCode(selfPresence, 201),
            "Joining an existing room MUST NOT result in status code 201");
    }

    @Test
    public void testSelfPresence_StatusCode201_NotSentOnPresenceUpdate() throws Exception {
        final long now = System.currentTimeMillis();
        setField(room, "lockedTime", now);
        setField(room, "creationDate", new java.util.Date(now));

        final Presence input = buildMucUserPresence();
        final Presence selfPresence = invokeSelfPresenceCopy(input, /* isJoinPresence= */ false);

        assertFalse(hasStatusCode(selfPresence, 201),
            "Status code 201 MUST NOT be sent for non-join presence updates");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal presence stanza with the {@code <x xmlns='…muc#user'>}
     * child element required by {@code createSelfPresenceCopy}.
     */
    private Presence buildMucUserPresence() {
        final Presence presence = new Presence();
        presence.setFrom(new JID("testroom", "conference.example.org", "testuser"));
        presence.setTo(new JID("user@example.org/res"));
        final Element x = presence.addChildElement("x", "http://jabber.org/protocol/muc#user");
        x.addElement("item")
            .addAttribute("affiliation", "none")
            .addAttribute("role", "participant");
        return presence;
    }

    /** Invokes the private {@code createSelfPresenceCopy} method via reflection. */
    private Presence invokeSelfPresenceCopy(Presence presence, boolean isJoinPresence)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(
            "createSelfPresenceCopy", Presence.class, boolean.class);
        method.setAccessible(true);
        return (Presence) method.invoke(room, presence, isJoinPresence);
    }

    /** Returns {@code true} if the presence contains a status element with the given code. */
    private boolean hasStatusCode(Presence presence, int code) {
        final Element x = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
        if (x == null) {
            return false;
        }
        return x.elements("status").stream()
            .anyMatch(e -> String.valueOf(code).equals(e.attributeValue("code")));
    }

    /**
     * Asserts that calling the named single-arg (JID) precondition method throws the expected
     * exception type.
     */
    private <T extends Exception> void assertThrowsPrecondition(
        String methodName, Class<T> expectedException, JID userAddress)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(methodName, JID.class);
        method.setAccessible(true);
        final InvocationTargetException ex =
            assertThrows(InvocationTargetException.class, () -> method.invoke(room, userAddress),
                "Expected " + expectedException.getSimpleName() + " from " + methodName);
        assertInstanceOf(expectedException, ex.getCause(),
            "Wrong exception type from " + methodName);
    }

    /**
     * Asserts that calling the named two-arg (JID, Affiliation) precondition method throws the
     * expected exception type.
     */
    private <T extends Exception> void assertThrowsPrecondition(
        String methodName, Class<T> expectedException, JID userAddress, Affiliation affiliation)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(
            methodName, JID.class, Affiliation.class);
        method.setAccessible(true);
        final InvocationTargetException ex =
            assertThrows(InvocationTargetException.class,
                () -> method.invoke(room, userAddress, affiliation),
                "Expected " + expectedException.getSimpleName() + " from " + methodName);
        assertInstanceOf(expectedException, ex.getCause(),
            "Wrong exception type from " + methodName);
    }

    /**
     * Asserts that calling the named two-arg (JID, String) precondition method throws the expected
     * exception type.
     */
    private <T extends Exception> void assertThrowsPrecondition(
        String methodName, Class<T> expectedException, JID userAddress, Object secondArg)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(
            methodName, JID.class, String.class);
        method.setAccessible(true);
        final InvocationTargetException ex =
            assertThrows(InvocationTargetException.class,
                () -> method.invoke(room, userAddress, secondArg),
                "Expected " + expectedException.getSimpleName() + " from " + methodName);
        assertInstanceOf(expectedException, ex.getCause(),
            "Wrong exception type from " + methodName);
    }

    /** Asserts that the single-arg (JID) precondition check passes without throwing. */
    private void assertNoPreconditionException(String methodName, JID userAddress)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(methodName, JID.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(room, userAddress);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }, "Unexpected exception from " + methodName);
    }

    /** Asserts that the two-arg (JID, Affiliation) precondition check passes without throwing. */
    private void assertNoPreconditionException(
        String methodName, JID userAddress, Affiliation affiliation)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(
            methodName, JID.class, Affiliation.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(room, userAddress, affiliation);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }, "Unexpected exception from " + methodName);
    }

    /** Asserts that the two-arg (JID, String) precondition check passes without throwing. */
    private void assertNoPreconditionException(
        String methodName, JID userAddress, Object secondArg)
        throws Exception
    {
        final Method method = MUCRoom.class.getDeclaredMethod(
            methodName, JID.class, String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                method.invoke(room, userAddress, secondArg);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }, "Unexpected exception from " + methodName);
    }

    /**
     * Creates a minimal {@link MUCOccupant} with the given nickname and user JID, using
     * reflection to set the private fields (without triggering any room-side effects).
     */
    private MUCOccupant createDummyOccupant(String nickname, JID userJid) throws Exception {
        final MUCOccupant occupant = new MUCOccupant();
        setField(occupant, "nick", nickname);
        setField(occupant, "userJid", userJid.asBareJID());
        setField(occupant, "occupantJID", new JID(ROOM_JID.getNode(), ROOM_JID.getDomain(), nickname));
        setField(occupant, "role", Role.participant);
        setField(occupant, "affiliation", Affiliation.none);
        return occupant;
    }

    /** Sets a declared private field on the given object using reflection. */
    static <E> void setField(E object, String fieldName, Object value)
        throws NoSuchFieldException, IllegalAccessException
    {
        // Walk the class hierarchy to find the declared field.
        Class<?> clazz = object.getClass();
        Field field = null;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException("Field '" + fieldName + "' not found in class hierarchy of " + object.getClass().getName());
        }
        final boolean wasAccessible = field.canAccess(object);
        field.setAccessible(true);
        try {
            field.set(object, value);
        } finally {
            field.setAccessible(wasAccessible);
        }
    }
}
