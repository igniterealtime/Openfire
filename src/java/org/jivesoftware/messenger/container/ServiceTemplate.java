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
 * <p>A template object used (query by example) in service lookups.
 * Nulls are used to indicate wildcards.</p>
 * <p/>
 * Matches occur when the service item (i) and a service template (t):
 * <ul>
 * <li>i.serviceID.equals(t.serviceID) or t.serviceID == null</li>
 * <li>AND</li>
 * <li>for each Entry in t.attributes[n] there is a matching i.attributes[m]</li>
 * <li>AND</li>
 * <li>i.service is an instanceof all t.serviceTypes[n]</li>
 * </ul>
 * Entries match if the template Entry:
 * <ul>
 * <li>Is an instanceof the item Entry</li>
 * <li>each field in the template entry is equal to the item, or is null</li>
 * </ul>
 * <p>The template attributes and/or serviceTypes arrays
 * may be null to indicate a wildcard match.</p>
 *
 * @author Iain Shigeoka
 */
public class ServiceTemplate implements Serializable {

    /**
     * <p>The attributes to require for a match or null for wildcard.</p>
     */
    public Entry[] attributes = null;
    /**
     * <p>The class type the service must implement
     * for a match or null for wildcard.</p>
     */
    public Class[] types = null;
    /**
     * <p>The ServiceID to require for a match or null for wildcard.</p>
     */
    public ServiceID serviceID = null;
    /**
     * Serialization id. Increment whenever the class signature changes.
     */
    private static final long serialVersionUID = 1;

    /**
     * <p>Create an empty service template.</p>
     */
    public ServiceTemplate() {

    }

    /**
     * <p>Create a service template with given properties.</p>
     *
     * @param id                the service ID to search for or null for wildcard
     * @param serviceAttributes The serviceAttributes to require
     *                          for a match or null for wildcard
     * @param serviceTypes      The classes to require for a match or null for a wildcard
     */
    public ServiceTemplate(ServiceID id,
                           Entry[] serviceAttributes,
                           Class[] serviceTypes) {
        serviceID = id;
        this.attributes = serviceAttributes;
        this.types = serviceTypes;
    }
}
