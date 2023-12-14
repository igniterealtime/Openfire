/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.admin;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.group.DefaultGroupProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GroupBasedAdminProviderTest {

    private static final List<JID> ADMINS = Arrays.asList(new JID("user-a"), new JID("user-b"));
    private static String mockGroupName;
    private static Group mockGroup;

    private GroupBasedAdminProvider adminProvider;

    @BeforeAll
    public static void beforeClass() throws Exception {
        Fixtures.disableDatabasePersistence();
        Fixtures.reconfigureOpenfireHome();
    }

    @BeforeEach
    public void setUp() throws Exception {
        CacheFactory.initialize();
        GroupManager.GROUP_PROVIDER.setValue(TestGroupProvider.class);
        mockGroupName = "mock-group-name";
        JiveGlobals.setProperty("provider.group.groupBasedAdminProvider.groupName", mockGroupName);
        mockGroup = mock(Group.class);
        doReturn(ADMINS).when(mockGroup).getMembers();
        adminProvider = new GroupBasedAdminProvider();
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
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

    @Test
    public void willNotSetTheListOfAdmins() {
        assertThat(adminProvider.isReadOnly(), is(true));
        assertThrows(UnsupportedOperationException.class, () -> adminProvider.setAdmins(ADMINS));
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
