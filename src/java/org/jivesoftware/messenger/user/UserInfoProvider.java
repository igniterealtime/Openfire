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

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * <p>A common interface to implement when creating a user management service plug-in.</p>
 * <p/>
 * <p>Provide meta-information about a user that's useful in server behavior. Implementation
 * of this provider is optional and systems where user information is stored and managed in
 * other systems may want to provide partial implementations or use the Jive dummy implementation
 * that returns no values.</p>
 * <p/>
 * <p>Messenger will cache much of the information it obtains from calling this provider. If you will be modifying
 * the underlying data outside of Messenger, please consult with Jive for information on maintaining a valid
 * cache.</p>
 *
 * @author Iain Shigeoka
 */
public interface UserInfoProvider {

    /**
     * <p>Obtain the UserInfo of a user.</p>
     * <p>If your implementation doesn't support user info, simply return a UserInfo object filled with default
     * values.</p>
     *
     * @param username the username of the user.
     * @return The user's info
     * @throws UserNotFoundException If a user with the given ID couldn't be found
     */
    UserInfo getInfo(String username) throws UserNotFoundException;

    /**
     * <p>Sets the user's info (optional operation).</p>
     *
     * @param username the username of the user.
     * @param info The user's new info
     * @throws UserNotFoundException         If a user with the given ID couldn't be found
     * @throws UnauthorizedException         If this operation is not allowed for the caller's permissions
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void setInfo(String username, UserInfo info)
            throws UserNotFoundException, UnauthorizedException, UnsupportedOperationException;
}
