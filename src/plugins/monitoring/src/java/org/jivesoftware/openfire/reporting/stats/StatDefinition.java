/**
 * $RCSfile  $
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
 * A class used by the StatsEngine to track all relevant meta information and data
 * relating to a graph. It also provides a mechanism for other classes in the stats
 * package to retrieve data relating to a stat.
 *
 * @author Alexander Wenckus
 */
abstract class StatDefinition {

    private String dbPath;
    private String datasourceName;
    private Statistic stat;
    public long lastSampleTime;
    public double lastSample;

    StatDefinition(String dbPath, String datasourceName, Statistic stat) {
        this.dbPath = dbPath;
        this.datasourceName = datasourceName;
        this.stat = stat;
    }

    public String getDbPath() {
        return dbPath;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public Statistic getStatistic() {
        return stat;
    }

    public abstract double[][] getData(long startTime, long endTime);

    public abstract double[][] getData(long startTime, long lastTime, int dataPoints);

    public abstract long getLastSampleTime();

    public abstract double getLastSample();

    public abstract double[] getMax(long startTime, long endTime);

    public abstract double[] getMin(long startTime, long endTime);

    public abstract double[] getMin(long startTime, long endTime, int dataPoints);

    public abstract double[] getMax(long l, long l1, int dataPoints);
}
