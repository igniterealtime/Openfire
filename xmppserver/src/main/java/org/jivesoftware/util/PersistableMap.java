/*
 * Copyright (C) 2017-2018 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util;

import java.util.HashMap;
import java.util.Map;

/**
 * This acts as a tag interface. It has no functionality, but it serves to make more clear the
 * intention to pass around a special type of map.
 *
 * @param <K> key. see {@link Map}
 * @param <V> value. see {@link Map}
 */
public abstract class PersistableMap<K, V> extends HashMap<K, V> {
    private static final long serialVersionUID = 1L;

    /**
     * Custom method to put properties into the map, optionally without
     * triggering persistence. This is used when the map is being
     * initially loaded from the database.
     *
     * @param key The property name
     * @param value The property value
     * @param persist True if the changes should be persisted to the database
     * @return The original value or null if the property did not exist
     */
    public abstract V put(K key, V value, boolean persist);
} 
