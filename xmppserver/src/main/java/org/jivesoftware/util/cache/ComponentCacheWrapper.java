/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.util.cache;


import java.io.Serializable;

/**
 * This specialized wrapper is used for the Components cache, which
 * should not be purged.
 * 
 * See <a href="https://igniterealtime.atlassian.net/browse/OF-114">OF-114</a> for more info.
 *
 */
public class ComponentCacheWrapper<K extends Serializable, V extends Serializable> extends CacheWrapper<K, V> {

    public ComponentCacheWrapper(Cache<K, V> cache) {
        super(cache);
    }

    @Override
    public void clear() {
        // no-op; we don't want to clear the components cache
    }
}
