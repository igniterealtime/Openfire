/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.stats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores statistics being tracked by the server.
 */
public class StatisticsManager {

    private static StatisticsManager instance = new StatisticsManager();

    public static StatisticsManager getInstance() {
        return instance;
    }

    private final Map<String, Statistic> statistics = new ConcurrentHashMap<String, Statistic>();
    private final Map<String, List<String>> multiStatGroups = new ConcurrentHashMap<String, List<String>>();
    private final Map<String, String> keyToGroupMap = new ConcurrentHashMap<String, String>();

    private StatisticsManager() {
        
    }

    /**
     * Adds a stat to be tracked to the StatManager.
     *
     * @param statKey the statistic key.
     * @param definition the statistic to be tracked.
     */
    public void addStatistic(String statKey, Statistic definition) {
        statistics.put(statKey, definition);
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

    public void addMultiStatistic(String statKey, String groupName, Statistic statistic) {
        addStatistic(statKey, statistic);
        List<String> group = multiStatGroups.get(groupName);
        if(group == null) {
            group = new ArrayList<String>();
            multiStatGroups.put(groupName, group);
        }
        group.add(statKey);
        keyToGroupMap.put(statKey, groupName);
    }

    public List<String> getStatGroup(String statGroup) {
        return multiStatGroups.get(statGroup);
    }

    public String getMultistatGroup(String statKey) {
        return keyToGroupMap.get(statKey);
    }

    /**
     * Returns all statistics that the StatManager is tracking.
     * @return Returns all statistics that the StatManager is tracking.
     */
    public Set<Map.Entry<String, Statistic>> getAllStatistics() {
        return statistics.entrySet();
    }

    /**
     * Removes a statistic from the server.
     *
     * @param statKey The key of the stat to be removed.
     */
    public void removeStatistic(String statKey) {
        statistics.remove(statKey);
    }

}