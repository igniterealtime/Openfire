package org.jivesoftware.openfire.muc.spi;

import junit.framework.Assert;
import org.jivesoftware.openfire.muc.MUCRole;
import org.junit.Test;

/**
 * @author Christian Schudt
 */
public class MucPrivilegesTest {

    @Test
    public void ownerShouldBeAbleToDoAnything() {
        Assert.assertTrue(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToRevokeModeratorPrivilegesFromOtherAdmin() {
        Assert.assertTrue(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.none));
    }

    @Test
    public void adminShouldBeAbleToGrantMembership() {
        Assert.assertTrue(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.none, MUCRole.Affiliation.none, MUCRole.Role.none, MUCRole.Affiliation.member, MUCRole.Role.participant));
    }

    @Test
    public void adminModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertFalse(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertTrue(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void ownerModeratorShouldBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        Assert.assertTrue(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromOwner() {
        Assert.assertFalse(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.owner, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberModeratorShouldNotBeAbleToRevokeModeratorPrivilegesFromAdmin() {
        Assert.assertFalse(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.moderator, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }

    @Test
    public void memberShouldNotBeAbleToDoAnything() {
        Assert.assertFalse(LocalMUCRoom.isPrivilegedToChangeAffiliationAndRole(MUCRole.Affiliation.member, MUCRole.Role.participant, MUCRole.Affiliation.admin, MUCRole.Role.moderator, MUCRole.Affiliation.none, MUCRole.Role.none));
    }
}
