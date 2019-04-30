/*
 * Copyright 2017 IgniteRealtime.org
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
package org.jivesoftware.openfire.user.property;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the UserPropertyProvider interface, which reads and writes data from the
 * {@code ofUserProp} database table.
 *
 * This implementation will not explicitly verify if a user exists, when operating on its properties. The methods of
 * this implementation will <em>not</em> throw {@link org.jivesoftware.openfire.user.UserNotFoundException}.
 *
 * <b>Warning:</b> in virtually all cases a user property provider should not be used directly. Instead, use the
 * Map returned by {@link User#getProperties()} to create, read, update or delete user properties. Failure to do so
 * is likely to result in inconsistent data behavior and race conditions. Direct access to the user property
 * provider is only provided for special-case logic.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see User#getProperties
 */
public class DefaultUserPropertyProvider implements UserPropertyProvider
{
    private static final Logger Log = LoggerFactory.getLogger( DefaultUserPropertyProvider.class );

    private static final String LOAD_PROPERTIES = "SELECT name, propValue FROM ofUserProp WHERE username=?";
    private static final String LOAD_PROPERTY = "SELECT propValue FROM ofUserProp WHERE username=? AND name=?";
    private static final String DELETE_PROPERTY = "DELETE FROM ofUserProp WHERE username=? AND name=?";
    private static final String UPDATE_PROPERTY = "UPDATE ofUserProp SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_PROPERTY = "INSERT INTO ofUserProp (username, name, propValue) VALUES (?, ?, ?)";

    @Override
    public Map<String, String> loadProperties( String username )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        final Map<String, String> properties = new ConcurrentHashMap<>();
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( LOAD_PROPERTIES );
            pstmt.setString( 1, username );
            rs = pstmt.executeQuery();
            while ( rs.next() )
            {
                properties.put( rs.getString( 1 ), rs.getString( 2 ) );
            }
        }
        catch ( SQLException sqle )
        {
            Log.error( sqle.getMessage(), sqle );
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, pstmt, con );
        }
        return properties;
    }

    @Override
    public String loadProperty( String username, String propertyName )
    {
        String propertyValue = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( LOAD_PROPERTY );
            pstmt.setString( 1, username );
            pstmt.setString( 2, propertyName );
            rs = pstmt.executeQuery();
            while ( rs.next() )
            {
                propertyValue = rs.getString( 1 );
            }
        }
        catch ( SQLException sqle )
        {
            Log.error( sqle.getMessage(), sqle );
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, pstmt, con );
        }
        return propertyValue;
    }

    @Override
    public void insertProperty( String username, String propName, String propValue )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( INSERT_PROPERTY );
            pstmt.setString( 1, username );
            pstmt.setString( 2, propName );
            pstmt.setString( 3, propValue );
            pstmt.executeUpdate();
        }
        catch ( SQLException e )
        {
            Log.error( e.getMessage(), e );
        }
        finally
        {
            DbConnectionManager.closeConnection( pstmt, con );
        }
    }

    @Override
    public void updateProperty( String username, String propName, String propValue )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( UPDATE_PROPERTY );
            pstmt.setString( 1, propValue );
            pstmt.setString( 2, propName );
            pstmt.setString( 3, username );
            pstmt.executeUpdate();
        }
        catch ( SQLException e )
        {
            Log.error( e.getMessage(), e );
        }
        finally
        {
            DbConnectionManager.closeConnection( pstmt, con );
        }
    }

    @Override
    public void deleteProperty( String username, String propName )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( DELETE_PROPERTY );
            pstmt.setString( 1, username );
            pstmt.setString( 2, propName );
            pstmt.executeUpdate();
        }
        catch ( SQLException e )
        {
            Log.error( e.getMessage(), e );
        }
        finally
        {
            DbConnectionManager.closeConnection( pstmt, con );
        }
    }

    @Override
    public boolean isReadOnly()
    {
        return false;
    }
}

