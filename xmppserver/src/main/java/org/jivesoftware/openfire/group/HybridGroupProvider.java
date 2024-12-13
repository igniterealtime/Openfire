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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Delegate GroupProvider operations among up to three configurable provider implementation classes.
 *
 * This class related to, but is distinct from {@link MappedGroupProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. The Mapped variant, however, maps each group to exactly
 * one provider.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class HybridGroupProvider extends GroupMultiProvider
{
    private static final Logger Log = LoggerFactory.getLogger(HybridGroupProvider.class);

    public static final SystemProperty<Class> PRIMARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridGroupProvider.primaryProvider.className")
        .setBaseClass(GroupProvider.class)
        .setDynamic(false)
        .build();

    public static final SystemProperty<String> PRIMARY_PROVIDER_CONFIG = SystemProperty.Builder.ofType(String.class)
        .setKey("hybridGroupProvider.primaryProvider.config")
        .setDynamic(false)
        .build();

    public static final SystemProperty<Class> SECONDARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridGroupProvider.secondaryProvider.className")
        .setBaseClass(GroupProvider.class)
        .setDynamic(false)
        .build();

    public static final SystemProperty<String> SECONDARY_PROVIDER_CONFIG = SystemProperty.Builder.ofType(String.class)
        .setKey("hybridGroupProvider.secondaryProvider.config")
        .setDynamic(false)
        .build();

    public static final SystemProperty<Class> TERTIARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridGroupProvider.tertiaryProvider.className")
        .setBaseClass(GroupProvider.class)
        .setDynamic(false)
        .build();

    public static final SystemProperty<String> TERTIARY_PROVIDER_CONFIG = SystemProperty.Builder.ofType(String.class)
        .setKey("hybridGroupProvider.tertiaryProvider.config")
        .setDynamic(false)
        .build();

    private final List<GroupProvider> groupProviders = new ArrayList<>();

    public HybridGroupProvider()
    {
        // Migrate group provider properties
        JiveGlobals.migrateProperty(PRIMARY_PROVIDER.getKey());
        JiveGlobals.migrateProperty(SECONDARY_PROVIDER.getKey());
        JiveGlobals.migrateProperty(TERTIARY_PROVIDER.getKey());

        // Load primary, secondary, and tertiary group providers.
        final GroupProvider primary = instantiate(PRIMARY_PROVIDER, PRIMARY_PROVIDER_CONFIG);
        if (primary != null) {
            groupProviders.add(primary);
        }
        final GroupProvider secondary = instantiate(SECONDARY_PROVIDER, SECONDARY_PROVIDER_CONFIG);
        if (secondary != null) {
            groupProviders.add(secondary);
        }
        final GroupProvider tertiary = instantiate(TERTIARY_PROVIDER, TERTIARY_PROVIDER_CONFIG);
        if (tertiary != null) {
            groupProviders.add(tertiary);
        }

        // Verify that there's at least one provider available.
        if ( groupProviders.isEmpty() )
        {
            Log.error( "At least one GroupProvider must be specified via openfire.xml or the system properties!" );
        }
    }

    /**
     * Returns all GroupProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    @Override
    Collection<GroupProvider> getGroupProviders()
    {
        return groupProviders;
    }

    /**
     * Returns the first provider that contains the group, or the first provider that is not read-only when the group
     * does not exist in any provider.
     *
     * @param groupName the name of the group (cannot be null or empty).
     * @return The group provider (never null)
     * @throws UnsupportedOperationException When the group is not found and no provider can be used to create the group.
     */
    @Override
    GroupProvider getGroupProvider(String groupName)
    {
        GroupProvider nonReadOnly = null;
        for (final GroupProvider provider : getGroupProviders()) {
            try {
                provider.getGroup(groupName);
                return provider;
            } catch (GroupNotFoundException ex) {
                Log.debug( "Group {} not found in GroupProvider {}", groupName, provider.getClass().getName() );

                if (nonReadOnly == null && !provider.isReadOnly()) {
                    nonReadOnly = provider;
                }
            }
        }

        // Group does not exist. Return a provider suitable for creating groups.
        if (nonReadOnly == null) {
            throw new UnsupportedOperationException();
        }

        return nonReadOnly;
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
        // Override the implementation in the superclass to prevent obtaining the griyo twice.
        for (final GroupProvider provider : groupProviders) {
            try {
                return provider.getGroup(name);
            } catch (GroupNotFoundException ex) {
                Log.debug( "Group {} not found in GroupProvider {}", name, provider.getClass().getName() );
            }
        }
        // If we get this far, no provider was able to load the group
        throw new GroupNotFoundException();
    }
}
