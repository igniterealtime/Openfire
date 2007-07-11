/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.stats;

/**
 * A statistic being tracked by the server.
 *
 * @see StatisticsManager
 */
public interface Statistic {

    /**
     * Returns the name of a stat.
     *
     * @return the name of a stat.
     */
    public String getName();

    /**
     * Returns the type of a stat.
     *
     * @return the type of a stat.
     */
    public Type getStatType();

    /**
     * Returns a description of the stat.
     *
     * @return a description of the stat.
     */
    public String getDescription();

    /**
     * Returns the units that relate to the stat.
     *
     * @return the name of the units that relate to the stat.
     */
    public String getUnits();

    /**
     * Returns the current sample of data.
     *
     * @return a sample of the data.
     */
    public double sample();

    /**
     * The type of statistic.
     */
    @SuppressWarnings({"UnnecessarySemicolon"})  // Support for QDox Parser
    public enum Type {

        /**
         * The average rate over time. For example, the averave kb/s in bandwidth used for
         * file transfers. Each time the {@link Statistic#sample()} method is invoked, it should
         * return the "amount" of data recorded since the last invocation.
         */
        rate,

        /**
         * The total rate over time. For example, the number of users created. Each time the
         * {@link Statistic#sample()} method is invoked, it should return the "amount" of data
         * recorded since the last invocation. The values will be totalled over the relevant
         * time interval (by minute, hourly, daily, etc.).
         */
        // TODO: rate_total,

        /*
         * The average count over a time period. An example would be the
         * number of users in multi-user chats. Each time the {@link Statistic#sample()}
         * method is invoked, it should return the current measurement of the data, irrelevant of
         * previous reads of the data.   
         */
        count;

        /**
         * The max count over a time period. An example would be the maximum number of users
         * connected to the server. Each time the {@link Statistic#sample()}
         * method is invoked, it should return the current measurement of the data, irrelevant of
         * previous reads of the data. The max value read will be stored for each time interval
         * (by minute, hourly, daily, etc.).
         */
        // TODO: count_max
    }
}
