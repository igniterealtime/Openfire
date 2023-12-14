/*
 * Copyright (C) 2022-2023 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Describes with which users a group is shared as a group on their roster.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public enum SharedGroupVisibility {

    /**
     * Users of this group are not shared with anyone.
     */
    nobody("nobody"),

    /**
     * Users of this group are shared with all other users in Openfire.
     */
    everybody ("everybody"),

    /**
     * Defines a collection of group names (one or more). Users in those groups will be able to see the users in the
     * group for which this visibility is defined.
     *
     * Note that this value is used for both the 'Users of the same group' and 'the following groups' UI setting.
     */
    usersOfGroups ("onlyGroup");

    /**
     * The value of the 'sharedRoster.showInRoster' property that represents the value.
     */
    private final String dbValue;

    SharedGroupVisibility(@Nonnull final String dbValue) {
        this.dbValue = dbValue;
    }

    @Nonnull
    public String getDbValue() {
        return dbValue;
    }

    @Nonnull
    public static SharedGroupVisibility fromDatabaseValue(@Nullable final String dbValue) {
        if (dbValue == null) {
            return nobody;
        }

        for (SharedGroupVisibility value : SharedGroupVisibility.values()) {
            if (value.dbValue.equals(dbValue)) {
                return value;
            }
        }

        throw new IllegalArgumentException(
            "No enum constant for SharedGroupVisibility with a dbValue that equals " + dbValue);
    }
}
