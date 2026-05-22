/*
 * Copyright (C) 2022-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests that verify the functionality of {@link GroupManager}.
 *
 * Implementation-wise, this class extends for DBTestCase, which is a JUnit Jupiter derivative. Practically, this means that
 * JUnit Jupiter annotations and assertions should be used.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GroupManagerNoMockTest extends DBTestCase
{
    public static final String DRIVER = "org.hsqldb.jdbcDriver";
    public static String URL;
    public static final String USERNAME = "sa";
    public static final String PASSWORD = "";

    static {
      final URL locationUrl = AbstractGroupProvider.class.getResource("/datasets/openfire.script");
      assert locationUrl != null;
      try {
        Path location = Path.of(locationUrl.toURI()).getParent().resolve("openfire");
        URL = "jdbc:hsqldb:"+location.toString()+";ifexists=true";
        // Setup database configuration of DBUnit.
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, DRIVER );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, URL );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, USERNAME );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, PASSWORD );

      } catch ( URISyntaxException e) {
        fail(e.getMessage());
      }
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception
    {
        // Ensure that DB-Unit's setUp is called!
        super.setUp();

        // Initialize Openfire's cache framework.
        CacheFactory.initialize();

        // Mock the XMPPServer implementation that is used internally.
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

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // Reset static fields after use (to not confuse other test classes).
        // TODO: this ideally goes in a static @AfterClass method, but that's not supported in JUnit 3.
        GroupManager.setInstance(null);

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
     * Verifies that a newly created group can be retrieved with its configured description, members, and administrators.
     */
    @Test
    public void testCreatedGroupCanBeRetrievedWithConfiguredData() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        // Execute system under test.
        final Group group = groupManager.createGroup("Test Group");
        group.setDescription("This is test group.");
        group.getMembers().add(new JID("john@example.org"));
        group.getMembers().add(new JID("jack@example.org"));
        group.getAdmins().add(new JID("jane@example.org"));

        // Verify result.
        final Group result = provider.getGroup("Test Group");
        assertNotNull(result, "Group should be retrievable after creation.");
        assertEquals("Test Group", result.getName(), "Group name should match the configured value.");
        assertEquals("This is test group.", result.getDescription(), "Group description should match the configured value.");
        assertEquals(2, result.getMembers().size(), "Group should have exactly two members.");
        assertTrue(result.getMembers().contains(new JID("john@example.org")), "Members should include john@example.org.");
        assertTrue(result.getMembers().contains(new JID("jack@example.org")), "Members should include jack@example.org.");
        assertEquals(1, result.getAdmins().size(), "Group should have exactly one administrator.");
        assertTrue(result.getAdmins().contains(new JID("jane@example.org")), "Administrators should include jane@example.org.");
        assertEquals(3, result.getAll().size(), "getAll() should return all members and administrators.");
        assertTrue(result.getAll().contains(new JID("john@example.org")), "getAll() should include john@example.org.");
        assertTrue(result.getAll().contains(new JID("jack@example.org")), "getAll() should include jack@example.org.");
        assertTrue(result.getAll().contains(new JID("jane@example.org")), "getAll() should include jane@example.org.");
    }

    /**
     * Verifies that creating a group with an empty name is rejected.
     */
    @Test
    public void testCreatingGroupWithEmptyNameFails() throws Exception
    {
        // Setup test fixture.
        final String GROUP_NAME = "";
        final GroupManager groupManager = GroupManager.getInstance();

        // Execute system under test and verify result.
        assertThrows(GroupNameInvalidException.class, () -> groupManager.createGroup(GROUP_NAME), "Creating a group with an empty name should be rejected.");
    }

    /**
     * Verifies that creating a second group with the same name is rejected.
     */
    @Test
    public void testCreatingGroupWithDuplicateNameFails() throws Exception
    {
        // Setup test fixture.
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();

        // Execute system under test.
        groupManager.createGroup(GROUP_NAME);

        // Verify result.
        assertThrows(GroupAlreadyExistsException.class, () -> groupManager.createGroup(GROUP_NAME), "Creating a second group with the same name should be rejected.");
    }

    /**
     * Verifies that a deleted group name can be reused for a new group.
     */
    @Test
    public void testDeletedGroupNameCanBeReused() throws Exception
    {
        // Setup test fixture.
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();

        // Execute system under test.
        final Group group = groupManager.createGroup(GROUP_NAME);
        groupManager.deleteGroup(group);
        final Group recreatedGroup = groupManager.createGroup(GROUP_NAME);

        // Verify result.
        assertNotNull(recreatedGroup, "Group should be successfully recreated with the same name.");
    }

    /**
     * Verifies that mutating a stale reference to a deleted group does not make that group
     * appear to exist again.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2426">Group cache can contain ghost entries</a>
     */
    @Test
    public void testDeletedGroupRemainsUnavailableAfterStaleReferenceMutation() throws Exception
    {
        // Setup test fixture.
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();
        final Group group = groupManager.createGroup(GROUP_NAME);
        groupManager.deleteGroup(group);

        // Execute system under test.
        group.getMembers().add(new JID("test@example.org"));

        // Verify result.
        assertThrows(GroupNotFoundException.class, () -> groupManager.getGroup(GROUP_NAME), "Deleted group should remain unavailable even after mutation of a stale reference.");
    }

    /**
     * Verifies that a group can be retrieved by the name it was created with.
     */
    @Test
    public void testGetGroupByName() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("test");

        // Execute system under test.
        final Group result = groupManager.getGroup("test");

        // Verify result.
        assertNotNull(result, "Group should be retrievable by name.");
        assertEquals("test", result.getName(), "Retrieved group name should match the requested name.");
    }

    /**
     * Verifies that {@link GroupManager#getGroupCount()} returns zero when no groups are present.
     */
    @Test
    public void testGroupCountEmpty() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(0, result, "Group count should be zero when no groups exist.");
    }

    /**
     * Verifies that {@link GroupManager#getGroupCount()} returns one when one group is present.
     */
    @Test
    public void testGroupCountOne() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("Test Group");

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(1, result, "Group count should be one when exactly one group exists.");
    }

    /**
     * Verifies that {@link GroupManager#getGroupCount()} returns the expected count when multiple groups are present.
     */
    @Test
    public void testGroupCountMultiple() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("Test Group A");
        groupManager.createGroup("Test Group B");

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(2, result, "Group count should match the number of created groups.");
    }

    /**
     * Verifies that deleting a shared group removes it from shared-group results.
     */
    @Test
    public void testDeletedSharedGroupIsNoLongerReturned() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final GroupManager groupManager = GroupManager.getInstance();
        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.shareWithEverybody("Users in group A");
        groupManager.createGroup("Test Group B");

        // Execute system under test.
        groupManager.deleteGroup(groupA);
        final Collection<Group> result = groupManager.getSharedGroups(needle.getNode());

        // Verify result.
        assertEquals(0, result.size(), "Deleted shared group should not appear in shared-group results.");
    }

    /**
     * Verifies that paginated group listings include newly created groups, even when the same page
     * has been requested before the new group was added.
     */
    @Test
    public void testPaginatedGroupListingReflectsNewGroupAfterPriorQuery() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("Test Group A");

        // Execute system under test (warm the cache with an initial page query).
        final Collection<Group> firstPage = groupManager.getGroups(0, 10);
        assertEquals(1, firstPage.size(), "Pre-condition: exactly one group should be present initially.");

        // Execute system under test (create a new group).
        groupManager.createGroup("Test Group B");
        final Collection<Group> updatedPage = groupManager.getGroups(0, 10);

        // Verify result.
        assertEquals(2, updatedPage.size(), "Paginated query should include the newly created group.");
    }

    /**
     * Verifies that changing a group to be shared with everybody makes that group visible to
     * users outside of that group.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void testSharingChangeToEverybodyUpdatesVisibleGroups() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final Group groupA = groupManager.createGroup("Test Group A");
        final Group groupB = groupManager.createGroup("Test Group B");

        // Verify pre-condition.
        assertFalse(groupManager.getVisibleGroups(groupB).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "Pre-condition: 'Test Group A' should not be visible to 'Test Group B' before it is shared with everybody.");

        // Execute system under test.
        groupA.shareWithEverybody("Test Group A");

        // Verify result.
        assertTrue(groupManager.getVisibleGroups(groupB).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After sharing with everybody, 'Test Group A' should be visible to users outside that group.");
    }

    /**
     * Verifies that changing a group from shared-with-everybody to not shared removes that group
     * from visibility results for users outside of that group.
     *
     * This test covers the reverse direction compared to
     * {@link #testSharingChangeToEverybodyUpdatesVisibleGroups()}.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void testSharingChangeFromEverybodyUpdatesVisibleGroups() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.shareWithEverybody("Test Group A");
        final Group groupB = groupManager.createGroup("Test Group B");

        // Verify pre-condition.
        assertTrue(groupManager.getVisibleGroups(groupB).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "Pre-condition: 'Test Group A' should be visible to 'Test Group B' while it is shared with everybody.");

        // Execute system under test.
        groupA.shareWithNobody();

        // Verify result.
        assertFalse(groupManager.getVisibleGroups(groupB).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After sharing is disabled, 'Test Group A' should no longer be visible to users outside that group.");
    }

    /**
     * Verifies that enabling sharing with users in the same group is immediately reflected in the shared-group
     * results of those users.
     */
    @Test
    public void testSharingEnabledForUsersInSameGroupIsImmediatelyReflected() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final JID jane = new JID("jane", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);
        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.getMembers().add(jane);

        // Execute system under test (warm with pre-change result).
        final Collection<Group> beforeChange = groupManager.getSharedGroups("jane");
        groupA.shareWithUsersInSameGroup("Users in Test Group A");
        final Collection<Group> afterChange = groupManager.getSharedGroups("jane");

        // Verify result.
        assertFalse(beforeChange.stream().anyMatch(g -> "Test Group A".equals(g.getName())), "Pre-condition: before sharing is enabled, users should not see 'Test Group A' in shared-group results.");
        assertTrue(afterChange.stream().anyMatch(g -> "Test Group A".equals(g.getName())), "After sharing with users in the same group is enabled, users should immediately see 'Test Group A'.");
    }

    /**
     * Verifies that disabling sharing with users in the same group is immediately reflected in the shared-group
     * results of those users.
     */
    @Test
    public void testSharingDisabledForUsersInSameGroupIsImmediatelyReflected() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final JID jane = new JID("jane", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);
        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.getMembers().add(jane);
        groupA.shareWithUsersInSameGroup("Users in Test Group A");

        // Execute system under test (warm with pre-change result).
        final Collection<Group> beforeChange = groupManager.getSharedGroups("jane");
        groupA.shareWithNobody();
        final Collection<Group> afterChange = groupManager.getSharedGroups("jane");

        // Verify result.
        assertTrue(beforeChange.stream().anyMatch(g -> "Test Group A".equals(g.getName())), "Pre-condition: before sharing is disabled, users should see 'Test Group A' in shared-group results.");
        assertFalse(afterChange.stream().anyMatch(g -> "Test Group A".equals(g.getName())), "After sharing is disabled, users should immediately stop seeing 'Test Group A'.");
    }

    /**
     * Verifies that when a group's sharing target list removes a group, users of that removed group
     * immediately stop seeing the shared group.
     */
    @Test
    public void testSharedGroupsQueryReflectsCurrentStateAfterSharingTargetListRemoval() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();

        final Group groupB = groupManager.createGroup("Test Group B");
        groupB.shareWithEverybody("Users in Test Group B");

        final Group groupC = groupManager.createGroup("Test Group C");

        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.shareWithUsersInGroups(List.of("Test Group B", "Test Group C"), "Users in Test Group A");

        // Verify pre-condition.
        assertTrue(groupManager.getVisibleGroups(groupC).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "Pre-condition: members of 'Test Group C' should see 'Test Group A' before 'Test Group C' is removed from the sharing target list.");

        // Execute system under test.
        groupA.shareWithUsersInGroups(List.of("Test Group B"), "Users in Test Group A");

        // Verify result.
        assertFalse(groupManager.getVisibleGroups(groupC).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After 'Test Group C' is removed from the sharing target list, members of 'Test Group C' should no longer see 'Test Group A'.");
    }

    /**
     * Verifies that toggling sharing with everybody is immediately reflected in per-user shared-group results.
     */
    @Test
    public void testSharingChangeToAndFromEverybodyUpdatesUserSharedGroups() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        final Group groupA = groupManager.createGroup("Test Group A");

        // Execute system under test.
        final Collection<Group> beforeChange = groupManager.getSharedGroups("jane");
        groupA.shareWithEverybody("Users in Test Group A");
        final Collection<Group> afterEnable = groupManager.getSharedGroups("jane");
        groupA.shareWithNobody();
        final Collection<Group> afterDisable = groupManager.getSharedGroups("jane");

        // Verify result.
        assertFalse(beforeChange.stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "Pre-condition: before sharing is enabled, users should not see 'Test Group A' in shared-group results.");
        assertTrue(afterEnable.stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After sharing with everybody is enabled, users should immediately see 'Test Group A' in shared-group results.");
        assertFalse(afterDisable.stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After sharing with everybody is disabled, users should immediately stop seeing 'Test Group A' in shared-group results.");
    }

    /**
     * Verifies that when a group's sharing target list is updated, users that are newly in scope
     * see that group in shared-group results.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void testSharedGroupsQueryReflectsCurrentStateAfterSharingTargetListChange() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();

        final Group groupB = groupManager.createGroup("Test Group B");
        groupB.shareWithEverybody("Users in Test Group B");

        final Group groupC = groupManager.createGroup("Test Group C");

        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.shareWithUsersInGroups(List.of("Test Group B"), "Users in Test Group A");

        // Verify pre-condition.
        assertFalse(groupManager.getVisibleGroups(groupC).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "Pre-condition: members of 'Test Group C' should not see 'Test Group A' before that group is shared with users in 'Test Group C'.");

        // Execute system under test.
        groupA.shareWithUsersInGroups(List.of("Test Group B", "Test Group C"), "Users in Test Group A");

        // Verify result.
        assertTrue(groupManager.getVisibleGroups(groupC).stream().anyMatch(g -> "Test Group A".equals(g.getName())),
            "After the sharing target list is updated, members of 'Test Group C' should see 'Test Group A'.");
    }
}
