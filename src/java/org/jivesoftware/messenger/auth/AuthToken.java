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

package org.jivesoftware.messenger.auth;

/**
 * Proves that a user has successfully logged in. The existence of an AuthToken object indicates
 * that a person has logged in correctly and has authentication to act as the user associated with
 * the authentication. An instance of this object can be obtained from the AuthFactory and
 * must be passed in to to get an instance of KBFactory or ForumFactory.<p>
 * <p/>
 * In the case of using the core faq or forum services through a web interface, the expected
 * behavior is to have a user login and then store the AuthToken object in their session. In
 * some app servers, all objects put in the session must be serializable. The default AuthToken
 * implementation obeys this rule, but ensure that custom AuthToken classes do as well.
 *
 * @author Iain Shigeoka
 * @see AuthFactory
 */
public interface AuthToken {

    /**
     * Returns the userID associated with this AuthToken.
     *
     * @return the userID associated with this AuthToken.
     */
    public long getUserID();

    /**
     * Returns true if this AuthToken is the Anonymous auth token.
     *
     * @return true if this token is the anonymous AuthToken.
     */
    public boolean isAnonymous();
}