/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.jrobin.core.ConsolFuns;
import org.jrobin.core.DsTypes;
import org.jrobin.core.FetchData;
import org.jrobin.core.RrdBackendFactory;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDef;
import org.jrobin.core.RrdException;
import org.jrobin.core.Sample;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The stats workhorse. Handles the job of sampling the different statistics existing in
 * the system and persiting them to the database. Also, it tracks through a <i>StatDefinition</i>
 * for each stat all the meta information related to a stat.
 *
 * @author Alexander Wenckus
 */
public class StatsEngine implements Startable {

	private static final Logger Log = LoggerFactory.getLogger(StatsEngine.class);
	
    private static final int STAT_RESOULUTION = 60;

    private final TaskEngine taskEngine;

    private final StatisticsManager statsManager;

    private final Map<String, StatDefinition> definitionMap = new HashMap<String, StatDefinition>();

    private final Map<String, List<StatDefinition>> multiMap = new HashMap<String, List<StatDefinition>>();

    private SampleTask samplingTask = new SampleTask();

    /**
     * The default constructor used by the plugin container.
     *
     * @param taskEngine Used to execute tasks.
     */
    public StatsEngine(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
        statsManager = StatisticsManager.getInstance();
    }

    public void start() {
        try {
            // Set that RRD files will be stored in the database
            RrdBackendFactory.registerAndSetAsDefaultFactory(new RrdSqlBackendFactory());

            // After 10 milliseconds begin sampling in 60 second intervals. Note: We need to start
            // asap so that the UI can access this info upon start up
            taskEngine.scheduleAtFixedRate(samplingTask, 10, STAT_RESOULUTION * 1000L);
        }
        catch (RrdException e) {
            Log.error("Error initializing RrdbPool.", e);
        }
    }

    public void stop() {
        // Clean-up sampling task
        samplingTask.cancel();
    }

    private void checkDatabase(StatDefinition[] def) throws RrdException, IOException {
        File directory = new File(getStatsDirectroy());
        if (directory.exists()) {
            // check if the rrd exists
            File rrdFile = new File(getRrdFilePath(def[0].getDbPath()));
            if (rrdFile.exists() && rrdFile.canRead()) {
                try {
                    // Import existing RRD file into the DB
                    RrdSqlBackend.importRRD(def[0].getDbPath(), rrdFile);
                    // Delete the RRD file
                    rrdFile.delete();
                } catch (IOException e) {
                    Log.error("Error importing rrd file: " + rrdFile, e);
                }
            }
        }

        // check if the rrd exists
        if (!RrdSqlBackend.exists(def[0].getDbPath())) {
            RrdDb db = null;
            try {
                RrdDef rrdDef = new RrdDef(def[0].getDbPath(), STAT_RESOULUTION);
                for (StatDefinition stat : def) {
                    String dsType = determineDsType(stat.getStatistic().getStatType());
                    rrdDef.addDatasource(stat.getDatasourceName(), dsType, 5 * STAT_RESOULUTION, 0,
                            Double.NaN);
                }

                // Every minute for 1 hour.
                rrdDef.addArchive(((DefaultStatDefinition) def[0]).
                        consolidationFunction, 0.5, 1, 60);
                // Every half-hour for 1 day.
                rrdDef.addArchive(ConsolFuns.CF_AVERAGE, 0.5, 30, 48);
                // Every day for 5 years.
                rrdDef.addArchive(ConsolFuns.CF_AVERAGE, 0.5, 1440, 1825);
                // Every week for 5 years.
                rrdDef.addArchive(ConsolFuns.CF_AVERAGE, 0.5, 10080, 260);
                // Every month for 5 years.
                rrdDef.addArchive(ConsolFuns.CF_AVERAGE, 0.5, 43200, 60);

                db = new RrdDb(rrdDef);
            }
            finally {
                if(db != null) {
                    db.close();
                }
            }
        }
    }

    private String determineDsType(Statistic.Type statType) {
        return DsTypes.DT_GAUGE;
    }

    /**
     * Returns the path to the RRD file.
     *
     * @param datasourceName the name of the data source.
     * @return the path to the RRD file.
     */
    private String getRrdFilePath(String datasourceName) {
        return getStatsDirectroy() + datasourceName + ".rrd";
    }

    /**
     * Returns the directory in which all of the stat databases will be stored.
     *
     * @return Returns the directory in which all of the stat databases will be stored.
     */
    private String getStatsDirectroy() {
        return JiveGlobals.getHomeDirectory() + File.separator + "monitoring"
                + File.separator + "stats" + File.separator;
    }

    private StatDefinition createDefintion(String key) {
        StatDefinition def = definitionMap.get(key);
        if (def == null) {
            Statistic statistic = statsManager.getStatistic(key);
            String statGroup = statsManager.getMultistatGroup(key);
            try {
                def = new DefaultStatDefinition(statGroup != null ? statGroup : key, key, statistic);

                // If the definition is a part of a group check to see all defiintions have been
                // made for that group
                StatDefinition[] definitions;
                if (statGroup != null) {
                    definitions = checkAndCreateGroup(statGroup, def, true);
                }
                else {
                    definitions = new StatDefinition[]{def};
                    multiMap.put(key, Arrays.asList(definitions));
                }

                if (definitions != null) {
                    checkDatabase(definitions);
                }
                definitionMap.put(key, def);
            }
            catch (RrdException e) {
                Log.error("Error creating database definition", e);
            }
            catch (IOException e) {
                Log.error("Error creating database definition", e);
            }
        }
        return def;
    }

    /**
     * Checks to see that all StatDefinitions for a stat group have been created. If they have
     * then an array of the StatDefinitions will be returned, if they haven't Null will be returned.
     * <p>
     * The purpose of this is to know when a database should be initialized, after all the StatDefinitions
     * have been created.
     *
     * @param statGroup The statGroup being checked
     * @param def The statdefinition that is being added to the statGroup
     * @return Null if the statgroup is completely defined and an array of statdefinitions if it is.
     */
    private StatDefinition[] checkAndCreateGroup(String statGroup, StatDefinition def,
                                                 boolean shouldCreate)
    {
        List<StatDefinition> statList = multiMap.get(statGroup);
        if (shouldCreate && statList == null) {
            statList = new ArrayList<StatDefinition>();
            multiMap.put(statGroup, statList);
        }
        if (statList == null) {
            return null;
        }
        if (shouldCreate) {
            statList.add(def);
        }
        StatDefinition[] definitions;
        if (statsManager.getStatGroup(statGroup).size() == statList.size()) {
            definitions = statList.toArray(new StatDefinition[statList.size()]);
        }
        else {
            definitions = null;
        }
        return definitions;
    }

    /**
     * Returns the last minute that passed in seconds since the epoch.
     *
     * @return the last minute that passed in seconds since the epoch.
     */
    private static long getLastMinute() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.SECOND, 0);
        return calendar.getTimeInMillis() / 1000;
    }

    /**
     * Returns the definition or definitions related to a statkey. There can be multiple
     * definitions if a stat is a multistat.
     *
     * @param statKey The key for which the definition is desired.
     * @return Returns the definition or definitions related to a statkey. There can be multiple
     * definitions if a stat is a multistat.
     */
    StatDefinition[] getDefinition(String statKey) {
        List<StatDefinition> defs = multiMap.get(statKey);
        if (defs == null) {
            StatDefinition def = definitionMap.get(statKey);
            if (def != null) {
                return new StatDefinition[] {def};
            }
            else {
                return null;
            }
        }
        else {
            return defs.toArray(new StatDefinition[defs.size()]);
        }
    }

    /**
     * Returns any multistat group names and any stats that are not part of a multistat.
     *
     * @return Returns any multistat group names and any stats that are not part of a multistat.
     */
    String [] getAllHighLevelNames() {
        Set<String> keySet = multiMap.keySet();

        return keySet.toArray(new String[keySet.size()]);
    }

    /**
     * The task which samples statistics and persits them to the database.
     *
     * @author Alexander Wenckus
     */
    private class SampleTask extends TimerTask {
        private long lastSampleTime = 0;

        @Override
		public void run() {
            if (!ClusterManager.isSeniorClusterMember()) {
                // Create statistics definitions but do not sample them since we are not the senior cluster member
                for (Map.Entry<String, Statistic> statisticEntry : statsManager.getAllStatistics()) {
                    String key = statisticEntry.getKey();
                    StatDefinition def = createDefintion(key);
                    // Check to see if this stat belongs to a multi-stat and if that multi-stat group
                    // has been completly defined
                    String group = statsManager.getMultistatGroup(key);
                    if (group != null) {
                        checkAndCreateGroup(group, def, false);
                    }
                }
                return;
            }
            long newTime = getLastMinute();
            if (lastSampleTime != 0 && newTime <= lastSampleTime) {
                Log.warn("Sample task not run because less then a second has passed since last " +
                        "sample.");
                return;
            }
            lastSampleTime = newTime;

            // Gather sample statistics from remote cluster nodes
            Collection<Object> remoteSamples = CacheFactory.doSynchronousClusterTask(new GetStatistics(), false);

            List<String> sampledStats = new ArrayList<String>();
            for (Map.Entry<String, Statistic> statisticEntry : statsManager.getAllStatistics()) {
                String key = statisticEntry.getKey();
                StatDefinition def = createDefintion(key);

                // Check to see if this stat belongs to a multi-stat and if that multi-stat group
                // has been completly defined
                String group = statsManager.getMultistatGroup(key);
                StatDefinition [] definitions;
                if (group != null) {
                    definitions = checkAndCreateGroup(group, def, false);
                    if (definitions == null || sampledStats.contains(def.getDatasourceName())) {
                        continue;
                    }
                }
                else {
                    definitions = new StatDefinition[]{def};
                }

                RrdDb db = null;
                try {
                    newTime = getLastMinute();
                    if (def.lastSampleTime <= 0) {
                        for(StatDefinition definition : definitions) {
                            definition.lastSampleTime = newTime;
                            // It is possible that this plugin and thus the StatsEngine didn't
                            // start when Openfire started so we want to put the stats in a known
                            // state for proper sampling.
                            sampleStat(key, definition);
                        }
                        continue;
                    }

                    db = new RrdDb(def.getDbPath(), false);
                    // We want to double check the last sample time recorded in the db so as to
                    // prevent the log files from being inundated if more than one instance of
                    // Openfire is updating the same database. Also, if there is a task taking a
                    // long time to complete
                    if(newTime <= db.getLastArchiveUpdateTime()) {
                        Log.warn("Sample time of " + newTime +  " for statistic " + key + " is " +
                                "invalid.");
                    }
                    Sample sample = db.createSample(newTime);

                    if (Log.isDebugEnabled()) {
                        Log.debug("Stat: " + db.getPath() + ". Last sample: " + db.getLastUpdateTime() +
                                ". New sample: " + sample.getTime());
                    }

                    for (StatDefinition definition : definitions) {
                        // Get a statistic sample of this JVM
                        double statSample = sampleStat(key, definition);
                        // Add up samples of remote cluster nodes
                        for (Object nodeResult : remoteSamples) {
                            Map<String, Double> nodeSamples = (Map<String, Double>) nodeResult;
                            Double remoteSample = nodeSamples.get(key);
                            if (remoteSample != null) {
                                statSample += remoteSample;
                            }
                        }
                        // Update sample with values
                        sample.setValue(definition.getDatasourceName(), statSample);
                        sampledStats.add(definition.getDatasourceName());
                        definition.lastSampleTime = newTime;
                        definition.lastSample = statSample;
                    }
                    sample.update();
                }
                catch (IOException e) {
                    Log.error("Error sampling for statistic " + key, e);
                }
                catch (RrdException e) {
                    Log.error("Error sampling for statistic " + key, e);
                }
                finally {
                    if (db != null) {
                        try {
                            db.close();
                        }
                        catch (IOException e) {
                           Log.error("Error releasing db resource", e);
                        }
                    }
                }
            }
        }

        /**
         * Profiles the sampling to make sure that it does not take longer than half a second to
         * complete, if it does, a warning is logged.
         *
         * @param statKey the key related to the statistic.
         * @param definition the statistic definition for the stat to be sampled.
         * @return the sample.
         */
        private double sampleStat(String statKey, StatDefinition definition) {
            long start = System.currentTimeMillis();
            double sample = definition.getStatistic().sample();
            if (System.currentTimeMillis() - start >= 500) {
                Log.warn("Stat " + statKey + " took longer than a second to sample.");
            }
            return sample;
        }
    }

    /**
     * Class to process all information retrieved from the stats databases. It also retains
     * any meta information related to these databases.
     *
     * @author Alexander Wenckus
     */
    private class DefaultStatDefinition extends StatDefinition {

        private String consolidationFunction;

        DefaultStatDefinition(String dbPath, String datasourceName, Statistic stat) {
            super(dbPath, datasourceName, stat);
            this.consolidationFunction = determineConsolidationFun(stat.getStatType());
        }

        private String determineConsolidationFun(Statistic.Type type) {
            switch (type) {
                case count:
                    return ConsolFuns.CF_LAST;
                default:
                    return ConsolFuns.CF_AVERAGE;
            }
        }

        @Override
		public double[][] getData(long startTime, long endTime) {
            return fetchData(consolidationFunction, startTime, endTime, -1);
        }

        @Override
		public double[][] getData(long startTime, long endTime, int dataPoints) {
            // Our greatest datapoints is 60 so if it is something less than that
            // then we want an average.
            return fetchData((dataPoints != 60 ? ConsolFuns.CF_AVERAGE : consolidationFunction),
                    startTime, endTime, dataPoints);
        }

        @Override
		public long getLastSampleTime() {
            return lastSampleTime;
        }

        @Override
		public double getLastSample() {
            return lastSample;
        }

        @Override
		public double[] getMax(long startTime, long endTime) {
            return getMax(startTime, endTime, 1);
        }

        private double discoverMax(double[] doubles) {
            double max = 0;
            for (double d : doubles) {
                if (d > max) {
                    max = d;
                }
            }
            return max;
        }

        private double[][] fetchData(String function, long startTime, long endTime, int dataPoints) {
            RrdDb db = null;
            try {
                db = new RrdDb(getDbPath(), true);

                FetchData data;
                if (dataPoints > 0) {
                    data = db.createFetchRequest(function, startTime, endTime,
                            getResolution(startTime, endTime, dataPoints)).fetchData();
                }
                else {
                    data = db.createFetchRequest(function, startTime, endTime).fetchData();
                }
                return data.getValues();
            }
            catch (IOException e) {
                Log.error("Error initializing Rrdb", e);
            }
            catch (RrdException e) {
                Log.error("Error initializing Rrdb", e);
            }
            finally {
                try {
                    if (db != null) {
                        db.close();
                    }
                }
                catch (IOException e) {
                    Log.error("Unable to release Rrdb resources",e);
                }
            }
            return null;
        }

        private long getResolution(long startTime, long endTime, int dataPoints) {
            return (endTime - startTime) / (dataPoints * 60);
        }

        @Override
		public double[] getMin(long startTime, long endTime) {
            return getMin(startTime, endTime, 1);
        }

        @Override
		public double[] getMin(long startTime, long endTime, int dataPoints) {
            double[][] fetchedData = fetchData(consolidationFunction, startTime,
                    endTime, dataPoints);
            if (fetchedData != null) {
                double[] toReturn = new double[fetchedData.length];
                for (int i = 0; i < fetchedData.length; i++) {
                    toReturn[i] = discoverMin(fetchedData[i]);
                }
                return toReturn;
            }
            return new double[] { 0 };
        }

        @Override
		public double[] getMax(long startTime, long endTime, int dataPoints) {
            double[][] fetchedData = fetchData(consolidationFunction, startTime,
                    endTime, dataPoints);
            if (fetchedData != null) {
                double[] toReturn = new double[fetchedData.length];
                for (int i = 0; i < fetchedData.length; i++) {
                    toReturn[i] = discoverMax(fetchedData[i]);
                }
                return toReturn;
            }
            return new double[] { 0 };
        }

        private double discoverMin(double[] doubles) {
            double min = doubles[0];
            for (double d : doubles) {
                if (d < min) {
                    min = d;
                }
            }
            return min;
        }
    }
}