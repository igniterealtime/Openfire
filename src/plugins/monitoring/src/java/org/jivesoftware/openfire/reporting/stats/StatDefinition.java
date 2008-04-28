/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
