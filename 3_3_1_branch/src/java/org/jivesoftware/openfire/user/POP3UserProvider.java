/**
 * $RCSfile$
 * $Revision: 1765 $
 * $Date: 2005-08-10 22:37:59 -0700 (Wed, 10 Aug 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

/**
 * A UserProvider to be used in conjunction with
 * {@link org.jivesoftware.openfire.auth.POP3AuthProvider POP3AuthProvider}, which
 * authenticates using a POP3 server. New user accounts will automatically be created
 * as needed (upon successful initial authentication) and are subsequently treated as
 * read-only (for the most part). To enable this provider, edit the XML config file
 * and set:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.auth.POP3AuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 *     &lt;user&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.user.POP3UserProvider&lt;/className&gt;
 *     &lt;/user&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * @see org.jivesoftware.openfire.auth.POP3AuthProvider POP3AuthProvider
 * @author Sean Meiners
 */
public class POP3UserProvider extends DefaultUserProvider {

    public void setEmail(String username, String email) throws UserNotFoundException {
        throw new UnsupportedOperationException();
    }
}