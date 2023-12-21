/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import java.time.Duration;

/**
 * Contains constant values representing various objects in Jive.
 */
public class JiveConstants {

    public static final int SYSTEM = 17;
    public static final int ROSTER = 18;
    public static final int OFFLINE = 19;
    public static final int MUC_ROOM = 23;
    public static final int SECURITY_AUDIT = 25;
    public static final int MUC_SERVICE = 26;
    public static final int MUC_MESSAGE_ID = 27;

    @Deprecated // Use Duration.ofSeconds(1).toMillis() instead. Remove in Openfire 4.9 or later.
    public static final long SECOND = Duration.ofSeconds(1).toMillis();

    @Deprecated // Use Duration.ofMinutes(1).toMillis() instead. Remove in Openfire 4.9 or later.
    public static final long MINUTE = Duration.ofMinutes(1).toMillis();

    @Deprecated // Use Duration.ofHours(1).toMillis() instead. Remove in Openfire 4.9 or later.
    public static final long HOUR = Duration.ofHours(1).toMillis();

    @Deprecated // Use Duration.ofDays(1).toMillis() instead. Remove in Openfire 4.9 or later.
    public static final long DAY = Duration.ofDays(1).toMillis();

    @Deprecated // Use Duration.ofDays(7).toMillis() instead. Remove in Openfire 4.9 or later.
    public static final long WEEK = Duration.ofDays(7).toMillis();
}
