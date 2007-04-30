/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-07 09:28:54 -0500 (Fri, 07 Apr 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sasl;

/**
 * This is the interface the AuthorizationManager uses to 
 * conduct authorizations.  
 * 
 * Users that wish to integrate with their own authorization 
 * system must implement this interface, and are strongly 
 * encouraged to extend either the AbstractAuthoriationPolicy
 * or the AbstractAuthorizationProvider classes which allow
 * the admin console manage the classes more effectively.
 * Register the class with Openfire in the <tt>openfire.xml</tt>
 * file.  An entry in that file would look like the following:
 *
 * <pre>
 *   &lt;provider&gt;
 *     &lt;authorizationpolicy&gt;
 *       &lt;classlist&gt;com.foo.auth.CustomPolicyProvider&lt;/classlist&gt;
 *     &lt;/authorizationpolicy&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Jay Kline
 */
public interface AuthorizationProvider {

    /**
     * Returns true if the principal is explicity authorized to the JID
     *
     * @param username The username requested.
     * @param principal The principal requesting the username.
     * @return true is the user is authorized to be principal
     */
    public boolean authorize(String username, String principal);

}