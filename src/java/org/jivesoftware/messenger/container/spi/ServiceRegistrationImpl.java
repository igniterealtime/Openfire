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
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.Entry;
import org.jivesoftware.messenger.container.ServiceID;
import org.jivesoftware.messenger.container.ServiceRegistration;

/**
 * A simple service registration implementation.
 *
 * @author Iain Shigeoka
 */
public class ServiceRegistrationImpl implements ServiceRegistration {

    /**
     * The lookup that owns this registration.
     */
    private ServiceLookupImpl lookup;
    /**
     * The ID of the service regististered.
     */
    private ServiceID id;

    /**
     * Create a service registration for the given lookup with the given ID.
     *
     * @param serviceLookup the service lookup that owns this registration.
     * @param ID            the id for the registration
     */
    ServiceRegistrationImpl(ServiceLookupImpl serviceLookup, ServiceID ID) {
        this.lookup = serviceLookup;
        this.id = ID;
    }

    public ServiceID getServiceID() {
        return id;
    }

    public void setAttributes(Entry[] attributes) {
        lookup.setAttributes(id, attributes);
    }

    public void cancel() {
        lookup.remove(id);
    }
}
