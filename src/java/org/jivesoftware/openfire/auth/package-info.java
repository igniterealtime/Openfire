/**
 * Authentication and Authorization service interfaces and classes. There are three components:
 *
 * <ul>
 * <li><b>Authentication</b>. Authentication is the process of verifying a user. Custom authentication implementations can be created by extending the {@link org.jivesoftware.openfire.auth.AuthProvider} interface.</li>
 * <li><b>Authorization</b>. Authorization is the process of allowing an authenticated identity to choose a username.  Default authorization will authorize an authenticated username to the same username only.  Custom authorization implementations can be created by extending the {@link org.jivesoftware.openfire.auth.AuthorizationPolicy} interface.</li>
 * <li><b>Authorization Mapping</b>. Mapping occurs when the client did not request any specific username. This provides a method of giving a default username in these situations. Custom authorization mappings can be created by extending the {@link org.jivesoftware.openfire.auth.AuthorizationMapping} interface.</li>
 * </ul>
 */
package org.jivesoftware.openfire.auth;
