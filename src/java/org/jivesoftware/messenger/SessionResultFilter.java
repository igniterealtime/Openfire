/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger;

import java.util.Comparator;
import java.util.Date;

/**
 * <p>Filters and sorts lists of sessions.</p>
 * <p>This allows for a very
 * rich set of possible queries that can be run on session data. Some examples
 * are: "Show all sessions started during the last hour by a
 * certain user".
 * </p><p>
 * The class also supports pagination of results with the setStartIndex(int)
 * and setNumResults(int) methods. If the start index is not set, it will
 * begin at index 0 (the start of results). If the number of results is not set,
 * it will be unbounded and return as many results as available.
 * </p><p>
 * Factory methods to create common queries are provided for convenience.
 * </p>
 *
 * @author Iain Shigeoka
 */
public class SessionResultFilter {

    // ############################################################
    // Search order criteria
    // ############################################################
    /**
     * Descending sort (ie 3, 2, 1...).
     */
    public static final int DESCENDING = 0;

    /**
     * Ascending sort (ie 3, 4, 5...).
     */
    public static final int ASCENDING = 1;

    // ############################################################
    // User search criteria
    // ############################################################
    /**
     * <p>Represents an anonymous user id that matches all anonymous users.</p>
     */
    public static final int ANONYMOUS_USER_ID = -1;

    /**
     * <p>Represents a user id that matches any/all users.</p>
     */
    public static final int ALL_USER_ID = -2;

    // ############################################################
    // Result limit search criteria
    // ############################################################
    /**
     * <p>Represents no result limit (infinite results).</p>
     */
    public static final int NO_RESULT_LIMIT = -1;

    // ############################################################
    // Packet limit search criteria
    // ############################################################
    /**
     * <p>Represents no result limit (infinite results).</p>
     */
    public static final long NO_PACKET_LIMIT = -1;
    // ############################################################
    // Sort fields
    // ############################################################
    public static final int SORT_USER = 0;
    public static final int SORT_CREATION_DATE = 1;
    public static final int SORT_LAST_ACTIVITY_DATE = 2;
    public static final int SORT_NUM_CLIENT_PACKETS = 3;
    public static final int SORT_NUM_SERVER_PACKETS = 4;

    /**
     * <p>Creates a default SessionResultFilter: no filtering with results sorted
     * by user.</p>
     */
    public static SessionResultFilter createDefaultSessionFilter() {
        SessionResultFilter resultFilter = new SessionResultFilter();
        resultFilter.setSortField(SORT_USER);
        resultFilter.setSortOrder(ASCENDING);
        return resultFilter;
    }

    private int sortField = SORT_LAST_ACTIVITY_DATE;
    private int sortOrder = DESCENDING;
    private long clientPacketRangeMin = NO_PACKET_LIMIT;
    private long clientPacketRangeMax = NO_PACKET_LIMIT;
    private long serverPacketRangeMin = NO_PACKET_LIMIT;
    private long serverPacketRangeMax = NO_PACKET_LIMIT;

    /**
     * The starting index for results. Default is 0.
     */
    private int startIndex = 0;

    /**
     * Number of results to return. Default is NO_RESULT_LIMIT
     * which means an unlimited number of results.
     */
    private int numResults = NO_RESULT_LIMIT;

    private long userID = ALL_USER_ID;
    private Date creationDateRangeMin = null;
    private Date creationDateRangeMax = null;
    private Date lastActivityDateRangeMin = null;
    private Date lastActivityDateRangeMax = null;

    /**
     * Returns the userID that results will be filtered on. The method will
     * return ALL_USER_ID if no user to filter on has been specified. The method
     * will return ANONYMOUS_USER_ID if filtering is to take place on all "anonymous" users.
     *
     * @return the userID that results will be filtered on.
     */
    public long getUserID() {
        return userID;
    }

    /**
     * Sets the userID that results will be filtered on. If you'd like to filter
     * on "anonymous" users, pass in an id of ANONYMOUS_USER_ID. By default, no filtering on
     * userID's will take place. To avoid filtering on userID pass ALL_USER_ID.
     *
     * @param userID the user ID to filter on.
     */
    public void setUserID(long userID) {
        this.userID = userID;
    }

    /**
     * Returns the creation date that represents the lower boundary for
     * sessions to be filtered on. If this value has not been set, the method
     * will return null.
     *
     * @return a Date representing the lower bound for creation dates to filter
     *         on.
     */
    public Date getCreationDateRangeMin() {
        return creationDateRangeMin;
    }

    /**
     * Sets the date that represents the lower boundary for sessions to
     * be selected by the result filter. If this value is not set the results filter will
     * be unbounded for the earliest creation date selected.<p>
     *
     * @param creationDateRangeMin Date representing the filter lowest value of
     *                             the creation date to be selected.
     */
    public void setCreationDateRangeMin(Date creationDateRangeMin) {
        this.creationDateRangeMin = creationDateRangeMin;
    }

    /**
     * Returns a date that represents the upper boundry for sessions to
     * be selected by the result filter. If this value is not set it will return null
     * and the results filter will be unbounded for the latest creation date selected.
     *
     * @return a Date representing the filter highest value of the creation date to be
     *         selected.
     */
    public Date getCreationDateRangeMax() {
        return creationDateRangeMax;
    }

    /**
     * Sets a date that represents the upper boundry for sessions to
     * be selected by the result filter. If this value is not set the results
     * filter will be unbounded for the latest creation date selected.
     *
     * @param creationDateRangeMax Date representing the filter lowest value of
     *                             the creation date range.
     */
    public void setCreationDateRangeMax(Date creationDateRangeMax) {
        this.creationDateRangeMax = creationDateRangeMax;
    }

    /**
     * Returns a date that represents the lower boundary for session
     * to be selected by the result filter. If this value is not set it will
     * return null and the results filter will be unbounded for the earliest
     * last activity date selected.
     *
     * @return a Date representing the filter lowest value of the last activity date
     *         range.
     */
    public Date getLastActivityDateRangeMin() {
        return lastActivityDateRangeMin;
    }

    /**
     * Sets a date that represents the lower boundary for sessions to
     * be selected by the result filter. If this value is not set the results
     * filter will be unbounded for the earliest last activity date selected.
     *
     * @param lastActivityDateRangeMin Date representing the filter lowest value of
     *                                 the last activity date to be selected.
     */
    public void setLastActivityDateRangeMin(Date lastActivityDateRangeMin) {
        this.lastActivityDateRangeMin = lastActivityDateRangeMin;
    }

    /**
     * Returns a date that represents the upper boundry for sessions to
     * be selected by the result filter. If this value is not set it will return null
     * and the results filter will be unbounded for the latest activity date selected.
     *
     * @return a Date representing the filter highest value of the last activity date to be
     *         selected.
     */
    public Date getLastActivityDateRangeMax() {
        return lastActivityDateRangeMax;
    }

    /**
     * Sets a date that represents the upper boundry for sessions to
     * be selected by the result filter. If this value is not set the results filter will
     * be unbounded for the latest activity date selected.
     *
     * @param lastActivityDateRangeMax Date representing the filter lowest value of
     *                                 the last activity date range.
     */
    public void setLastActivityDateRangeMax(Date lastActivityDateRangeMax) {
        this.lastActivityDateRangeMax = lastActivityDateRangeMax;
    }

    /**
     * <p>Get a lower boundary on client packets for sessions to be
     * selected by the result filter.</p>
     * <p>A value of NO_PACKET_LIMIT will be returned if
     * there is lower packet limit.</p>
     *
     * @return The upper limit of client packets allowed for sessions to meet this filter requirement
     */
    public long getClientPacketRangeMin() {
        return clientPacketRangeMin;
    }

    /**
     * <p>Set an lower boundary on client packets for sessions to be
     * selected by the result filter.</p>
     * <p>If this value is not set
     * the results filter will have no lower bounds for client packets selected.</p>
     *
     * @param max The lower limit of client packets allowed for sessions to meet this filter requirement
     */
    public void setClientPacketRangeMin(long max) {
        this.clientPacketRangeMin = max;
    }

    /**
     * <p>Get an upper boundary on client packets for sessions to be
     * selected by the result filter.</p>
     * <p>A value of NO_PACKET_LIMIT will be returned if
     * there is upper packet limit.</p>
     *
     * @return The upper limit of client packets allowed for sessions to meet this filter requirement
     */
    public long getClientPacketRangeMax() {
        return clientPacketRangeMax;
    }

    /**
     * <p>Set an upper boundary on client packets for sessions to be
     * selected by the result filter.</p>
     * <p>If this value is not set
     * the results filter will have no upper bounds
     * for client packets selected.</p>
     *
     * @param max The upper limit of client packets allowed for sessions to meet this filter requirement
     */
    public void setClientPacketRangeMax(long max) {
        this.clientPacketRangeMax = max;
    }

    /**
     * <p>Get a lower boundary on server packets for sessions to be
     * selected by the result filter.</p>
     * <p>A value of NO_PACKET_LIMIT will be returned if
     * there is lower packet limit.</p>
     *
     * @return The upper limit of server packets allowed for sessions to meet this filter requirement
     */
    public long getServerPacketRangeMin() {
        return serverPacketRangeMin;
    }

    /**
     * <p>Set an lower boundary on server packets for sessions to be
     * selected by the result filter.</p>
     * <p>If this value is not set
     * the results filter will have no lower bounds for server packets selected.</p>
     *
     * @param max The lower limit of server packets allowed for sessions to meet this filter requirement
     */
    public void setServerPacketRangeMin(long max) {
        this.serverPacketRangeMin = max;
    }

    /**
     * <p>Get an upper boundary on server packets for sessions to be
     * selected by the result filter.</p>
     * <p>A value of NO_PACKET_LIMIT will be returned if
     * there is upper packet limit.</p>
     *
     * @return The upper limit of server packets allowed for sessions to meet this filter requirement
     */
    public long getServerPacketRangeMax() {
        return serverPacketRangeMax;
    }

    /**
     * <p>Set an upper boundary on server packets for sessions to be
     * selected by the result filter.</p>
     * <p>If this value is not set
     * the results filter will have no upper bounds
     * for server packets selected.</p>
     *
     * @param max The upper limit of server packets allowed for sessions to meet this filter requirement
     */
    public void setServerPacketRangeMax(long max) {
        this.serverPacketRangeMax = max;
    }

    /**
     * Returns the currently selected sort field. The default value is
     * SessionResultFilter.SORT_LAST_ACTIVITY_DATE.
     *
     * @return current sort field.
     */
    public int getSortField() {
        return sortField;
    }

    /**
     * Sets the sort field to use. The default value is
     * SessionResultFilter.SORT_LAST_ACTIVITY_DATE.
     *
     * @param sortField the field that will be used for sorting.
     */
    public void setSortField(int sortField) {
        this.sortField = sortField;
    }

    /**
     * Returns the sort order, which will be SessionResultFilter.ASCENDING for
     * ascending sorting, or SessionResultFilter.DESCENDING for descending sorting.
     * Descending sorting is: 3, 2, 1, etc. Ascending sorting is 1, 2, 3, etc.
     *
     * @return the sort order.
     */
    public int getSortOrder() {
        return this.sortOrder;
    }

    /**
     * Sets the sort type. Valid arguments are SessionResultFilter.ASCENDING for
     * ascending sorting or SessionResultFilter.DESCENDING for descending sorting.
     * Descending sorting is: 3, 2, 1, etc. Ascending sorting is 1, 2, 3, etc.
     *
     * @param sortOrder the order that results will be sorted in.
     */
    public void setSortOrder(int sortOrder) {
        if (!(sortOrder == SessionResultFilter.ASCENDING || sortOrder == SessionResultFilter.DESCENDING)) {
            throw new IllegalArgumentException();
        }
        this.sortOrder = sortOrder;
    }

    /**
     * <p>Returns the max number of results that should be returned.</p>
     * <p>The default value for is NO_RESULT_LIMIT, which means there will be no limit
     * on the number of results. This method can be used in combination with
     * setStartIndex(int) to perform pagination of results.</p>
     *
     * @return the max number of results to return or NO_RESULT_LIMIT for no limit
     * @see #setStartIndex(int)
     */
    public int getNumResults() {
        return numResults;
    }

    /**
     * <p>Sets the limit on the number of results to be returned.</p>
     * <p>User NO_RESULT_LIMIT if you don't want to limit the results returned.</p>
     *
     * @param numResults the number of results to return or NO_RESULT_LIMIT for no limit
     */
    public void setNumResults(int numResults) {
        if (numResults != NO_RESULT_LIMIT && numResults < 0) {
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
     * <p>Obtains a comparator that will sort a standard sorted set according
     * to this filter's sort order.</p>
     *
     * @return A comparator that sorts Sessions matching the sort order for this filter.
     */
    public Comparator getSortComparator() {
        return new SessionComparator();
    }

    /**
     * <p>Compares sessions according to sort fields.</p>
     *
     * @author Iain Shigeoka
     */
    private class SessionComparator implements Comparator {

        /**
         * <p>Compare two sessions according to sort order.</p>
         *
         * @param o1 The first session to compare
         * @param o2 The second session to compare
         * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
         */
        public int compare(Object o1, Object o2) {
            Session lhs = (Session)o1;
            Session rhs = (Session)o2;
            int comparison;
            switch (sortField) {
                case SessionResultFilter.SORT_CREATION_DATE:
                    comparison = lhs.getCreationDate().compareTo(rhs.getCreationDate());
                    break;
                case SessionResultFilter.SORT_LAST_ACTIVITY_DATE:
                    comparison = lhs.getLastActiveDate().compareTo(rhs.getCreationDate());
                    break;
                case SessionResultFilter.SORT_NUM_CLIENT_PACKETS:
                    comparison = (int)(lhs.getNumClientPackets() - rhs.getNumClientPackets());
                    break;
                case SessionResultFilter.SORT_NUM_SERVER_PACKETS:
                    comparison = (int)(lhs.getNumServerPackets() - rhs.getNumServerPackets());
                    break;
                case SessionResultFilter.SORT_USER:
                    // sort first by name, then by resource
                    comparison = compareString(lhs.getAddress().getName(),
                            rhs.getAddress().getName());
                    if (comparison == 0) {
                        comparison = compareString(lhs.getAddress().getResource(),
                                rhs.getAddress().getResource());
                    }
                    break;
                default:
                    comparison = 0;
            }
            if (sortOrder == SessionResultFilter.DESCENDING) {
                comparison *= -1; // Naturally ascending, flip sign if descending
            }
            return comparison;
        }

        private int compareString(String lhs, String rhs) {
            if (lhs == null) {
                lhs = "";
            }
            if (rhs == null) {
                rhs = "";
            }
            return lhs.compareTo(rhs);
        }
    }

    /**
     * Rounds the given date down to the nearest specified second. The following
     * table shows sample input and expected output values: (Note, only
     * the time portion of the date is shown for brevity) <p>
     * <p/>
     * <table border="1">
     * <tr>
     * <th>Date</th><th>Seconds</th><th>Result</th>
     * </tr>
     * <tr>
     * <td>1:37.48</td><td>5</td><td>1:37.45</td>
     * </tr>
     * <tr>
     * <td>1:37.48</td><td>10</td><td>1:37.40</td>
     * </tr>
     * <tr>
     * <td>1:37.48</td><td>30</td><td>1:37.30</td>
     * </tr>
     * <tr>
     * <td>1:37.48</td><td>60</td><td>1:37.00</td>
     * </tr>
     * <tr>
     * <td>1:37.48</td><td>120</td><td>1:36.00</td>
     * </tr>
     * </table><p>
     * <p/>
     * This method is useful when calculating the last post in
     * a forum or the number of new messages from a given date. Using a rounded
     * date allows Jive to internally cache the results of the date query.
     * Here's an example that shows the last posted message in a forum accurate
     * to the last 60 seconds: <p>
     * <p/>
     * <code>
     * SessionResultFilter filter = new SessionResultFilter(); <br>
     * filter.setSortOrder(SessionResultFilter.DESCENDING); <br>
     * filter.setSortField(JiveGlobals.SORT_CREATION_DATE); <br>
     * <b>filter.setCreationDateRangeMin(SessionResultFilter.roundDate(forum.getModificationDate(), 60));</b> <br>
     * filter.setNumResults(1); <br>
     * Iterator messages = forum.messages(filter); <br>
     * ForumMessage lastPost = (ForumMessage)messages.next(); <br>
     * </code><p>
     *
     * @param date    the <tt>Date</tt> we want to round.
     * @param seconds the number of seconds we want to round the date to.
     * @return the given date, rounded down to the nearest specified number of seconds.
     */
    public static Date roundDate(Date date, int seconds) {
        return new Date(roundDate(date.getTime(), seconds));
    }

    /**
     * Rounds the given date down to the nearest specfied second.
     *
     * @param date    the date (as a long) that we want to round.
     * @param seconds the number of seconds we want to round the date to.
     * @return the given date (as a long), rounded down to the nearest
     *         specified number of seconds.
     */
    public static long roundDate(long date, int seconds) {
        return date - (date % (1000 * seconds));
    }

    /**
     * Clones a SessionResultFilter
     */
    public Object clone() {
        SessionResultFilter clonedFilter = new SessionResultFilter();
        clonedFilter.setCreationDateRangeMax(getCreationDateRangeMax());
        clonedFilter.setCreationDateRangeMin(getCreationDateRangeMin());
        clonedFilter.setLastActivityDateRangeMax(getLastActivityDateRangeMax());
        clonedFilter.setLastActivityDateRangeMin(getLastActivityDateRangeMin());
        clonedFilter.setNumResults(getNumResults());
        clonedFilter.setSortField(getSortField());
        clonedFilter.setSortOrder(getSortOrder());
        clonedFilter.setStartIndex(getStartIndex());
        clonedFilter.setUserID(getUserID());
        return clonedFilter;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof SessionResultFilter) {
            SessionResultFilter o = (SessionResultFilter)object;
            return
                    sortField == o.sortField &&
                    sortOrder == o.sortOrder &&
                    startIndex == o.startIndex &&
                    numResults == o.numResults &&
                    userID == o.userID &&
                    ((creationDateRangeMin == null && o.creationDateRangeMin == null) ||
                    creationDateRangeMin.equals(o.creationDateRangeMin)) &&
                    ((creationDateRangeMax == null && o.creationDateRangeMax == null) ||
                    creationDateRangeMax.equals(o.creationDateRangeMax)) &&
                    ((lastActivityDateRangeMin == null && o.lastActivityDateRangeMin == null) ||
                    lastActivityDateRangeMin.equals(o.lastActivityDateRangeMin)) &&
                    ((lastActivityDateRangeMax == null && o.lastActivityDateRangeMax == null) ||
                    lastActivityDateRangeMax.equals(o.lastActivityDateRangeMax));
        }
        else {
            return false;
        }
    }
}