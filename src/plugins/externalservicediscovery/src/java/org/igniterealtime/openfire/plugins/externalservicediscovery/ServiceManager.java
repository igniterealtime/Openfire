/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
package org.igniterealtime.openfire.plugins.externalservicediscovery;

import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A manager of {@link Service} instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ServiceManager
{
    private static final Logger Log = LoggerFactory.getLogger( ServiceManager.class );

    private Set<Service> services = new HashSet<>();

    // Not making this a singleton, to allow for database-reloads in a cluster.
    public static ServiceManager getInstance()
    {
        final ServiceManager instance = new ServiceManager();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet resultSet = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( "SELECT * FROM ofExternalServices " );
            resultSet = pstmt.executeQuery();
            while ( resultSet.next() )
            {
                final long databaseId = resultSet.getLong( "serviceID" );

                String name = resultSet.getString( "name" );
                if ( resultSet.wasNull() || name == null || name.isEmpty() )
                {
                    name = null;
                }

                String host = resultSet.getString( "host" );
                if ( resultSet.wasNull() || host == null || host.isEmpty() )
                {
                    host = null;
                }

                Integer port = resultSet.getInt( "port" );
                if ( resultSet.wasNull() )
                {
                    port = null;
                }

                Boolean restricted = resultSet.getBoolean( "restricted" );
                if ( resultSet.wasNull() )
                {
                    restricted = null;
                }

                String transport = resultSet.getString( "transport" );
                if ( resultSet.wasNull() || transport == null || transport.isEmpty() )
                {
                    transport = null;
                }

                String type = resultSet.getString( "type" );
                if ( resultSet.wasNull() || type == null || type.isEmpty() )
                {
                    type = null;
                }

                String username = resultSet.getString( "username" );
                if ( resultSet.wasNull() || username == null || username.isEmpty() )
                {
                    username = null;
                }

                String password = resultSet.getString( "password" );
                if ( resultSet.wasNull() || password == null || password.isEmpty() )
                {
                    password = null;
                }

                String sharedSecret = resultSet.getString( "sharedSecret" );
                if ( resultSet.wasNull() || sharedSecret == null || sharedSecret.isEmpty() )
                {
                    sharedSecret = null;
                }

                final Service service = new Service( databaseId, name, host, port, restricted, transport, type, username, password, sharedSecret );
                instance.services.add( service );
                Log.debug( "Loaded {} service at {} from database.", service.getType(), service.getHost() );
            }
        }
        catch ( Exception e )
        {
            Log.error( "Unable to load services from database!", e );
        }
        finally
        {
            DbConnectionManager.closeConnection( resultSet, pstmt, con );
        }
        return instance;
    }

    public void addService( Service service )
    {
        if ( services.add( service ) )
        {
            Connection con = null;
            PreparedStatement pstmt = null;
            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement( "INSERT INTO ofExternalServices VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" );
                pstmt.setLong( 1, service.getDatabaseId() );

                if ( service.getName() == null || service.getName().isEmpty() )
                {
                    pstmt.setNull( 2, Types.VARCHAR );
                }
                else
                {
                    pstmt.setString( 2, service.getName() );
                }

                if ( service.getHost() == null || service.getHost().isEmpty() )
                {
                    pstmt.setNull( 3, Types.VARCHAR );
                }
                else
                {
                    pstmt.setString( 3, service.getHost() );
                }

                if ( service.getPort() == null )
                {
                    pstmt.setNull( 4, Types.INTEGER );
                }
                else
                {
                    pstmt.setInt( 4, service.getPort() );
                }

                if ( service.getRestricted() == null )
                {
                    pstmt.setNull( 5, Types.BOOLEAN );
                }
                else
                {
                    pstmt.setBoolean( 5, service.getRestricted() );
                }

                if ( service.getTransport() == null || service.getTransport().isEmpty() )
                {
                    pstmt.setNull( 6, Types.CHAR );
                }
                else
                {
                    pstmt.setString( 6, service.getTransport() );
                }

                if ( service.getType() == null || service.getType().isEmpty() )
                {
                    pstmt.setNull( 7, Types.CHAR );
                }
                else
                {
                    pstmt.setString( 7, service.getType() );
                }

                if ( service.getRawUsername() == null || service.getRawUsername().isEmpty() )
                {
                    pstmt.setNull( 8, Types.VARCHAR );
                }
                else
                {
                    pstmt.setString( 8, service.getRawUsername() );
                }

                if ( service.getRawPassword() == null || service.getRawPassword().isEmpty() )
                {
                    pstmt.setNull( 9, Types.VARCHAR );
                }
                else
                {
                    pstmt.setString( 9, service.getRawPassword() );
                }

                if ( service.getSharedSecret() == null || service.getSharedSecret().isEmpty() )
                {
                    pstmt.setNull( 10, Types.VARCHAR );
                }
                else
                {
                    pstmt.setString( 10, service.getSharedSecret() );
                }

                pstmt.executeUpdate();
                Log.info( "Added {} service at {}.", service.getType(), service.getHost() );
            }
            catch ( Exception e )
            {
                Log.error( "Unable to persists service ({} at {}) in database!", service.getType(), service.getHost(), e );
                services.remove( service );
            }
            finally
            {
                DbConnectionManager.closeConnection( pstmt, con );
            }
        }
    }

    public void removeService( Service service )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( "DELETE FROM ofExternalServices WHERE serviceID=?" );
            pstmt.setLong( 1, service.getDatabaseId() );

            if ( pstmt.executeUpdate() == 0 )
            {
                Log.warn( "The query to remove {} service at {} from the database did not remove anything.", service.getType(), service.getHost() );
            }
            else
            {
                services.remove( service );
                Log.info( "Removed {} service at {}.", service.getType(), service.getHost() );
            }
        }
        catch ( Exception e )
        {
            Log.error( "Unable to remove service ({} at {}) from database!", service.getType(), service.getHost(), e );
        }
        finally
        {
            DbConnectionManager.closeConnection( pstmt, con );
        }
    }

    public Set<Service> getAllServices()
    {
        return new HashSet<>( services );
    }

    public Map<Service, Credentials> getServicesFor( JID requester )
    {
        Log.debug( "Obtaining credentials for {}", requester );

        final Map<Service, Credentials> result = new HashMap<>();
        for ( final Service service : services )
        {
            try
            {
                final Credentials credentials = service.getCredentialsFor( requester );
                result.put( service, credentials );
            }
            catch ( Exception e )
            {
                Log.warn( "Unable to obtain credentials for requester '{}', for the {} service at: {}", requester, service.getType(), service.getHost(), e );
            }
        }

        return result;
    }

    public Map<Service, Credentials> getServicesFor( JID requester, String requestedType )
    {
        Log.debug( "Obtaining credentials for {} of type {}", requester, requestedType );

        final Map<Service, Credentials> result = new HashMap<>();
        for ( final Service service : services )
        {
            if ( requestedType.equals( service.getType() ) )
            {
                try
                {
                    final Credentials credentials = service.getCredentialsFor( requester );
                    result.put( service, credentials );
                }
                catch ( Exception e )
                {
                    Log.warn( "Unable to obtain credentials for requester '{}', for the {} service at: {}", requester, service.getType(), service.getHost(), e );
                }
            }
        }

        return result;
    }

    public Map<Service, Credentials> getServicesFor( JID requester, String requestedHost, String requestedType )
    {
        Log.debug( "Obtaining credentials for {} on {} of type {}", requester, requestedHost, requestedType );

        final Map<Service, Credentials> result = new HashMap<>();
        for ( final Service service : services )
        {
            if ( requestedType.equals( service.getType() )
                && requestedHost.equals( service.getHost() ) )
            {
                try
                {
                    final Credentials credentials = service.getCredentialsFor( requester );
                    result.put( service, credentials );
                }
                catch ( Exception e )
                {
                    Log.warn( "Unable to obtain credentials for requester '{}', for the {} service at: {}", requester, service.getType(), service.getHost(), e );
                }
            }
        }

        return result;
    }

    public Map<Service, Credentials> getServicesFor( JID requester, String requestedHost, String requestedType, int requestedPort )
    {
        Log.debug( "Obtaining credentials for {} on {}:{} of type {}", requester, requestedHost, requestedPort, requestedType );
        final Map<Service, Credentials> result = new HashMap<>();
        for ( final Service service : services )
        {
            if ( requestedType.equals( service.getType() )
                && requestedHost.equals( service.getHost() )
                && requestedPort == service.getPort() )
            {
                try
                {
                    final Credentials credentials = service.getCredentialsFor( requester );
                    result.put( service, credentials );
                }
                catch ( Exception e )
                {
                    Log.warn( "Unable to obtain credentials for requester '{}', for the {} service at: {}:{}", requester, requestedType, requestedHost, requestedPort, e );
                }
            }
        }

        return result;
    }
}
