package org.jivesoftware.openfire.admin;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.group.DefaultGroupProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GroupBasedAdminProviderTest {

    private static final List<JID> ADMINS = Arrays.asList(new JID("user-a"), new JID("user-b"));
    private static String mockGroupName;
    private static Group mockGroup;

    private GroupBasedAdminProvider adminProvider;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @Before
    public void setUp() {
        Fixtures.clearExistingProperties();
        GroupManager.GROUP_PROVIDER.setValue(TestGroupProvider.class);
        mockGroupName = "mock-group-name";
        JiveGlobals.setProperty("provider.group.groupBasedAdminProvider.groupName", mockGroupName);
        mockGroup = mock(Group.class);
        doReturn(ADMINS).when(mockGroup).getMembers();
        adminProvider = new GroupBasedAdminProvider();
    }

    @Test
    public void willRetrieveGroupMembers() {

        final List<JID> admins = adminProvider.getAdmins();

        assertThat(admins, is(ADMINS));
    }

    @Test
    public void willReturnEmptyListIfGroupIsNotFound() {

        mockGroupName = "another group";

        final List<JID> admins = adminProvider.getAdmins();

        assertThat(admins, is(Collections.emptyList()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void willNotSetTheListOfAdmins() {
        assertThat(adminProvider.isReadOnly(), is(true));
        adminProvider.setAdmins(ADMINS);
    }

    public static class TestGroupProvider extends DefaultGroupProvider {
        @Override
        public Group getGroup(String name) throws GroupNotFoundException {
            if(name.equals(mockGroupName)) {
                return mockGroup;
            } else {
                throw new GroupNotFoundException();
            }
        }
    }
}
