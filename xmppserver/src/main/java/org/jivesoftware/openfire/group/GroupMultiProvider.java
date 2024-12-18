/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved
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
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link GroupProvider} that delegates to one or more 'backing' GroupProvider.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class GroupMultiProvider implements GroupProvider
{
    private final static Logger Log = LoggerFactory.getLogger( GroupMultiProvider.class );

    /**
     * Instantiates a GroupProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * GroupProvider classes are required to have a public, no-argument constructor.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @return A group provider (can be null).
     */
    public static GroupProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty)
    {
        return instantiate(implementationProperty, null);
    }

    /**
     * Instantiates a GroupProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * GroupProvider classes are required to have a public, no-argument constructor, but can have an optional additional
     * constructor that takes a single String argument. If such constructor is defined, then it is invoked with the
     * value of the second argument of this method. This is typically used to (but needs not) identify a property
     * (by name) that holds additional configuration for the to be instantiated GroupProvider. This
     * implementation will pass on any non-empty value to the constructor. When a configuration argument is provided,
     * but no constructor exists in the implementation that accepts a single String value, this method will log a
     * warning and attempt to return an instance based on the no-arg constructor of the class.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @param configProperty an opaque string value passed to the constructor.
     * @return A group provider (can be null).
     */
    public static GroupProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty, @Nullable final SystemProperty<String> configProperty)
    {
        final Class<? extends GroupProvider> implementationClass = implementationProperty.getValue();
        if (implementationClass == null) {
            Log.debug( "Property '{}' is undefined or has no value. Skipping.", implementationProperty.getKey() );
            return null;
        }
        Log.debug("About to to instantiate an GroupProvider '{}' based on the value of property '{}'.", implementationClass, implementationProperty.getKey());

        try {
            if (configProperty != null && configProperty.getValue() != null && !configProperty.getValue().isEmpty()) {
                try {
                    final Constructor<? extends GroupProvider> constructor = implementationClass.getConstructor(String.class);
                    final GroupProvider result = constructor.newInstance(configProperty.getValue());
                    Log.debug("Instantiated GroupProvider '{}' with configuration: '{}'", implementationClass.getName(), configProperty.getValue());
                    return result;
                } catch (NoSuchMethodException e) {
                    Log.warn("Custom configuration is defined for the a provider but the configured class ('{}') does not provide a constructor that takes a String argument. Custom configuration will be ignored. Ignored configuration: '{}'", implementationProperty.getValue().getName(), configProperty);
                }
            }

            final GroupProvider result = implementationClass.getDeclaredConstructor().newInstance();
            Log.debug("Instantiated GroupProvider '{}'", implementationClass.getName());
            return result;
        } catch (Exception e) {
            Log.error("Unable to load GroupProvider '{}'. Data from this provider will not be available.", implementationClass.getName(), e);
            return null;
        }
    }

    /**
     * Returns all GroupProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    abstract Collection<GroupProvider> getGroupProviders();

    /**
     * Returns the 'backing' provider that serves the provided group. Note that the group need not exist.
     *
     * Finds a suitable GroupProvider for the group.
     *
     * Note that the provided group name need not reflect a pre-existing group (the instance might be used to determine in
     * which provider a new group is to be created).
     *
     * Implementations are expected to be able to find a GroupProvider for any group name. If an implementation fails to do
     * so, such a failure is assumed to be the result of a problem in implementation or configuration.
     *
     * @param groupName A group identifier (cannot be null or empty).
     * @return A GroupProvider for the group (never null).
     */
    abstract GroupProvider getGroupProvider(String groupName);

    /**
     * Returns the number of groups in the system, calculated as the sum of groups in each provider.
     *
     * @return the number of groups in the system.
     */
    @Override
    public int getGroupCount()
    {
        return getGroupProviders().parallelStream()
            .map(GroupProvider::getGroupCount)
            .reduce(0, Integer::sum);
    }

    /**
     * Returns the Collection of all group names in each of the providers.
     *
     * @return the Collection of all groups.
     */
    @Override
    public Collection<String> getGroupNames()
    {
        return getGroupProviders().parallelStream()
            .map(GroupProvider::getGroupNames)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns true if at least one provider allows group sharing. Shared groups
     * enable roster sharing.
     *
     * @return true if group sharing is supported.
     */
    @Override
    public boolean isSharingSupported()
    {
        return getGroupProviders().parallelStream()
            .anyMatch(GroupProvider::isSharingSupported);
    }

    /**
     * Returns an unmodifiable Collection of all shared groups from each provider that supports group sharing.
     *
     * @return unmodifiable Collection of all shared groups in the system.
     */
    @Override
    public Collection<String> getSharedGroupNames()
    {
        return getGroupProviders().parallelStream()
            .filter(GroupProvider::isSharingSupported)
            .map(GroupProvider::getSharedGroupNames)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given user.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param user The bare JID for the user (node@domain)
     * @return unmodifiable Collection of all shared groups in the system for a given user.
     */
    @Override
    public Collection<String> getSharedGroupNames(JID user)
    {
        return getGroupProviders().parallelStream()
            .filter(GroupProvider::isSharingSupported)
            .map(groupProvider -> groupProvider.getSharedGroupNames(user))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns an unmodifiable Collection of all public shared groups by each of the providers that support sharing.
     *
     * @return unmodifiable Collection of all public shared groups in the system.
     */
    @Override
    public Collection<String> getPublicSharedGroupNames()
    {
        return getGroupProviders().parallelStream()
            .filter(GroupProvider::isSharingSupported)
            .map(GroupProvider::getPublicSharedGroupNames)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns an unmodifiable Collection of groups that are visible to the members of the given group.
     *
     * @param userGroup The given group
     * @return unmodifiable Collection of group names that are visible to the given group.
     */
    @Override
    public Collection<String> getVisibleGroupNames(String userGroup)
    {
        return getGroupProviders().parallelStream()
            .map(groupProvider -> groupProvider.getVisibleGroupNames(userGroup))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns the Collection of all groups provided by each of the providers, ordered by provider-order.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return the Collection of all group names given the {@code startIndex} and {@code numResults}.
     */
    @Override
    public Collection<String> getGroupNames(int startIndex, int numResults)
    {
        final List<String> result = new ArrayList<>();
        int totalCount = 0;

        for ( final GroupProvider provider : getGroupProviders() )
        {
            final int providerStartIndex = Math.max( ( startIndex - totalCount ), 0 );
            totalCount += provider.getGroupCount();
            if ( startIndex >= totalCount )
            {
                continue;
            }
            final int providerResultMax = numResults - result.size();
            result.addAll( provider.getGroupNames( providerStartIndex, providerResultMax ) );
            if ( result.size() >= numResults )
            {
                break;
            }
        }
        return result;
    }

    /**
     * Returns the Collection of group names that an entity belongs to.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param user the (bare) JID of the entity.
     * @return the Collection of group names that the user belongs to.
     */
    @Override
    public Collection<String> getGroupNames(JID user)
    {
        return getGroupProviders().parallelStream()
            .map(groupProvider -> groupProvider.getGroupNames(user))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns whether <em>all</em> backing providers are read-only. When read-only, groups can not be created, deleted,
     * or modified. If at least one provider is not read-only, this method returns false.
     *
     * @return true when all backing providers are read-only, otherwise false.
     */
    @Override
    public boolean isReadOnly()
    {
        // If at least one provider is not readonly, neither is this proxy.
        return getGroupProviders().parallelStream()
            .allMatch(GroupProvider::isReadOnly);
    }

    @Override
    public Collection<String> search(String query)
    {
        return getGroupProviders().parallelStream()
            .filter(groupProvider -> isSearchSupported())
            .map(groupProvider -> groupProvider.search(query))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns the group names that match a search. The search is over group names and implicitly uses wildcard matching
     * (although the exact search semantics are left up to each provider implementation). For example, a search for "HR"
     * should match the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method to ensure that searching is
     * supported.
     *
     * This method throws an UnsupportedOperationException when none of the backing providers support search.
     *
     * @param query the search string for group names.
     * @return all groups that match the search.
     * @throws UnsupportedOperationException When none of the providers support search.
     */
    @Override
    public Collection<String> search(String query, int startIndex, int numResults)
    {
        if (!isSearchSupported()) {
            throw new UnsupportedOperationException("None of the backing providers support this operation.");
        }

        // TODO improve the performance and efficiency of this! Do not collect _all_ results before returning the page!
        final List<String> allResults = new ArrayList<>();
        for ( final GroupProvider provider : getGroupProviders() )
        {
            if ( provider.isSearchSupported()) {
                allResults.addAll(provider.search(query));
            }
        }
        return allResults.subList(startIndex, Math.min(allResults.size(), startIndex + numResults));
    }

    /**
     * Returns the names of groups that have a property matching the given key/value pair. This provides a simple
     * extensible search mechanism for providers with differing property sets and storage models.
     *
     * The semantics of the key/value matching (wildcard support, scoping, etc.) are unspecified by the interface and
     * may vary for each implementation.
     *
     * When a key/value combination ia provided that are not supported by a particular provider, this is ignored by that
     * provider (but can still be used by other providers).
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method to ensure that searching is
     * supported.
     *
     * @param key The name of a group property (e.g. "sharedRoster.showInRoster")
     * @param value The value to match for the given property
     * @return unmodifiable Collection of group names that match the given key/value pair.
     * @throws UnsupportedOperationException When none of the providers support search.
     */
    @Override
    public Collection<String> search(String key, String value)
    {
        if (!isSearchSupported()) {
            throw new UnsupportedOperationException("None of the backing providers support this operation.");
        }

        return getGroupProviders().parallelStream()
            .filter(groupProvider -> isSearchSupported())
            .map(groupProvider -> groupProvider.search(key, value))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Returns true if group searching is supported by at least one of the providers.
     *
     * @return true if searching is supported.
     */
    @Override
    public boolean isSearchSupported()
    {
        // If at least one provider is supports search, so does this proxy.
        return getGroupProviders().parallelStream()
            .anyMatch(GroupProvider::isSearchSupported);
    }

    /**
     * Loads a group from the first provider that contains the group.
     *
     * @param name the name of the group (cannot be null or empty).
     * @return The group (never null).
     * @throws GroupNotFoundException When none of the providers contains the group.
     */
    @Override
    public Group getGroup(String name) throws GroupNotFoundException
    {
        final GroupProvider groupProvider;
        try {
            groupProvider = getGroupProvider(name);
        } catch (RuntimeException e){
            throw new GroupNotFoundException("Unable to identify group provider for group name " + name, e);
        }
        return groupProvider.getGroup(name);
    }

    /**
     * Creates a group with the given name in the first provider that is not read-only.
     *
     * @param name name of the group.
     * @return the newly created group.
     * @throws GroupAlreadyExistsException if a group with the same name already exists.
     * @throws UnsupportedOperationException if the provider does not support the operation.
     * @throws GroupNameInvalidException if the provided new name is an unacceptable value.
     */
    @Override
    public Group createGroup(String name) throws GroupAlreadyExistsException, GroupNameInvalidException
    {
        return getGroupProvider(name).createGroup(name);
    }

    /**
     * Removes a group from all non-read-only providers.
     *
     * @param name the name of the group to delete.
     */
    @Override
    public void deleteGroup(String name) throws GroupNotFoundException
    {
        // all providers are read-only
        if (isReadOnly()) {
            throw new UnsupportedOperationException();
        }

        for (final GroupProvider provider : getGroupProviders()) {
            if (provider.isReadOnly()) {
                continue;
            }
            provider.deleteGroup(name);
        }
    }

    /**
     * Sets the name of a group to a new name in the provider that is used for this group.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws GroupAlreadyExistsException if a group with the same name already exists.
     * @throws UnsupportedOperationException if the provider does not support the operation.
     * @throws GroupNotFoundException if the provided old name does not refer to an existing group.
     * @throws GroupNameInvalidException if the provided new name is an unacceptable value.
     */
    @Override
    public void setName(String oldName, String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException
    {
        getGroupProvider(oldName).setName(oldName, newName);
    }

    /**
     * Updates the group's description in the provider that is used for this group.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws GroupNotFoundException if no existing group could be found to update.
     */
    @Override
    public void setDescription(String name, String description) throws GroupNotFoundException
    {
        getGroupProvider(name).setDescription(name, description);
    }

    /**
     * Adds an entity to a group (optional operation).
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group to add the member to
     * @param user the (bare) JID of the entity to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not support the operation.
     * @throws GroupNotFoundException if the provided group name does not refer to an existing group.
     */
    @Override
    public void addMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
    {
        getGroupProvider(groupName).addMember(groupName, user, administrator);
    }

    /**
     * Updates the privileges of an entity in a group.
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group where the change happened
     * @param user the (bare) JID of the entity with new privileges
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not support the operation.
     * @throws GroupNotFoundException if the provided group name does not refer to an existing group.
     */
    @Override
    public void updateMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
    {
        getGroupProvider(groupName).updateMember(groupName, user, administrator);
    }

    /**
     * Deletes an entity from a group (optional operation).
     *
     * Implementations should use the bare JID representation of the JID passed as an argument to this method.
     *
     * @param groupName the group name.
     * @param user the (bare) JID of the entity to delete.
     * @throws UnsupportedOperationException if the provider does not support the operation.
     */
    @Override
    public void deleteMember(String groupName, JID user)
    {
        getGroupProvider(groupName).deleteMember(groupName, user);
    }

    /**
     * Loads the group properties (if any) from the backend data store. If the properties can be changed, the provider
     * implementation must ensure that updates to the resulting {@link Map} are persisted to the backend data store.
     * Otherwise, if a mutator method is called, the implementation should throw an {@link UnsupportedOperationException}.
     *
     * If there are no corresponding properties for the given group, or if the provider does not support group
     * properties, this method should return an empty Map rather than null.
     *
     * @param group The target group
     * @return The properties for the given group
     */
    @Override
    public PersistableMap<String, String> loadProperties(Group group)
    {
        return getGroupProvider(group.getName()).loadProperties(group);
    }
}
