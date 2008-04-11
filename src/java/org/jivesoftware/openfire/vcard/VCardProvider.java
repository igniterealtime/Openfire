/**
 * $RCSfile$
 * $Revision: 1651 $
 * $Date: 2005-07-20 00:20:39 -0300 (Wed, 20 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;

/**
 * Provider interface for users vcards.
 *
 * @author Gaston Dombiak
 */
public interface VCardProvider {

    /**
     * Loads the specified user vcard by username. Returns <tt>null</tt> if no
     * vCard was found for the specified username.
     *
     * @param username the username
     * @return the vCard as an DOM element or <tt>null</tt> if none was found.
     */
    Element loadVCard(String username);

    /**
     * Creates and saves the new user vcard. This method should throw an
     * UnsupportedOperationException if this operation is not supported by
     * the backend vcard store.
     *
     * The method is expected to return the vCard after it has had a chance
     * to make any modifications to it that it needed to.  In many cases, this
     * may be a simple return of the passed in vCard.  This change was made in 3.4.4.
     *
     * @param username the username.
     * @param vCardElement the vCard to save.
     * @return vCard as it is after the provider has a chance to adjust it.
     * @throws AlreadyExistsException if the user already has a vCard.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     */
    Element createVCard(String username, Element vCardElement) throws AlreadyExistsException;

    /**
     * Updates the user vcard in the backend store. This method should throw an
     * UnsupportedOperationException if this operation is not supported by
     * the backend vcard store.
     *
     * The method is expected to return the vCard after it has had a chance
     * to make any modifications to it that it needed to.  In many cases, this
     * may be a simple return of the passed in vCard.  This change was made in 3.4.4.
     *
     * @param username the username.
     * @param vCardElement the vCard to save.
     * @return vCard as it is after the provider has a chance to adjust it.
     * @throws NotFoundException if the vCard to update does not exist.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     */
    Element updateVCard(String username, Element vCardElement) throws NotFoundException;

    /**
     * Delets a user vcard. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend vcard store.
     *
     * @param username the username to delete.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     */
    void deleteVCard(String username);

    /**
     * Returns true if this VCardProvider is read-only. When read-only,
     * vcards can not be created, deleted, or modified.
     *
     * @return true if the vcard provider is read-only.
     */
    boolean isReadOnly();
}
