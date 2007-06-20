/**
 * $RCSfile$
 * $Revision: 1651 $
 * $Date: 2005-07-20 00:20:39 -0300 (Wed, 20 Jul 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
     * @param username the username.
     * @param vCardElement the vCard to save.
     * @throws AlreadyExistsException if the user already has a vCard.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     * @deprecated use {@link #setVCard(String, org.dom4j.Element)}
     */
    void createVCard(String username, Element vCardElement) throws AlreadyExistsException;

    /**
     * Updates the user vcard in the backend store. This method should throw an
     * UnsupportedOperationException if this operation is not supported by
     * the backend vcard store.
     *
     * @param username the username.
     * @param vCardElement the vCard to save.
     * @throws NotFoundException if the vCard to update does not exist.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     * @deprecated use {@link #setVCard(String, org.dom4j.Element)}
     */
    void updateVCard(String username, Element vCardElement) throws NotFoundException;

    /**
     * Sets the vCard of the user to the passed in element.
     *
     * @param username the username of the user whose vCard is being set
     * @param vCardElement the vCard being set.
     */
    void setVCard(String username, Element vCardElement);

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
