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
 * <p>A interface for listening to service tracking events.</p>
 * <p>Implementations may choose to maintain a list of services
 * obtained through the interface, or just one (first or last).
 * It is critical that you watch for the removal of services as
 * well as additions since services may go offline during hot
 * deployments.</p>
 *
 * @author Iain Shigeoka
 */
public interface ServiceTrackerListener {

    /**
     * <p>A service has been added to the lookup matching a tracker class.</p>
     * <p>The listener can check the instance of the service and use it
     * appropriately. Instanceof is a bit expensive but since this only occurs
     * when a service is added (startup, reconfiguration, shutdown) I don't
     * think it should be an issue.</p>
     *
     * @param service The service that has just been added to the lookup.
     */
    void addService(Object service);

    /**
     * <p>A service has been removed from the lookup matching a tracker class.</p>
     * <p>The listener can check the instance of the service and use it
     * appropriately. It is highly recommended that references to the service
     * be removed (set to null) so that the garbage collector can remove them.
     * Instanceof is a bit expensive but since this only occurs
     * when a service is removed (startup, reconfiguration, shutdown) I don't
     * think it should be an issue.</p>
     *
     * @param service The service that has just been added to the lookup.
     */
    void removeService(Object service);
}
