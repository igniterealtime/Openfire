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

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the persistence provider used to manage pubsub data in persistent storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PubSubPersistenceProviderManager
{
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
        String className = JiveGlobals.getProperty( "provider.pubsub-persistence.className", PubSubPersistenceManager.class.getName() );

        // Check if we need to reset the provider class
        if (provider == null || !className.equals(provider.getClass().getName())) {
            if ( provider != null ) {
                provider.shutdown();
                provider = null;
            }
            try {
                Log.info("Loading PubSub persistence provider: {}.", className);
                Class c = ClassUtils.forName( className );
                provider = (PubSubPersistenceProvider) c.newInstance();
                provider.initialize();
            }
            catch (Exception e) {
                Log.error("Error loading PubSub persistence provider: {}. Using default provider instead.", className, e);
                provider = new PubSubPersistenceManager();
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
