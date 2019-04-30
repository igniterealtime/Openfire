/*
 * Copyright 2016 IgniteRealtime.org
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link AuthProviderMapper} that can be used to draw administrative users from another source than the regular, non-
 * administrative users.
 *
 * This implementation uses {@link AdminManager} to determine if a particular user is an administrative user. When a
 * user is not recognized, it is deemed a regular, non-administrative user.
 *
 * To configure this provider, the both system properties from the example below <em>must</em> be defined. Their value
 * must reference the classname of an {@link AuthProvider}.
 *
 * <ul>
 * <li>{@code authorizationBasedAuthMapper.adminProvider.className = org.jivesoftware.openfire.auth.DefaultAuthProvider}</li>
 * <li>{@code authorizationBasedAuthMapper.userProvider.className = org.jivesoftware.openfire.auth.NativeAuthProvider}</li>
 * </ul>
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class AuthorizationBasedAuthProviderMapper implements AuthProviderMapper
{
    /**
     * Name of the property of which the value is expected to be the classname of the AuthProvider which will serve the
     * administrative users.
     */
    public static final String PROPERTY_ADMINPROVIDER_CLASSNAME = "authorizationBasedAuthMapper.adminProvider.className";

    /**
     * Name of the property of which the value is expected to be the classname of the AuthProvider which will serve the
     * regular, non-administrative users.
     */
    public static final String PROPERTY_USERPROVIDER_CLASSNAME = "authorizationBasedAuthMapper.userProvider.className";

    /**
     * Serves the administrative users.
     */
    protected final AuthProvider adminProvider;

    /**
     * Serves the regular, non-administrative users.
     */
    protected final AuthProvider userProvider;

    public AuthorizationBasedAuthProviderMapper()
    {
        // Migrate properties.
        JiveGlobals.migrateProperty( PROPERTY_ADMINPROVIDER_CLASSNAME );
        JiveGlobals.migrateProperty( PROPERTY_USERPROVIDER_CLASSNAME );

        // Instantiate providers.
        adminProvider = instantiateProvider( PROPERTY_ADMINPROVIDER_CLASSNAME );
        userProvider = instantiateProvider( PROPERTY_USERPROVIDER_CLASSNAME );
    }

    protected static AuthProvider instantiateProvider( String propertyName )
    {
        final String className = JiveGlobals.getProperty( propertyName );
        if ( className == null )
        {
            throw new IllegalStateException( "A class name must be specified via openfire.xml or the system properties." );
        }

        try
        {
            final Class c = ClassUtils.forName( className );
            return (AuthProvider) c.newInstance();
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Unable to create new instance of AuthProvider: " + className, e );
        }
    }

    @Override
    public AuthProvider getAuthProvider( String username )
    {
        // TODO add optional caching, to prevent retrieving the administrative users upon every invocation.
        final JID jid = XMPPServer.getInstance().createJID( username, null );
        final boolean isAdmin = AdminManager.getAdminProvider().getAdmins().contains( jid );

        if ( isAdmin )
        {
            return adminProvider;
        } else
        {
            return userProvider;
        }
    }

    @Override
    public Set<AuthProvider> getAuthProviders()
    {
        final Set<AuthProvider> result = new LinkedHashSet<>();
        result.add( adminProvider );
        result.add( userProvider );

        return result;
    }
}
