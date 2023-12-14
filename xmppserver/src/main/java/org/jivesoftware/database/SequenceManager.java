/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2020 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.database;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages sequences of unique ID's that get stored in the database. Database support for sequences
 * varies widely; some don't use them at all. Instead, we handle unique ID generation with a
 * combination VM/database solution.
 * <p>
 * A special table in the database doles out blocks of unique ID's to each
 * virtual machine that interacts with Jive. This has the following consequences:</p>
 * <ul>
 * <li>There is no need to go to the database every time we want a new unique id.
 * <li>Multiple app servers can interact with the same db without id collision.
 * <li>The order of unique id's may not correspond to the creation date of objects.
 * <li>There can be gaps in ID's after server restarts since blocks will get "lost" if the block
 * size is greater than 1.
 * </ul>
 * Each sequence type that this class manages has a different block size value. Objects that aren't
 * created often have a block size of 1, while frequently created objects such as entries and
 * comments have larger block sizes.
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 */
public class SequenceManager {

    private static final Logger Log = LoggerFactory.getLogger(SequenceManager.class);

    private static final String CREATE_ID =
            "INSERT INTO ofID (id, idType) VALUES (1, ?)";

    private static final String LOAD_ID =
            "SELECT id FROM ofID WHERE idType=?";

    private static final String UPDATE_ID =
            "UPDATE ofID SET id=? WHERE idType=? AND id=?";

    // Statically startup a sequence manager for each of the sequence counters.
    private static final Map<Integer, SequenceManager> managers = new ConcurrentHashMap<>();

    static {
        new SequenceManager(JiveConstants.ROSTER, 5);
        new SequenceManager(JiveConstants.OFFLINE, 5);
        new SequenceManager(JiveConstants.MUC_ROOM, 5);
        new SequenceManager(JiveConstants.MUC_MESSAGE_ID, 50);
    }

    private static final Cache<Integer, Data> sequenceBlocks = CacheFactory.createCache("Sequences");

    /**
     * Returns the next ID of the specified type.
     *
     * @param type the type of unique ID.
     * @return the next unique ID of the specified type.
     */
    public static long nextID(int type) {
        if (managers.containsKey(type)) {
            return managers.get(type).nextUniqueID();
        }
        else {
            // Verify type is valid from the db, if so create an instance for the type
            // And return the next unique id
            SequenceManager manager = new SequenceManager(type, 1);
            return manager.nextUniqueID();
        }
    }

    /**
     * Returns the next id for an object that has defined the annotation {@link JiveID}.
     * The JiveID annotation value is the synonymous for the type integer.<p>
     *
     * The annotation JiveID should contain the id type for the object (the same number you would
     * use to call nextID(int type)). Example class definition:</p>
     * <code>
     * \@JiveID(10)
     * public class MyClass {
     *
     * }
     * </code>
     *
     * @param o object that has annotation JiveID.
     * @return the next unique ID.
     * @throws IllegalArgumentException If the object passed in does not defined {@link JiveID}
     */
    public static long nextID(Object o) {
        JiveID id = o.getClass().getAnnotation(JiveID.class);

        if (id == null) {
            Log.error("Annotation JiveID must be defined in the class " + o.getClass());
            throw new IllegalArgumentException(
                    "Annotation JiveID must be defined in the class " + o.getClass());
        }

        return nextID(id.value());
    }

    /**
     * Used to set the blocksize of a given SequenceManager. If no SequenceManager has
     * been registered for the type, the type is verified as valid and then a new
     * sequence manager is created.
     *
     * @param type the type of unique id.
     * @param blockSize how many blocks of ids we should.
     */
    public static void setBlockSize(int type, int blockSize) {
        if (managers.containsKey(type)) {
            managers.get(type).blockSize = blockSize;
        }
        else {
            new SequenceManager(type, blockSize);
        }
    }

    private final int type;
    private int blockSize;

    /**
     * Creates a new DbSequenceManager.
     *
     * @param seqType the type of sequence.
     * @param size the number of id's to "checkout" at a time.
     */
    public SequenceManager(int seqType, int size) {
        managers.put(seqType, this);
        this.type = seqType;
        this.blockSize = size;
    }

    /**
     * Returns the next available unique ID. Essentially this provides for the functionality of an
     * auto-increment database field.
     * @return the next sequence number
     */
    public long nextUniqueID() {
        final Lock lock = sequenceBlocks.getLock(type);
        lock.lock();
        try {
            Data data = sequenceBlocks.get(type);
            if (data == null || !(data.getCurrentID() < data.getMaxID())) {
                data = getNextBlock();
            }

            final long id = data.getCurrentID();
            data.setCurrentID(id + 1);
            sequenceBlocks.put(type, data);
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performs a lookup to get the next available ID block. The algorithm is as follows:
     * <ol>
     * <li> Select currentID from appropriate db row.
     * <li> Increment id returned from db.
     * <li> Update db row with new id where id=old_id.
     * </ol>
     *
     * Calls to this method should only occur while a lock has already been acquired.
     *
     * @return the next available ID block
     */
    private Data getNextBlock() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            // Get the current ID from the database.
            pstmt = con.prepareStatement(LOAD_ID);
            pstmt.setInt(1, type);
            rs = pstmt.executeQuery();

            long currentID = 1;
            if (rs.next()) {
                currentID = rs.getLong(1);
            }
            else {
                createNewID(con, type);
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

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
            // already changed the original id that we read. This code should be
            // called under a lock, so this is not supposed to happen. Throw a
            // very verbose error if it does.
            if (pstmt.executeUpdate() == 1) {
                final Data result = new Data(currentID, newID);
                sequenceBlocks.put(type, result);
                return result;
            } else {
                throw new IllegalStateException("Failed at attempt to obtain an ID, aborting...");
            }
        }
        catch (SQLException e) {
            Log.error("An exception occurred while trying to obtain new sequence values from the database for type {}", type, e);
            throw new IllegalStateException("Failed at attempt to obtain an ID, aborting...", e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void createNewID(Connection con, int type) throws SQLException {
        Log.warn("Autocreating jiveID row for type '" + type + "'");

        // create new ID row
        PreparedStatement pstmt = null;

        try {
            pstmt = con.prepareStatement(CREATE_ID);
            pstmt.setInt(1, type);
            pstmt.execute();
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    static class Data implements Serializable {
        private long currentID;
        private long maxID;

        Data() { // for serialization.
        }

        public Data(long currentID, long maxID) {
            this.currentID = currentID;
            this.maxID = maxID;
        }

        public long getCurrentID() {
            return currentID;
        }

        public void setCurrentID(long currentID) {
            this.currentID = currentID;
        }

        public long getMaxID() {
            return maxID;
        }

        public void setMaxID(long maxID) {
            this.maxID = maxID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Data data = (Data) o;
            return currentID == data.currentID && maxID == data.maxID;
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentID, maxID);
        }

        @Override
        public String toString() {
            return "Data{" +
                "currentID=" + currentID +
                ", maxID=" + maxID +
                '}';
        }
    }
}
