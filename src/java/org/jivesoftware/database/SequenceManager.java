/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

/**
 * Manages sequences of unique ID's that get stored in the database. Database support for sequences
 * varies widely; some don't use them at all. Instead, we handle unique ID generation with a
 * combination VM/database solution.<p>
 * <p/>
 * A special table in the database doles out blocks of unique ID's to each
 * virtual machine that interacts with Jive. This has the following consequences:
 * <ul>
 * <li>There is no need to go to the database every time we want a new unique id.
 * <li>Multiple app servers can interact with the same db without id collision.
 * <li>The order of unique id's may not correspond to the creation date of objects.
 * <li>There can be gaps in ID's after server restarts since blocks will get "lost" if the block
 * size is greater than 1.
 * </ul><p>
 * <p/>
 * Each sequence type that this class manages has a different block size value. Objects that aren't
 * created often have a block size of 1, while frequently created objects such as entries and
 * comments have larger block sizes.
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 */
public class SequenceManager {

    private static final String LOAD_ID =
            "SELECT id FROM jiveID WHERE idType=?";
    private static final String UPDATE_ID =
            "UPDATE jiveID SET id=? WHERE idType=? AND id=?";

    // Statically startup a sequence manager for each of the sequence counters.
    private static Map<Integer,Object> managers;

    static {
        managers = new HashMap<Integer,Object>();
        new SequenceManager(JiveConstants.USER, 1);
        new SequenceManager(JiveConstants.GROUP, 1);
        new SequenceManager(JiveConstants.ROSTER, 5);
        new SequenceManager(JiveConstants.OFFLINE, 1);
        new SequenceManager(JiveConstants.WORKGROUP_AGENT, 1);
        new SequenceManager(JiveConstants.WORKGROUP_GROUP, 1);
        new SequenceManager(JiveConstants.WORKGROUP_QUEUE, 1);
        new SequenceManager(JiveConstants.MUC_ROOM, 1);
}

    /**
     * Returns the next ID of the specified type.
     *
     * @param type the type of unique ID.
     * @return the next unique ID of the specified type.
     */
    public static long nextID(int type) {
        if (managers.containsKey(type)) {
            return ((SequenceManager)managers.get(type)).nextUniqueID();
        }
        else {
            throw new IllegalArgumentException("Invalid type");
        }
    }

    private int type;
    private long currentID;
    private long maxID;
    private int blockSize;

    /**
     * Creates a new DbSequenceManager.
     *
     * @param seqType the type of sequence.
     * @param size    the number of id's to "checkout" at a time.
     */
    public SequenceManager(int seqType, int size) {
        managers.put(seqType, this);
        this.type = seqType;
        this.blockSize = size;
        currentID = 0l;
        maxID = 0l;
    }

    /**
     * Returns the next available unique ID. Essentially this provides for the functionality of an
     * auto-increment database field.
     */
    public synchronized long nextUniqueID() {
        if (!(currentID < maxID)) {
            // Get next block -- make 5 attempts at maximum.
            getNextBlock(5);
        }
        long id = currentID;
        currentID++;
        return id;
    }

    /**
     * Performs a lookup to get the next available ID block. The algorithm is as follows:
     * <ol>
     * <li> Select currentID from appropriate db row.
     * <li> Increment id returned from db.
     * <li> Update db row with new id where id=old_id.
     * <li> If update fails another process checked out the block first; go back to step 1.
     * Otherwise, done.
     * </ol>
     */
    private void getNextBlock(int count) {
        if (count == 0) {
            Log.error("Failed at last attempt to obtain an ID, aborting...");
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        boolean success = false;

        try {
            con = DbConnectionManager.getTransactionConnection();
            // Get the current ID from the database.
            pstmt = con.prepareStatement(LOAD_ID);
            pstmt.setInt(1, type);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Loading the current ID failed. The " +
                        "jiveID table may not be correctly populated.");
            }
            long currentID = rs.getLong(1);
            pstmt.close();

            // Increment the id to define our block.
            long newID = currentID + blockSize;
            // The WHERE clause includes the last value of the id. This ensures
            // that an update will occur only if nobody else has performed an
            // update first.
            pstmt = con.prepareStatement(UPDATE_ID);
            pstmt.setLong(1, newID);
            pstmt.setInt(2, type);
            pstmt.setLong(3, currentID);
            // Check to see if the row was affected. If not, some other process
            // already changed the original id that we read. Therefore, this
            // round failed and we'll have to try again.
            success = pstmt.executeUpdate() == 1;
            if (success) {
                this.currentID = currentID;
                this.maxID = newID;
            }
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }

        if (!success) {
            Log.error("WARNING: failed to obtain next ID block due to " +
                    "thread contention. Trying again...");
            // Call this method again, but sleep briefly to try to avoid thread contention.
            try {
                Thread.sleep(75);
            }
            catch (InterruptedException ie) {
            }
            getNextBlock(count - 1);
        }
    }
}
