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
 * A long-lived association or connection with a room. Affiliation is distinct from role. An affiliation lasts
 * across a user's visits to a room.
 */
public enum Affiliation
{

    /**
     * Owner of the room.
     */
    owner(10),

    /**
     * Administrator of the room.
     */
    admin(20),

    /**
     * A user who is on the "whitelist" for a members-only room or who is registered
     * with an open room.
     */
    member(30),

    /**
     * A user who has been banned from a room.
     */
    outcast(40),

    /**
     * A user who doesn't have an affiliation. This kind of users can register with members-only
     * rooms and may enter an open room.
     */
    none(50);

    private final int value;

    Affiliation(int value)
    {
        this.value = value;
    }

    /**
     * Returns the value for the affiliation.
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
    public static Affiliation valueOf(int value)
    {
        switch (value) {
            case 10:
                return owner;
            case 20:
                return admin;
            case 30:
                return member;
            case 40:
                return outcast;
            default:
                return none;
        }
    }
}
