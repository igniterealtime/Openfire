/**
 * $RCSfile$
 * $Revision$
 * $Date: 2005-07-26 19:10:33 +0200 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.vcard;

/**
 * Interface to listen for vCard changes. Use the
 * {@link org.jivesoftware.openfire.vcard.VCardManager#addListener(VCardListener)}
 * method to register for events.
 *
 * @author Remko Tron&ccedil;on
 */
public interface VCardListener {
    /**
     * A vCard was created.
     *
     * @param user the user for which the vCard was created.
     * @deprecated use {@link #vCardSet(String)}
     */
    public void vCardCreated(String user);

    /**
     * A vCard was updated.
     *
     * @param user the user for which the vCard was updated.
     * @deprecated use {@link #vCardSet(String)}
     */
    public void vCardUpdated(String user);

    /**
     * A vCard was set.
     *
     * @param username the user for which the vCard was set
     */
    public void vCardSet(String username);

    /**
     * A vCard was deleted.
     *
     * @param user the user for which the vCard was deleted.
     */
    public void vCardDeleted(String user);
}
