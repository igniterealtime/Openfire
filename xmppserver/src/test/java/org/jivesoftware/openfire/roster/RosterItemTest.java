/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.roster;

import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RosterItem}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
class RosterItemTest
{
    private GroupManager groupManager;
    private Group sharedGroup;
    private Group regularGroup;

    private MockedStatic<GroupManager> staticMock;

    @BeforeEach
    void setup()
    {
        // Mock instance and static accessor
        groupManager = mock(GroupManager.class);
        staticMock = mockStatic(GroupManager.class);
        staticMock.when(GroupManager::getInstance).thenReturn(groupManager);

        // Mock groups
        sharedGroup = mock(Group.class);
        regularGroup = mock(Group.class);

        when(sharedGroup.isShared()).thenReturn(true);
        when(regularGroup.isShared()).thenReturn(false);
    }

    @AfterEach
    void tearDown()
    {
        staticMock.close();
    }

    /**
     * Verifies that a group known to be directly shared is removed from the collection.
     */
    @Test
    void removesDirectSharedGroup() throws Exception
    {
        when(groupManager.getGroup("shared")).thenReturn(sharedGroup);
        when(groupManager.getGroup("keep")).thenReturn(regularGroup);

        List<String> groups = new ArrayList<>(List.of("shared", "keep"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(List.of("keep"), groups);
    }

    /**
     * Ensures that a directly loaded group that is not shared is retained in the collection.
     */
    @Test
    void keepsDirectNonSharedGroup() throws Exception
    {
        when(groupManager.getGroup("team")).thenReturn(regularGroup);

        List<String> groups = new ArrayList<>(List.of("team"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(List.of("team"), groups);
    }

    /**
     * Ensures that when a group cannot be found by name and fallback lookup finds a shared group by display name, the
     * group name is removed from the collection.
     */
    @Test
    void removesFallbackSharedGroupWhenGroupNotFound() throws Exception
    {
        when(groupManager.getGroup("display")).thenThrow(new GroupNotFoundException());
        when(groupManager.search(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "display"))
            .thenReturn(List.of(sharedGroup));

        List<String> groups = new ArrayList<>(List.of("display"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(List.of(), groups);
    }

    /**
     * Ensures that when a group cannot be found by name and fallback lookup finds only non-shared groups, the group
     * name is retained.
     */
    @Test
    void keepsFallbackNonSharedWhenOnlyNonSharedGroupsMatch() throws Exception
    {
        when(groupManager.getGroup("display")).thenThrow(new GroupNotFoundException());
        when(groupManager.search(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "display"))
            .thenReturn(List.of(regularGroup));

        List<String> groups = new ArrayList<>(List.of("display"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(List.of("display"), groups);
    }

    /**
     * Verifies correct behavior when a mix of shared, fallback-shared, and non-shared groups are present.
     */
    @Test
    void handlesMixedGroupsCorrectly() throws Exception
    {
        when(groupManager.getGroup("shared1")).thenReturn(sharedGroup);
        when(groupManager.getGroup("normal")).thenReturn(regularGroup);

        when(groupManager.getGroup("fallback")).thenThrow(new GroupNotFoundException());
        when(groupManager.search(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "fallback"))
            .thenReturn(List.of(sharedGroup));

        List<String> groups = new ArrayList<>(List.of("shared1", "normal", "fallback"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(List.of("normal"), groups);
    }

    /**
     * Ensures that even if multiple fallback matches are shared, the group name is removed only once and no additional
     * removal attempts occur (OF-3149).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3149">issue OF-3149</a>
     */
    @Test
    void avoidsMultipleRemoveAttempts_OF3149() throws Exception
    {
        when(groupManager.getGroup("dup")).thenThrow(new GroupNotFoundException());
        when(groupManager.search(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "dup"))
            .thenReturn(List.of(sharedGroup, sharedGroup));

        List<String> groups = new ArrayList<>(List.of("dup"));
        RosterItem.removeSharedGroups(groups);

        assertEquals(0, groups.size(), "Group should be removed exactly once");
        verify(groupManager).search(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "dup");
    }
}
