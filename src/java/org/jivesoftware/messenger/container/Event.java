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

import java.io.Serializable;
import java.util.EventObject;

/**
 * Extends the standard Java event object for sequence ordering and payload
 * delivery. Events are used to deliver data between services in a generic
 * way. Event payloads should be serializable for transportation to
 * services on other machines.
 * <p/>
 * The event ID identifies the event type. The combination
 * of event type and source object determines a unique event type.
 * All events of a type should identify an identical event within
 * the context of the source object.
 * </p><p>
 * The sequence number is provided as a hint for ordering events in the system.
 * Increasing sequence numbers indicate events occuring later in time.
 * e.g. If event x has a higher sequence number than event y and both events
 * share the same source object and event ID, then x occured after y.
 * Services that don't want to transmit sequence information should send
 * all events with the same sequence number.
 * </p><p>
 * Some services may have a stronger guarantee that sequence numbers are
 * sequential without any missing or repeated sequence numbers. This
 * guarantee can be used to detect missed or repeated events but is not
 * a requirement of all event generators.
 * </p>
 *
 * @author Iain Shigeoka
 */
public class Event extends EventObject {

    /**
     * the event id
     */
    private long id;
    /**
     * The sequence number for the event
     */
    private long sequenceNumber;
    /**
     * The payload for this event, may be null
     */
    private Serializable payload;

    /**
     * Create a Jive event.
     *
     * @param source        The source object that generated this event
     * @param eventID       The event identifier relative to the source object
     * @param seqNumber     The sequence number for this event
     * @param payloadObject The payload for this event
     *                      (may be null if not needed for event)
     */
    public Event(Object source,
                 long eventID,
                 long seqNumber,
                 Serializable payloadObject) {
        super(source);
        this.id = eventID;
        this.sequenceNumber = seqNumber;
        this.payload = payloadObject;
    }

    /**
     * Obtain the event id for this event.
     *
     * @return The event identifier
     */
    public long getID() {
        return id;
    }

    /**
     * Obtain the sequence number for this event.
     *
     * @return The sequence number
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Obtain the payload for this event.
     *
     * @return The payload for the event (may be null)
     */
    public Serializable getPayload() {
        return payload;
    }
}
