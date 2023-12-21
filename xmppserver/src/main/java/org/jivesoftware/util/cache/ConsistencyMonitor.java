/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved
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
package org.jivesoftware.util.cache;

import com.google.common.collect.Multimap;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Caches, especially clustered caches, have various supporting data structures that allow them to work properly in
 * certain conditions. The content of these caches and their supporting data structures are expected to be in a certain
 * consistent state. This monitor periodically performs a check if this is true.
 *
 * Note that validation of consistency is a resource intensive process. Enabling the monitor might have severe effects
 * on the availability and performance of the Openfire domain.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConsistencyMonitor
{
    private static final Logger Log = LoggerFactory.getLogger(ConsistencyMonitor.class);

    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("cache.checks.consistency.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(a -> getInstance().reinitialize())
        .build();

    public static final SystemProperty<Duration> DELAY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("cache.checks.consistency.delay")
        .setDefaultValue(Duration.ofSeconds(20)) // Should be long enough to allow for human interaction in the admin console to not cause immediate, consecutive executions.
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(true)
        .addListener(a -> getInstance().reinitialize())
        .build();

    public static final SystemProperty<Duration> PERIOD = SystemProperty.Builder.ofType(Duration.class)
        .setKey("cache.checks.consistency.period")
        .setDefaultValue(Duration.ofMinutes(30))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(true)
        .addListener(a -> getInstance().reinitialize())
        .build();

    public static ConsistencyMonitor INSTANCE;

    public synchronized static ConsistencyMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConsistencyMonitor();
            INSTANCE.reinitialize();
        }
        return INSTANCE;
    }

    private Timer timer = new Timer();
    private Task task = null;

    private ConsistencyMonitor() {}

    protected synchronized void reinitialize()
    {
        final boolean isCurrentlyRunning = task != null;
        final boolean shouldRun = ENABLED.getValue();

        if (shouldRun) {
            Log.info("Applying configuration for cache consistency check. Enabled: {}, initial delay: {}, frequency: {}", shouldRun, DELAY.getValue(), PERIOD.getValue());
        } else {
            Log.info("Applying configuration for cache consistency check. Enabled: {}", shouldRun);
        }

        // Always cancel any running task (if only to restart with most up-to-date configuration).
        if (isCurrentlyRunning) {
            timer.cancel();
            task = null;
        }

        if (shouldRun) {
            timer = new Timer();
            task = new Task();
            timer.schedule(task, DELAY.getValue().toMillis(), PERIOD.getValue().toMillis());
        }
    }

    protected static class Task extends TimerTask
    {
        @Override
        public void run()
        {
            final Instant start = Instant.now();
            Log.debug("Starting new cache consistency check.");

            final RoutingTableImpl routingTable = (RoutingTableImpl) XMPPServer.getInstance().getRoutingTable();
            final SessionManager sessionManager = XMPPServer.getInstance().getSessionManager();
            final MultiUserChatManager multiUserChatManager = XMPPServer.getInstance().getMultiUserChatManager();

            final Set<String> offenders = new HashSet<>();

            final Collection<String> serverRoutesFailures = routingTable.clusteringStateConsistencyReportForServerRoutes().get("fail");
            if (serverRoutesFailures != null && !serverRoutesFailures.isEmpty()) {
                offenders.add(RoutingTableImpl.S2S_CACHE_NAME);
            }

            final Collection<String> componentRoutesFailures = routingTable.clusteringStateConsistencyReportForComponentRoutes().get("fail");
            if (componentRoutesFailures != null && !componentRoutesFailures.isEmpty()) {
                offenders.add(RoutingTableImpl.COMPONENT_CACHE_NAME);
            }

            final Collection<String> clientRoutesFailures = routingTable.clusteringStateConsistencyReportForClientRoutes().get("fail");
            if (clientRoutesFailures != null && !clientRoutesFailures.isEmpty()) {
                // This check operates on multiple caches.
                offenders.add(RoutingTableImpl.C2S_CACHE_NAME);
                offenders.add(RoutingTableImpl.ANONYMOUS_C2S_CACHE_NAME);
            }

            final Collection<String> usersSessionsFailures = routingTable.clusteringStateConsistencyReportForUsersSessions().get("fail");
            if (usersSessionsFailures != null && !usersSessionsFailures.isEmpty()) {
                offenders.add(RoutingTableImpl.C2S_SESSION_NAME);
            }

            final Collection<String> incomingServerSessionsFailures = sessionManager.clusteringStateConsistencyReportForIncomingServerSessionInfos().get("fail");
            if (incomingServerSessionsFailures != null && !incomingServerSessionsFailures.isEmpty()) {
                offenders.add(SessionManager.ISS_CACHE_NAME);
            }

            final Collection<String> sessionInfosFailures = sessionManager.clusteringStateConsistencyReportForSessionInfos().get("fail");
            if (sessionInfosFailures != null && !sessionInfosFailures.isEmpty()) {
                offenders.add(SessionManager.C2S_INFO_CACHE_NAME);
            }

            final List<Multimap<String, String>> mucReportsList = multiUserChatManager.clusteringStateConsistencyReportForMucRoomsAndOccupant();
            final Collection<String> mucOccupantFailures = new ArrayList<>();
            for (Multimap<String, String> mucReport : mucReportsList) {
                mucReport.get("fail").addAll(mucOccupantFailures);
            }
            if (!mucOccupantFailures.isEmpty()) {
                offenders.add("MUC Service");
            }

            if (offenders.isEmpty()) {
                Log.info("Cache consistency check completed in {}. No issues found.", Duration.between(start, Instant.now()));
            } else {
                Log.warn("Cache consistency check completed in {}. Detected issues in: {}", Duration.between(start, Instant.now()), String.join(", ", offenders));
                XMPPServer.getInstance().sendMessageToAdmins("Cache inconsistencies were detected. This can cause bugs, especially when running in a cluster. " +
                    "If this problem persists, all Openfire instances in the cluster need to be restarted at the same time. Affected cache(s): " + String.join(", ", offenders));
            }
        }
    }
}
