/**
 * $RCSfile$
 * $Revision: 24750 $
 * $Date: 2005-12-13 19:58:56 -0800 (Tue, 13 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.dispatcher;

import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.UserAlreadyExistsException;

/**
 * A common interface to implement when creating a Fastpath service plug-in.<p>
 *
 * Provide meta-information about a dispatcher that's useful in server behavior. Implementation
 * of this provider is required for .<p>
 *
 * Openfire will cache much of the information it obtains from calling this provider.
 * If you will be modifying the underlying data outside of Openfire, please consult
 * with Jive for information on maintaining a valid cache.
 *
 * @author Derek DeMoro
 */
public interface DispatcherInfoProvider {

    /**
     * Returns the DispatcherInfo of a queue.<p>
     *
     * If your implementation doesn't support dispatcher info, simply
     * return a DispatcherInfo object filled with default values.
     *
     * @param workgroup the workgroup where the queue belongs.
     * @param queueID the queueID of the queue the dispatcher belongs to.
     * @return the dispatcher's info.
     * @throws NotFoundException if a queue with the given ID couldn't be found.
     */
    DispatcherInfo getDispatcherInfo(Workgroup workgroup, long queueID) throws NotFoundException;

    /**
     * Sets the dispatcher's info (optional operation).
     *
     * @param queueID the queueID of the queue the dispatcher belongs to
     * @param info the dispatcher's new info.
     * @throws NotFoundException if a queue with the given ID couldn't be found.
     * @throws UnauthorizedException if this operation is not allowed for the caller's
     *      permissions.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation (this is an optional operation).
     */
    void updateDispatcherInfo(long queueID, DispatcherInfo info)
            throws NotFoundException, UnauthorizedException, UnsupportedOperationException;

    /**
     * Creates a dispatcher info associated with the given queue (optional operation).
     *
     * @param queueID the queueID of the queue the dispatcher belongs to.
     * @param info the dispatcher's new info.
     * @throws UserAlreadyExistsException if a queue with the given ID couldn't be found.
     * @throws UnauthorizedException if this operation is not allowed for the caller's
     *      permissions.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation (this is an optional operation).
     */
    void insertDispatcherInfo(long queueID, DispatcherInfo info)
            throws UserAlreadyExistsException, UnauthorizedException,
            UnsupportedOperationException;

    /**
     * Deletes the dispatcher info associated with the given queue.
     *
     * @param queueID the queueID of the queue the dispatcher belongs to.
     * @throws UnauthorizedException If this operation is not allowed for the caller's
     *      permissions.
     */
    void deleteDispatcherInfo(long queueID) throws UnauthorizedException;
}