package org.jivesoftware.openfire.muc.spi;

import junit.framework.Assert;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.junit.Test;

/**
 * @author Christian Schudt
 */
public class MucPrivilegesTest {

    @Test
    public void ownerShouldBeAbleToDoAnything() {
        Assert.assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToRevokeModeratorPrivilegesFromOtherAdmin() {
        Assert.assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToGrantMembership() {
        Assert.assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.none, MUCRole.Role.none, MUCRole.Affiliation.member, MUCRole.Role.participant));
    }

    @Test
    public void adminModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        Assert.assertTrue(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        Assert.assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberShouldNotBeAbleToDoAnything() {
        Assert.assertFalse(MUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.participant, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }
}
