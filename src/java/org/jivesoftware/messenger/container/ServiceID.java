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
import java.util.Random;

/**
 * A globally unique identifier of services. Service IDs should be generated
 * by the ServiceLookup (and found in the ServiceRegistration) and not by clients.
 *
 * @author Iain Shigeoka
 */
public class ServiceID implements Serializable {

    private static final Random RANDOM_GENERATOR = new Random();

    // mimic Jini UUID
    private long highLong;
    private long lowLong;
    private String asString = null;

    /**
     * Serializable id. Increment whenever class signature changes.
     */
    private static final long serialVersionUID = 1;

    /**
     * A weak implementation. Should move to a true unique ID soon.
     */
    public ServiceID() {
        highLong = RANDOM_GENERATOR.nextLong();
        lowLong = RANDOM_GENERATOR.nextLong();
    }

    /**
     * Create a copy of the given id
     *
     * @param id The object to copy
     */
    public ServiceID(ServiceID id) {
        highLong = id.highLong;
        lowLong = id.lowLong;
    }

    /**
     * <p>Conducts a comparison of service IDs only.</p>
     *
     * @param o The service ID to compare
     * @return True if they are equal
     */
    public boolean equals(Object o) {
        boolean eq = false;
        if (o instanceof ServiceID) {
            ServiceID id = (ServiceID)o;
            if (id.highLong == highLong && id.lowLong == lowLong) {
                eq = true;
            }
        }
        return eq;
    }

    /**
     * <p>Generate a pseudo hash code based on the number.</p>
     *
     * @return A semi-unique hashcode for the item
     */
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * <p>A string representation of the id.</p>
     *
     * @return The service id as a string
     */
    public String toString() {
        if (asString == null) {
            asString = Long.toHexString(highLong) + "-" + Long.toHexString(lowLong);
        }
        return asString;
    }
}
