/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.muc;

import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Presence;

/**
 * Interface for any object that can accept chat messages and presence
 * for delivery.
 *
 * @author Gaston Dombiak
 */
public interface ChatDeliverer {
    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     */
    void send(Message packet);

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     */
    void send(Presence packet);

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     */
    void send(IQ packet);
}
