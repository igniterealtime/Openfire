/**
 * $RCSfile$
 * $Revision: 28440 $
 * $Date: 2006-03-10 17:01:41 -0800 (Fri, 10 Mar 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.xmpp.packet.JID;

/**
 * Encapsulates a search for transcripts in a workgroup. Use the public constructor
 * {@link #ChatSearch(Workgroup, Date, Date, JID, String)} to create a search where
 * the Workgroup parameter defines the workgroup where the search will be performed.
 * You can mix and match the constructor parameters depending on what kind of query
 * you'd like to perform.<p>
 *
 * The result is composed of {@link QueryResult} that hold basic information about
 * the transcript. You can use {@link QueryResult#getSessionID()} for locating
 * the original session that generated the chat transcript.<p>
 *
 * Below is a code sample that performs a search:<p>
 *
 * <pre>
 * ChatSearch search = new ChatSearch(workgroup, null, new Date(), null, "Jive is cool");
 * for (QueryResult result : search.getResults()) {
 *     Map<String, Object> fields = new LinkedHashMap<String, Object>();
 *     fields.put("sessionID", result.getSessionID());
 *     fields.put("startDate", result.getStartDate());
 *     fields.put("agentJIDs", result.getAgentJIDs());
 *     fields.put("relevance", Float.valueOf(result.getRelevance()));
 *     searchResults.addItemFields(fields);
 * }
 * </pre><p>
 *
 * The following workgroup properties are used by this class:
 * <ul>
 *  <li><tt>workgroup.search.maxResultsSize</tt> -- max number of results to include in the search
 *      answer. Default is <tt>500</tt>.</li>
 *  <li><tt>workgroup.search.wildcardIgnored</tt> -- Determine if wildcards should be ignored.
 *      Default is <tt>false</tt>.</li>
 *  <li><tt>workgroup.search.defaultOperator</tt> -- Boolean operator of the QueryParser. Default
 *      is <tt>OR</tt>.</li>
 * </ul>
 *
 * @author Gaston Dombiak
 */
public class ChatSearch {

    // private static final int MAX_RESULTS_SIZE;
    /**
     * Indicates whether wildcards should be enabled or ignored in searches.
     */
    private static boolean wildcardIgnored = false;

    private Date afterDate;
    private Date beforeDate;
    private Workgroup workgroup;
    private JID agentJID;
    private String queryString;
    /**
     * The results of the query.
     */
    private transient List<QueryResult> results = new ArrayList<QueryResult>();

    static {
//        // Load a custom max results size value from the Jive property file or
//        // default to 500.
//        int maxSize = 500;
//        String maxResultsSize = JiveGlobals.getProperty("workgroup.search.maxResultsSize");
//        if (maxResultsSize != null) {
//            try {
//                maxSize = Integer.parseInt(maxResultsSize);
//            }
//            catch (NumberFormatException nfe) {
//                // Ignore.
//            }
//        }
//        MAX_RESULTS_SIZE = maxSize;
        // Determine if wildcards should be ignored
        wildcardIgnored = JiveGlobals.getBooleanProperty("workgroup.search.wildcardIgnored");
    }

    public static boolean isWildcardIgnored() {
        return wildcardIgnored;
    }

    public static void setWildcardIgnored(boolean value) {
        wildcardIgnored = value;
        JiveGlobals.setProperty("workgroup.search.wildcardIgnored", "" + value);
    }

    /**
     * Creates a search on the specified workgroup. The only required parameters are
     * <tt>workgroup</tt> and <tt>queryString</tt>.
     *
     * @param workgroup Workgroup where the search will be performed.
     * @param from min date for the transcripts to include in the search. Optional parameter.
     * @param upto max date for the transcripts to include in the search. Optional parameter.
     * @param agentJID JID of the agent involved in the transcripts to include. Optional parameter.
     * @param queryString String that the transcripts should have.
     */
    public ChatSearch(Workgroup workgroup, Date from, Date upto, JID agentJID, String queryString) {
        this.afterDate = from;
        this.beforeDate = upto;
        this.workgroup = workgroup;
        this.agentJID = agentJID;
        this.queryString = wildcardIgnored ? stripWildcards(queryString) : queryString;

        // we lowercase all queries because Wildcard, Prefix, and Fuzzy queries are case sensitive.
        // (http://www.jguru.com/faq/view.jsp?EID=538312)
        // however, we need to make sure that AND and OR terms are left uppercase
        // (Otherwise they do not work)
        if (!wildcardIgnored && containsWildcards(queryString)) {
            this.queryString = lowerCaseQueryString(this.queryString);
        }
    }

    public Date getAfterDate() {
        return afterDate;
    }

    public Date getBeforeDate() {
        return beforeDate;
    }

    public Workgroup getWorkgroup() {
        return workgroup;
    }

    public JID getAgentJID() {
        return agentJID;
    }

    public String getQueryString() {
        return queryString;
    }

    public List<QueryResult> getResults() {
        if (getQueryString() == null) {
            return Collections.emptyList();
        }

        if (results.isEmpty()) {
            executeQuery();
        }

        if (results.size() > 1) {
            //Collections.sort(results, new QueryResultComparator(this));
        }

        return Collections.unmodifiableList(results);
    }

    public List<QueryResult> getResults(int startIndex, int numResults){
        if (startIndex < 0 || numResults < 0) {
            throw new IllegalArgumentException("Parameter value can't be less than 0.");
        }

        if (getQueryString() == null) {
            return Collections.emptyList();
        }

        if (results.isEmpty()) {
            executeQuery();
        }

        int endIndex = startIndex + numResults - 1;
        if (endIndex > results.size() - 1) {
            endIndex = results.size() - 1;
        }

        if (((endIndex - startIndex) + 1) <= 0) {
            return Collections.emptyList();
        }
        else {
            if (results.size() > 1) {
                //Collections.sort(results, new QueryResultComparator(this));
            }

            return Collections.unmodifiableList(results.subList(startIndex, endIndex + 1));
        }
    }

    /**
     * Execute the query and store the results in the results array.
     */
    private void executeQuery() {
        // TODO: FIX THIS CODE!
        /*ChatSearchManager searchManager = ChatSearchManager.getInstanceFor(workgroup);

        try {
            // Acquire a read lock on the searcher lock so we know the query will be completed
            // correctly.
            searchManager.searcherLock.readLock().lock();
            Searcher searcher = searchManager.getSearcher();
            if (searcher == null) {
                // Searcher can be null if the index doesn't exist.
                results.clear();
                return;
            }

            Query query = null;
            if (queryString == null || queryString.equals("")) {
                results.clear();
                return;
            }
            else {
                String cleanQueryString = escapeBadCharacters(queryString);

                // Create the QueryParser instance
                QueryParser bodyQP = new QueryParser("body", searchManager.getAnalyzer());

                // Override the default OR operator, if set
                if ("AND".equals(JiveGlobals.getProperty("workgroup.search.defaultOperator"))) {
                    bodyQP.setOperator(QueryParser.DEFAULT_OPERATOR_AND);
                }

                query = bodyQP.parse(cleanQueryString);
            }


            MultiFilter multiFilter = new MultiFilter(2);
            int filterCount = 0;

            // Date filter
            if (beforeDate != null || afterDate != null) {
                if (beforeDate != null && afterDate != null) {
                    String key = "bDate:" + beforeDate.getTime() + ":aDate:" + afterDate.getTime();
                    Filter filter = (Filter) searchManager.getFilter(key);
                    if (filter == null) {
                        filter = new CachingWrapperFilter(new DateFilter("creationDate", beforeDate, afterDate));
                        searchManager.putFilter(key, filter);
                    }
                    multiFilter.add(filter);
                    filterCount++;
                }
                else if (beforeDate == null) {
                    String key = "aDate:" + afterDate.getTime();
                    Filter filter = (Filter) searchManager.getFilter(key);
                    if (filter == null) {
                        filter = new CachingWrapperFilter(DateFilter.After("creationDate", afterDate));
                        searchManager.putFilter(key, filter );
                    }
                    multiFilter.add(filter);
                    filterCount++;
                }
                else {
                    String key = "bDate:" + beforeDate.getTime();
                    Filter filter = (Filter) searchManager.getFilter(key);
                    if (filter == null) {
                        filter = new CachingWrapperFilter(DateFilter.Before("creationDate", beforeDate));
                        searchManager.putFilter(key, filter );
                    }
                    multiFilter.add(filter);
                    filterCount++;
                }
            }

            // Agent filter
            if (agentJID != null) {
                String bareJID = agentJID.toBareJID();
                String key = "agentJID:" + bareJID;
                Filter filter = (Filter) searchManager.getFilter(key);
                if (filter == null) {
                    filter = new CachingWrapperFilter(new FieldFilter("agentJID", "" + bareJID));
                    searchManager.putFilter(key, filter);
                }
                multiFilter.add(filter);
                filterCount++;
            }

            Hits hits;
            // Only apply filters if any are defined.
            if (filterCount > 0) {
                hits = searcher.search(query, multiFilter);
            }
            else {
                hits = searcher.search(query);
            }

            // Don't return more search results than the maximum number allowed.
            int numResults = hits.length() < MAX_RESULTS_SIZE ? hits.length() : MAX_RESULTS_SIZE;

            for (int i = 0; i < numResults; i++) {
                try {
                    Document doc = hits.doc(i);
                    String sessionID = doc.get("sessionID");
                    Date createDate = DateField.stringToDate(doc.get("creationDate"));
                    String[] jids = doc.getValues("agentJID");
                    List<String> agentJIDs = jids == null ?
                            Collections.EMPTY_LIST : Arrays.asList(jids);
                    QueryResult result = new QueryResult(workgroup, sessionID, createDate,
                            agentJIDs, hits.score(i));
                    results.add(result);
                }
                catch (NumberFormatException e) {
                    Log.error(e.getMessage(), e);
                }
            }

            hits = null;
        }
        catch (ParseException e) {
            Log.error("Search failure - " +
                    "lucene error parsing query: " + queryString, e);
            results.clear();
        }
        catch(Exception e) {
            Log.error(e.getMessage(), e);
            results.clear();
        }
        finally {
            searchManager.searcherLock.readLock().unlock();
        } */
    }

    private String stripWildcards(String string) {
        string = StringUtils.replace(string, "*", "");
        string = StringUtils.replace(string, "?", "");
        string = StringUtils.replace(string, "~", "");
        return string;
    }

    private boolean containsWildcards(String string) {
        return string.indexOf('?') != -1 || string.indexOf('*') != -1 || string.indexOf('~') != -1;
    }

//    private String escapeBadCharacters(String queryString) {
//        return StringUtils.replace(queryString, ":", "\\:");
//    }

    private String lowerCaseQueryString(String queryString) {
        char[] chars = queryString.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (c == 'A') {
                    // leave uppercase if standalone "AND"
                    if (i > 0 && i + 3 < chars.length &&
                            !Character.isLetterOrDigit(chars[i-1]) &&
                            !Character.isLetterOrDigit(chars[i+3]) &&
                            (chars[i+1] == 'n' || chars[i+1] == 'N') &&
                            (chars[i+2] == 'd' || chars[i+2] == 'D'))
                    {
                        chars[i+1] = Character.toUpperCase(chars[i+1]);
                        chars[i+2] = Character.toUpperCase(chars[i+2]);
                        i += 2;
                        continue;
                    }
                }
                else if (c == 'O') {
                     // leave uppercase if standalone "OR"
                    if (i > 0 && i + 2 < chars.length &&
                            !Character.isLetterOrDigit(chars[i-1]) &&
                            !Character.isLetterOrDigit(chars[i+2]) &&
                            (chars[i+1] == 'r' || chars[i+1] == 'R'))
                    {
                        chars[i+1] = Character.toUpperCase(chars[i+1]);
                        i++;
                        continue;
                    }
                }

                chars[i] = Character.toLowerCase(c);
            }
        }

        return new String(chars);
    }
}