/**
 * $RCSfile$
 * $Revision: $
 * $Date: 2006-04-07 09:28:54 -0500 (Fri, 07 Apr 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.auth;

/**
 * This is the interface the used to provide default defualt authorization 
 * ID's when none was selected by the client.
 * <p/>
 * Users that wish to integrate with their own authorization
 * system must implement this interface.
 * Register the class with Openfire in the <tt>openfire.xml</tt>
 * file.  An entry in that file would look like the following:
 * <p/>
 * <pre>
 *   &lt;provider&gt;
 *     &lt;authorizationMapping&gt;
 *       &lt;classlist&gt;com.foo.auth.CustomProvider&lt;/classlist&gt;
 *     &lt;/authorizationMapping&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Jay Kline
 */
public interface AuthorizationMapping {

    /**
     * Returns true if the principal is explicity authorized to the JID
     *
     * @param principal The autheticated principal requesting authorization.
     * @return The name of the default username to use.
     */
    public String map(String principal);

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    public abstract String name();

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    public abstract String description();
}