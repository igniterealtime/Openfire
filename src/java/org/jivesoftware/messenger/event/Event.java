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

package org.jivesoftware.messenger.event;

import java.util.Map;
import java.util.Date;

/**
 * Base interface for events. Every event has a date the event was generated and
 * optional parameters.
 *
 * @author Matt Tucker
 */
public interface Event {

    /**
     * Returns the date this event was created.
     *
     * @return the date this event was created.
     */
    public Date getDate();

    /**
     * Returns a map of event parameters, which can contain extra information about
     * the event.
     *
     * @return map of event parameters.
     */
    public Map getParams();
}