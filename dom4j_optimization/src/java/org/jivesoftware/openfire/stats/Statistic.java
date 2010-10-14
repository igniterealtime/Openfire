/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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
     * Returns true if the sample value represents only the value of the cluster node
     * or otherwise it represents the value of the entire cluster. Statistics that keep
     * only a sample of the local node will need to get added up with the value of the
     * other cluster nodes. On the other hand, statistics that hold the value of the
     * entire cluster can be sampled in any cluster node and they don't need to get
     * added up.<p>
     *
     * An example of a partial sample statistic would be network traffic. Each node keeps
     * track of its own network traffic information. Whilst an example of a total sample
     * statistic would be user sessions since every cluster node knows the total number of
     * connected users across the cluster. 
     *
     * @return true if the sample value represents only the value of the cluster node
     * or otherwise it represents the value of the entire cluster. 
     */
    public boolean isPartialSample();

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
