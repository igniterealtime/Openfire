/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.privacy;

/**
 * /**
 * Interface to listen for privacy list events. Use the
 * {@link PrivacyListManager#addListener(PrivacyListEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface PrivacyListEventListener {

    /**
     * A privacy list was created.
     *
     * @param list the privacy list.
     */
    public void privacyListCreated(PrivacyList list);

    /**
     * A privacy list is being deleted.
     *
     * @param listName name of the the privacy list that has been deleted.
     */
    public void privacyListDeleting(String listName);

    /**
     * Properties of the privacy list were changed.
     *
     * @param list the privacy list.
     */
    public void privacyListModified(PrivacyList list);
}
