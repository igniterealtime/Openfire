/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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