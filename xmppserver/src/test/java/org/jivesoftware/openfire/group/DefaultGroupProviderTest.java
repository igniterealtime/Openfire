/*
 * Copyright (C) 2022 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
            Field field = GroupManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, null);
            field.setAccessible(false);
        }
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
     * Verifies that a {@link DefaultGroupProvider#getPublicSharedGroupNames()} returns nothing when there
     * are no groups that are shared with everyone.
     */
    public void testPublicSharedGroupNamesNone() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getPublicSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getPublicSharedGroupNames()} returns one group when there
     * is one groups that is shared with everyone.
     */
    public void testPublicSharedGroupNamesOne() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getPublicSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getPublicSharedGroupNames()} returns all group names when
     * there are multiple groups that are shared with everyone.
     */
    public void testPublicSharedGroupNamesMultiple() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B").shareWithEverybody("Users in group B");

        // Execute system under test.
        final Collection<String> result = provider.getPublicSharedGroupNames();

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getPublicSharedGroupNames()} returns nothing when there
     * are no groups that are shared with everyone, but when there is a group that is shared explicitly with 'nobody'.
     */
    public void testPublicSharedGroupNamesWithNobody() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getPublicSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getPublicSharedGroupNames()} returns nothing when there
     * are no groups that are shared with everyone, but when there is a group that is shared explicitly with members
     * of that group.
     */
    public void testPublicSharedGroupNamesWithOnlyGroup() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getPublicSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} returns nothing when there are no groups
     * that are shared.
     */
    public void testSharedGroupNamesNone() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} returns nothing when there are no groups
     * that are shared, but one that's explicitly shared with 'nobody'.
     */
    public void testSharedGroupNamesNobody() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
     * everyone.
     */
    public void testSharedGroupNamesOneWithEveryone() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
     * members of that group.
     */
    public void testSharedGroupNamesOneWithGroup() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
     * everyone and another group that is shared explicitly with members of that group.
     */
    public void testSharedGroupNamesOneWithMix() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");
        provider.createGroup("Test Group B").shareWithEverybody("Users in group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationEverybodyToEverybody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Users in group A"); // Technically not a 'change' - but should not generate false results anyway!
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationEverybodyToEverybodyRename() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Users in group A RENAMED"); // Different name
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationEverybodyToSameGroup() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationEverybodyToOtherGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to no longer be shared.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationEverybodytoSharedWithNobody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithNobody();
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSameGroupToEverybody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSameGroupToSameGroup() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A"); // Technically not a 'change' - but should not generate false results anyway!
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSameGroupRenamed() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A RENAMED"); // Different name
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSameGroupToOtherGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to no longer be shared.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSameGrouptoSharedWithNobody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithNobody();
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherGroupToEverybody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherGroupToSameGroup() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherRename() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A RENAMED"); // Different name
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherToOtherGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A"); // Technically not a 'change' - but should not generate false results anyway!
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherToAdditionalGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Arrays.asList("Test Group B", "Test Group C"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherToFewerGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Arrays.asList("Test Group B", "Test Group C"), "Users in group A");
        provider.createGroup("Test Group B");
        provider.createGroup("Test Group C");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to no longer be shared.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationOtherGrouptoSharedWithNobody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithNobody();
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has been removed.
     */
    public void testSharedGroupNamesStaleCacheTestAfterGroupRemoval() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithEverybody("Users in group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        provider.deleteGroup(group.getName());
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationUnsharedToEverybody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationUnsharedToSameGroup() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationUnsharedToOtherGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to no longer be shared.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationUnsharedSharedWithNobody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithNobody();
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSharedWithNobodyToEverybody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithNobody();

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithEverybody("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSharedWithNobodyToSameGroup() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithNobody();

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInSameGroup("Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to be shared with a different set of users.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSharedWithNobodyToOtherGroups() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithNobody();
        provider.createGroup("Test Group B");

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
     * previously obtained group has reconfigured to no longer be shared.
     */
    public void testSharedGroupNamesStaleCacheTestAfterModificationSharedWithNobodytoSharedWithNobody() throws Exception
    {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithNobody();

        // Execute system under test;
        provider.getSharedGroupNames(); // populate cache.
        group.shareWithNobody(); // Technically not a 'change' - but should not generate false results anyway!
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, without the user being in any of the groups.
     */
    public void testSharedGroupNamesByNameNone() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, with the user being in a member of one of the groups.
     */
    public void testSharedGroupNamesByNameNoneButMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, with the user being in an admin of one of the groups.
     */
    public void testSharedGroupNamesByNameNoneButAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, but one that is explicitly shared with 'nobody', without the user being in any of the groups.
     */
    public void testSharedGroupNamesByNameNobody() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, but one that is explicitly shared with 'nobody', with the user being in a member of that group.
     */
    public void testSharedGroupNamesByNameNobodyButMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.shareWithNobody();
        input.getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, but one that is explicitly shared with 'nobody', with the user being in an admin of that group.
     */
    public void testSharedGroupNamesByNameNobodyButAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.shareWithNobody();
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
     * group that is shared with everybody, without the user being in that group.
     */
    public void testSharedGroupNamesByNameWithEveryoneNoAssociation() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
     * group that is shared with everybody, with the user being in a member of that group.
     */
    public void testSharedGroupNamesByNameWithEveryoneMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.shareWithEverybody("Users in group B");
        input.getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
     * group that is shared with everybody, with the user being in an admin of that group.
     */
    public void testSharedGroupNamesByNameWithEveryoneAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.shareWithEverybody("Users in group B");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there's a group
     * shared with users in the group, without the user being in that group.
     */
    public void testSharedGroupNamesByNameWithGroupNoAssociation() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");

        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
     * group shared with users in the group, with the user being in a member of that group.
     */
    public void testSharedGroupNamesByNameWithGroupMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group input = provider.createGroup("Test Group A");
        input.shareWithUsersInSameGroup("Users in group A");
        input.getMembers().add(needle);

        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
     * group shared with users in the group, with the user being in an admin of that group.
     */
    public void testSharedGroupNamesByNameWithGroupAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group input = provider.createGroup("Test Group A");
        input.shareWithUsersInSameGroup("Users in group A");
        input.getAdmins().add(needle);

        provider.createGroup("Test Group B");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
     * group that is shared with users in the group, with the user being in a member of another group.
     */
    public void testSharedGroupNamesByNameWithGroupMemberOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");

        final Group input = provider.createGroup("Test Group B");
        input.getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
     * group that is shared with users in the group, with the user being in an admin of another group.
     */
    public void testSharedGroupNamesByNameWithGroupAdminOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");

        final Group input = provider.createGroup("Test Group B");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns a group when there is one
     * group that is shared with users in another group, with the user being in a member of that other group.
     */
    public void testSharedGroupNamesByNameWithOtherGroupMemberOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A") // Group A is shared with users in group B!
            .shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");

        final Group input = provider.createGroup("Test Group B");
        input.getMembers().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
     * group that is shared with users in the group, with the user being in an admin of another group.
     */
    public void testSharedGroupNamesByNameWithOtherGroupAdminOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A") // Group A is shared with users in group B!
            .shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");

        final Group input = provider.createGroup("Test Group B");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns only groups that are configured to
     * be directly visible to the (group of) the provided user, when recursion is disabled.
     */
    public void testSharedGroupNamesByNameRecursionDisabled() throws Exception
    {
        // Setup test fixture.
        DefaultGroupProvider.SHARED_GROUP_RECURSIVE.setValue(false);

        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A") // Group A is shared with users in group B!
            .shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");

        provider.createGroup("Test Group B") // Group B is shared with users in group C!
            .shareWithUsersInGroups(Collections.singletonList("Test Group C"), "Users in group B");

        provider.createGroup("Test Group C") // Group C is shared with users in group D!
            .shareWithUsersInGroups(Collections.singletonList("Test Group D"), "Users in group C");

        final Group input = provider.createGroup("Test Group D");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group C"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns groups that are configured to be
     * directly visible to the (group of) the provided user, but also groups that are configured to be visible to
     * members of those groups (etc), when recursion is enabled.
     */
    public void testSharedGroupNamesByNameRecursionEnabled() throws Exception
    {
        // Setup test fixture.
        DefaultGroupProvider.SHARED_GROUP_RECURSIVE.setValue(true);

        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A") // Group A is shared with users in group B!
            .shareWithUsersInGroups(Collections.singletonList("Test Group B"), "Users in group A");

        provider.createGroup("Test Group B") // Group B is shared with users in group C!
            .shareWithUsersInGroups(Collections.singletonList("Test Group C"), "Users in group B");

        provider.createGroup("Test Group C") // Group C is shared with users in group D!
            .shareWithUsersInGroups(Collections.singletonList("Test Group D"), "Users in group C");

        final Group input = provider.createGroup("Test Group D");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(3, result.size());
        assertTrue(result.contains("Test Group C"));
        assertTrue(result.contains("Test Group B"));
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
     * a member is added to a shared group after the group as already loaded in a cache.
     */
    public void testSharedGroupNamesByNameStaleCacheTestMemberAdded() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in Group A");

        // Execute system under test.
        provider.getSharedGroupNames(needle); // populate cache
        group.getMembers().add(needle);
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
     * a member is removed from a shared group after the group as already loaded in a cache.
     */
    public void testSharedGroupNamesByNameStaleCacheTestMemberRemoved() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.shareWithUsersInSameGroup("Users in Group A");
        group.getMembers().add(needle);

        // Execute system under test.
        provider.getSharedGroupNames(needle); // populate cache
        group.getMembers().remove(needle);
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
     * sharing is enabled after the group as already loaded in a cache.
     */
    public void testSharedGroupNamesByNameStaleCacheTestSharingEnabled() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.getMembers().add(needle);

        // Execute system under test.
        provider.getSharedGroupNames(needle); // populate cache
        group.shareWithUsersInSameGroup("Users in Group A");
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
     * sharing is disabled after the group as already loaded in a cache.
     */
    public void testSharedGroupNamesByNameStaleCacheTestSharingDisabled() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group group = provider.createGroup("Test Group A");
        group.getMembers().add(needle);
        group.shareWithUsersInSameGroup("Users in Group A");

        // Execute system under test.
        provider.getSharedGroupNames(needle); // populate cache
        group.shareWithNobody();
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(0, result.size());
    }

    public void testDeleteGroup() throws Exception{
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    public void testDeleteGroupShared() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Users in group A");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames(needle);

        assertEquals(0, result.size());
    }

    public void testDeleteGroupWithNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        assertThrows(GroupNotFoundException.class,() -> provider.deleteGroup("Test Group C"));
    }

    public void testSetName() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setName("Test Group A", "Test Group B");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    public void testSetNameWithEmptyStringThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNameInvalidException.class, () -> provider.setName("Test Group A", ""));
    }

    public void testSetNameToExistingGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        assertThrows(GroupAlreadyExistsException.class, () -> provider.setName("Test Group A", "Test Group B"));
    }

    public void testSetNameOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setName("Test Group B", "Test Group C"));
    }

    public void testSetDescription() throws Exception {
        final String DESC = "The description of Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setDescription("Test Group A", DESC);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(DESC, result.getDescription());
    }

    public void testSetDescriptionWithEmptyString() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").setDescription("The description of Test Group A");

        provider.setDescription("Test Group A", "");
        final Group result = provider.getGroup("Test Group A");

        assertEquals("", result.getDescription());
    }

    public void testSetDescriptionOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setDescription("Test Group B", "Some Description"));
    }

    public void testAddMember() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getMembers().contains(needle));
    }

    public void testAddAdminMember() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, true);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getAdmins().contains(needle));
    }

    public void testAddMemberOnNonExistentGroupThrows() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.addMember("Test Group B", needle, false));
    }
}
