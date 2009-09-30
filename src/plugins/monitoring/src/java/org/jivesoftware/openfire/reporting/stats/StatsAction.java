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

import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.archive.Conversation;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.reporting.graph.GraphEngine;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Provides the server side callbacks for client side JavaScript functions for
 * the stats dashboard page.
 *
 * @author Aaron Johnson
 */
public class StatsAction {


    /**
     * Retrieves a map containing the high / low and current count statistics
     * for the 'sessions', 'conversations' and 'packet_count' statistics.
     * @return map containing 3 maps (keys = 'sessions, 'conversations' and
     * 'packet_count') each containing an array of int (low value, high value
     * and current value).
     */
    public Map<String, Map> getUpdatedStats(String timePeriod) {
        Map<String, Map> results = new HashMap<String, Map>();
        long[] startAndEnd = GraphEngine.parseTimePeriod(timePeriod);
        String[] stats = new String[] {"sessions", "conversations", "packet_count",
                "proxyTransferRate", "muc_rooms", "server_sessions", "server_bytes"};
        for (String stat : stats) {
            results.put(stat, getUpdatedStat(stat, startAndEnd));
        }
        return results;
    }

    /**
     * Retrieve a a single stat update given a stat name and the name of a
     * time period.
     * @param statkey
     * @param timePeriod
     * @return map containing keys 'low', 'high' and 'count'.
     */
    public Map getUpdatedStat(String statkey, String timePeriod) {
        long[] startAndEnd = GraphEngine.parseTimePeriod(timePeriod);
        return getUpdatedStat(statkey, startAndEnd);
    }

    private Map getUpdatedStat(String statkey, long[] timePeriod) {
        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
        StatsViewer viewer = (StatsViewer)plugin.getModule(StatsViewer.class);
        String[] lowHigh = getLowAndHigh(statkey, timePeriod);
        Map stat = new HashMap();
        stat.put("low", lowHigh[0]);
        stat.put("high", lowHigh[1]);
        stat.put("count", (int)viewer.getCurrentValue(statkey)[0]);
        return stat;
    }


    /**
     * Retrieves the last n conversations from the system that were created after
     * the given conversationID.
     *
     * @param count the count of conversations to return.
     * @param mostRecentConversationID the last conversationID that has been retrieved.
     * @return a List of Map objects.
     */
    public List getNLatestConversations(int count, long mostRecentConversationID) {
        // TODO Fix plugin name 2 lines below and missing classes
        List<Map> cons = new ArrayList<Map>();
        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        Collection<Conversation> conversations = conversationManager.getConversations();
        List<Conversation> lConversations = Arrays.asList(conversations.toArray(new Conversation[conversations.size()]));
        Collections.sort(lConversations, conversationComparator);
        int counter = 0;
        for (Iterator<Conversation> i = lConversations.iterator(); i.hasNext() && counter < count;) {
            Conversation con = i.next();
            if (mostRecentConversationID == con.getConversationID()) {
                break;
            } else {
                Map mCon = new HashMap();
                mCon.put("conversationid", con.getConversationID());
                String users[];
                int usersIdx = 0;
                if (con.getRoom() == null) {
                    users = new String[con.getParticipants().size()];
                    for (JID jid : con.getParticipants()) {
                        String identifier = jid.toBareJID();
                        try {
                            identifier = UserNameManager.getUserName(jid, jid.toBareJID());
                        }
                        catch (UserNotFoundException e) {
                            // Ignore
                        }
                        users[usersIdx++] = StringUtils.abbreviate(identifier, 20);
                    }
                }
                else {
                    users = new String[2];
                    users[0] = LocaleUtils.getLocalizedString("dashboard.group_conversation", "monitoring");
                    try {
                        users[1] = "(<i>" + LocaleUtils.getLocalizedString("muc.room.summary.room") +
                                ": <a href='../../muc-room-occupants.jsp?roomName=" +
                                URLEncoder.encode(con.getRoom().getNode(), "UTF-8") + "'>" + con.getRoom().getNode() +
                                "</a></i>)";
                    } catch (UnsupportedEncodingException e) {
                        Log.error(e);
                    }
                }
                mCon.put("users", users);
                mCon.put("lastactivity", formatTimeLong(con.getLastActivity()));
                mCon.put("messages", con.getMessageCount());
                cons.add(0, mCon);
                counter++;
            }
        }
        return cons;
    }

    /**
     * Given a statistic key and a start date, end date and number of datapoints, returns
     * a String[] containing the low and high values (in that order) for the given time period.
     * 
     * @param key the name of the statistic to return high and low values for.
     * @param timePeriod start date, end date and number of data points.
     * @return low and high values for the given time period / number of datapoints
     */
    public static String[] getLowAndHigh(String key,  long[] timePeriod) {
        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
        StatsViewer viewer = (StatsViewer)plugin.getModule(StatsViewer.class);
        Statistic.Type type = viewer.getStatistic(key)[0].getStatType();
        double[] lows = viewer.getMin(key, timePeriod[0], timePeriod[1], (int)timePeriod[2]);
        double[] highs = viewer.getMax(key, timePeriod[0], timePeriod[1], (int)timePeriod[2]);
        String low;
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(0);
        if(lows.length > 0) {
            if(type == Statistic.Type.count) {
                low = String.valueOf((int) lows[0]);
            }
            else {
                double l = lows[0];
                if(Double.isNaN(l)) {
                    l = 0;
                }
                low = format.format(l);
            }
        }
        else {
            low = String.valueOf(0);
        }
        String high;
        if(highs.length > 0) {
            if(type == Statistic.Type.count) {
                high = String.valueOf((int) highs[0]);
            }
            else {
                double h= highs[0];
                if(Double.isNaN(h)) {
                    h = 0;
                }
                high = format.format(h);
            }

        }
        else {
            high = String.valueOf(0);
        }

        return new String[]{low, high};
    }

    private Comparator<Conversation> conversationComparator = new Comparator<Conversation>() {
        public int compare(Conversation conv1, Conversation conv2) {
           return conv2.getLastActivity().compareTo(conv1.getLastActivity());
        }
    };

    /**
     * Formats a given time using the <code>DateFormat.MEDIUM</code>. In the 'en' locale, this
     * should result in a time formatted like this: 4:59:23 PM. The seconds are necessary when
     * displaying time in the conversation scroller.
     * @param time
     * @return string a date formatted using DateFormat.MEDIUM
     */
    public static String formatTimeLong(Date time) {
        DateFormat formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM, JiveGlobals.getLocale());
        formatter.setTimeZone(JiveGlobals.getTimeZone());
        return formatter.format(time);
    }

}
