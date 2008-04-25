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

import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/**
 *
 */
public class MockStatsViewer implements StatsViewer {
    private StatsEngine engine;

    private Map<String, double[][]> dataCache = new HashMap<String, double[][]>();

    Random random = new Random();

    public MockStatsViewer(StatsEngine engine) {
        this.engine = engine;
    }

    public String [] getAllHighLevelStatKeys() {
        return engine.getAllHighLevelNames();
    }

    public Statistic[] getStatistic(String statKey) {
        StatDefinition[] definitions = engine.getDefinition(statKey);
        Statistic[] statistics = new Statistic[definitions.length];
        int i = 0;
        for (StatDefinition def : definitions) {
            statistics[i++] = def.getStatistic();
        }
        return statistics;
    }

    public long getLastSampleTime(String key) {
        return System.currentTimeMillis() / 1000;
    }

    public double[][] getData(String key, long startTime, long endTime) {
        return getData(key, true);
    }

    public double[][] getData(String key, long startTime, long endTime, int dataPoints) {
        return getData(key, true);
    }

    private double[][] getData(String key, boolean shouldUpdate) {
        synchronized (("mock_" + key).intern()) {
            double[][] data = dataCache.get(key);
            if (data == null) {
                Statistic[] stats = getStatistic(key);
                data = new double[stats.length][];
                for (int i = 0; i < data.length; i++) {
                    data[i] = new double[60];
                    for (int j = 0; j < data[i].length; j++) {
                        data[i][j] = random.nextInt(500);
                    }
                }
                dataCache.put(key, data);
            }
            else if(shouldUpdate) {
                for (int i = 0; i < data.length; i++) {
                    double [] newData = new double[data[i].length];
                    System.arraycopy(data[i], 1, newData, 0, data[i].length - 1);
                    newData[newData.length - 1] = random.nextInt(500);
                    data[i] = newData;
                }
            }
            return data;
        }
    }

    public StatView getData(String key, TimePeriod timePeriod) {
        long time = getLastSampleTime(key);
        double[][] data = getData(key, timePeriod.getStartTime(time), time);
        return new StatView(timePeriod.getStartTime(time), time, data);
    }

    public double[] getMax(String key, long startTime, long endTime) {
        double [][] data = getData(key, false);
        double[] toReturn = new double[data.length];
        for(int i = 0; i < toReturn.length; i++) {
            toReturn[i] = discoverMax(data[i]);
        }
        return toReturn;
    }

    public double[] getMax(String key, long startTime, long endTime, int dataPoints) {
        return getMax(key, startTime, endTime);
    }

    public double[] getMax(String key, TimePeriod timePeriod) {
        long time = getLastSampleTime(key);
        return getMax(key, timePeriod.getStartTime(time), time);
    }

    private double discoverMax(double[] doubles) {
            double max = 0;
            for(double d : doubles) {
                if(d > max) {
                    max = d;
                }
            }
            return max;
        }

    public double[] getMin(String key, long startTime, long endTime) {
        double [][] data = getData(key, false);
        double[] toReturn = new double[data.length];
        for(int i = 0; i < toReturn.length; i++) {
            toReturn[i] = discoverMin(data[i]);
        }
        return toReturn;
    }

    public double[] getMin(String key, long startTime, long endTime, int dataPoints) {
        return getMin(key, startTime, endTime);
    }

    public double[] getMin(String key, TimePeriod timePeriod) {
        long time = getLastSampleTime(key);
        return getMin(key, timePeriod.getStartTime(time), time);
    }

    private double discoverMin(double[] doubles) {
            double min = doubles[0];
            for(double d : doubles) {
                if(d < min) {
                    min = d;
                }
            }
            return min;
        }

    public double[] getCurrentValue(String key) {
        double [][] data = getData(key, false);
        double[] toReturn = new double[data.length];
        for(int i = 0; i < toReturn.length; i++) {
            toReturn[i] = data[i][data[i].length - 1];
        }
        return toReturn;
    }
}
