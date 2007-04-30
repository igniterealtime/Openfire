/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.xmpp.packet.Message;

/**
 * Interface to listen for offline message events. Use the
 * {@link OfflineMessageStrategy#addListener(OfflineMessageListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface OfflineMessageListener {

    /**
     * Notification message indicating that a message was not stored offline but bounced
     * back to the sender.
     *
     * @param message the message that was bounced.
     */
    void messageBounced(Message message);

    /**
     * Notification message indicating that a message was stored offline since the target entity
     * was not online at the moment.
     *
     * @param message the message that was stored offline.
     */
    void messageStored(Message message);
}
