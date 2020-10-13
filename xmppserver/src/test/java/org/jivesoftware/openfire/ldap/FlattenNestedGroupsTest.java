package org.jivesoftware.openfire.ldap;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmpp.packet.JID;
import org.zapodot.junit.ldap.EmbeddedLdapRule;
import org.zapodot.junit.ldap.EmbeddedLdapRuleBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * Tests the flattenNestedGroups mode of the {@link LdapGroupProvider}
 *
 * @author Marcel Heckel, 2019
 */
@Ignore("See OF-2070: Tests in this class give inconsistent results.")
@RunWith(MockitoJUnitRunner.class)
public class FlattenNestedGroupsTest {

    static {
        // TODO: is there a better place to set this?
        // When not set, an error is thrown. So set it to an arbitrary value.
        JiveGlobals.setHomeDirectory(new File(".").getAbsolutePath());
    }

    private static final String THIS_HOST_NAME = "test-host-name";

    private final int LDAP_SERVER_PORT = 12345;

    @Rule
    public EmbeddedLdapRule embeddedLdapRule = EmbeddedLdapRuleBuilder
        .newInstance()
        .bindingToPort(LDAP_SERVER_PORT)
        .usingDomainDsn("dc=mobikat,dc=net")
        .importingLdifs("org/jivesoftware/openfire/ldap/flattenNestedGroups.ldif")
        .build();

    @Mock
    private XMPPServer xmppServer;
    @Mock
    private XMPPServerInfo xmppServerInfo;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
    }

    @Before
    public void setUp() {
        Fixtures.clearExistingProperties();

        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);
        //doReturn(THIS_HOST_NAME).when(xmppServerInfo).getHostname();
        //doReturn(THIS_HOST_NAME).when(xmppServerInfo).getXMPPDomain();

        doReturn(xmppServerInfo).when(xmppServer).getServerInfo();

        doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                return new JID((String) args[0], THIS_HOST_NAME, (String) args[1]);
            }
        ).when(xmppServer).createJID(any(), any());

        doAnswer(invocation -> {
                JID jid = (JID) invocation.getArguments()[0];
                return jid != null && jid.getDomain().equals(THIS_HOST_NAME);
            }
        ).when(xmppServer).isLocal(any());

    }

    private void initLdapManager(boolean posix, boolean flattenNestedGroups) {
        final Map<String, String> properties = new HashMap<>();
        properties.put("ldap.host", "localhost");
        properties.put("ldap.port", "" + LDAP_SERVER_PORT);
        properties.put("ldap.sslEnabled", "false" );
        properties.put("ldap.startTlsEnabled", "false" );
        properties.put("ldap.baseDN", "dc=mobikat,dc=net");
        properties.put("ldap.adminDN", EmbeddedLdapRuleBuilder.DEFAULT_BIND_DSN);
        properties.put("ldap.adminPassword", EmbeddedLdapRuleBuilder.DEFAULT_BIND_CREDENTIALS);
        properties.put("ldap.usernameField", "uid");
        properties.put("ldap.nameField", "cn");
        properties.put("ldap.searchFilter", "(objectClass=inetOrgPerson)");
        properties.put("ldap.groupNameField", "cn");
        properties.put("ldap.groupMemberField", posix ? "memberUid" : "uniqueMember");
        properties.put("ldap.groupSearchFilter", posix ? "(objectClass=posixGroup)" : "(objectClass=groupOfUniqueNames)");

        if (posix)
            properties.put("ldap.posixMode", "true");
        if (flattenNestedGroups)
            properties.put("ldap.flattenNestedGroups", "true");

        LdapManager.setInstance(new LdapManager(properties));
        UserManager.setProvider(new LdapUserProvider());
        UserManager.getInstance().clearCaches();
    }


    @Test
    public void testConnection() throws Exception {
        initLdapManager(false, false);

        LdapManager ldapManager = LdapManager.getInstance();
        assertEquals("cn=admins,ou=groups,dc=mobikat,dc=net", ldapManager.findGroupAbsoluteDN("admins").toString());

        UserManager userManager = UserManager.getInstance();

        User user = userManager.getUser("j.bond");
        assertNotNull(user);
        assertEquals("James Bond", user.getName());
    }


    @Test
    public void testNormalUsersOfGroupsNoPosix() throws GroupNotFoundException {
        initLdapManager(false, false);
        testNormalUsersOfGroups();
    }

    @Test
    public void testNormalUsersOfGroupsPosix() throws GroupNotFoundException {
        initLdapManager(true, false);
        testNormalUsersOfGroups();
    }

    @Test
    public void testFlattenUsersOfGroupsNoPosix() throws GroupNotFoundException {
        initLdapManager(false, true);
        testFlattenUsersOfGroups();
    }

    @Test
    public void testFlattenUsersOfGroupsPosix() throws GroupNotFoundException {
        initLdapManager(true, true);
        testFlattenUsersOfGroups();
    }

    public void testNormalUsersOfGroups() throws GroupNotFoundException {
        LdapGroupProvider groupProvider = new LdapGroupProvider();

        // admins
        assertThat(groupProvider.getGroup("admins").getAll(),
            containsInAnyOrder(
                new JID("j.bond", THIS_HOST_NAME, null),
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
        // cycle1
        assertThat(groupProvider.getGroup("cycle1").getAll(),
            containsInAnyOrder(
                new JID("l.densivillja", THIS_HOST_NAME, null),
                new JID("p.silie", THIS_HOST_NAME, null)
            ));
        // cycle2
        assertThat(groupProvider.getGroup("cycle2").getAll(),
            containsInAnyOrder(
                new JID("c.man", THIS_HOST_NAME, null),
                new JID("h.wurst", THIS_HOST_NAME, null)
            ));
        // cycle3
        assertThat(groupProvider.getGroup("cycle3").getAll(),
            containsInAnyOrder(
                new JID("a.schweis", THIS_HOST_NAME, null),
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
        // group1
        assertThat(groupProvider.getGroup("group1").getAll(),
            containsInAnyOrder(
                new JID("c.man", THIS_HOST_NAME, null),
                new JID("h.wurst", THIS_HOST_NAME, null)
            ));
        // group2
        assertThat(groupProvider.getGroup("group2").getAll(),
            containsInAnyOrder(
                new JID("l.densivillja", THIS_HOST_NAME, null),
                new JID("p.silie", THIS_HOST_NAME, null)
            ));
        // group3
        assertThat(groupProvider.getGroup("group3").getAll(),
            containsInAnyOrder(
                new JID("a.schweis", THIS_HOST_NAME, null)
            ));
        // group4
        assertThat(groupProvider.getGroup("group4").getAll(),
            containsInAnyOrder(
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
    }

    public void testFlattenUsersOfGroups() throws GroupNotFoundException {
        LdapGroupProvider groupProvider = new LdapGroupProvider();
        // admins
        assertThat(groupProvider.getGroup("admins").getAll(),
            containsInAnyOrder(
                new JID("j.bond", THIS_HOST_NAME, null),
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
        // cycle1..3
        for (String cycle : new String[]{"cycle1", "cycle2", "cycle3"}) {
            assertThat(groupProvider.getGroup(cycle).getAll(),
                containsInAnyOrder(
                    new JID("l.densivillja", THIS_HOST_NAME, null),
                    new JID("p.silie", THIS_HOST_NAME, null),
                    new JID("c.man", THIS_HOST_NAME, null),
                    new JID("h.wurst", THIS_HOST_NAME, null),
                    new JID("a.schweis", THIS_HOST_NAME, null),
                    new JID("j.lopez", THIS_HOST_NAME, null)
                ));
        }
        // group1
        assertThat(groupProvider.getGroup("group1").getAll(),
            containsInAnyOrder(
                new JID("c.man", THIS_HOST_NAME, null),
                new JID("h.wurst", THIS_HOST_NAME, null),
                new JID("l.densivillja", THIS_HOST_NAME, null),
                new JID("p.silie", THIS_HOST_NAME, null),
                new JID("a.schweis", THIS_HOST_NAME, null),
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
        // group2
        assertThat(groupProvider.getGroup("group2").getAll(),
            containsInAnyOrder(
                new JID("l.densivillja", THIS_HOST_NAME, null),
                new JID("p.silie", THIS_HOST_NAME, null),
                new JID("a.schweis", THIS_HOST_NAME, null),
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
        // group3
        assertThat(groupProvider.getGroup("group3").getAll(),
            containsInAnyOrder(
                new JID("a.schweis", THIS_HOST_NAME, null)
            ));
        // group4
        assertThat(groupProvider.getGroup("group4").getAll(),
            containsInAnyOrder(
                new JID("j.lopez", THIS_HOST_NAME, null)
            ));
    }

    @Test
    public void testNormalGroupsOfUsersNoPosix() throws GroupNotFoundException {
        initLdapManager(false, false);
        testNormalGroupsOfUsers();
    }

    @Test
    public void testNormalGroupsOfUsersPosix() throws GroupNotFoundException {
        initLdapManager(true, false);
        testNormalGroupsOfUsers();
    }

    @Test
    public void testFlattenGroupsOfUsersNoPosix() throws GroupNotFoundException {
        initLdapManager(false, true);
        testFlattenGroupsOfUsers();
    }

    @Test
    public void testFlattenGroupsOfUsersPosix() throws GroupNotFoundException {
        initLdapManager(true, true);
        testFlattenGroupsOfUsers();
    }

    public void testNormalGroupsOfUsers() throws GroupNotFoundException {
        LdapGroupProvider groupProvider = new LdapGroupProvider();

        assertThat(groupProvider.getGroupNames(new JID("j.bond", THIS_HOST_NAME, null)),
            containsInAnyOrder("admins"));

        assertThat(groupProvider.getGroupNames(new JID("a.schweis", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle3", "group3"));

        assertThat(groupProvider.getGroupNames(new JID("c.man", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle2", "group1"));

        assertThat(groupProvider.getGroupNames(new JID("h.wurst", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle2", "group1"));

        assertThat(groupProvider.getGroupNames(new JID("j.lopez", THIS_HOST_NAME, null)),
            containsInAnyOrder("admins", "cycle3", "group4"));

        assertThat(groupProvider.getGroupNames(new JID("l.densivillja", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "group2"));

        assertThat(groupProvider.getGroupNames(new JID("p.silie", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "group2"));
    }

    public void testFlattenGroupsOfUsers() throws GroupNotFoundException {
        LdapGroupProvider groupProvider = new LdapGroupProvider();

        assertThat(groupProvider.getGroupNames(new JID("j.bond", THIS_HOST_NAME, null)),
            containsInAnyOrder("admins"));

        assertThat(groupProvider.getGroupNames(new JID("a.schweis", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "cycle2", "cycle3", "group1", "group2", "group3"));

        assertThat(groupProvider.getGroupNames(new JID("c.man", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "cycle2", "cycle3", "group1"));

        assertThat(groupProvider.getGroupNames(new JID("h.wurst", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "cycle2", "cycle3", "group1"));

        assertThat(groupProvider.getGroupNames(new JID("j.lopez", THIS_HOST_NAME, null)),
            containsInAnyOrder("admins", "cycle1", "cycle2", "group1", "group2", "cycle3", "group4"));

        assertThat(groupProvider.getGroupNames(new JID("l.densivillja", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "cycle2", "cycle3", "group1", "group2"));

        assertThat(groupProvider.getGroupNames(new JID("p.silie", THIS_HOST_NAME, null)),
            containsInAnyOrder("cycle1", "cycle2", "cycle3", "group1", "group2"));
    }
}
