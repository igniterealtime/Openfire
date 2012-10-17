/**
 * $Revision$
 * $Date$
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

import java.util.concurrent.atomic.AtomicInteger;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.openfire.stats.i18nStatistic;
import org.picocontainer.Startable;
import org.xmpp.packet.Packet;

/**
 * Creates and manages Enteprise-specific statistics, specifically: <ul>
 *      <li>Incoming and outgoing packet traffic.
 *      <li>Server to server connections.
 *      <li>Active group chat rooms.
 *      <li>Active user sessions.
 * </ul>
 *
 * @author Derek DeMoro
 */
public class StatisticsModule implements Startable {
    public static final String MUC_ROOMS_KEY = "active_group_chats";
    public static final String SERVER_2_SERVER_SESSIONS_KEY = "server_sessions";
    public static final String SESSIONS_KEY = "sessions";
    public static final String TRAFFIC_KEY = "packet_count";

    private StatisticsManager statisticsManager;

    private AtomicInteger packetCount = new AtomicInteger();
    private PacketInterceptor packetInterceptor;

    public void start() {
        // Retrieve instance of StatisticsManager
        statisticsManager = StatisticsManager.getInstance();

        // Register a packet listener so that we can track packet traffic.
        packetInterceptor = new PacketInterceptor() {
            public void interceptPacket(Packet packet, Session session, boolean incoming,
                                        boolean processed)
            {
                // Only track processed packets so that we don't count them twice.
                if (processed) {
                    packetCount.incrementAndGet();
                }
            }
        };
        InterceptorManager.getInstance().addInterceptor(packetInterceptor);

        // Register all statistics.
        addServerToServerStatistic();
        addActiveSessionsStatistic();
        addPacketStatistic();
    }

    /**
     * Remove all registered statistics.
     */
    public void stop() {

        // Remove Server to Server Statistic
        statisticsManager.removeStatistic(SERVER_2_SERVER_SESSIONS_KEY);

        // Remove Active Session Statistic
        statisticsManager.removeStatistic(SESSIONS_KEY);

        // Remove Packet Traffic Statistic
        statisticsManager.removeStatistic(TRAFFIC_KEY);

        statisticsManager = null;

        // Remove the packet listener.
        InterceptorManager.getInstance().removeInterceptor(packetInterceptor);
        packetInterceptor = null;
        packetCount = null;
    }

    /**
     * Tracks the number of Server To Server connections taking place in the server at anyone time.
     * This includes both incoming and outgoing connections.
     */
    private void addServerToServerStatistic() {
        // Register a statistic.
        Statistic serverToServerStatistic = new i18nStatistic(SERVER_2_SERVER_SESSIONS_KEY, MonitoringConstants.NAME,
                Statistic.Type.count)
        {
            public double sample() {
                return (SessionManager.getInstance().getIncomingServers().size() + SessionManager.
                        getInstance().getOutgoingServers().size());
            }

            public boolean isPartialSample() {
                return false;
            }
        };

        // Add to StatisticsManager
        statisticsManager.addStatistic(SERVER_2_SERVER_SESSIONS_KEY, serverToServerStatistic);
    }

    /**
     * Tracks the number of Active Sessions with the server at any point in time.
     * Active Sessions are defined as one client connection.
     */
    private void addActiveSessionsStatistic() {
        // Register a statistic.
        Statistic activeSessionStatistic = new i18nStatistic(SESSIONS_KEY, MonitoringConstants.NAME, Statistic.Type.count) {
            public double sample() {
                return SessionManager.getInstance().getUserSessionsCount(false);
            }

            public boolean isPartialSample() {
                return false;
            }
        };
        statisticsManager.addStatistic(SESSIONS_KEY, activeSessionStatistic);
    }

    /**
     * Tracks the total number of packets both incoming and outgoing in the server.
     */
    private void addPacketStatistic() {
        // Register a statistic.
        Statistic packetTrafficStatistic = new i18nStatistic(TRAFFIC_KEY, MonitoringConstants.NAME, Statistic.Type.rate) {
            public double sample() {
                return packetCount.getAndSet(0);
            }

            public boolean isPartialSample() {
                return true;
            }
        };
        statisticsManager.addStatistic(TRAFFIC_KEY, packetTrafficStatistic);
    }
}