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
