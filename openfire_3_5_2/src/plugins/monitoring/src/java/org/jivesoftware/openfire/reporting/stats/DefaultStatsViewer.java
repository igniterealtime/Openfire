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

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.util.Log;
import org.jrobin.core.RrdDb;


public class DefaultStatsViewer implements StatsViewer {

    private StatsEngine engine;

    /**
     * Default constructor used by the plugin container to create this class.
     *
     * @param engine The stats engine used to retrieve the stats.
     */
    public DefaultStatsViewer(StatsEngine engine) {
        this.engine = engine;
    }

    public String [] getAllHighLevelStatKeys() {
        return engine.getAllHighLevelNames();
    }


    public Statistic[] getStatistic(String statKey) {
        StatDefinition[] definitions = engine.getDefinition(statKey);
        if(definitions == null) {
            throw new IllegalArgumentException("Illegal stat key: " + statKey);
        }
        Statistic[] statistics = new Statistic[definitions.length];
        int i = 0;
        for(StatDefinition def : definitions) {
            statistics[i++] = def.getStatistic();
        }
        return statistics;
    }


    public long getLastSampleTime(String key) {
        return engine.getDefinition(key)[0].getLastSampleTime() * 1000;
    }


    public double[][] getData(String key, long startTime, long endTime) {
        return engine.getDefinition(key)[0].getData(parseTime(startTime), parseTime(endTime));
    }

    /**
     * Converts milliseconds to seconds.
     *
     * @param time the time to convert
     * @return the converted time
     */
    private long parseTime(long time) {
        return time / 1000;
    }

    public double[][] getData(String key, long startTime, long endTime, int dataPoints) {
        return engine.getDefinition(key)[0].getData(parseTime(startTime), parseTime(endTime), dataPoints);
    }


    public StatView getData(String key, TimePeriod timePeriod) {
        StatDefinition def = engine.getDefinition(key)[0];
        long endTime = def.getLastSampleTime();
        long startTime = timePeriod.getStartTime(endTime);
        double [][] data = def.getData(startTime, endTime, timePeriod.getDataPoints());
        return new StatView(startTime, endTime, data);
    }


    public double[] getMax(String key, long startTime, long endTime) {
        return engine.getDefinition(key)[0].getMax(parseTime(startTime), parseTime(endTime));
    }

    public double[] getMax(String key, long startTime, long endTime, int dataPoints) {
        return engine.getDefinition(key)[0].getMax(parseTime(startTime), parseTime(endTime), dataPoints);
    }

    public double[] getMax(String key, TimePeriod timePeriod) {
        StatDefinition def = engine.getDefinition(key)[0];
        long lastTime = def.getLastSampleTime();
        return def.getMax(timePeriod.getStartTime(lastTime), lastTime);
    }


    public double[] getMin(String key, long startTime, long endTime) {
        return engine.getDefinition(key)[0].getMin(parseTime(startTime), parseTime(endTime));
    }

    public double[] getMin(String key, long startTime, long endTime, int dataPoints) {
        return engine.getDefinition(key)[0].getMin(parseTime(startTime), parseTime(endTime), dataPoints);
    }

    public double[] getMin(String key, TimePeriod timePeriod) {
        StatDefinition def = engine.getDefinition(key)[0];
        long lastTime = def.getLastSampleTime();
        return def.getMin(timePeriod.getStartTime(lastTime), lastTime);
    }


    public double[] getCurrentValue(String key) {
        if (ClusterManager.isSeniorClusterMember()) {
            return new double[] { engine.getDefinition(key)[0].getLastSample() };
        }
        else {
            try {
                if (RrdSqlBackend.exists(key)) {
                    RrdDb db = new RrdDb(key, true);
                    return new double[] { db.getLastDatasourceValues()[0] };
                }
            } catch (Exception e) {
                Log.error("Error retrieving last sample value for: " + key, e);
            }
            return new double[] { 0 };
        }
    }
}
