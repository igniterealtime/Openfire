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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.XMPPAddress;

/**
 * <p>A simple service that allows components to retrieve a roster based solely on the ID of the owner.</p>
 * <p/>
 * <p>The User, Chatbot, and other ID based 'resources owners' have convenience methods for obtaining
 * a roster associated with the owner. However there are many components that need to retrieve the
 * roster based solely on the generic ID owner key. This interface defines a service that can do that.
 * This allows classes that generically manage resource for resource owners (such as presence updates)
 * to generically offer their services without knowing or caring if the roster owner is a user, chatbot, etc.</p>
 *
 * @author Iain Shigeoka
 */
public interface RosterManager {
    /**
     * <p>Obtain the roster for the given username.</p>
     *
     * @param username the username to search for
     * @return The roster associated with the ID
     * @throws UserNotFoundException If the ID does not correspond to a known entity on the server
     */
    CachedRoster getRoster(String username) throws UserNotFoundException;

    /**
     * Removes the entire roster of a given user. This is necessary when a user account is being
     * deleted from the server.
     *
     * @param user the user to remove his roster.
     */
    void deleteRoster(XMPPAddress user);
}
