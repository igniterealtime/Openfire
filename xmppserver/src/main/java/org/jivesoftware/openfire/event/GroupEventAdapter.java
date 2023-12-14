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
package org.jivesoftware.openfire.event;

import java.util.Map;

import org.jivesoftware.openfire.group.Group;

/**
 * An abstract adapter class for receiving group events. 
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
public class GroupEventAdapter implements GroupEventListener {

    @Override
    public void groupCreated(Group group, Map params) {
    }

    @Override
    public void groupDeleting(Group group, Map params) {
    }

    @Override
    public void groupModified(Group group, Map params) {
    }

    @Override
    public void memberAdded(Group group, Map params) {
    }

    @Override
    public void memberRemoved(Group group, Map params) {
    }

    @Override
    public void adminAdded(Group group, Map params) {
    }

    @Override
    public void adminRemoved(Group group, Map params) {
    }

}
