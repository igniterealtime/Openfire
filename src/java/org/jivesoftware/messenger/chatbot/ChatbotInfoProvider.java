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
package org.jivesoftware.messenger.chatbot;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;

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
public interface ChatbotInfoProvider {
    /**
     * <p>Obtain the UserInfo of a user.</p>
     * <p>If your implementation doesn't support user info, simply return a UserInfo object filled with default
     * values.</p>
     *
     * @param id The id of the user
     * @return The user's info
     * @throws org.jivesoftware.messenger.user.UserNotFoundException
     *          If a user with the given ID couldn't be found
     */
    ChatbotInfo getInfo(long id) throws UserNotFoundException;

    /**
     * <p>Sets the user's info (optional operation).</p>
     *
     * @param id   The ID of the user
     * @param info The user's new info
     * @throws UserNotFoundException         If a user with the given ID couldn't be found
     * @throws org.jivesoftware.messenger.auth.UnauthorizedException
     *                                       If this operation is not allowed for the caller's permissions
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void updateInfo(long id, ChatbotInfo info)
            throws UserNotFoundException, UnauthorizedException, UnsupportedOperationException;
}
