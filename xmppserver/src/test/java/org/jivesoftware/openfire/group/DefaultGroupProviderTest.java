/*
 * Copyright (C) 2022-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.group;

import org.dbunit.DBTestCase;
import org.dbunit.PropertiesBasedJdbcDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.jivesoftware.Fixtures;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.DefaultConnectionProvider;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;

/**
 * Unit tests that verify the functionality of {@link DefaultGroupProvider}.
 *
 * Implementation-wise, this class extends for DBTestCase, which is as JUnit 3 derivative. Practically, this means that
 * Junit 4 annotations in this class will be ignored.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DefaultGroupProviderTest extends DBTestCase
{
    public static final String DRIVER = "org.hsqldb.jdbcDriver";
    public static final String URL;
    public static final String USERNAME = "sa";
    public static final String PASSWORD = "";

    static {
        final URL location = AbstractGroupProvider.class.getResource("/datasets/openfire.script");
        assert location != null;
        final String fileLocation = location.toString().substring(0, location.toString().lastIndexOf("/")+1) + "openfire";
        URL = "jdbc:hsqldb:"+fileLocation+";ifexists=true";

        // Setup database configuration of DBUnit.
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, DRIVER );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, URL );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, USERNAME );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, PASSWORD );
    }

    public void setUp() throws Exception
    {
        // Ensure that DB-Unit's setUp is called!
        super.setUp();

        // Initialize Openfire's cache framework.
        CacheFactory.initialize();

        // Mock the XMPPServer implementation that's used internally.
        Fixtures.clearExistingProperties();
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

        // Ensure that Openfire caches are reset before each test to avoid tests to affect each-other.
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);

        // Wire the database connection provider used by the GroupProvider.
        final DefaultConnectionProvider conProvider = new DefaultConnectionProvider();
        conProvider.setDriver(DRIVER);
        conProvider.setServerURL(URL);
        conProvider.setUsername(USERNAME);
        conProvider.setPassword(PASSWORD);
        DbConnectionManager.setConnectionProvider(conProvider);
    }

    public void tearDown() throws Exception {
        super.tearDown();

        // Reset static fields after use (to not confuse other test classes).
        // TODO: this ideally goes in a static @AfterClass method, but that's not supported in JUnit 3.
        for (String fieldName : Arrays.asList("INSTANCE", "provider")) {
            final Field field = GroupManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, null);
            field.setAccessible(false);
        }

        final Field field = GroupEventDispatcher.class.getDeclaredField("listeners");
        field.setAccessible(true);
        ((List<?>)field.get(null)).clear();
        field.setAccessible(false);

        Fixtures.clearExistingProperties();
    }

    @Override
    protected IDataSet getDataSet() throws Exception {
        // This dataset restores the state of the database to one that does not contain any groups (or metadata for
        // groups) between each test.
        return new XmlDataSet(getClass().getResourceAsStream("/datasets/clean.xml"));
    }

    /**
     * Asserts that a simple test group that is created by the provider can be retrieved again in good order.
     */
    public void testCreateGroup() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        // Execute system under test.
        final Group group = provider.createGroup("Test Group");
        group.setDescription("This is test group.");
        group.getMembers().add(new JID("john@example.org"));
        group.getMembers().add(new JID("jack@example.org"));
        group.getAdmins().add(new JID("jane@example.org"));

        // Verify results.
        final Group result = provider.getGroup("Test Group");
        assertNotNull(result);
        assertEquals("Test Group", result.getName());
        assertEquals("This is test group.", result.getDescription());
        assertEquals(2, result.getMembers().size());
        assertTrue(result.getMembers().contains(new JID("john@example.org")));
        assertTrue(result.getMembers().contains(new JID("jack@example.org")));
        assertEquals(1, result.getAdmins().size());
        assertTrue(result.getAdmins().contains(new JID("jane@example.org")));
        assertEquals(3, result.getAll().size());
        assertTrue(result.getAll().contains(new JID("john@example.org")));
        assertTrue(result.getAll().contains(new JID("jack@example.org")));
        assertTrue(result.getAll().contains(new JID("jane@example.org")));
    }

    /**
     * Asserts that a with no name cannot be created
     */
    public void testCreateGroupWithEmptyNameThrows() throws Exception
    {
        final String GROUP_NAME = "";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        assertThrows(GroupNameInvalidException.class, ()-> provider.createGroup(GROUP_NAME));
    }

    /**
     * Asserts that two groups with the same name cannot be created
     */
    public void testCreateGroupWithDuplicateNameThrows() throws Exception
    {
        final String GROUP_NAME = "Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);
        assertThrows(GroupAlreadyExistsException.class, ()-> provider.createGroup(GROUP_NAME));
    }

    /**
     * Asserts that a group can be created, removed and recreated again, with the same name.
     */
    public void testRecreateGroup() throws Exception
    {
        final String GROUP_NAME = "Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);
        provider.deleteGroup(GROUP_NAME);
        provider.createGroup(GROUP_NAME);
    }

    /**
     * Verifies that a group can be retrieved based on the name that it was created with.
     */
    public void testGetGroupByName() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("test");

        // Execute system under test.
        final Group result = provider.getGroup("test");

        // Verify result.
        assertNotNull(result);
        assertEquals("test", result.getName());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupCount()} returns the correct count of groups when no groups
     * are present.
     */
    public void testGroupCountEmpty() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        // Execute system under test.
        final int result = provider.getGroupCount();

        // Verify result.
        assertEquals(0, result);
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupCount()} returns the correct count of groups when one group
     * is present.
     */
    public void testGroupCountOne() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group");

        // Execute system under test.
        final int result = provider.getGroupCount();

        // Verify result.
        assertEquals(1, result);
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupCount()} returns the correct count of groups when multiple
     * groups are present.
     */
    public void testGroupCountMultiple() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final int result = provider.getGroupCount();

        // Verify result.
        assertEquals(2, result);
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames()} returns an empty collection when no groups
     * are present.
     */
    public void testGroupNamesEmpty() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames()} returns a collection containing one group name
     * when one groups is present.
     */
    public void testGroupNamesOne() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames()} returns a collection containing all group name
     * when multiple groups are present.
     */
    public void testGroupNamesMultiple() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames();

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns an empty collection when the provided
     * user is not a member or admin of any group.
     */
    public void testGroupNamesByJIDNone() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns a group in which the user is a member.
     */
    public void testGroupNamesByJIDOneMember() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A").getMembers().add(needle);
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns all group in which the user is a member.
     */
    public void testGroupNamesByJIDTwoMember() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A").getMembers().add(needle);
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C").getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(result.toString(),2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group C"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns a group in which the user is an admin.
     */
    public void testGroupNamesByJIDOneAdmin() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A").getAdmins().add(needle);
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns all group in which the user is an admin.
     */
    public void testGroupNamesByJIDTwoAdmin() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A").getAdmins().add(needle);
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C").getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group C"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getGroupNames(JID)} returns all group in which the user is either
     * a member or an admin.
     */
    public void testGroupNamesByJIDMemberAndAdmin() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final JID needle = new JID("jane@example.org");
        provider.createGroup("Test Group A").getAdmins().add(needle);
        provider.createGroup("Test Group B").getMembers().add(needle);
        provider.createGroup("Test Group C");

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames(needle);

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteGroup(String)} deletes the named group, such that it cannot be
     * retrieved
     */
    public void testDeleteGroup() throws Exception{
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteGroup(String)} deletes the named shared group, such that it
     * cannot be retrieved
     */
    public void testDeleteGroupShared() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames(needle);

        assertEquals(0, result.size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteGroup(String)} throws a {@link GroupNotFoundException} when
     * attempting to delete a group by a name that does not exist
     */
    public void testDeleteGroupWithNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        assertThrows(GroupNotFoundException.class,() -> provider.deleteGroup("Test Group C"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setName(String, String)} changes the name of the group
     */
    public void testSetName() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setName("Test Group A", "Test Group B");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setName(String, String)} throws a {@link GroupNameInvalidException}
     * when attempting to rename a group to an empty string
     */
    public void testSetNameWithEmptyStringThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNameInvalidException.class, () -> provider.setName("Test Group A", ""));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setName(String, String)} throws a {@link GroupAlreadyExistsException}
     * when attempting to rename a group to name of another existing group
     */
    public void testSetNameToExistingGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        assertThrows(GroupAlreadyExistsException.class, () -> provider.setName("Test Group A", "Test Group B"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setName(String, String)} throws a {@link GroupNotFoundException}
     * when attempting to rename a group from a name that does not exist
     */
    public void testSetNameOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setName("Test Group B", "Test Group C"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setDescription(String, String)} sets the description of a group
     */
    public void testSetDescription() throws Exception {
        final String DESC = "The description of Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setDescription("Test Group A", DESC);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(DESC, result.getDescription());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setDescription(String, String)} changes the description of a group
     */
    public void testSetDescriptionReplacesExistingDescription() throws Exception {
        final String DESC = "The description of Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.setDescription("Test Group A", "Old Description");

        provider.setDescription("Test Group A", DESC);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(DESC, result.getDescription());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setDescription(String, String)} sets the description of a group to an
     * empty string
     */
    public void testSetDescriptionWithEmptyString() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").setDescription("The description of Test Group A");

        provider.setDescription("Test Group A", "");
        final Group result = provider.getGroup("Test Group A");

        assertEquals("", result.getDescription());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#setDescription(String, String)} throws a {@link GroupNotFoundException}
     * when attempting to set the description of a group by a name that does not exist
     */
    public void testSetDescriptionOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setDescription("Test Group B", "Some Description"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} adds a regular member to a group
     */
    public void testAddMember() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getMembers().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} adds an admin member to a group
     */
    public void testAddAdminMember() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, true);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getAdmins().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} adds a remote member to a group
     */
    public void testAddRemoteMember() throws Exception {
        final JID needle = new JID("stranger@remoteexample.com");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getMembers().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} adds a regular member to a group by
     * their full JID
     */
    public void testAddMemberByFullJid() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN + "/laptop");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(1, result.getMembers().size());
        assertTrue(result.getMembers().contains(needle.asBareJID()));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} adds a remote member to a group by
     * their full JID
     */
    public void testAddRemoteMemberByFullJid() throws Exception {
        final JID needle = new JID("stranger@remoteexample.org/pda");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(1, result.getMembers().size());
        assertTrue(result.getMembers().contains(needle.asBareJID()));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} silently completes when attempting
     * to add a duplicate user to a group, but does not throw
     */
    public void testAddMemberDuplicate() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.addMember("Test Group A", needle, false);

        try {
            provider.addMember("Test Group A", needle, false);
        } catch (Throwable t) {
            fail("Failed to add a member for a second time. This should succeed as a no-op");
        }
        final Group result = provider.getGroup("Test Group A");

        assertEquals(1, result.getMembers().size());
        assertTrue(result.getMembers().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#addMember(String, JID, boolean)} throws a
     * {@link GroupNotFoundException} when attempting to add a member to a group by a name that does not exist
     */
    public void testAddMemberOnNonExistentGroupThrows() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.addMember("Test Group B", needle, false));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#updateMember(String, JID, boolean)} sets a member to an admin
     */
    public void testUpdateMemberToAdmin() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final String GROUP_NAME = "Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);
        provider.addMember(GROUP_NAME, needle, false);

        provider.updateMember(GROUP_NAME, needle, true);
        final Group result = provider.getGroup(GROUP_NAME);

        assertTrue(result.getAdmins().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#updateMember(String, JID, boolean)} sets an admin to a member
     */
    public void testUpdateMemberFromAdminToMember() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final String GROUP_NAME = "Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);
        provider.addMember(GROUP_NAME, needle, true);

        provider.updateMember(GROUP_NAME, needle, false);
        final Group result = provider.getGroup(GROUP_NAME);

        assertFalse(result.getAdmins().contains(needle));
        assertTrue(result.getMembers().contains(needle));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#updateMember(String, JID, boolean)} throws when trying to update
     * membership of a non-existent group
     */
    public void testUpdateMemberOfNonExistentGroup() throws Exception {
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.addMember("Test Group A", needle, false);

        assertThrows(GroupNotFoundException.class, () -> provider.updateMember("Test Group B", needle, false));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteMember(String, JID)} remove a member from a group
     */
    public void testDeleteMember() throws Exception {
        final String GROUP_NAME = "Test Group A";
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);
        provider.addMember(GROUP_NAME, needle, false);

        provider.deleteMember(GROUP_NAME, needle);
        final Group result = provider.getGroup(GROUP_NAME);

        assertEquals(0, result.getMembers().size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteMember(String, JID)} fails silently when removing a non-existent
     * member from a group
     */
    public void testDeleteNonExistentMember() throws Exception {
        final String GROUP_NAME = "Test Group A";
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup(GROUP_NAME);

        try {
            provider.deleteMember(GROUP_NAME, needle);
        } catch (Throwable t){
            fail("Exception deleting a non-existent group member. This should fail silently as a no-op.");
        }
        final Group result = provider.getGroup(GROUP_NAME);

        assertEquals(0, result.getMembers().size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#deleteMember(String, JID)} fails silently when removing a member from
     * a non-existent group
     */
    public void testDeleteMemberFromNonExistentGroup() throws Exception {
        final String GROUP_NAME = "Test Group A";
        final JID needle = new JID("jane@" + Fixtures.XMPP_DOMAIN);
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        try {
            provider.deleteMember(GROUP_NAME, needle);
        } catch (Throwable t){
            fail("Exception deleting a group member from a non-existent group. This should fail silently as a no-op.");
        }

        final Collection<String> result = provider.getGroupNames();
        assertEquals(0, result.size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#isReadOnly()} always returns false
     */
    public void testDefaultGroupProviderIsAlwaysWritable() throws Exception {
        assertFalse(new DefaultGroupProvider().isReadOnly());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#isSearchSupported()} always returns true
     */
    public void testDefaultGroupProviderIsSearchable() throws Exception {
        assertTrue(new DefaultGroupProvider().isSearchSupported());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#isSharingSupported()} ()} always returns true
     */
    public void testDefaultGroupProviderSupportsSharing() throws Exception {
        assertTrue(new DefaultGroupProvider().isSharingSupported());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#search(String)} returns sensible results
     */
    public void testSimpleSearch() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        final Collection<String> result = provider.search("Group");
        assertEquals(2, result.size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#search(String)} returns successfully when finding no results
     */
    public void testSimpleUnsuccessfulSearch() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        final Collection<String> result = provider.search("Something Else");
        assertEquals(0, result.size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#search(String, int, int)} returns a subset of results
     */
    public void testPaginatedSearch() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        for(int i=1; i<=9; i++){
            provider.createGroup("Test Group " + i);
        }

        final Collection<String> result = provider.search("Group", 4, 2);
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group 5"));
        assertTrue(result.contains("Test Group 6"));
    }

    /**
     * Verifies that {@link DefaultGroupProvider#search(String, int, int)} returns no rows when given a start index
     * higher than the number of possible results
     */
    public void testPaginatedSearchWithIndexOOB() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        for(int i=1; i<=9; i++){
            provider.createGroup("Test Group " + i);
        }

        final Collection<String> result = provider.search("Group", 11, 1);

        assertEquals(0, result.size());
    }

    /**
     * Verifies that {@link DefaultGroupProvider#search(String, int, int)} returns sensible results when given a start
     * index and given a number of results higher than the number of possible results
     */
    public void testPaginatedSearchWithNumResultsAcrossBoundary() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        for(int i=1; i<=9; i++){ //Create 9 groups
            provider.createGroup("Test Group " + i);
        }

        final Collection<String> result = provider.search("Group", 7, 5); //Index 7 is 8th item

        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group 8"));
        assertTrue(result.contains("Test Group 9"));
    }
}
