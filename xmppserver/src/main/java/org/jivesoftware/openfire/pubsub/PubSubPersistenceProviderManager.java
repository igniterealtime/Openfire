/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the persistence provider used to manage pubsub data in persistent storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PubSubPersistenceProviderManager
{
    public static final SystemProperty<Class> PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.pubsub-persistence.className")
        .setBaseClass(PubSubPersistenceProvider.class)
        .setDynamic(false)
        .build();

    private static final Logger Log = LoggerFactory.getLogger( PubSubPersistenceProviderManager.class );

    private static PubSubPersistenceProviderManager instance;

    private PubSubPersistenceProvider provider;

    public synchronized static PubSubPersistenceProviderManager getInstance()
    {
        if ( instance == null ) {
            instance = new PubSubPersistenceProviderManager();
        }

        return instance;
    }

    private PubSubPersistenceProviderManager() {
        initProvider();
    }

    public PubSubPersistenceProvider getProvider() {
        return this.provider;
    }

    private void initProvider()
    {
        Class clazz = PROVIDER.getValue();
        if ( clazz == null ) {
            if ( ClusterManager.isClusteringEnabled() ) {
                Log.debug("Clustering is enabled. Falling back to non-cached provider");
                clazz = DefaultPubSubPersistenceProvider.class;
            } else {
                clazz = CachingPubsubPersistenceProvider.class;
            }
        }

        // Check if we need to reset the provider class
        if (provider == null || !clazz.equals(provider.getClass()) ) {
            if ( provider != null ) {
                provider.shutdown();
                provider = null;
            }
            try {
                Log.info("Loading PubSub persistence provider: {}.", clazz);
                provider = (PubSubPersistenceProvider) clazz.newInstance();
                provider.initialize();
            }
            catch (Exception e) {
                Log.error("Error loading PubSub persistence provider: {}. Using default provider instead.", clazz, e);
                provider = new DefaultPubSubPersistenceProvider();
                provider.initialize();
            }
        }
    }

    public void shutdown()
    {
        if ( provider != null )
        {
            provider.shutdown();
            provider = null;
        }
    }
}
