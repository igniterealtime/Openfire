/*
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

    private final Map<String, Statistic> statistics = new ConcurrentHashMap<>();
    private final Map<String, List<String>> multiStatGroups = new ConcurrentHashMap<>();
    private final Map<String, String> keyToGroupMap = new ConcurrentHashMap<>();

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
            group = new ArrayList<>();
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
