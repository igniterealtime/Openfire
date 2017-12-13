package com.reucon.openfire.plugins.userstatus;

import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.session.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.Date;

/**
 * Implementation of the PersistenceManager interface for phpBB3 integration.
 */
public class PhpBB3PersistenceManager implements PersistenceManager
{
    private static final Logger Log = LoggerFactory.getLogger(PhpBB3PersistenceManager.class);
    
    private static final String UPDATE_USER_STATUS =
            "UPDATE users SET user_jabber_online = 1 WHERE user_jid = ?";

    private static final String SET_PRESENCE =
            "UPDATE users SET user_jabber_presence = ? WHERE user_jid = ?";

    private static final String SET_OFFLINE =
            "UPDATE users SET user_jabber_online = 0 WHERE user_jid = ?";

    private static final String SET_ALL_OFFLINE =
            "UPDATE users SET user_jabber_online = 0 WHERE user_jabber_online = 1";

    private String connectionString = null;

    public PhpBB3PersistenceManager()
    {
        connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
    }

    public void setHistoryDays(int historyDays)
    {
        // history not supported
    }

    public void setAllOffline()
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement(SET_ALL_OFFLINE);
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to clean up user status", e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setOnline(Session session)
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement(UPDATE_USER_STATUS);
            pstmt.setString(1, session.getAddress().getNode());
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
          Log.error("Unable to update user status for " + session.getAddress(), e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setOffline(Session session, Date logoffDate)
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement(SET_OFFLINE);
            pstmt.setString(1, session.getAddress().getNode());
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to update user status for " + session.getAddress(), e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setPresence(Session session, String presenceText)
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement(SET_PRESENCE);
            pstmt.setString(1, presenceText);
            pstmt.setString(2, session.getAddress().getNode());
            pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to update presence for " + session.getAddress(), e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private Connection getConnection() throws SQLException
    {
        if (connectionString == null)
        {
            return DbConnectionManager.getConnection();
        }
        else
        {
            return DriverManager.getConnection(connectionString);
        }
    }

    public void deleteOldHistoryEntries()
    {
        // history not supported
    }


}