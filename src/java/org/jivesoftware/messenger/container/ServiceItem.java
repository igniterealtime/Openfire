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
 * Represents a service registered in a lookup. See ServiceTemplate
 * and ServiceLookup for how it is used.
 *
 * @author Iain Shigeoka
 */
public class ServiceItem implements Serializable {
    /**
     * The atributes associated with the item or null for wildcard
     */
    public Entry[] attributes = null;
    /**
     * The service associated with the item or null for wildcard
     */
    public Object service = null;
    /**
     * The service ID associated with the item or null for wildcard
     */
    public ServiceID serviceID = null;
    /**
     * Serializable id. Increment whenever class signature changes.
     */
    private static final long serialVersionUID = 1;

    /**
     * <p>Create an empty service item.</p>
     */
    public ServiceItem() {

    }

    /**
     * <p>Create a serviceObject item.</p>
     *
     * @param id            The serviceObject ID for the serviceObject or null for wildcard
     * @param serviceObject The serviceObject object or null to set it later
     * @param atts          The atts for the serviceObject or null for wildcard
     */
    public ServiceItem(ServiceID id, Object serviceObject, Entry[] atts) {
        this.serviceID = id;
        this.service = serviceObject;
        this.attributes = atts;
    }
}
