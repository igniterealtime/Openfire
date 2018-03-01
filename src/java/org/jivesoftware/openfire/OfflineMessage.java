/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire;

import org.dom4j.Element;
import org.xmpp.packet.Message;

import java.util.Date;

/**
 * Subclass of Message that keeps the date when the offline message was stored in the database.
 * The creation date and the user may be used as a unique identifier of the offline message.
 *
 * @author Gaston Dombiak
 */
public class OfflineMessage extends Message {

    private Date creationDate;

    public OfflineMessage(Date creationDate, Element element) {
        super(element, true);
        this.creationDate = creationDate;
    }

    /**
     * Returns the date when the offline message was stored in the database.
     *
     * @return the date the offline message was stored.
     */
    public Date getCreationDate() {
        return creationDate;
    }
}
