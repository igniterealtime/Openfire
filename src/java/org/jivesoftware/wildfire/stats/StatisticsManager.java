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
package org.jivesoftware.wildfire.stats;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Stores statistics being tracked by the server.
 */
public class StatisticsManager {
    private static StatisticsManager instance = new StatisticsManager();

    public static StatisticsManager getInstance() {
        return instance;
    }

    private final Map<String, Statistic> statistics = new HashMap<String, Statistic>();

    private StatisticsManager() {}

    /**
     * Adds a stat to be tracked to the StatManager.
     *
     * @param definition The statistic to be tracked.
     */
    public void addStatistic(Statistic definition) {
        statistics.put(definition.getKey(), definition);
    }

    /**
     * Returns a statistic being tracked by the StatManager.
     *
     * @param statKey The key of the definition.
     * @return Returns the related stat.
     */
    public Statistic getStatistic(String statKey) {
        return statistics.get(statKey);
    }

    /**
     * Returns all statistics that the StatManager is tracking.
     * @return Returns all statistics that the StatManager is tracking.
     */
    public Collection<Statistic> getAllStatistics() {
        return statistics.values();
    }

}
