/*
 * Copyright (C) 2021 Ignite Realtime Community. All rights reserved.
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
package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.openfire.muc.MUCUser;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Each instance of this class takes responsibility of maintaining the in-memory representation of MUCUsers for exactly
 * one instance of {@link org.jivesoftware.openfire.muc.MultiUserChatService}, which is expected to be the sole invoking
 * entity. This instance that is provided as an argument to the constructor. This class makes extensive use of the
 * 'package' access modifier to reflect this.
 *
 * It is the responsibility of invoking codes that changes applied to instances managed by this class are made available
 * to other users (eg: cluster nodes). To achieve this, the {@link #sync(MUCUser)} method must be used. Changes to an
 * instance that are not synced will not be reflected in subsequent instances returned by the various getters in this
 * class (behavior can differ based on the deployment model of Openfire: clustered environments are more susceptible to
 * data loss than a single-server Openfire instance.
 *
 * To control (cluster-wide) access to instances, a MUCUser-based Lock instance can be obtained through {@link #getLock(JID)}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LocalMUCUserManager
{
    private static final Logger Log = LoggerFactory.getLogger(LocalMUCUserManager.class);

    /**
     * Name of the MUC service that this instance is operating for.
     */
    private final String serviceName;

    /**
     * Chat users for this service.
     *
     * The JID that is the key for this cache should be the 'real' JID of the user (as opposed to a role-based JID like
     * room-at-service-slash-nickname).
     */
    private final Cache<JID, MUCUser> USER_CACHE;

    /**
     * A cluster-local copy of user, used to (re)populating #USER_CACHE upon cluster join or leave.
     */
    private final Map<JID, MUCUser> localUsers = new HashMap<>();

    /**
     * Creates a new instance, specific for the provided MUC service.
     *
     * @param service The service for which the new instance will be operating.
     */
    LocalMUCUserManager(@Nonnull final MultiUserChatService service)
    {
        this.serviceName = service.getServiceName();
        Log.debug("Instantiating for service '{}'", serviceName);
        USER_CACHE = CacheFactory.createCache("MUC Service '" + serviceName + "' Users");
        USER_CACHE.setMaxLifetime(-1);
        USER_CACHE.setMaxCacheSize(-1L);
    }

    /**
     * Returns the number of chat users that are currently being maintained by this manager..
     *
     * @return a chat user
     */
    int size()
    {
        final int result = USER_CACHE.size();
        Log.trace("User count for service '{}': {}", serviceName, result);
        return result;
    }

    /**
     * Generates a mutex object that controls cluster-wide access to a MUCUser instance that represents the user in this
     * service identified by the provided name.
     *
     * The JID that is to be provided as the argument to this method should be the 'real' JID of the user (as opposed to
     * a role-based JID like room-at-service-slash-nickname).
     *
     * The lock, once returned, is not acquired/set.
     *
     * @param userAddress JID of the user for which to return a lock.
     * @return The lock (which has not been set yet).
     */
    @Nonnull
    Lock getLock(@Nonnull final JID userAddress)
    {
        Log.trace("Obtaining lock for user '{}' of service '{}'", userAddress, serviceName);
        return USER_CACHE.getLock(userAddress);
    }

    /**
     * Adds a user instance to this manager.
     *
     * @param user The user to be added.
     */
    void add(@Nonnull final MUCUser user)
    {
        final Lock lock = USER_CACHE.getLock(user.getAddress());
        lock.lock();
        try {
            Log.trace("Adding user '{}' of service '{}'", user.getAddress(), serviceName);
            USER_CACHE.put(user.getAddress(), user);
            localUsers.put(user.getAddress(), user);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Makes available the current state of the provided MUCUser instance to all nodes in the Openfire cluster (if the
     * local server is part of such a cluster). This method should be used whenever a MUCUser instance has been changed.
     *
     * @param user The user for which to persist state changes across the Openfire cluster.
     */
    void sync(@Nonnull final MUCUser user)
    {
        final Lock lock = USER_CACHE.getLock(user.getAddress());
        lock.lock();
        try {
            Log.trace("Syncing user '{}' of service '{}'. In rooms: {}", user.getAddress(), serviceName, String.join(",", user.getRoomNames()));
            USER_CACHE.put(user.getAddress(), user);
            localUsers.put(user.getAddress(), user);
        } finally {
            lock.unlock();
        }
    }

    // TODO As modifications to users won't be persisted in the cache without the user having being explicitly put back in the cache,
    //      this method probably needs work. Documentation should be added and/or this should return an Unmodifiable collection (although
    //      that still does not rule out modifications to individual collection items. Can we replace it completely with a 'getUserAddresses()'
    //      method, which would then force usage to acquire a lock before operating on a user.
    Collection<MUCUser> getAll()
    {
        return USER_CACHE.values();
    }

    /**
     * Retrieve a specific user, if one is currently managed by this instance.
     *
     * Note that when obtaining an user instance using this method, the caller should take responsibility to make sure
     * that any changes to the instance will become visible to other cluster nodes (which is done by invoking
     * {@link #sync(MUCUser)}.  Where appropriate, the caller should apply a mutex (as returned by {@link #getLock(JID)})
     * to control concurrent access to chat room instances.
     *
     * @param userAddress The (real) address of the room to retrieve.
     * @return The user
     */
    @Nullable
    MUCUser get(@Nonnull final JID userAddress)
    {
        return USER_CACHE.get(userAddress);
    }

    /**
     * Removes an user instance from this manager.
     *
     * @param userAddress The (real) address of the user to be removed.
     */
    @Nullable
    MUCUser remove(@Nonnull final JID userAddress)
    {
        final Lock lock = USER_CACHE.getLock(userAddress);
        lock.lock();
        try {
            Log.trace("Removing user '{}' of service '{}'", userAddress, serviceName);
            final MUCUser user = USER_CACHE.remove(userAddress);
            localUsers.remove(userAddress);
            return user;
        } finally {
            lock.unlock();
        }
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#joinedCluster()} or leaving
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#leftCluster()} a cluster.
     */
    void restoreCacheContent()
    {
        Log.trace( "Restoring cache content for cache '{}' by adding all MUC Users that are known to the local node.", USER_CACHE.getName() );

        for (Map.Entry<JID, MUCUser> localUserEntry : localUsers.entrySet()) {
            final Lock lock = USER_CACHE.getLock(localUserEntry.getKey());
            lock.lock();
            try {
                final MUCUser localUser = localUserEntry.getValue();
                if (!USER_CACHE.containsKey(localUserEntry.getKey())) {
                    USER_CACHE.put(localUserEntry.getKey(), localUser);
                } else {
                    final MUCUser userInCluster = USER_CACHE.get(localUserEntry.getKey());
                    if (!userInCluster.equals(localUser)) {
                        // TODO: unsure if #equals() is enough to verify equality here.
                        Log.warn("Joined an Openfire cluster on which a MUC user exists that clashes with a MUC users that exists locally. MUC user name: '{}' on service '{}'", localUserEntry.getKey(), serviceName);
                        // TODO: handle collision. Two nodes have different users using the same name.
                        // Current handling is to not change the user in the local storage - and ignore the clustered cache entry.
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public Cache<JID, MUCUser> getUSER_CACHE() {
        return USER_CACHE;
    }

    public Map<JID, MUCUser> getLocalUsers() {
        return localUsers;
    }
}
