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

package org.jivesoftware.messenger.container;

/**
 * Interface for objects interested in receiving Jive events.
 *
 * @author Iain Shigeoka
 */
public interface EventListener {
    /**
     * This method will be called with event notifications. Throw
     * an UnknownEventException if you no longer want to receive
     * events of the type sent in the current notification.
     *
     * @param e The event causing the notification
     * @throws UnknownEventException Thrown if you no longer wish to receive events of this type
     */
    void notifyEvent(Event e) throws UnknownEventException;
}
