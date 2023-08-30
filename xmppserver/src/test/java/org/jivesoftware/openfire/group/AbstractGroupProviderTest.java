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
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Unit tests that verify the functionality of {@link AbstractGroupProvider} methods not overridden by the
 * {@link DefaultGroupProvider}.
 *
 * Implementation-wise, this class extends for DBTestCase, which is as JUnit 3 derivative. Practically, this means that
 * Junit 4 annotations in this class will be ignored.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class AbstractGroupProviderTest extends DBTestCase {

    public static final String DRIVER = "org.hsqldb.jdbcDriver";
    public static final String URL;
    public static final String USERNAME = "sa";
    public static final String PASSWORD = "";

    static {
        final java.net.URL location = AbstractGroupProvider.class.getResource("/datasets/openfire.script");
        assert location != null;
        final String fileLocation = location.toString().substring(0, location.toString().lastIndexOf("/")+1) + "openfire";
        URL = "jdbc:hsqldb:"+fileLocation+";ifexists=true";

        // Setup database configuration of DBUnit.
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, DRIVER );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, URL );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, USERNAME );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, PASSWORD );
    }

    @Override
    protected IDataSet getDataSet() throws Exception {
        // This dataset restores the state of the database to one that does not contain any groups (or metadata for
        // groups) between each test.
        return new XmlDataSet(getClass().getResourceAsStream("/datasets/clean.xml"));
    }

    public void setUp() throws Exception {
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

        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} returns nothing a group that is shared with
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames()} does not return a stale cache entry after a
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns nothing when there's a group
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns a group when there is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns no group when there are is one
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns only groups that are configured to
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} returns groups that are configured to be
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
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
     * Verifies that a {@link AbstractGroupProvider#getSharedGroupNames(JID)} does not return a stale cache test when
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

    /**
     * Verifies that a {@link AbstractGroupProvider#getPublicSharedGroupNames()} returns nothing when there
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
     * Verifies that a {@link AbstractGroupProvider#getPublicSharedGroupNames()} returns one group when there
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
     * Verifies that a {@link AbstractGroupProvider#getPublicSharedGroupNames()} returns all group names when
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
     * Verifies that a {@link AbstractGroupProvider#getPublicSharedGroupNames()} returns nothing when there
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
     * Verifies that a {@link AbstractGroupProvider#getPublicSharedGroupNames()} returns nothing when there
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
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns false when there are no share groups
     */
    public void testHasSharedGroupsReturnsFalseWhenThereAreNone() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");
        assertFalse(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when one group is shared with
     * members of the same group
     */
    public void testHasSharedGroupsReturnsTrueWhenOneIsSharedWithTheSameWithTheSameGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");
        provider.createGroup("Test Group B");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when all groups are shared with
     * members of the same group
     */
    public void testHasSharedGroupsReturnsTrueWhenAllAreSharedWithTheSameWithTheSameGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in group A");
        provider.createGroup("Test Group B").shareWithUsersInSameGroup("Users in group B");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when one group is shared with
     * everybody
     */
    public void testHasSharedGroupsReturnsTrueWhenOneIsSharedWithTheSameWithEverybody() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithEverybody("Shared with Everybody");
        provider.createGroup("Test Group B");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when one group is shared with
     * members of the other group
     */
    public void testHasSharedGroupsReturnsTrueWhenOneIsSharedWithTheOther() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").shareWithUsersInGroups(List.of("Test Group A"),"Users in Group A");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when one group is shared with
     * members of a non-existent group (since it shows intent)
     */
    public void testHasSharedGroupsReturnsTrueWhenOneIsSharedWithNonExistent() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").shareWithUsersInGroups(List.of("Non-existent Group"),"Non-existent Group");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when one group is shared with
     * members of a non-existent group that's been deleted (since it shows intent)
     */
    public void testHasSharedGroupsReturnsTrueWhenOneIsSharedWithNonExistentByWayOfDeletion() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").shareWithUsersInGroups(List.of("Test Group A"),"Users in Group A");
        provider.deleteGroup("Test Group A");
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns true when called the second time (since
     * the value is cached as part of processing
     */
    public void testHasSharedGroupsReturnsTrueWhenCalledTheSecondTime() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B").shareWithEverybody("Everybody");
        provider.hasSharedGroups();
        assertTrue(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns false when called the second time (since
     * the value is cached as part of processing
     */
    public void testHasSharedGroupsReturnsFalseWhenCalledTheSecondTime() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");
        provider.hasSharedGroups();
        assertFalse(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#hasSharedGroups()} returns false when group is explicitly shared
     * with nobody
     */
    public void testHasSharedGroupsReturnsFalseWhenSharedWithNobody() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        assertFalse(provider.hasSharedGroups());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#getVisibleGroupNames(String)} returns an empty collection when no
     * groups are shared
     */
    public void testGetVisibleGroupNamesWithNoSharedGroups() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Collection<String> result = provider.getVisibleGroupNames("Test Group A");
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#getVisibleGroupNames(String)} returns an empty collection when
     * groups are shared explicitly with Nobody
     */
    public void testGetVisibleGroupNamesWithGroupSharedExplicitlyWithNobody() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        final Collection<String> result = provider.getVisibleGroupNames("Test Group A");
        assertEquals(0, result.size());
    }

    /**
     * Verifies that a {@link AbstractGroupProvider#getVisibleGroupNames(String)} returns a group shared with itself
     */
    public void testGetVisibleGroupNamesWithGroupSharedWithItself() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithUsersInSameGroup("Users in Test Group A");
        final Collection<String> result = provider.getVisibleGroupNames("Test Group A");
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    public void testSearchByKeyAndValueReturnsGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        final Collection<String> result = provider.search(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.nobody.getDbValue());
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    public void testSearchByKeyAndValueReturnsNothingForInvalidProperty() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        final Collection<String> result = provider.search("Invalid Property", "value");
        assertEquals(0, result.size());
    }

    public void testSearchByKeyAndValueReturnsNothingForInvalidValue() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        final Collection<String> result = provider.search(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, "Invalid Value");
        assertEquals(0, result.size());
    }

    public void testSearchByKeyAndValueReturnsMultipleGroups() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        provider.createGroup("Test Group B").shareWithNobody();
        final Collection<String> result = provider.search(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.nobody.getDbValue());
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    public void testSearchByKeyAndValueReturnsResultsForBlankValues() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").shareWithNobody();
        provider.createGroup("Test Group B").shareWithNobody();
        final Collection<String> result = provider.search(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, "");
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    public void testLoadPropertiesReturnsPropertiesForGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        Group a = provider.createGroup("Test Group A");
        a.shareWithNobody();
        PersistableMap<String,String> result =  provider.loadProperties(a);

        assertEquals(3, result.size());
        assertTrue(result.containsKey(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY));
        assertEquals("", result.get(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY));
        assertTrue(result.containsKey(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY));
        assertEquals("", result.get(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY));
        assertTrue(result.containsKey(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY));
        assertEquals(SharedGroupVisibility.nobody.getDbValue(), result.get(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY));
    }

    public void testLoadPropertiesReturnsNoPropertiesForNewGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        Group a = provider.createGroup("Test Group A");
        PersistableMap<String,String> result =  provider.loadProperties(a);
        assertEquals(0, result.size());
    }

    public void testLoadPropertiesReturnsNoPropertiesForNonExistentGroup() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        Group newGroup = new Group("New", "New Description", new ArrayList<>(), new ArrayList<>());
        PersistableMap<String,String> result =  provider.loadProperties(newGroup);
        assertEquals(0, result.size());
    }
}
