/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexModifier;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dom4j.DocumentFactory;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLProperties;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Indexes archived conversations. If conversation archiving is not enabled,
 * this class does nothing. The search index is maintained in the <tt>monitoring/search</tt>
 * directory of the Openfire home directory. It's automatically updated with the latest
 * conversation content as long as conversation archiving is enabled. The index update
 * interval is controllec by the Jive property "conversation.search.updateInterval" and
 * the default value is 15 minutes.
 *
 * @see ArchiveSearcher
 * @author Matt Tucker
 */
public class ArchiveIndexer implements Startable {

	private static final Logger Log = LoggerFactory.getLogger(ArchiveIndexer.class);

    private static final String ALL_CONVERSATIONS =
            "SELECT conversationID, isExternal FROM ofConversation";
    private static final String NEW_CONVERSATIONS =
            "SELECT DISTINCT conversationID FROM ofMessageArchive WHERE sentDate > ?";
    private static final String CONVERSATION_METADATA =
            "SELECT isExternal FROM ofConversation WHERE conversationID=?";
    private static final String CONVERSATION_MESSAGES =
            "SELECT conversationID, sentDate, fromJID, toJID, body FROM ofMessageArchive " +
            "WHERE conversationID IN ? ORDER BY conversationID";

    private File searchDir;
    private TaskEngine taskEngine;
    private ConversationManager conversationManager;
    private XMLProperties indexProperties;
    private Directory directory;
    private IndexSearcher searcher;
    private Lock writerLock;
    private boolean stopped = false;

    private boolean rebuildInProgress = false;
    private RebuildFuture rebuildFuture;

    private long lastModified = 0;

    private TimerTask indexUpdater;

    /**
     * Constructs a new archive indexer.
     *
     * @param conversationManager a ConversationManager instance.
     * @param taskEngine a task engine instance.
     */
    public ArchiveIndexer(ConversationManager conversationManager, TaskEngine taskEngine) {
        this.conversationManager = conversationManager;
        this.taskEngine = taskEngine;
    }

    public void start() {
        searchDir = new File(JiveGlobals.getHomeDirectory() +
                    File.separator + MonitoringConstants.NAME + File.separator + "search");
        if (!searchDir.exists()) {
            searchDir.mkdirs();
        }
        boolean indexCreated = false;
        try {
            loadPropertiesFile(searchDir);
            // If the index already exists, use it.
            if (IndexReader.indexExists(searchDir)) {
                directory = FSDirectory.getDirectory(searchDir, false);
            }
            // Otherwise, create a new index.
            else {
                directory = FSDirectory.getDirectory(searchDir, true);
                indexCreated = true;
            }
        }
        catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
        }
        writerLock = new ReentrantLock(true);

        // Force the directory unlocked if it's locked (due to non-clean app shut-down,
        // for example).
        try {
            if (IndexReader.isLocked(directory)) {
                Log.warn("Archiving search index was locked, probably due to non-clean " +
                        "application shutdown.");
                IndexReader.unlock(directory);
            }
        }
        catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
        }

        String modified = indexProperties.getProperty("lastModified");
        if (modified != null) {
            try {
                lastModified = Long.parseLong(modified);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        // If the index has never been updated, build it from scratch.
        if (lastModified == 0 || indexCreated) {
            taskEngine.submit(new Runnable() {
                public void run() {
                    rebuildIndex();
                }
            });
        }

        indexUpdater = new TimerTask() {
            @Override
			public void run() {
                updateIndex();
            }
        };
        int updateInterval = JiveGlobals.getIntProperty("conversation.search.updateInterval", 15);
        taskEngine.scheduleAtFixedRate(indexUpdater, JiveConstants.MINUTE * 5,
                JiveConstants.MINUTE * updateInterval);
    }

    public void stop() {
        stopped = true;
        indexUpdater.cancel();
        if (searcher != null) {
            try {
                searcher.close();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            searcher = null;
        }
        try {
            directory.close();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        directory = null;
        indexProperties = null;
        conversationManager = null;
        searchDir = null;
        rebuildFuture = null;
    }

    /**
     * Returns the total size of the search index (in bytes).
     *
     * @return the total size of the search index (in bytes).
     */
    public long getIndexSize() {
        File [] files = searchDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                // Ignore the index properties file since it's not part of the index.
                return !name.equals("indexprops.xml");
            }
        });
        if (files == null) {
            // Search folder does not exist so size of index is 0
            return 0;
        }
        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    /**
     * Updates the search index with all new conversation data since the last index update.
     */
    public void updateIndex() {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return;
        }
        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return;
        }
        // If we're currently rebuilding the index, return.
        if (rebuildInProgress) {
            return;
        }
        writerLock.lock();
        IndexModifier writer = null;
        try {
            writer = new IndexModifier(directory, new StandardAnalyzer(), false);
            List<Long> conversationIDs = new ArrayList<Long>();
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(NEW_CONVERSATIONS);
                pstmt.setLong(1, lastModified);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    conversationIDs.add(rs.getLong(1));
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }

            // Delete any conversations found -- they may have already been indexed, but
            // updated since then.
            for (long conversationID : conversationIDs) {
                writer.deleteDocuments(new Term("conversationID", Long.toString(conversationID)));
            }

            // Load meta-data for each conversation.
            Map<Long, Boolean> externalMetaData = new HashMap<Long, Boolean>();
            for (long conversationID : conversationIDs) {
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(CONVERSATION_METADATA);
                    pstmt.setLong(1, conversationID);
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        externalMetaData.put(conversationID, rs.getInt(1) == 1);
                    }
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(rs, pstmt, con);
                }
            }

            // Now index all the new conversations.
            long newestDate = indexConversations(conversationIDs, externalMetaData, writer, false);

            writer.optimize();

            // Done indexing so store a last modified date.
            if (newestDate != -1) {
                lastModified = newestDate;
                indexProperties.setProperty("lastModified", Long.toString(lastModified));
            }
        }
        catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
            writerLock.unlock();
        }
    }

    /**
     * Rebuilds the search index with all archived conversation data. This method returns
     * a Future that represents the status of the index rebuild process (also available
     * via {@link #getIndexRebuildProgress()}). The integer value
     * (values 0 through 100) represents the percentage of work done. If message archiving
     * is disabled, this method will return <tt>null</tt>.
     *
     * @return a Future to indicate the status of rebuilding the index or <tt>null</tt> if
     *      rebuilding the index is not possible.
     */
    public synchronized Future<Integer> rebuildIndex() {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return null;
        }
        // If a rebuild is already happening, return.
        if (rebuildInProgress) {
            return null;
        }
        rebuildInProgress = true;
        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return null;
        }

        // Create a future to track the index rebuild progress.
        rebuildFuture = new RebuildFuture();

        // Create a runnable that will perform the actual rebuild work.
        Runnable rebuildTask = new Runnable() {

            public void run() {
                List<Long> conversationIDs = new ArrayList<Long>();
                Map<Long, Boolean> externalMetaData = new HashMap<Long, Boolean>();
                Connection con = null;
                PreparedStatement pstmt = null;
                ResultSet rs = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(ALL_CONVERSATIONS);
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        long conversationID = rs.getLong(1);
                        conversationIDs.add(conversationID);
                        externalMetaData.put(conversationID, rs.getInt(2) == 1);
                    }
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(rs, pstmt, con);
                }

                if (!conversationIDs.isEmpty()) {
                    // Index the conversations.
                    writerLock.lock();
                    IndexModifier writer = null;
                    try {
                        writer = new IndexModifier(directory, new StandardAnalyzer(), true);
                        long newestDate = indexConversations(conversationIDs, externalMetaData,
                                writer, true);
                        writer.optimize();

                        // Done indexing so store a last modified date.
                        if (newestDate != -1) {
                            lastModified = newestDate;
                            indexProperties.setProperty("lastModified", Long.toString(lastModified));
                        }
                    }
                    catch (IOException ioe) {
                        Log.error(ioe.getMessage(), ioe);
                    }
                    finally {
                        if (writer != null) {
                            try {
                                writer.close();
                            }
                            catch (Exception e) {
                                Log.error(e.getMessage(), e);
                            }
                        }
                        writerLock.unlock();
                    }
                }
                // Done rebuilding the index, so reset state.
                rebuildFuture = null;
                rebuildInProgress = false;
            }
        };
        taskEngine.submit(rebuildTask);

        return rebuildFuture;
    }

    /**
     * Returns a Future representing the status of an index rebuild operation. This is the
     * same Future returned by the {@link #rebuildIndex()} method; access is provided via
     * this method as a convenience. If the index is not currently being rebuilt, this method
     * will return <tt>null</tt>.
     *
     * @return a Future that represents the index rebuild status or <tt>null</tt> if the
     *      index is not being rebuilt.
     */
    public Future<Integer> getIndexRebuildProgress() {
        return rebuildFuture;
    }

    /**
     * Indexes a set of conversations. Each conversation is stored as a single Lucene document
     * by appending message bodies together. The date of the newest message indexed is
     * returned, or -1 if no conversations are indexed.
     *
     * @param conversationIDs the ID's of the conversations to index.
     * @param externalMetaData meta-data about whether each conversation involves a participant on
     *      an external server.
     * @param writer an IndexModifier to add the documents to.
     * @param indexRebuild true if this is an index rebuild operation.
     * @return the date of the newest message archived.
     */
    private long indexConversations(List<Long> conversationIDs, Map<Long, Boolean> externalMetaData,
            IndexModifier writer, boolean indexRebuild) throws IOException
    {
        if (conversationIDs.isEmpty()) {
            return -1;
        }

        // Keep track of how many conversations we index for index rebuild stats.
        int indexedConversations = 0;

        long newestDate = -1;
        // Index 250 items at a time.
        final int OP_SIZE = 250;
        int n = ((conversationIDs.size() - 1) / OP_SIZE);
        if (n == 0) {
            n = 1;
        }
        for (int i = 0; i < n; i++) {
            StringBuilder inSQL = new StringBuilder();
            inSQL.append(" (");
            int start = i * OP_SIZE;
            int end = (start + OP_SIZE > conversationIDs.size()) ? conversationIDs.size() : start + OP_SIZE;
            if (end > conversationIDs.size()) {
                end = conversationIDs.size();
            }
            inSQL.append(conversationIDs.get(start));
            for (int j = start + 1; j < end; j++) {
                inSQL.append(", ").append(conversationIDs.get(j));
            }
            inSQL.append(")");
            // Get the messages.
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(CONVERSATION_MESSAGES.replaceAll("\\?", inSQL.toString()));
                rs = pstmt.executeQuery();
                long conversationID = -1;
                long date = -1;
                Set<String> jids = null;
                StringBuilder text = null;
                // Loop through each message. Each conversation is a single document. So, as
                // we find each conversation we save off the last chunk of content as a document.
                while (rs.next()) {
                    long id = rs.getLong(1);
                    if (id != conversationID) {
                        if (conversationID != -1) {
                            // Index the previously defined doc.
                            boolean external = externalMetaData.get(conversationID);
                            indexDocument(writer, conversationID, external, date, jids, text.toString());
                        }
                        // Reset the variables to index the next conversation.
                        conversationID = id;
                        date = rs.getLong(2);
                        jids = new TreeSet<String>();
                        // Get the JID's. Each JID may be stored in full format. We convert
                        // to bare JID for indexing so that searching is possible.
                        jids.add(new JID(rs.getString(3)).toBareJID());
                        jids.add(new JID(rs.getString(4)).toBareJID());
                        text = new StringBuilder();
                    }
                    // Make sure that we record the earliest date of the conversation start
                    // for consistency.
                    long msgDate = rs.getLong(2);
                    if (msgDate < date) {
                        date = msgDate;
                    }
                    // See if this is the newest message found so far.
                    if (msgDate > newestDate) {
                        newestDate = msgDate;
                    }
                    // Add the body of the current message to the buffer.
                    text.append(DbConnectionManager.getLargeTextField(rs, 5)).append("\n");
                }
                // Finally, index the last document found.
                if (conversationID != -1) {
                    // Index the previously defined doc.
                    boolean external = externalMetaData.get(conversationID);
                    indexDocument(writer, conversationID, external, date, jids, text.toString());
                }
                // If this is an index rebuild, we need to track the percentage done.
                if (indexRebuild) {
                    indexedConversations++;
                    rebuildFuture.setPercentageDone(indexedConversations/conversationIDs.size());
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
        }
        return newestDate;
    }

    /**
     * Indexes a single conversation.
     *
     * @param writer the index modifier.
     * @param conversationID the ID of the conversation to index.
     * @param external true if the conversation has a participant from an external server.
     * @param date the date the conversation was started.
     * @param jids the JIDs of the users in the conversation.
     * @param text the full text of the conversation.
     * @throws IOException if an IOException occurs.
     */
    private void indexDocument(IndexModifier writer, long conversationID, boolean external,
            long date, Set<String> jids, String text) throws IOException
    {
        Document document = new Document();
        document.add(new Field("conversationID", String.valueOf(conversationID),
                Field.Store.YES, Field.Index.UN_TOKENIZED));
        document.add(new Field("external", String.valueOf(external),
                Field.Store.YES, Field.Index.UN_TOKENIZED));
        document.add(new Field("date", DateTools.timeToString(date, DateTools.Resolution.DAY),
                Field.Store.YES, Field.Index.UN_TOKENIZED));
        for (String jid : jids) {
            document.add(new Field("jid", jid, Field.Store.YES, Field.Index.TOKENIZED));
        }
        document.add(new Field("text", text, Field.Store.NO, Field.Index.TOKENIZED));
        writer.addDocument(document);
    }

    /**
     * Returns an IndexSearcher to search the archive index.
     *
     * @return an IndexSearcher.
     * @throws IOException if an IOException occurs.
     */
    synchronized IndexSearcher getSearcher() throws IOException {
        // If the searcher hasn't been instantiated, create it.
        if (searcher == null) {
            searcher = new IndexSearcher(directory);
        }
        // See if the searcher needs to be closed due to the index being updated.
        else if (!searcher.getIndexReader().isCurrent()) {
            searcher.close();
            searcher = new IndexSearcher(directory);
        }
        return searcher;
    }

    /**
     * Loads a property manager for search properties if it isn't already
     * loaded. If an XML file for the search properties isn't already
     * created, it will attempt to make a file with default values.
     */
    private void loadPropertiesFile(File searchDir) throws IOException {
        File indexPropertiesFile = new File(searchDir, "indexprops.xml");

        // Make sure the file actually exists. If it doesn't, a new file
        // will be created.
        // If it doesn't exists we have to create it.
        if (!indexPropertiesFile.exists()) {
            org.dom4j.Document doc = DocumentFactory.getInstance().createDocument(
                    DocumentFactory.getInstance().createElement("search"));
            // Now, write out to the file.
            Writer out = null;
            try {
                // Use JDOM's XMLOutputter to do the writing and formatting.
                out = new FileWriter(indexPropertiesFile);
                XMLWriter outputter = new XMLWriter(out, OutputFormat.createPrettyPrint());
                outputter.write(doc);
                outputter.flush();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                }
                catch (Exception e) {
                    // Ignore.
                }
            }
        }
        indexProperties = new XMLProperties(indexPropertiesFile);
    }

    /**
     * A Future class to track the status of index rebuilding.
     */
    private class RebuildFuture implements Future<Integer> {

        private int percentageDone = 0;

        public boolean cancel(boolean mayInterruptIfRunning) {
            // Don't allow cancels.
            return false;
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return percentageDone == 100;
        }

        public Integer get() throws InterruptedException, ExecutionException {
            return percentageDone;
        }

        public Integer get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException
        {
            return percentageDone;
        }

        /**
         * Sets the percentage done.
         *
         * @param percentageDone the percentage done.
         */
        public void setPercentageDone(int percentageDone) {
            if (percentageDone < 0 || percentageDone > 100) {
                throw new IllegalArgumentException("Invalid value: " + percentageDone);
            }
            this.percentageDone = percentageDone;
        }
    }
}