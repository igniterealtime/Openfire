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
import org.jivesoftware.util.PersistableMap;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.JID;

import java.net.URL;
import java.util.Collection;

import static org.junit.Assert.assertThrows;

/**
 * Unit tests that verify the functionality of {@link DefaultGroupProvider}.
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
    }

    public DefaultGroupProviderTest(String name) {
        super( name );

        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, DRIVER );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, URL );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, USERNAME );
        System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, PASSWORD );
    }

    @Before
    public void setUp() throws Exception
    {
        // Ensure that DB-Unit's setUp is called!
        super.setUp();

        Fixtures.clearExistingProperties();
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

        // Wire the database connection provider used by the GroupProvider.
        final DefaultConnectionProvider conProvider = new DefaultConnectionProvider();
        conProvider.setDriver(DRIVER);
        conProvider.setServerURL(URL);
        conProvider.setUsername(USERNAME);
        conProvider.setPassword(PASSWORD);
        DbConnectionManager.setConnectionProvider(conProvider);
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
    @Test
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
    @Test
    public void testCreateGroupWithEmptyNameThrows() throws Exception
    {
        final String GROUP_NAME = "";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        assertThrows(GroupNameInvalidException.class, ()-> provider.createGroup(GROUP_NAME));
    }

    /**
     * Asserts that two groups with the same name cannot be created
     */
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
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
    @Test
    public void testPublicSharedGroupNamesOne() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "everybody");
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
    @Test
    public void testPublicSharedGroupNamesMultiple() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "everybody");
        provider.createGroup("Test Group B").getProperties().put("sharedRoster.showInRoster", "everybody");

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
    @Test
    public void testPublicSharedGroupNamesWithNobody() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "nobody");
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
    @Test
    public void testPublicSharedGroupNamesWithOnlyGroup() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
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
    @Test
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
    @Test
    public void testSharedGroupNamesNobody() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "nobody");
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
    @Test
    public void testSharedGroupNamesOneWithEveryone() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "everybody");
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
    @Test
    public void testSharedGroupNamesOneWithGroup() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
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
    @Test
    public void testSharedGroupNamesOneWithMix() throws Exception
    {
        // Setup test fixture.
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
        provider.createGroup("Test Group B").getProperties().put("sharedRoster.showInRoster", "everybody");

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames();

        // Verify result.
        assertEquals(2, result.size());
        assertTrue(result.contains("Test Group A"));
        assertTrue(result.contains("Test Group B"));
    }

    /**
     * Verifies that a {@link DefaultGroupProvider#getSharedGroupNames(JID)} returns nothing when there are no groups
     * that are shared, without the user being in any of the groups.
     */
    @Test
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
    @Test
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
    @Test
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
    @Test
    public void testSharedGroupNamesByNameNobody() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "nobody");
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
    @Test
    public void testSharedGroupNamesByNameNobodyButMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.getProperties().put("sharedRoster.showInRoster", "nobody");
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
    @Test
    public void testSharedGroupNamesByNameNobodyButAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.getProperties().put("sharedRoster.showInRoster", "nobody");
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
    @Test
    public void testSharedGroupNamesByNameWithEveryoneNoAssociation() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").getProperties().put("sharedRoster.showInRoster", "everybody");
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
    @Test
    public void testSharedGroupNamesByNameWithEveryoneMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.getProperties().put("sharedRoster.showInRoster", "everybody");
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
    @Test
    public void testSharedGroupNamesByNameWithEveryoneAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        final Group input = provider.createGroup("Test Group B");
        input.getProperties().put("sharedRoster.showInRoster", "everybody");
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
    @Test
    public void testSharedGroupNamesByNameWithGroupNoAssociation() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
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
    @Test
    public void testSharedGroupNamesByNameWithGroupMember() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group input = provider.createGroup("Test Group A");
        input.getMembers().add(needle);
        final PersistableMap<String, String> properties = input.getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
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
    @Test
    public void testSharedGroupNamesByNameWithGroupAdmin() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final Group input = provider.createGroup("Test Group A");
        input.getAdmins().add(needle);
        final PersistableMap<String, String> properties = input.getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");
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
    @Test
    public void testSharedGroupNamesByNameWithGroupMemberOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");

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
    @Test
    public void testSharedGroupNamesByNameWithGroupAdminOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group A");

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
    @Test
    public void testSharedGroupNamesByNameWithOtherGroupMemberOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group B"); // Group A is shared with users of group B!

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
    @Test
    public void testSharedGroupNamesByNameWithOtherGroupAdminOfOtherGroup() throws Exception
    {
        // Setup test fixture.
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "onlyGroup");
        properties.put("sharedRoster.groupList", "Test Group B"); // Group A is shared with users of group B!

        final Group input = provider.createGroup("Test Group B");
        input.getAdmins().add(needle);

        // Execute system under test.
        final Collection<String> result = provider.getSharedGroupNames(needle);

        // Verify result.
        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group A"));
    }

    @Test
    public void testDeleteGroup() throws Exception{
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    @Test
    public void testDeleteGroupShared() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        final PersistableMap<String, String> properties = provider.createGroup("Test Group A").getProperties();
        properties.put("sharedRoster.showInRoster", "everyone");
        provider.createGroup("Test Group B");

        provider.deleteGroup("Test Group A");
        final Collection<String> result = provider.getSharedGroupNames(needle);

        assertEquals(0, result.size());
    }

    @Test
    public void testDeleteGroupWithNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        assertThrows(GroupNotFoundException.class,() -> provider.deleteGroup("Test Group C"));
    }

    @Test
    public void testSetName() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setName("Test Group A", "Test Group B");
        final Collection<String> result = provider.getGroupNames();

        assertEquals(1, result.size());
        assertTrue(result.contains("Test Group B"));
    }

    @Test
    public void testSetNameWithEmptyStringThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNameInvalidException.class, () -> provider.setName("Test Group A", ""));
    }

    @Test
    public void testSetNameToExistingGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");
        provider.createGroup("Test Group B");

        final Collection<String> result = provider.getGroupNames();

        assertThrows(GroupAlreadyExistsException.class, () -> provider.setName("Test Group A", "Test Group B"));
    }

    @Test
    public void testSetNameOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setName("Test Group B", "Test Group C"));
    }

    @Test
    public void testSetDescription() throws Exception {
        final String DESC = "The description of Test Group A";
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.setDescription("Test Group A", DESC);
        final Group result = provider.getGroup("Test Group A");

        assertEquals(DESC, result.getDescription());
    }

    @Test
    public void testSetDescriptionWithEmptyString() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A").setDescription("The description of Test Group A");

        provider.setDescription("Test Group A", "");
        final Group result = provider.getGroup("Test Group A");

        assertEquals("", result.getDescription());
    }

    @Test
    public void testSetDescriptionOnNonExistentGroupThrows() throws Exception {
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.setDescription("Test Group B", "Some Description"));
    }

    @Test
    public void testAddMember() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, false);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getMembers().contains(needle));
    }

    @Test
    public void testAddAdminMember() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        provider.addMember("Test Group A", needle, true);
        final Group result = provider.getGroup("Test Group A");

        assertTrue(result.getAdmins().contains(needle));
    }

    @Test
    public void testAddMemberOnNonExistentGroupThrows() throws Exception {
        final JID needle = new JID("jane@example.org");
        final DefaultGroupProvider provider = new DefaultGroupProvider();
        provider.createGroup("Test Group A");

        assertThrows(GroupNotFoundException.class, () -> provider.addMember("Test Group B", needle, false));
    }
}
