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

import java.io.Serializable;

/**
 * A class that groups all the information needed to manage an event
 * listener registration.
 *
 * @author Iain Shigeoka
 */
public abstract class EventRegistration implements Serializable {
    private long eventID;
    private long seqNumber;
    private Object sourceObject;
    /**
     * Serializable id. Increment whenever class signature changes.
     */
    private static final long serialVersionUID = 1;

    /**
     * Constructor
     *
     * @param id             The event ID for events associated with this registration
     * @param source         The source of the registration (the lookup)
     * @param sequenceNumber The current sequence number when registering
     */
    public EventRegistration(long id, Object source, long sequenceNumber) {
        eventID = id;
        this.sourceObject = source;
        this.seqNumber = sequenceNumber;
    }

    /**
     * Obtain the event ID that will be used with all events
     * generated using this registration.
     *
     * @return The event id for all events from this registration
     */
    public long getID() {
        return eventID;
    }

    /**
     * The sequence number current at the time of registration. Useful
     * for tracking future event notifications relative to the registration.
     *
     * @return The sequence number current at time of registration
     */
    public long getSequenceNumber() {
        return seqNumber;
    }

    /**
     * The source object that will be used for all events from this registration.
     *
     * @return The source object for events coming from this registration
     */
    public Object getSource() {
        return sourceObject;
    }

    /**
     * Cancels the event registration.
     */
    public abstract void cancel();
}
