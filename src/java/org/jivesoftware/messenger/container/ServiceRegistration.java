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
 * Provides the information needed to manage a service lookup registration.
 *
 * @author Iain Shigeoka
 */
public interface ServiceRegistration {
    /**
     * Obtains the serviceID for the service (created during registration).
     *
     * @return The service ID for the service this registration represents
     */
    ServiceID getServiceID();

    /**
     * Deletes all existing lookup attributes for the service and adds the given
     * attributes.
     *
     * @param attributes The new lookup attributes for the service
     */
    void setAttributes(Entry[] attributes);

    /**
     * Cancels the lookup registration.
     */
    void cancel();
}
