/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * Defines a search query for use with an {@link ArchiveSearcher}. In general, there
 * are two types of searches that users might perform:<ul>
 *
 *      <li>Query string search: search conversations for specific keywords, optionally
 *          filtering the results by conversation participants or date range.
 *      <li>Meta-data search: find all conversations by certain users or within a certain
 *          date range. This search is typical for compliance purposes.
 *
 * @author Matt Tucker
 */
public class ArchiveSearch {

    /**
     * An integer value that represents NULL. The actual value is
     * Integer.MAX_VALUE - 123 (an arbitrary number that has a very low
     * probability of actually being selected by a user as a valid value).
     */
    public static final int NULL_INT = Integer.MAX_VALUE - 123;

    private String queryString;
    private Collection<JID> participants = Collections.emptyList();
    /**
     * Start of conversation has to be bigger or equal to this value (if set)
     */
    private Date dateRangeMin;
    /**
     * Start of conversation has to be smaller or equal to this value (if set)
     */
    private Date dateRangeMax;
    /**
     * Specified timestamp has to be between start and last activity dates
     */
    private Date includeTimestamp;
    private JID room;
    private int startIndex = 0;
    private int numResults = NULL_INT;
    private SortField sortField;
    private SortOrder sortOrder;
    private boolean externalWildcardMode;

    /**
     * Creates a new search on a query string.
     *
     * @param queryString the query string to use for the search.
     * @return an ArchiveSearch instance to search using the specified query string.
     */
    public static ArchiveSearch createKeywordSearch(String queryString) {
        ArchiveSearch search = new ArchiveSearch();
        search.setQueryString(queryString);
        search.setSortField(SortField.relevance);
        return search;
    }

    /**
     * Constructs a new archive search, sorted on date descending.
     */
    public ArchiveSearch() {
        this.sortOrder = SortOrder.descending;
        this.sortField = SortField.date;
    }

    /**
     * Returns the query string used for the search or <tt>null</tt> if no query string
     * has been set. The query String can contain the full
     * <a href="http://lucene.apache.org/java/docs/queryparsersyntax.html">search syntax</a>
     * supported by Lucene.
     *
     * @return the query string or <tt>null</tt> if no query string has been set.
     */
    public String getQueryString() {
        return queryString;
    }

    /**
     * Sets the query string used for the search, which can be <tt>null</tt> to indicate that
     * no query string should be used. The query String can contain the full
     * <a href="http://lucene.apache.org/java/docs/queryparsersyntax.html">search syntax</a>
     * supported by Lucene.
     *
     * @param queryString the query string or <tt>null</tt> if no query string should be used.
     */
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    /**
     * Returns the participants that this search covers. If no participants are specified
     * (via an empty collection), then this search will wildcard match against both users.
     * If a single participant is specified, this search will wildcard match against the
     * other participant. The wildcard matching mode is either external users only, or all
     * users, depending on the value returned by {@link #isExternalWildcardMode()}.
     *
     * @return the participants that this search covers.
     */
    public Collection<JID> getParticipants() {
        return participants;
    }

    /**
     * Sets the participants that this search covers. If no participants are specified
     * then this search will wildcard match against both users. If a single participant
     * is specified, this search will wildcard match against the other participant.
     * The wildcard matching mode is either external users only, or all
     * users, depending on the value returned by {@link #isExternalWildcardMode()}.
     *
     * @param participants the participants that this search covers.
     */
    public void setParticipants(JID... participants) {
        if (participants == null) {
            this.participants = Collections.emptyList();
        }
        else {
            if (participants.length > 2) {
                throw new IllegalArgumentException("Not possible to search on more than " +
                        "two participants.");
            }
            // Enforce using the bare JID.
            for (int i=0; i<participants.length; i++) {
                participants[i] = new JID(participants[i].toBareJID());
            }
            this.participants = Arrays.asList(participants);
        }
    }

    /**
     * Returns the date that represents the lower boundary for conversations
     * that will be returned by the search. If this value has not been set, the method
     * will return <tt>null</tt>.
     *
     * @return a Date representing the lower bound for dates to search on or <tt>null</tt>
     *      if there is no lower bound.
     */
    public Date getDateRangeMin() {
        return dateRangeMin;
    }

    /**
     * Sets the date that represents the lower boundary for conversations
     * that will be returned by the search. A value of <tt>null</tt> indicates that
     * there should be no lower boundary.
     *
     * @param dateRangeMin a Date representing the lower bound for dates to search on
     *      or <tt>null</tt> if there is no lower bound.
     */
    public void setDateRangeMin(Date dateRangeMin) {
        this.dateRangeMin = dateRangeMin;
    }

    /**
     * Returns the date that represents the upper boundary for conversations
     * that will be returned by the search. If this value has not been set, the method
     * will return <tt>null</tt>.
     *
     * @return a Date representing the upper bound for dates to search on or <tt>null</tt>
     *      if there is no upper bound.
     */
    public Date getDateRangeMax() {
        return dateRangeMax;
    }

    /**
     * Sets the date that represents the upper boundary for conversations
     * that will be returned by the search. A value of <tt>null</tt> indicates that
     * there should be no upper boundary.
     *
     * @param dateRangeMax a Date representing the upper bound for dates to search on
     *      or <tt>null</tt> if there is no upper bound.
     */
    public void setDateRangeMax(Date dateRangeMax) {
        this.dateRangeMax = dateRangeMax;
    }

    /**
     * Returns the JID of the room for conversations that will be returned by the search. If
     * this value has not been set, the method will return <tt>null</tt>.
     *
     * @return JID of the room or <tt>null</tt> if there is no room to filter on.
     */
    public JID getRoom() {
        return room;
    }

    /**
     * Sets the JID of the room for conversations that will be returned by the search. If
     * this value has not been set, the method will return <tt>null</tt>.
     *
     * @param room JID of the room or <tt>null</tt> if there is no room to filter on.
     */
    public void setRoom(JID room) {
        this.room = room;
    }

    /**
     * Returns the timestamp to use for filtering conversations. This timestamp
     * has to be between the time when the conversation started and ended.
     *
     * @return timestamp between the time when the conversation started and ended.
     */
    public Date getIncludeTimestamp() {
        return includeTimestamp;
    }

    /**
     * Set the timestamp to use for filtering conversations. This timestamp
     * has to be between the time when the conversation started and ended.
     *
     * @param includeTimestamp timestamp between the time when the conversation started and ended.
     */
    public void setIncludeTimestamp(Date includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
    }

    /**
     * Returns the sort order, which will be {@link SortOrder#ascending ascending} or
    * {@link SortOrder#descending descending}.
     *
     * @return the sort order.
     */
    public SortOrder getSortOrder() {
        return this.sortOrder;
    }

    /**
     * Sets the sort type, which will be {@link SortOrder#ascending ascending} or
    * {@link SortOrder#descending descending}.
     *
     * @param sortOrder the order that results will be sorted in.
     */
    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * Returns the sort field, which will be {@link SortField#relevance relevance} or
    * {@link SortField#relevance relevance}.
     *
     * @return the sort field.
     */
    public SortField getSortField() {
        return this.sortField;
    }

    /**
     * Sets the sort field, which will be {@link SortField#relevance relevance} or
    * {@link SortField#relevance relevance}.
     *
     * @param sortField the field that results will be sorted on.
     */
    public void setSortField(SortField sortField) {
        this.sortField = sortField;
    }

    /**
     * Returns the max number of results that should be returned.
     * The default value for is NULL_INT, which means there will be no limit
     * on the number of results. This method can be used in combination with
     * setStartIndex(int) to perform pagination of results.
     *
     * @return the max number of results to return.
     * @see #setStartIndex(int)
     */
    public int getNumResults() {
        return numResults;
    }

    /**
     * Sets the limit on the number of results to be returned.
     *
     * @param numResults the number of results to return.
     */
    public void setNumResults(int numResults) {
        if (numResults != NULL_INT && numResults < 0) {
            throw new IllegalArgumentException("numResults cannot be less than 0.");
        }
        this.numResults = numResults;
    }

    /**
     * Returns the index of the first result to return.
     *
     * @return the index of the first result which should be returned.
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Sets the index of the first result to return. For example, if the start
     * index is set to 20, the Iterator returned will start at the 20th result
     * in the query. This method can be used in combination with
     * setNumResults(int) to perform pagination of results.
     *
     * @param startIndex the index of the first result to return.
     */
    public void setStartIndex(int startIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("A start index less than 0 is not valid.");
        }
        this.startIndex = startIndex;
    }

    /**
     * Returns true if the wildcard matching for participants is for users
     * on external servers. Otherwise, wildcard matching will apply to any user
     * (external or internal). For example, if a single participant "jsmith" is set
     * and external wildcard mode is true, then the search will match any conversation
     * between "jsmith" and any external users. If the external wildcard mode is false,
     * then the search will match all conversations between "jsmith" and any other users.
     *
     * @return true if external wildcard mode is enabled.
     */
    public boolean isExternalWildcardMode() {
        return externalWildcardMode;
    }

    /**
     * Sets whether wildcard matching for participants is for users on external
     * servers. Otherwise, wildcard matching will apply to any user (external or
     * internal). For example, if a single participant "jsmith" is set and external
     * wildcard mode is true, then the search will match any conversation between
     * "jsmith" and any external users. If the external wildcard mode is false, then
     * the search will match all conversations between "jsmith" and any other users.
     *
     * @param mode true if external wildcard mode is enabled.
     */
    public void setExternalWildcardMode(boolean mode) {
        this.externalWildcardMode = mode;
    }

    /**
     * The sort order of search results. The default sort order is descending. Note that
     * if if the sort field is {@link SortField#relevance} (for a query string search),
     * then the sort order is irrelevant. Relevance searches will always display the
     * most relevant results first.
     */
    public enum SortOrder {

        /**
         * Ascending sort (ie 3, 4, 5...).
         */
        ascending,

        /**
         * Descending sort (ie 3, 2, 1...).
         */
        descending
    }

    /**
     * The field to sort results on.
     */
    public enum SortField {

        /**
         * Sort results based on relevance. This sort type can only be used when
         * searching with a query string. It <b>should</b> be the default sort field when
         * doing a query string search.
         */
        relevance,

        /**
         * Sort results based on date.
         */
        date
    }
}
