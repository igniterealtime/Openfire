/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.spi.dispatcher;

import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.UserAlreadyExistsException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.dispatcher.BasicDispatcherInfo;
import org.jivesoftware.xmpp.workgroup.dispatcher.DispatcherInfo;
import org.jivesoftware.xmpp.workgroup.dispatcher.DispatcherInfoProvider;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>The Jive default implementation of dispatch info provider relying on any standard
 * JDBC database.</p>
 *
 * @author Derek DeMoro
 */
public class DbDispatcherInfoProvider implements DispatcherInfoProvider {

    private static final String INSERT_DISPATCHER =
            "INSERT INTO fpDispatcher (name, description, offerTimeout, requestTimeout, queueID) VALUES (?,?,?,?,?)";
    private static final String LOAD_DISPATCHER_BY_ID =
            "SELECT name, description, offerTimeout, requestTimeout FROM fpDispatcher WHERE queueID=?";
    private static final String UPDATE_DISPATCHER =
            "UPDATE fpDispatcher SET name=?, description=?, offerTimeout=?, requestTimeout=? WHERE queueID=?";
    private static final String DELETE_DISPATCHER =
            "DELETE FROM fpDispatcher WHERE queueID=?";

    public DbDispatcherInfoProvider() {
    }

    /**
     * Returns the Dispatcher to be used for the given queue.
     *
     * @param workgroup the owning workgroup.
     * @param queueID the id of the queue this dispatcher belongs to.
     * @return the Dispatcher.
     * @throws NotFoundException thrown if no dispatcher was found.
     */
    public DispatcherInfo getDispatcherInfo(Workgroup workgroup, long queueID) throws NotFoundException {
        BasicDispatcherInfo userInfo = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_DISPATCHER_BY_ID);
            pstmt.setLong(1, queueID);

            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException();
            }
            userInfo = new BasicDispatcherInfo(workgroup,
                    queueID,
                    rs.getString(1), // name
                    rs.getString(2), // description
                    rs.getInt(3), // offer timeout
                    rs.getInt(4)); // request timeout

        }
        catch (SQLException e) {
            throw new NotFoundException("Failed to read dispatcher " + queueID + " from database. " + e.getMessage());
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new NotFoundException("Dispatcher with id "
                    + queueID + " could not be loaded from the database.");
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return userInfo;
    }

    /**
     * Updates a RequestQueues dispatcher.
     * @param queueID the id of the queue to update.
     * @param info the new DispatcherInfo.
     * @throws NotFoundException
     * @throws UnauthorizedException
     */
    public void updateDispatcherInfo(long queueID, DispatcherInfo info) throws NotFoundException, UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_DISPATCHER);
            pstmt.setString(1, info.getName());
            pstmt.setString(2, info.getDescription());
            pstmt.setInt(3, (int)info.getOfferTimeout());
            pstmt.setInt(4, (int)info.getRequestTimeout());
            pstmt.setLong(5, queueID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Adds a new DispathcerInfo to the requestQueue.
     * @param queueID the id of the queue to add the Dispatcher to.
     * @param info the DispatcherInfo to add.
     * @throws UserAlreadyExistsException
     * @throws UnauthorizedException
     */
    public void insertDispatcherInfo(long queueID, DispatcherInfo info) throws UserAlreadyExistsException, UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_DISPATCHER);
            pstmt.setString(1, info.getName());
            pstmt.setString(2, info.getDescription());
            pstmt.setInt(3, (int)info.getOfferTimeout());
            pstmt.setInt(4, (int)info.getRequestTimeout());
            pstmt.setLong(5, queueID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Deletes the DispatcherInfo Object from the given RequestQueue.
     * @param queueID the id of the RequestQueue.
     * @throws UnauthorizedException thrown if the user is not allowed to delete from the db.
     */
    public void deleteDispatcherInfo(long queueID) throws UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_DISPATCHER);
            pstmt.setLong(1, queueID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }
}
