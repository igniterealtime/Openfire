/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import java.util.Date;

/**
 * Represents information about a retired MUC room.
 */
public class MUCRoomRetiree {
    private final String name;
    private final String alternateJID;
    private final String reason;
    private final Date retiredAt;

    public MUCRoomRetiree(String name, String alternateJID, String reason, Date retiredAt) {
        this.name = name;
        this.alternateJID = alternateJID;
        this.reason = reason;
        this.retiredAt = retiredAt;
    }

    public String getName() {
        return name;
    }

    public String getAlternateJID() {
        return alternateJID;
    }

    public String getReason() {
        return reason;
    }

    public Date getRetiredAt() {
        return retiredAt;
    }
}
