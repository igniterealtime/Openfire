package com.reucon.openfire.plugins.userstatus;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jivesoftware.openfire.session.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.net.UnknownHostException;

/**
 * Default implementation of the PersistenceManager interface.
 */
public class DefaultPersistenceManager implements PersistenceManager
{
  private static final Logger Log = LoggerFactory.getLogger(DefaultPersistenceManager.class);
  
    private static final int SEQ_ID = 510;

    private static final String ADD_USER_STATUS =
            "INSERT INTO userStatus (username, resource, online, lastIpAddress, lastLoginDate) " +
                    "VALUES (?, ?, 1, ?, ?)";

    private static final String UPDATE_USER_STATUS =
            "UPDATE userStatus SET online = 1, lastIpAddress = ?, lastLoginDate = ? " +
                    "WHERE username = ? AND resource = ?";

    private static final String SET_PRESENCE =
            "UPDATE userStatus SET presence = ? WHERE username = ? AND resource = ?";

    private static final String SET_OFFLINE =
            "UPDATE userStatus SET online = 0, lastLogoffDate = ? WHERE username = ? AND resource = ?";

    private static final String SET_ALL_OFFLINE =
            "UPDATE userStatus SET online = 0 WHERE online = 1";

    private static final String ADD_USER_STATUS_HISTORY =
            "INSERT INTO userStatusHistory (historyID, username, resource, lastIpAddress," +
                    "lastLoginDate, lastLogoffDate) " +
            "SELECT ?, username, resource, lastipaddress, lastlogindate, lastlogoffdate " +
            "from userStatus WHERE username = ? and resource = ?";

    private static final String DELETE_OLD_USER_STATUS_HISTORY =
            "DELETE from userStatusHistory WHERE lastLogoffDate < ?";

    /**
     * Number of days to keep history entries.<p>
     * 0 for no history entries, -1 for unlimited.
     */
    private int historyDays = -1;

    public void setHistoryDays(int historyDays)
    {
        this.historyDays = historyDays;
    }

    public void setAllOffline()
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = DbConnectionManager.getConnection();
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
        int rowsUpdated = 0;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_USER_STATUS);
            pstmt.setString(1, getHostAddress(session));
            pstmt.setString(2, StringUtils.dateToMillis(session.getCreationDate()));
            pstmt.setString(3, session.getAddress().getNode());
            pstmt.setString(4, session.getAddress().getResource());
            rowsUpdated = pstmt.executeUpdate();
        }
        catch (SQLException e)
        {
            Log.error("Unable to update user status for " + session.getAddress(), e);
        }
        finally
        {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // if update did not affect any rows insert a new row
        if (rowsUpdated == 0)
        {
            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(ADD_USER_STATUS);
                pstmt.setString(1, session.getAddress().getNode());
                pstmt.setString(2, session.getAddress().getResource());
                pstmt.setString(3, getHostAddress(session));
                pstmt.setString(4, StringUtils.dateToMillis(session.getCreationDate()));
                pstmt.executeUpdate();
            }
            catch (SQLException e)
            {
                Log.error("Unable to insert user status for " + session.getAddress(), e);
            }
            finally
            {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
    }

    public void setOffline(Session session, Date logoffDate)
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_OFFLINE);
            pstmt.setString(1, StringUtils.dateToMillis(logoffDate));
            pstmt.setString(2, session.getAddress().getNode());
            pstmt.setString(3, session.getAddress().getResource());
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

        // write history entry
        if (historyDays != 0)
        {
            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(ADD_USER_STATUS_HISTORY);
                pstmt.setLong(1, SequenceManager.nextID(SEQ_ID));
                pstmt.setString(2, session.getAddress().getNode());
                pstmt.setString(3, session.getAddress().getResource());
                pstmt.executeUpdate();
            }
            catch (SQLException e)
            {
                Log.error("Unable to add user status history for " + session.getAddress(), e);
            }
            finally
            {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }

        deleteOldHistoryEntries();
    }

    public void setPresence(Session session, String presenceText)
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        try
        {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_PRESENCE);
            pstmt.setString(1, presenceText);
            pstmt.setString(2, session.getAddress().getNode());
            pstmt.setString(3, session.getAddress().getResource());
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


    public void deleteOldHistoryEntries()
    {
        Connection con = null;
        PreparedStatement pstmt = null;

        if (historyDays > 0)
        {
            final Date deleteBefore;

            deleteBefore = new Date(System.currentTimeMillis() - historyDays * 24L * 60L * 60L * 1000L);

            try
            {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(DELETE_OLD_USER_STATUS_HISTORY);
                pstmt.setString(1, StringUtils.dateToMillis(deleteBefore));
                pstmt.executeUpdate();
            }
            catch (SQLException e)
            {
                Log.error("Unable to delete old user status history", e);
            }
            finally
            {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
    }

    private String getHostAddress(Session session)
    {
        try
        {
            return session.getHostAddress();
        }
        catch (UnknownHostException e)
        {
            return "";
        }
    }
}
