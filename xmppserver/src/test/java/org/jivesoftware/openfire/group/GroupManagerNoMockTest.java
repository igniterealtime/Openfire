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
 * Unit tests that verify the functionality of {@link GroupManager}.
 *
 * Implementation-wise, this class extends for DBTestCase, which is as JUnit 3 derivative. Practically, this means that
 * Junit 4 annotations in this class will be ignored.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GroupManagerNoMockTest extends DBTestCase
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
        final GroupManager groupManager = GroupManager.getInstance();
        final DefaultGroupProvider provider = new DefaultGroupProvider();

        // Execute system under test.
        final Group group = groupManager.createGroup("Test Group");
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
        final GroupManager groupManager = GroupManager.getInstance();
        assertThrows(GroupNameInvalidException.class, ()-> groupManager.createGroup(GROUP_NAME));
    }

    /**
     * Asserts that two groups with the same name cannot be created
     */
    public void testCreateGroupWithDuplicateNameThrows() throws Exception
    {
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup(GROUP_NAME);
        assertThrows(GroupAlreadyExistsException.class, ()-> groupManager.createGroup(GROUP_NAME));
    }

    /**
     * Asserts that a group can be created, removed and recreated again, with the same name.
     */
    public void testRecreateGroup() throws Exception
    {
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();
        final Group group = groupManager.createGroup(GROUP_NAME);
        groupManager.deleteGroup(group);
        groupManager.createGroup(GROUP_NAME);
    }

    /**
     * Reproduces an issue where adding a member to a group that does not exist would cause the group to be added
     * to a cache, making it appear that this group exists.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2426">Group cache can contain ghost entries</a>
     */
    public void testAddMemberToDeletedGroup() throws Exception
    {
        // Setup test fixture
        final String GROUP_NAME = "Test Group A";
        final GroupManager groupManager = GroupManager.getInstance();
        final Group group = groupManager.createGroup(GROUP_NAME);
        groupManager.deleteGroup(group);

        // Execute system under test.
        group.getMembers().add(new JID("test@example.org"));

        // Verify results.
        assertThrows(GroupNotFoundException.class, ()-> groupManager.getGroup(GROUP_NAME));
    }

    /**
     * Verifies that a group can be retrieved based on the name that it was created with.
     */
    public void testGetGroupByName() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("test");

        // Execute system under test.
        final Group result = groupManager.getGroup("test");

        // Verify result.
        assertNotNull(result);
        assertEquals("test", result.getName());
    }

    /**
     * Verifies that a {@link GroupManager#getGroupCount()} returns the correct count of groups when no groups
     * are present.
     */
    public void testGroupCountEmpty() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(0, result);
    }

    /**
     * Verifies that a {@link GroupManager#getGroupCount()} returns the correct count of groups when one group
     * is present.
     */
    public void testGroupCountOne() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("Test Group");

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(1, result);
    }

    /**
     * Verifies that a {@link GroupManager#getGroupCount()} returns the correct count of groups when multiple
     * groups are present.
     */
    public void testGroupCountMultiple() throws Exception
    {
        // Setup test fixture.
        final GroupManager groupManager = GroupManager.getInstance();
        groupManager.createGroup("Test Group A");
        groupManager.createGroup("Test Group B");

        // Execute system under test.
        final int result = groupManager.getGroupCount();

        // Verify result.
        assertEquals(2, result);
    }

    /**
     * Verifies that {@link GroupManager#deleteGroup(Group)} deletes a shared group, such that it cannot be retrieved
     */
    public void testDeleteGroupShared() throws Exception {
        final JID needle = new JID("jane@example.org");
        final GroupManager groupManager = GroupManager.getInstance();
        final Group groupA = groupManager.createGroup("Test Group A");
        groupA.shareWithEverybody("Users in group A");
        groupManager.createGroup("Test Group B");

        groupManager.deleteGroup(groupA);
        final Collection<Group> result = groupManager.getSharedGroups(needle.getNode());

        assertEquals(0, result.size());
    }
}
