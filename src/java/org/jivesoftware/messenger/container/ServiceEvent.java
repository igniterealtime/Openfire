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

/**
 * Special events sent by service lookup to registered listeners to notifyEvent of
 * service registration changes. The extra information covers the service
 * that caused the change, and the transition that occured. Sequence numbers
 * are guaranteed to be sequential and increasing. Missing sequence numbers
 * indicate an event may have been missed (missing numbers may also indicate
 * that a remote server has crashed and recovered).
 *
 * @author Iain Shigeoka
 */
public class ServiceEvent extends Event {

    private int transition;
    private ServiceID serviceID;
    private ServiceItem item;

    /**
     * Create a service event.
     *
     * @param source the event source (service lookup).
     * @param eventID the event type.
     * @param sequenceNumber the sequence number.
     * @param payload the payload for the event.
     * @param id the id of the server that caused the event.
     * @param serviceItem the state of the service that caused the event.
     * @param eventTransition the transition that triggered the event.
     */
    public ServiceEvent(Object source, long eventID, long sequenceNumber,
        Serializable payload, ServiceItem serviceItem, ServiceID id, int eventTransition)
    {
        super(source, eventID, sequenceNumber, payload);
        this.transition = eventTransition;
        this.serviceID = id;
        this.item = serviceItem;
    }

    /**
     * Returns the transition that caused the event.
     *
     * @return The transition that caused the event
     */
    public int getTransition() {
        return transition;
    }

    /**
     * Returns the service id of the service that caused the event.
     *
     * @return The service id of the service that caused the event.
     */
    public ServiceID getServiceID() {
        return serviceID;
    }

    /**
     * Returns the state of the item that caused the event.
     *
     * @return The state of the item that caused the event.
     */
    public ServiceItem getServiceItem() {
        return item;
    }
}
