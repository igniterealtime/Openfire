/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-20 10:46:24 -0500 (Thu, 20 Apr 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.auth.UnauthorizedException;

/**
 * Provider interface for authorization policy. Users that wish to integrate with
 * their own authorization system must implement this class and then register
 * the implementation with Openfire in the <tt>openfire.xml</tt>
 * file. An entry in that file would look like the following:
 *
 * <pre>
 *   &lt;provider&gt;
 *     &lt;authorizationpolicy&gt;
 *       &lt;className&gt;com.foo.auth.CustomPolicyProvider&lt;/className&gt;
 *     &lt;/authorizationpolicy&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Jay Kline
 */
public interface AuthorizationPolicyProvider {

    /**
     * Returns if the principal is explicity authorized to the JID, throws 
     * an UnauthorizedException otherwise
     *
     * @param username The username requested.
     * @param principal The principal requesting the username.
     * @throws UnauthorizedException
     */
    public void authorize(String username, String principal) throws UnauthorizedException;
    
    
}