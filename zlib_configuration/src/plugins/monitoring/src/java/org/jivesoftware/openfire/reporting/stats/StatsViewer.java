/**
 * $Revision  $
 * $Date  $
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

package org.jivesoftware.openfire.reporting.stats;

import org.jivesoftware.openfire.stats.Statistic;

/**
 * Provides a view into the stats being tracked by the stats engine.
 *
 * @author Alexander Wenckus
 */
public interface StatsViewer {

    /**
     * Returns any multistat group keys and any keys that are not part of a multistat.
     *
     * @return any multistat group names and any stats that are not part of a multistat.
     */
    String [] getAllHighLevelStatKeys();

    /**
     * Returns all statistic objects for a related key. For instance if the key is a multistat
     * more than one Statistic will be returned. Statistic objects contain the meta information
     * for a stat.
     *
     * @param statKey the key of the stat.
     * @return all statistic objects for a related key.
     * @throws IllegalArgumentException if the stat related to the statKey does not exist.
     */
    Statistic[] getStatistic(String statKey);

    /**
     * Returns the last time this statistic was sampled.
     *
     * @param key the key for the statistic.
     * @return the last time this statistic was sampled in milliseconds since the epoch.
     */
    long getLastSampleTime(String key);

    /**
     * Retrieves the data for the related stat between the specified time period.
     *
     * @param key the key for the stat of which the data is being retrieved.
     * @param startTime the lower bound of the time period in milliseconds since the epoch.
     * @param endTime the upper bound of the time period in milliseconds since the epoch.
     * @return an array of doubles representing the stat. If the stat is a multistat,
     *      more than one array is returned.
     */
    double[][] getData(String key, long startTime, long endTime);

    /**
     * Retrieves the data for the related stat between the time period specified. The number
     * of datapoints indicates how many doubles are to be returned for each stat, the
     * statviewer will make a best effort ot conform to this, but if the backing datastore doesn't
     * have a resolution for that particular amount of points then the closest match will be
     * returned.
     *
     * @param key the key for the stat of which the data is being retrieved.
     * @param startTime the lower bound of the time period.
     * @param endTime the upper bound of the time period.
     * @param dataPoints the number of desired datapoints
     * @return an array of doubles representing the stat. If the stat is a multistat,
     *      more than one array is returned.
     * @deprecated will be removed pending the completion of #getData(String, TimePeriod)
     */
    @Deprecated
	double[][] getData(String key, long startTime, long endTime, int dataPoints);

    /**
     * Retrieves the data for the related stat for the time period.
     *
     * @param key the key for the statistic.
     * @param timePeriod the timeperiod for which the data is desired.
     * @return an array of doubles representing the stat. If the stat is a multistat,
     *      more than one array is returned.
     * @see #getData(String, long, long)
     */
    StatView getData(String key, TimePeriod timePeriod);

    /**
     * Returns an array of doubles which is the max value between the time periods.
     * If the provided key relates to a multistat, each array element relates to a max
     * for that particular stat. If it is not a multistat, the array will be of length 1.
     *
     * @param key the multistat or stat key related to the stat.
     * @param startTime the start time or lower range.
     * @param endTime the end time or upper range
     * @return an array of doubles which is the max value between the time periods.
     */
    double[] getMax(String key, long startTime, long endTime);

    double[] getMax(String key, long startTime, long endTime, int dataPoints);

    /**
     * Returns an array of doubles which is the max value for a time period.
     * If the provided key relates to a multistat, each array element relates to a max
     * for that particular stat. If it is not a multistat, the array will be of length 1.
     *
     * @param key the multistat or stat key related to the stat.
     * @param timePeriod the time period over which the max should be returned.
     * @return an array of doubles which is the max value for the time period.
     */
    double[] getMax(String key, TimePeriod timePeriod);

    /**
     * Returns an array of doubles which is the minimum value between the time periods.
     * If the provided key relates to a multistat, each array element relates to a minimum
     * for that particular stat. If it is not a multistat, the array will be of length 1.
     *
     * @param key the multistat or stat key related to the stat.
     * @param startTime the start time or lower range.
     * @param endTime the end time or upper range
     * @return an array of doubles which is the min value between the time periods.
     */
    double[] getMin(String key, long startTime, long endTime);

    double[] getMin(String key, long startTime, long endTime, int dataPoints);

    /**
     * Returns an array of doubles which is the minimum value for a time period.
     * If the provided key relates to a multistat, each array element relates to a minimum
     * for that particular stat. If it is not a multistat, the array will be of length 1.
     *
     * @param key the multistat or stat key related to the stat.
     * @param timePeriod the time period over which the min should be returned.
     * @return an array of doubles which is the min value for the time period.
     */
    double[] getMin(String key, TimePeriod timePeriod);

    /**
     * Returns the last recorded value for a stat.
     *
     * @param key the key for the stat.
     * @return the last value for a stat.
     * @see #getLastSampleTime(String)
     */
    double[] getCurrentValue(String key);

    /**
     * An enumeration for time period choices. A time period helps the stats viewer
     * determine the period of time which data should be  returned, it also provides a
     * suggestion on the number of datapoints that should be provided.
     */
    public enum TimePeriod {
        last_hour(3600, 15),
        last_day(43200, 15);

        private long timePeriod;
        private int dataPoints;

        private TimePeriod(long timePeriod, int dataPoints) {
            this.timePeriod = timePeriod;
            this.dataPoints = dataPoints;
        }

        /**
         * Takes an end time and returns a relative start time based off of the time period
         * this method is being operated off of.
         *
         * @param endTime the end time, the time period is substracted from this to
         *      determine the start time.
         * @return the determined start time.
         */
        public long getStartTime(long endTime) {
            return endTime - timePeriod;
        }

        /**
         * A suggestion for the number of data points that should be returned.
         *
         * @return a suggestion for the number of data points that should be returned.
         */
        public int getDataPoints() {
            return dataPoints;
        }

        public long getTimePeriod() {
            return timePeriod;
        }
    }

    /**
     * A snapshot of a stat in time.
     */
    public final class StatView {
        private long startTime;
        private long endTime;
        private double[][] data;

        public StatView(long startTime, long endTime, double[][] data) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.data = data;
        }

        /**
         * The starting time of the snap shot.
         *
         * @return the starting time of the snap shot.
         */
        public long getStartTime() {
            return startTime;
        }

        /**
         * The end time of the snap shot.
         *
         * @return the end time of the snap shot.
         */
        public long getEndTime() {
            return endTime;
        }

        /**
         * The data related to the snap shot.
         *
         * @return The data related to the snap shot.
         */
        public double[][] getData() {
            return data;
        }
    }
}