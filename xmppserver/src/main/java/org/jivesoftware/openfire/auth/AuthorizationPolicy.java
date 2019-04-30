/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
 * This is the interface the AuthorizationManager uses to
 * conduct authorizations.
 * <p>
 * Users that wish to integrate with their own authorization
 * system must implement this interface, and are strongly
 * encouraged to extend either the AbstractAuthoriationPolicy
 * or the AbstractAuthorizationProvider classes which allow
 * the admin console manage the classes more effectively.
 * Register the class with Openfire in the {@code openfire.xml}
 * file.  An entry in that file would look like the following:
 * </p>
 * <pre>
 *   &lt;provider&gt;
 *     &lt;authorization&gt;
 *       &lt;classlist&gt;com.foo.auth.CustomPolicyProvider&lt;/classlist&gt;
 *     &lt;/authorization&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Jay Kline
 */
public interface AuthorizationPolicy {

    /**
     * Returns true if the principal is explicitly authorized to the JID
     *
     * @param username  The username requested.
     * @param principal The principal requesting the username.
     * @return true is the user is authorized to be principal
     */
    boolean authorize( String username, String principal );

    /**
     * Returns the short name of the Policy
     *
     * @return The short name of the Policy
     */
    String name();

    /**
     * Returns a description of the Policy
     *
     * @return The description of the Policy.
     */
    String description();
}
