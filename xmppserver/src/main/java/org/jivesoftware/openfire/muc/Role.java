/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.muc;

/**
 * A temporary position or privilege level within a room, distinct from a user's long-lived affiliation with the
 * room. A role lasts only for the duration of an occupant's visit to a room.
 */
public enum Role
{

    /**
     * Runs moderated discussions. Is allowed to kick users, grant and revoke voice, etc.
     */
    moderator(0),

    /**
     * A normal occupant of the room. An occupant who does not have administrative privileges; in
     * a moderated room, a participant is further defined as having voice
     */
    participant(1),

    /**
     * An occupant who does not have voice  (can't speak in the room)
     */
    visitor(2),

    /**
     * An occupant who does not permission to stay in the room (was banned)
     */
    none(3);

    private final int value;

    Role(int value)
    {
        this.value = value;
    }

    /**
     * Returns the value for the role.
     *
     * @return the value.
     */
    public int getValue()
    {
        return value;
    }

    /**
     * Returns the affiliation associated with the specified value.
     *
     * @param value the value.
     * @return the associated affiliation.
     */
    public static Role valueOf(int value)
    {
        switch (value) {
            case 0:
                return moderator;
            case 1:
                return participant;
            case 2:
                return visitor;
            default:
                return none;
        }
    }
}
