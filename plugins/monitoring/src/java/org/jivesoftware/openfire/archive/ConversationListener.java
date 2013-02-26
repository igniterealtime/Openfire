/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import java.util.Date;

/**
 * Listens for conversations being created, finished, and updated. Note that listeners
 * are notified using application threads so any long running processing tasks that result
 * from notifications should be scheduled for separate threads.
 *
 * @see ConversationManager#addConversationListener(ConversationListener)
 * @author Matt Tucker
 */
public interface ConversationListener {

    /**
     * A conversation was created.
     *
     * @param conversation the conversation.
     */
    public void conversationCreated(Conversation conversation);

    /**
     * A conversation was updated, which means that a new message was sent between
     * the participants.
     *
     * @param conversation the conversation.
     * @param date the date the conversation was updated.
     */
    public void conversationUpdated(Conversation conversation, Date date);

    /**
     * A conversation ended due to inactivity or because the maximum conversation time
     * was hit.
     *
     * @param conversation the conversation.
     */
    public void conversationEnded(Conversation conversation);

}