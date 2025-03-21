/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.util.PersistableMap;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A very basic implementation of a GroupProvider, that retains data in-memory.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestGroupProvider implements GroupProvider
{
    final Map<String, Group> data = new HashMap<>();

    @Override
    public Group createGroup(String name) throws GroupAlreadyExistsException, GroupNameInvalidException
    {
        if (data.containsKey(name)) {
            throw new GroupAlreadyExistsException();
        }
        final Group group = new Group(name, "Used in unit tests", Collections.emptySet(), Collections.emptySet());
        data.put(name, group);
        return group;
    }

    @Override
    public void deleteGroup(String name) throws GroupNotFoundException
    {
        data.remove(name);
    }

    @Override
    public Group getGroup(String name) throws GroupNotFoundException
    {
        final Group result = data.get(name);
        if (result == null) {
            throw new GroupNotFoundException();
        }
        return result;
    }

    @Override
    public void setName(String oldName, String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException
    {
        if (!data.containsKey(oldName)) {
            throw new GroupNotFoundException();
        }
        if (data.containsKey(newName)) {
            throw new GroupAlreadyExistsException();
        }
        data.put(newName, data.remove(oldName));
    }

    @Override
    public void setDescription(String name, String description) throws GroupNotFoundException
    {
        final Group group = data.get(name);
        if (group == null) {
            throw new GroupNotFoundException();
        }
        group.setDescription(description);
    }

    @Override
    public int getGroupCount()
    {
        return data.size();
    }

    @Override
    public Collection<String> getGroupNames()
    {
        return data.keySet();
    }

    @Override
    public boolean isSharingSupported()
    {
        return false;
    }

    @Override
    public Collection<String> getSharedGroupNames()
    {
        return List.of();
    }

    @Override
    public Collection<String> getSharedGroupNames(JID user)
    {
        return List.of();
    }

    @Override
    public Collection<String> getPublicSharedGroupNames()
    {
        return List.of();
    }

    @Override
    public Collection<String> getVisibleGroupNames(String userGroup)
    {
        return List.of();
    }

    @Override
    public Collection<String> getGroupNames(int startIndex, int numResults)
    {
        return new ArrayList<>(data.keySet()).subList(startIndex, Math.min(numResults, data.size() - startIndex));
    }

    @Override
    public Collection<String> getGroupNames(JID user)
    {
        return data.values().stream()
            .filter(group -> group.isUser(user))
            .map(Group::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public void addMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
    {
        final Group group = data.get(groupName);
        if (group == null) {
            throw new GroupNotFoundException();
        }
        (administrator ? group.getAdmins() : group.getMembers()).add(user);
    }

    @Override
    public void updateMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
    {
        final Group group = data.get(groupName);
        if (group == null) {
            throw new GroupNotFoundException();
        }
        (administrator ? group.getAdmins() : group.getMembers()).add(user);
        (!administrator ? group.getAdmins() : group.getMembers()).remove(user);
    }

    @Override
    public void deleteMember(String groupName, JID user)
    {
        final Group group = data.get(groupName);
        if (group != null) {
            group.getMembers().remove(user);
            group.getAdmins().remove(user);
        }
    }

    @Override
    public boolean isReadOnly()
    {
        return false;
    }

    @Override
    public Collection<String> search(String query)
    {
        return List.of();
    }

    @Override
    public Collection<String> search(String query, int startIndex, int numResults)
    {
        return List.of();
    }

    @Override
    public Collection<String> search(String key, String value)
    {
        return List.of();
    }

    @Override
    public boolean isSearchSupported()
    {
        return false;
    }

    @Override
    public PersistableMap<String, String> loadProperties(Group group)
    {
        return null;
    }

    /**
     * A provider that holds no data.
     */
    public static class NoGroupProvider extends TestGroupProvider
    {}

    public static class LittleEndiansGroupProvider extends TestGroupProvider
    {
        public LittleEndiansGroupProvider() {
            try {
                createGroup("little-endians");
            } catch (GroupAlreadyExistsException | GroupNameInvalidException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class BigEndiansGroupProvider extends TestGroupProvider
    {
        public BigEndiansGroupProvider() {
            try {
                createGroup("big-endians");
            } catch (GroupAlreadyExistsException | GroupNameInvalidException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
