/**
 * $RCSfile$
 * $Revision: 29543 $
 * $Date: 2006-04-19 15:38:04 -0700 (Wed, 19 Apr 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.search;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventDispatcher;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventListener;
import org.jivesoftware.openfire.fastpath.providers.ChatNotes;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Searcher;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.ClassUtils;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.component.Log;
import org.xmpp.packet.JID;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages the transcript search feature by defining properties of the search indexer. Each
 * workgroup will use an instance of this class. Each instance can be configured according to
 * the needs of each workgroup or may just use the global configuration. Read the properties
 * section below to learn the variables that can be configured globaly and per workgroup.<p>
 * <p/>
 * Indexing can either be done real-time by calling updateIndex(boolean) or rebuildIndex(). Out of
 * the box Live Assistant runs the indexer in timed update mode with a queue that holds the
 * generated transcripts since the last update. Once the queue has been filled full an update will
 * be forced even before the time interval has not been completed. It is possible to configure the
 * size of the queue or even disable it and only update the index based on a timed update.<p>
 * <p/>
 * The automated updating mode can be adjusted by setting how often batch indexing is done. You
 * can adjust this interval to suit your needs. Frequent updates mean that transcripts will be
 * searchable more quickly. Less frequent updates use fewer system resources.<p>
 * <p/>
 * The following global properties are used by this class. Global properties will apply to all the
 * workgroups unless the workgroup has overriden the property.
 * <ul>
 * <li><tt>workgroup.search.frequency.execution</tt> -- number of minutes to wait until the next
 * update process is performed. Default is <tt>5</tt> minutes.</li>
 * <li><tt>workgroup.search.pending.transcripts</tt> -- maximum number of transcripts that can be
 * generated since the last update process was executed before forcing the update process to
 * be executed. A value of -1 disables this feature. Default is <tt>5</tt> transcripts.</li>
 * <li><tt>workgroup.search.frequency.optimization</tt> -- number of hours to wait until the next
 * optimization. Default is <tt>24</tt> hours.</li>
 * <li><tt>workgroup.search.analyzer.className</tt> -- name of the Lucene analyzer class to be
 * used for indexing. If none was defined then {@link StandardAnalyzer} will be used.</li>
 * <li><tt>workgroup.search.analyzer.stopWordList</tt> -- String[] of words to use in the global
 * analyzer. If none was defined then the default stop words defined in Lucene will be used.
 * </li>
 * <li><tt>workgroup.search.maxdays</tt> -- maximum number of days a transcript could be old in
 * order to be included when rebuilding the index. Default is <tt>365</tt> days.</li>
 * </ul>
 * <p/>
 * The following workgroup properties are used by this class. Each workgroup has the option to
 * override the corresponding defined global property.
 * <ul>
 * <li><tt>search.analyzer.className</tt> -- name of the Lucene analyzer class to be
 * used for indexing. If none was defined then the value defined in
 * <tt>workgroup.search.analyzer.className</tt> will be used instead.</li>
 * <li><tt>search.analyzer.stopWordList</tt> -- String[] of words to use in the analyzer defined
 * for the workgroup. If none was defined then the default stop words defined in Lucene will
 * be used.</li>
 * <li><tt>search.maxdays</tt> -- maximum number of days a transcript could be old in
 * order to be included when rebuilding the index. If none was defined then the value defined
 * in <tt>workgroup.search.maxdays</tt> will be used.</li>
 * </ul>
 *
 * @author Gaston Dombiak
 */
public class ChatSearchManager implements WorkgroupEventListener {

    private static final String CHATS_SINCE_DATE =
            "SELECT sessionID,transcript,startTime FROM fpSession WHERE workgroupID=? AND " +
                    "startTime>? AND transcript IS NOT NULL ORDER BY startTime";
    private static final String AGENTS_IN_SESSION =
            "SELECT agentJID FROM fpAgentSession WHERE sessionID=?";
    private static final String LOAD_DATES =
            "SELECT lastUpdated,lastOptimization FROM fpSearchIndex WHERE workgroupID=?";
    private static final String INSERT_DATES =
            "INSERT INTO fpSearchIndex(workgroupID, lastUpdated, lastOptimization) VALUES(?,?,?)";
    private static final String UPDATE_DATES =
            "UPDATE fpSearchIndex SET lastUpdated=?,lastOptimization=? WHERE workgroupID=?";
    private static final String DELETE_DATES =
            "DELETE FROM fpSearchIndex WHERE workgroupID=?";

    private static Map<String, ChatSearchManager> instances = new ConcurrentHashMap<String, ChatSearchManager>();

    /**
     * Holds the path to the parent folder of the folders that will store the workgroup
     * index files.
     */
    private static String parentFolder = JiveGlobals.getHomeDirectory() + File.separator + "index";
    private static final long ONE_HOUR = 60 * 60 * 1000;

    /**
     * Hold the workgroup whose chats are being indexed by this instance. Each workgroup will
     * have a ChatSearchManager since each ChatSearchManager may use a different Analyzer according
     * to the workgroup needs.
     */
    private Workgroup workgroup;
    private Analyzer indexerAnalyzer;
    private String searchDirectory;
    private Searcher searcher = null;
    private IndexReader searcherReader = null;
    ReadWriteLock searcherLock = new ReentrantReadWriteLock();

    /**
     * Holds the date of the last chat that was added to the index. This information is used for
     * getting the new chats since this date that should be added to the index.
     */
    private Date lastUpdated;
    /**
     * Keeps the last time when the index was optimized. The index is optimized once a day.
     */
    private Date lastOptimization;
    /**
     * Keeps the last date when the updating process was executed. Every time
     * {@link #updateIndex(boolean)} or {@link #rebuildIndex()} are invoked this variable will
     * be updated.
     */
    private Date lastExecution;
    /**
     * Keeps the number of transcripts that have been generated since the last update process
     * was executed.
     */
    private AtomicInteger pendingTranscripts = new AtomicInteger(0);
    /**
     * Caches the filters for performance. The cached filters will be cleared when the index is
     * modified.
     */
    private ConcurrentHashMap<String, Filter> cachedFilters = new ConcurrentHashMap<String, Filter>();

    static {
        // Check if we need to create the parent folder
        File dir = new File(parentFolder);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdir();
        }
    }

    /**
     * Returns the ChatSearchManager that should be used for a given {@link Workgroup}. The index
     * Analyzer that the returned ChatSearchManager will use could be determined by the workgroup
     * property <tt>search.analyzer.className</tt>. If the workgroup property has not been defined
     * then the global Analyzer will be used.<p>
     * <p/>
     * The class of the global Analyzer can be specified setting the
     * <tt>workgroup.search.analyzer.className</tt> property. If this property does not exist
     * then a {@link StandardAnalyzer} will be used as the global Analyzer..
     *
     * @param workgroup the workgroup to index.
     * @return the ChatSearchManager that should be used for a given workgroup.
     */
    public static ChatSearchManager getInstanceFor(Workgroup workgroup) {
        String workgroupName = workgroup.getJID().getNode();
        ChatSearchManager answer = instances.get(workgroupName);
        if (answer == null) {
            synchronized (workgroupName.intern()) {
                answer = instances.get(workgroupName);
                if (answer == null) {
                    answer = new ChatSearchManager(workgroup);
                    instances.put(workgroupName, answer);
                }
            }
        }
        return answer;
    }

    /**
     * Returns the Lucene analyzer class that is be used for indexing. The analyzer class
     * name is stored as the Jive Property <tt>workgroup.search.analyzer.className</tt>.
     *
     * @return the name of the analyzer class that is used for indexing.
     */
    public static String getAnalyzerClass() {
        String analyzerClass = JiveGlobals.getProperty("workgroup.search.analyzer.className");
        if (analyzerClass == null) {
            return StandardAnalyzer.class.getName();
        }
        else {
            return analyzerClass;
        }
    }

    /**
     * Sets the Lucene analyzer class that is used for indexing. Anytime the analyzer class
     * is changed, the search index must be rebuilt for searching to work reliably. The analyzer
     * class name is stored as the Jive Property <tt>workgroup.search.analyzer.className</tt>.
     *
     * @param className the name of the analyzer class will be used for indexing.
     */
    public static void setAnalyzerClass(String className) {
        if (className == null) {
            throw new NullPointerException("Argument is null.");
        }
        // If the setting hasn't changed, do nothing.
        if (className.equals(getAnalyzerClass())) {
            return;
        }
        JiveGlobals.setProperty("workgroup.search.analyzer.className", className);
    }

    /**
     * Notification message saying that the workgroup service is being shutdown. Release all
     * the instances so the GC can claim all the workgroup objects.
     */
    public static void shutdown() {
        for (ChatSearchManager manager : instances.values()) {
             manager.stop();
        }
        instances.clear();
    }

    private void stop() {
        WorkgroupEventDispatcher.removeListener(this);
    }

    /**
     * Returns the number of minutes to wait until the next update process is performed. The update
     * process may be executed before the specified frequency if a given number of transcripts
     * have been generated since the last execution. The maximum number of transcripts that can
     * be generated before triggering the update process is specified by
     * {@link #getMaxPendingTranscripts()}.
     */
    private static int getExecutionFrequency() {
        return JiveGlobals.getIntProperty("workgroup.search.frequency.execution", 5);
    }

    /**
     * Returns the maximum number of transcripts that can be generated since the last update
     * process was executed before forcing the update process to be executed. If the returned
     * value is <= 0 then this functionality will be ignored.<p>
     * <p/>
     * In summary, the update process runs periodically but it may be force to be executed
     * if a certain number of transcripts have been generated since the last update execution.
     *
     * @return the maximum number of transcripts that can be generated since the last update
     *         process was executed.
     */
    private static int getMaxPendingTranscripts() {
        return JiveGlobals.getIntProperty("workgroup.search.pending.transcripts", 5);
    }

    /**
     * Returns the number of hours to wait until the next optimization. Optimizing the index makes
     * the searches faster and reduces the number of files too.
     */
    private static int getOptimizationFrequency() {
        return JiveGlobals.getIntProperty("workgroup.search.frequency.optimization", 24);
    }

    ChatSearchManager(Workgroup workgroup) {
        this.workgroup = workgroup;
        searchDirectory = parentFolder + File.separator + workgroup.getJID().getNode();
        loadAnalyzer();
        loadLastUpdated();
        WorkgroupEventDispatcher.addListener(this);
    }

    /**
     * Load the search analyzer. A custom analyzer class will be used if it is defined.
     */
    private void loadAnalyzer() {
        Analyzer analyzer = null;

        String analyzerClass = null;
        String words = null;
        // First check if the workgroup should use a special Analyzer
        analyzerClass = workgroup.getProperties().getProperty("search.analyzer.className");
        if (analyzerClass != null) {
            words = workgroup.getProperties().getProperty("search.analyzer.stopWordList");
        }
        else {
            // Use the global analyzer
            analyzerClass = getAnalyzerClass();
            words = JiveGlobals.getProperty("workgroup.search.analyzer.stopWordList");
        }

        // get stop word list is there was one
        List stopWords = new ArrayList();
        if (words != null) {
            StringTokenizer st = new StringTokenizer(words, ",");
            while (st.hasMoreTokens()) {
                stopWords.add(st.nextToken().trim());
            }
        }
        try {
            analyzer = getAnalyzerInstance(analyzerClass, stopWords);
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error("Error loading custom " +
                    "search analyzer: " + analyzerClass, e);
        }
        // If the analyzer is null, use the standard analyzer.
        if (analyzer == null && stopWords.size() > 0) {
            analyzer = new StandardAnalyzer((String[])stopWords.toArray(new String[stopWords.size()]));
        }
        else if (analyzer == null) {
            analyzer = new StandardAnalyzer();
        }

        indexerAnalyzer = analyzer;
    }

    private Analyzer getAnalyzerInstance(String analyzerClass, List stopWords) throws Exception {
        Analyzer analyzer = null;
        // Load the class.
        Class c = null;
        try {
            c = ClassUtils.forName(analyzerClass);
        }
        catch (ClassNotFoundException e) {
            c = getClass().getClassLoader().loadClass(analyzerClass);
        }
        // Create an instance of the custom analyzer.
        if (stopWords.size() > 0) {
            Class[] params = new Class[]{String[].class};
            try {
                Constructor constructor = c.getConstructor(params);
                Object[] initargs = {(String[])stopWords.toArray(new String[stopWords.size()])};
                analyzer = (Analyzer)constructor.newInstance(initargs);
            }
            catch (NoSuchMethodException e) {
                // no String[] parameter to the constructor
                analyzer = (Analyzer)c.newInstance();
            }
        }
        else {
            analyzer = (Analyzer)c.newInstance();
        }

        return analyzer;
    }

    private void loadLastUpdated() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet result = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_DATES);
            pstmt.setLong(1, workgroup.getID());
            result = pstmt.executeQuery();
            while (result.next()) {
                lastUpdated = new Date(Long.parseLong(result.getString(1)));
                lastOptimization = new Date(Long.parseLong(result.getString(2)));
                lastExecution = lastUpdated;
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }

            try {
                if (result != null) {
                    result.close();
                }
            }
            catch (SQLException e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }
    }

    /**
     * Deletes the existing index and creates it again indexing the chats that took place
     * since a given date. The lower limit date is calculated as the max number of days since a
     * chat took place. There is a global property that holds the max number of days as well as
     * a workgroup property that may redefine the default global value.
     *
     * @throws IOException if the directory cannot be read/written to, or there is a problem
     *                     adding a document to the index.
     */
    public synchronized void rebuildIndex() throws IOException {
        // Calculate the max number of days based on the defined properties
        int numDays = Integer.parseInt(JiveGlobals.getProperty("workgroup.search.maxdays", "365"));
        String workgroupDays = workgroup.getProperties().getProperty("search.maxdays");
        if (workgroupDays != null) {
            numDays = Integer.parseInt(workgroupDays);
        }
        Calendar since = Calendar.getInstance();
        since.add(Calendar.DATE, numDays * -1);

        // Get the chats that took place since the specified date and add them to the index
        rebuildIndex(since.getTime());
    }

    /**
     * Updates the index file with new chats that took place since the last added chat to the
     * index. If the index file is missing or a chat was never added to the index file then
     * {@link #rebuildIndex} will be used instead.
     *
     * @param forceUpdate true if the index should be updated despite of the execution frequency.
     * @throws IOException if the directory cannot be read/written to, or it does not exist, or
     *                     there is a problem adding a document to the index.
     */
    public synchronized void updateIndex(boolean forceUpdate) throws IOException {
        // Check that the index files exist
        File dir = new File(searchDirectory);
        boolean create = !dir.exists() || !dir.isDirectory();
        if (lastUpdated == null || create) {
            // Recreate the index since it was never created or the index files disappeared
            rebuildIndex();
        }
        else {
            if (forceUpdate || (System.currentTimeMillis() - lastExecution.getTime()) / 60000 > getExecutionFrequency()) {
                List<ChatInformation> chatsInformation = getChatsInformation(lastUpdated);
                if (!chatsInformation.isEmpty()) {
                    // Reset the number of transcripts pending to be added to the index
                    pendingTranscripts.set(0);
                    Date lastDate = null;
                    IndexWriter writer = getWriter(false);
                    for (ChatInformation chat : chatsInformation) {
                        addTranscriptToIndex(chat, writer);
                        lastDate = chat.getCreationDate();
                    }
                    // Check if we need to optimize the index. The index is optimized once a day
                    if ((System.currentTimeMillis() - lastOptimization.getTime()) / ONE_HOUR >
                            getOptimizationFrequency()) {
                        writer.optimize();
                        // Update the optimized date
                        lastOptimization = new Date();
                    }
                    writer.close();
                    closeSearcherReader();
                    // Reset the filters cache
                    cachedFilters.clear();
                    // Update the last updated date
                    lastUpdated = lastDate;
                    // Save the last updated and optimized dates to the database
                    saveDates();
                }
                // Update the last time the update process was executed
                lastExecution = new Date();
            }
        }
    }

    public void delete() {
        try {
            searcherLock.writeLock().lock();
            try {
                closeSearcherReader();
            }
            catch (IOException e) {
                // Ignore.
            }
            // Delete index files
            String[] files = new File(searchDirectory).list();
            for (int i = 0; i < files.length; i++) {
                File file = new File(searchDirectory, files[i]);
                file.delete();
            }
            new File(searchDirectory).delete();
            // Delete dates from the database
            deleteDates();
            // Remove this instance from the list of instances
            instances.remove(workgroup.getJID().getNode());
            // Remove this instance as a listener of the workgroup events
            WorkgroupEventDispatcher.removeListener(this);
        }
        finally {
            searcherLock.writeLock().unlock();
        }

    }

    /**
     * Returns a Lucene Searcher that can be used to execute queries. Lucene
     * can handle index reading even while updates occur. However, in order
     * for index changes to be reflected in search results, the reader must
     * be re-opened whenever the modificationDate changes.<p>
     * <p/>
     * The location of the index is the "index" subdirectory in [jiveHome].
     *
     * @return a Searcher that can be used to execute queries.
     */
    public Searcher getSearcher() throws IOException {
        synchronized (indexerAnalyzer) {
            if (searcherReader == null) {
                if (searchDirectory != null && IndexReader.indexExists(searchDirectory)) {
                    searcherReader = IndexReader.open(searchDirectory);
                    searcher = new IndexSearcher(searcherReader);
                }
                else {
                    // Log warnings.
                    if (searchDirectory == null) {
                        ComponentManagerFactory.getComponentManager().getLog().warn("Search " +
                                "directory not set, you must rebuild the index.");
                    }
                    else if (!IndexReader.indexExists(searchDirectory)) {
                        ComponentManagerFactory.getComponentManager().getLog().warn("Search " +
                                "directory " + searchDirectory + " does not appear to " +
                                "be a valid search index. You must rebuild the index.");
                    }
                    return null;
                }
            }
        }
        return searcher;
    }

    Analyzer getAnalyzer() {
        return indexerAnalyzer;
    }

    void putFilter(String key, Filter filter) {
        cachedFilters.put(key, filter);
    }

    Filter getFilter(String key) {
        return cachedFilters.get(key);
    }

    /**
     * Closes the reader used by the searcher to indicate that a change to the index was made.
     * A new searcher will be opened the next time one is requested.
     *
     * @throws IOException if an error occurs while closing the reader.
     */
    private void closeSearcherReader() throws IOException {
        if (searcherReader != null) {
            try {
                searcherLock.writeLock().lock();
                searcherReader.close();
            }
            finally {
                searcherReader = null;
                searcherLock.writeLock().unlock();
            }
        }
    }

    /**
     * Returns information about the chats that took place since a given date. The result is
     * sorted from oldest chats to newest chats.
     *
     * @param since the date to use as the lower limit.
     * @return information about the chats that took place since a given date.
     */
    private List<ChatInformation> getChatsInformation(Date since) {
        List<ChatInformation> chats = new ArrayList<ChatInformation>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet result = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CHATS_SINCE_DATE);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, StringUtils.dateToMillis(since));
            result = pstmt.executeQuery();
            while (result.next()) {
                String sessionID = result.getString(1);
                String transcript = result.getString(2);
                String startTime = result.getString(3);

                ChatNotes chatNotes = new ChatNotes();
                String notes = chatNotes.getNotes(sessionID);

                // Create a ChatInformation with the retrieved information
                ChatInformation chatInfo = new ChatInformation(sessionID, transcript, startTime, notes);
                if (chatInfo.getTranscript() != null) {
                    chats.add(chatInfo);
                }
            }
            result.close();

            // For each ChatInformation add the agents involved in the chat
            for (ChatInformation chatInfo : chats) {
                pstmt.close();
                pstmt = con.prepareStatement(AGENTS_IN_SESSION);
                pstmt.setString(1, chatInfo.getSessionID());
                result = pstmt.executeQuery();
                while (result.next()) {
                    chatInfo.getAgentJIDs().add(result.getString(1));
                }
                result.close();
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);

            // Reset the answer if an error happened
            chats = new ArrayList<ChatInformation>();
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }


            try {
                if (result != null) {
                    result.close();
                }
            }
            catch (SQLException e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }

            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }

            try {
                if (result != null) {
                    result.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }
        // Return the chats order by startTime
        return chats;
    }

    /**
     * Retrieves information about each transcript that took place since the specified date and
     * adds it to the index.<p>
     * <p/>
     * Note: In order to cope with large volumes of data we don't want to load
     * all the information into memory. Therefore, for each retrieved row we create a
     * ChatInformation instance and add it to the index.
     *
     * @param since the date to use as the lower limit.
     * @throws IOException if rebuilding the index fails.
     */
    private void rebuildIndex(Date since) throws IOException {
        Date lastDate = null;
        IndexWriter writer = getWriter(true);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet result = null;
        try {
            // TODO Review logic for JDBC drivers that load all the answer into memory
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CHATS_SINCE_DATE);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, StringUtils.dateToMillis(since));
            result = pstmt.executeQuery();
            while (result.next()) {
                String sessionID = result.getString(1);
                String transcript = result.getString(2);
                String startTime = result.getString(3);
                String chatNotes = new ChatNotes().getNotes(sessionID);
                ChatInformation chatInfo = new ChatInformation(sessionID, transcript, startTime, chatNotes);

                if (chatInfo.getTranscript() != null) {
                    addAgentHistoryToChatInformation(chatInfo);

                    // Add the ChatInformation to the index
                    addTranscriptToIndex(chatInfo, writer);
                    lastDate = chatInfo.getCreationDate();
                }
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
            // Reset the lastDate if an error happened
            lastDate = null;
        }
        finally {
            try {
                if (result != null) {
                    result.close();
                }
            }
            catch (SQLException e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }

            DbConnectionManager.closeConnection(pstmt, con);
        }
        writer.optimize();
        writer.close();
        if (lastDate != null) {
            closeSearcherReader();
            // Reset the filters cache
            cachedFilters.clear();
            // Update the last updated and optimized dates
            lastOptimization = new Date();
            lastUpdated = lastDate;
            lastExecution = new Date();
            pendingTranscripts.set(0);
            // Save the last updated and optimized dates to the database
            saveDates();
        }
    }

    private void addAgentHistoryToChatInformation(ChatInformation chatInfo) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet result = null;
        try {
            // Add the agents involved in the chat to the ChatInformation
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(AGENTS_IN_SESSION);
            pstmt.setString(1, chatInfo.getSessionID());
            result = pstmt.executeQuery();
            while (result.next()) {
                chatInfo.getAgentJIDs().add(result.getString(1));
            }
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            if (result != null) {
                try {
                    result.close();
                }
                catch (SQLException e) {
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
                }
            }

            DbConnectionManager.closeConnection(pstmt, con);
        }

    }

    /**
     * Update the dates of this indexer in the database
     */
    private void saveDates() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_DATES);
            pstmt.setString(1, StringUtils.dateToMillis(lastUpdated));
            pstmt.setString(2, StringUtils.dateToMillis(lastOptimization));
            pstmt.setLong(3, workgroup.getID());
            boolean updated = pstmt.executeUpdate() > 0;

            // If the row was not updated (because it doesn't exist) then insert a new row
            if (!updated) {
                pstmt.close();
                pstmt = con.prepareStatement(INSERT_DATES);
                pstmt.setLong(1, workgroup.getID());
                pstmt.setString(2, StringUtils.dateToMillis(lastUpdated));
                pstmt.setString(3, StringUtils.dateToMillis(lastOptimization));
                pstmt.executeUpdate();
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }
    }

    /**
     * Update the dates of this indexer in the database
     */
    private void deleteDates() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_DATES);
            pstmt.setLong(1, workgroup.getID());
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                ComponentManagerFactory.getComponentManager().getLog().error(e);
            }
        }
    }

    private void addTranscriptToIndex(ChatInformation chat, IndexWriter writer) throws IOException {
        // Flag that indicates if the transcript includes one or more messages. If no message was
        // found then nothing will be added to the index
        boolean hasMessages = false;
        Document document = new Document();

        for (Iterator elements = chat.getTranscript().elementIterator(); elements.hasNext();) {
            Element element = (Element)elements.next();
            // Only add Messages to the index (Presences are discarded)
            if ("message".equals(element.getName())) {
                // TODO Index XHTML bodies?
                String body = element.elementTextTrim("body");
                String from = element.attributeValue("from");
                String to = element.attributeValue("to");

                String fromNickname = new JID(from).getResource();
                String toNickname = new JID(to).getResource();

                final StringBuilder builder = new StringBuilder();
                builder.append(body);
                builder.append(" ");
                builder.append(fromNickname);
                builder.append(" ");
                builder.append(toNickname);

                if (body != null) {
                    if (chat.getNotes() != null) {
                        builder.append(" ");
                        builder.append(chat.getNotes());
                    }

                    if (chat.getAgentJIDs() != null) {
                        for (String jid : chat.getAgentJIDs()) {
                            builder.append(" ");
                            builder.append(jid);
                        }
                    }
                    document.add(new Field("body", builder.toString(), Field.Store.NO,
                            Field.Index.TOKENIZED));
                    // Indicate that a message was found
                    hasMessages = true;
                }
            }
        }
        if (hasMessages) {
            // Add the sessionID that indentifies the chat session to the document
            document.add (new Field("sessionID", String.valueOf(chat.getSessionID()),
                    Field.Store.YES, Field.Index.UN_TOKENIZED));
            // Add the JID of the agents involved in the chat to the document
            for (String agentJID : chat.getAgentJIDs()) {
                document.add(new Field("agentJID", agentJID,
                        Field.Store.YES, Field.Index.UN_TOKENIZED));
            }
            // Add the date when the chat started to the document
            long date = chat.getCreationDate().getTime();
            document.add(new Field("creationDate",  DateTools.timeToString(date, 
                    DateTools.Resolution.DAY), Field.Store.YES, Field.Index.UN_TOKENIZED));

            writer.addDocument(document);
        }
    }

    /**
     * Returns a Lucene IndexWriter. The create param indicates whether an
     * existing index should be used if it's found there.
     */
    private IndexWriter getWriter(boolean create) throws IOException {
        IndexWriter writer = new IndexWriter(searchDirectory, indexerAnalyzer, create);
        return writer;
    }

    // ###############################################################################
    // WorkgroupEventListener implemented methods
    // ###############################################################################
    public void workgroupCreated(Workgroup workgroup) {
        //Do nothing
    }

    public void workgroupDeleting(Workgroup workgroup) {
        //Do nothing
    }

    public void workgroupDeleted(Workgroup workgroup) {
        // Do nothing if the notification is related to other workgroup
        if (this.workgroup != workgroup) {
            return;
        }
        delete();
    }

    public void workgroupOpened(Workgroup workgroup) {
        //Do nothing
    }

    public void workgroupClosed(Workgroup workgroup) {
        //Do nothing
    }

    public void agentJoined(Workgroup workgroup, AgentSession agentSession) {
        //Do nothing
    }

    public void agentDeparted(Workgroup workgroup, AgentSession agentSession) {
        //Do nothing
    }

    public void chatSupportStarted(Workgroup workgroup, String sessionID) {
        //Do nothing
    }

    public void chatSupportFinished(Workgroup workgroup, String sessionID) {
        // Do nothing if the notification is related to other workgroup
        if (this.workgroup != workgroup) {
            return;
        }
        // Update the number of generated transcripts since the last update process was executed
        // If the maximum number of pending transcripts has been reached then force an update of
        // the index
        if (getMaxPendingTranscripts() > 0 &&
                pendingTranscripts.incrementAndGet() == getMaxPendingTranscripts())
        {
            // Update in another thread
            TaskEngine.getInstance().submit(new Runnable() {
                public void run() {
                    try {
                        updateIndex(true);
                    }
                    catch (IOException e) {
                        ComponentManagerFactory.getComponentManager().getLog().error(e);
                    }
                }
            });
        }
    }

    public void agentJoinedChatSupport(Workgroup workgroup, String sessionID,
                                       AgentSession agentSession) {
        //Do nothing
    }

    public void agentLeftChatSupport(Workgroup workgroup, String sessionID,
                                     AgentSession agentSession) {
        //Do nothing
    }

    /**
     * Class that holds information about a chat. Having this class avoids having to pass
     * all the chat information as parameters across the methods.
     */
    class ChatInformation {

        private String sessionID;
        private Date creationDate;
        private Element transcript;
        private List<String> agentJIDs;
        private String notes;

        public ChatInformation(String sessionID, String transcriptXML, String startTime, String notes) {
            this.sessionID = sessionID;
            try {
                this.transcript = DocumentHelper.parseText(transcriptXML).getRootElement();
            }
            catch (DocumentException e) {
                Log log = ComponentManagerFactory.getComponentManager().getLog();
                log.error("Error retrieving chat information of session: " + sessionID, e);
                log.debug("Error retrieving chat information of session: " + sessionID +
                        " and transcript: " + transcriptXML, e);
            }
            this.creationDate = new Date(Long.parseLong(startTime));
            agentJIDs = new ArrayList<String>();

            this.notes = notes;
        }

        public String getSessionID() {
            return sessionID;
        }

        public Date getCreationDate() {
            return creationDate;
        }

        public Element getTranscript() {
            return transcript;
        }

        public List<String> getAgentJIDs() {
            return agentJIDs;
        }

        public String getNotes() {
            return notes;
        }
    }
}
