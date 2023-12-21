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

import java.util.List;
import java.util.Set;

/**
 * This list specifies additional methods that understand groups among 
 * the items in the list.
 * 
 * @author Tom Evans
 */
public interface GroupAwareList<T> extends List<T> {

    /**
     * Returns true if the list contains the given JID. If the JID
     * is not found explicitly, search the list for groups and look 
     * for the JID in each of the corresponding groups.
     * 
     * @param o The target, presumably a JID
     * @return True if the target is in the list, or in any groups in the list
     */
    boolean includes(Object o);

    /**
     * Returns the groups that are implied (resolvable) from the items in the list.
     * 
     * @return A Set containing the groups in the list
     */
    Set<Group> getGroups();
}
