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
package org.jivesoftware.openfire.group;

import java.util.Map;
import java.util.Set;

/**
 * This map specifies additional methods that understand groups among 
 * the entries in the map.
 * 
 * @author Tom Evans
 */

public interface GroupAwareMap<K, V> extends Map<K, V> {

    /**
     * Returns true if the map's keySet contains the given JID. If the JID
     * is not found explicitly, search the keySet for groups and look 
     * for the JID in each of the corresponding groups.
     * 
     * @param key The target, presumably a JID
     * @return True if the target is in the key list, or in any groups in the key list
     */
    boolean includesKey( Object key );

    /**
     * Returns true if the map contains a value referencing the given JID. If the JID
     * is not found explicitly, search the values for groups and look 
     * for the JID in each of the corresponding groups.
     * 
     * @param value The target, presumably a JID
     * @return True if the target is in the key list, or in any groups in the key list
     */
    boolean includesValue( Object value );
    
    /**
     * Returns the groups that are implied (resolvable) from the keys in the map.
     * 
     * @return A new Set containing the groups in the keySet
     */
    Set<Group> getGroupsFromKeys();
    
    /**
     * Returns the groups that are implied (resolvable) from the values in the map.
     * 
     * @return A new Set containing the groups among the mapped values
     */
    Set<Group> getGroupsFromValues();
    
}
