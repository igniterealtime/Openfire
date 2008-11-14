/**
 * $RCSfile$
 * $Revision: 32923 $
 * $Date: 2006-08-04 14:53:43 -0700 (Fri, 04 Aug 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.dispatcher;

import org.jivesoftware.xmpp.workgroup.UnauthorizedException;

/**
 * Information about a dispatcher.<p>
 *
 * Dispatcher information is not expected to change frequently, and when it does, that most/all fields
 * will be updated.
 *
 * @author Derek DeMoro
 */
public interface DispatcherInfo {

    /**
     * Returns the long ID of the queue that owns the disptacher.
     *
     * @return The ID of the queue that owns the dispatcher
     */
    long getId();

    /**
     * Returns the dispatcher's name. The dispatcher's name does not have to be to be unique in
     * the system.
     *
     * @return the name of the dispatcher.
     */
    String getName();

    /**
     * Sets the dispatcher's name. The dispatcher's name does not have to be to be unique in
     * the system.
     *
     * @param name new name for the dispatcher.
     * @throws org.jivesoftware.xmpp.workgroup.UnauthorizedException if does not have permission to make the change.
     */
    void setName(String name) throws UnauthorizedException;

    /**
     * Returns the dispatcher's name. The dispatcher's name does not have to be to be unique in
     * the system. Some dispatchers may opt to not let others see their name for privacy reasons.
     * In that case, the dispatcher can set nameVisible to false. In that case, a call to this
     * method will return null.
     *
     * @return the name of the dispatcher.
     */
    String getDescription();

    /**
     * Sets the dispatcher's description. The dispatcher's description does not have to be to
     * be unique in the system.
     *
     * @param description new description for the dispatcher.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    void setDescription(String description) throws UnauthorizedException;

    /**
     * Sets the amount of time in milliseconds a request waits in the queue before being rejected.
     *
     * @param timeout The amount of time in milliseconds before requests are timed out
     */
    void setRequestTimeout(long timeout);

    /**
     * Obtains the amount of time in milliseconds a request waits in the queue before being
     * rejected.
     *
     * @return The amount of time in milliseconds before requests are timed out
     */
    long getRequestTimeout();

    /**
     * Sets the amount of time in milliseconds an offer is made before being revoked.
     *
     * @param timeout The amount of time in milliseconds before requests are timed out
     */
    void setOfferTimeout(long timeout);

    /**
     * Obtains the amount of time in milliseconds an offer is made before being revoked.
     *
     * @return The amount of time in milliseconds before requests are timed out
     */
    long getOfferTimeout();

}