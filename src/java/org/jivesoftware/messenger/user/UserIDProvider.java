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
package org.jivesoftware.messenger.user;

import org.jivesoftware.util.LongList;

/**
 * <p>The essential interface to implement when creating a user management service plug-in.</p>
 * <p/>
 * <p>All Messenger systems dealing with a user are keyed on a long user ID value. However, external
 * systems and XMPP addressing must be able to map between user IDs and usernames and back again.
 * The default provider of the user ID provider maintains a simple table of the username and it's
 * corresponding long ID. If you replace the default authentication system, you will need to make sure
 * to create yoru own UserIDProvider to correctly maintain a mapping between user names and IDs.
 * Alternatively, you can simply update the Jive default userID table when the information changes
 * in your other backend systems.</p>
 * <p/>
 * <p>Messenger will cache much of the information it obtains from calling this provider. If you will be modifying
 * the underlying data outside of Messenger, please consult with Jive for information on maintaining a valid
 * cache.</p>
 * <p/>
 * <p>The other user management service plug-in that is commonly overridden is UserInfoProvider.</p>
 *
 * @author Iain Shigeoka
 * @see UserInfoProvider
 */
public interface UserIDProvider {

    /**
     * <p>Obtain the user's username from their ID.</p>
     *
     * @param id The id of the user
     * @return The user's username
     * @throws UserNotFoundException If a user with the given ID couldn't be found
     */
    String getUsername(long id) throws UserNotFoundException;

    /**
     * <p>Obtain the user's username from their ID.</p>
     *
     * @param username The user's username
     * @return The ID  for the user
     * @throws UserNotFoundException If a user with the given ID couldn't be found
     */
    long getUserID(String username) throws UserNotFoundException;

    /**
     * <p>Obtain the total number of users on the system.</p>
     * <p/>
     * <p>If the provider doesn't support user listings, return a 0 (zero).</p>
     *
     * @return The number of users on the system
     */
    int getUserCount();

    /**
     * <p>Obtain a list all user IDs on the system.</p>
     * <p>If the provider doesn't support user listings, return an empty list.</p>
     *
     * @return The number of users on the system
     */
    LongList getUserIDs();

    /**
     * <p>Obtain a restricted list all user IDs on the system.</p>
     * <p/>
     * <p>Assuming your system has a natural listing of users, this
     * interface will be used to present users in pages rather than
     * loading all the users on a single. In large user databases
     * this method may be critical to reducing the amount of memory
     * this call consumes.</p>
     * <p>If the provider doesn't support user listings, return an empty list.</p>
     *
     * @param startIndex The number of users to skip before
     * @param numResults The number of users to include in the list
     * @return The number of users on the system
     */
    LongList getUserIDs(int startIndex, int numResults);
}
