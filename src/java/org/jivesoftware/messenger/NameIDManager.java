/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * <p>Provides a generic interface to the known names and unique long IDs on the server.</p>
 * <p/>
 * <p>Several types of entities can be addressed in Messenger using a username-id combination
 * including users and chatbots. Usernames are used to create unique XMPP addresses of the form
 * username@server.com and the long ID provides a key used to associate all related resources
 * (private storage, rosters, etc) to that username. In many cases it is desirable to distinguish between
 * users, chatbots, etc and the appropriate *Manager classes allow this specialized access (e.g.
 * Users are found with the UserManager, Chatbots with the ChatbotManager). The NameIDManager is the
 * generic interface to all names and ids on the system without distinguishing what particular
 * type of username-id owner it belongs to.</p>
 */
public interface NameIDManager {

    /**
     * <p>Obtain the username associated with the given ID.</p>
     *
     * @param id The id used for lookup
     * @return The username corresponding to the ID
     * @throws UserNotFoundException If the id doesn't correspond to a known username-id combination on the server
     */
    String getUsername(long id) throws UserNotFoundException;

    /**
     * <p>Obtain the ID associated with the given username.</p>
     *
     * @param username The username used for lookup
     * @return The ID corresponding to the username
     * @throws UserNotFoundException If the id doesn't correspond to a known username-id combination on the server
     */
    long getID(String username) throws UserNotFoundException;
}
