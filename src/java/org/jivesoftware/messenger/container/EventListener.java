/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
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
