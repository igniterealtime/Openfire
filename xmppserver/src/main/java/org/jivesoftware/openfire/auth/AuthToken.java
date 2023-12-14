/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.auth;

/**
 * A token that proves that a user has successfully authenticated.
 *
 * @author Matt Tucker
 * @see AuthFactory
 */
public class AuthToken {

    private final String username;

    /**
     * Constructs a new AuthToken that represents an authenticated user identified by
     * the provider username.
     *
     * @param username the username to create an authToken token with.
     * @return the auth token for the user
     */
    public static AuthToken generateUserToken( String username )
    {
        if ( username == null || username.isEmpty() ) {
            throw new IllegalArgumentException( "Argument 'username' cannot be null." );
        }
        return new AuthToken( username );
    }

    /**
     * Constructs a new AuthToken that represents an authenticated, but anonymous user.
     * @return an anonymouse auth token
     */
    public static AuthToken generateAnonymousToken()
    {
        return new AuthToken( null );
    }

    /**
     * Constructs a new OneTimeAuthToken that represents an one time recovery user.
     *
     * @param token the one time token.
     * @return the newly generated auth token
     */
    public static AuthToken generateOneTimeToken(String token) {
        return new OneTimeAuthToken(token);
    }

    /**
     * Constructs a new AuthToken with the specified username.
     * The username can be either a simple username or a full JID.
     *
     * @param jid the username or bare JID to create an authToken token with.
     */
    protected AuthToken(String jid) {
        if (jid == null) {
            this.username = null;
            return;
        }
        int index = jid.indexOf("@");
        if (index > -1) {
            this.username = jid.substring(0,index);
        } else {
            this.username = jid;
        }
    }

    /**
     * Returns the username associated with this AuthToken. A {@code null} value
     * means that the authenticated user is anonymous.
     *
     * @return the username associated with this AuthToken or null when using an anonymous user.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns true if this AuthToken is the Anonymous auth token.
     *
     * @return true if this token is the anonymous AuthToken.
     */
    public boolean isAnonymous() {
        return username == null;
    }

    /**
     * A token that proves that a user uses a one time access token.
     *
     * @author ma1uta
     */
    public static class OneTimeAuthToken extends AuthToken {

        public OneTimeAuthToken(String token) {
            super(token);
        }
    }
}
