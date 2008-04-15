package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


/**
 * <code>ChatHistoryImpl</code> class is used for statistical reporting of
 * the Live Assistant Server.
 *
 * @author Derek DeMoro
 */
public final class ChatHistoryUtils {

    private static final String ALL_SESSION_TIMES =
            "SELECT startTime, endTime FROM fpSession";
    private static final String ACCEPTED_CHATS_COUNT =
            "SELECT count(*) FROM fpSession WHERE state=2 AND workgroupID=? " +
            "AND startTime >= ? AND endTime <= ?";
    private static final String CHAT_TIMES_FOR_WORKGROUPS =
            "SELECT startTime, endTime FROM fpSession WHERE workgroupID=?";
    private static final String WORKGROUP_REQUEST_COUNT =
            "SELECT count(*) FROM fpSession WHERE workgroupID=? AND startTime >= ? " +
            "AND endTime <= ?";
    private static final String WORKGROUP_STATE_REQUEST_COUNT =
            "SELECT count(*) FROM fpSession where workgroupID=? AND state=? AND startTime >= ? " +
            "AND endTime <= ?";
    private static final String ALL_SESSIONS =
            "SELECT sessionID FROM fpSession";
    private static final String ALL_CHATS_COUNT =
            "SELECT count(*) FROM fpSession WHERE state=2";
    private static final String ALL_REQUESTS_COUNT =
            "SELECT count(*) FROM fpSession";
    private static final String TOTAL_WAIT_TIME =
            "SELECT sum(queueWaitTime) FROM fpSession";
    private static final String WORKGROUP_WAIT_TIME =
            "SELECT sum(queueWaitTime) FROM fpSession WHERE workgroupID=? AND startTime >= ? " +
            "AND endTime <= ?";

    /**
     * Creates a new ChatHistoryImpl object.
     */
    private ChatHistoryUtils() {
    }


    /**
     * Returns the average chat time for all workgroups in the server.
     *
     * @return the average time of all chats.
     */
    public static long getAverageChatLengthForServer() {
        int numberOfChats = getTotalChatsInSystem();
        long chatLength = getTotalTimeForAllChatsInServer();

        if(numberOfChats == 0 ) {
            return 0;
        }
        return chatLength / numberOfChats;
    }

    /**
     * Returns the total amount of time for all the chats in all workgroups.
     *
     * @return the total length of all chats in the system.
     */
    public static long getTotalTimeForAllChatsInServer() {
        int totalWorkgroupChatTime = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_SESSION_TIMES);

            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    String startTimeString = rs.getString(1);
                    String endTimeString = rs.getString(2);

                    if ((startTimeString != null) && (startTimeString.trim().length() > 0) &&
                            (endTimeString != null) && (endTimeString.trim().length() > 0)) {
                        long startLong = Long.parseLong(startTimeString);
                        long endLong = Long.parseLong(endTimeString);

                        totalWorkgroupChatTime += endLong - startLong;
                    }
                }
                catch (SQLException e) {
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
                }
                catch (NumberFormatException e) {
                    ComponentManagerFactory.getComponentManager().getLog().error(e);
                }
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return totalWorkgroupChatTime;
    }

    /**
     * Returns the number of chat requests that were accepted.
     *
     * @param workgroupName the name of the workgroup where the request(s) were made.
     * @param startDate the start date.
     * @param endDate the end date.
     * @return the number of chats requests accepted by the workgroup.
     */
    public static int getNumberOfChatsAccepted(String workgroupName, Date startDate, Date endDate) {
        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        if (workgroup == null) {
            return 0;
        }

        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ACCEPTED_CHATS_COUNT);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, StringUtils.dateToMillis(startDate));
            pstmt.setString(3, StringUtils.dateToMillis(endDate));

            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return count;
    }

    /**
     * Returns the total chat length of an individual workgroup.
     *
     * @param workgroupName the name of the workgroup.
     * @return the total length of all chats in the specified workgroup.
     */
    public static long getTotalChatTimeForWorkgroup(String workgroupName) {
        Workgroup workgroup = null;

        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }

        int totalWorkgroupChatTime = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CHAT_TIMES_FOR_WORKGROUPS);
            pstmt.setLong(1, workgroup.getID());
            rs = pstmt.executeQuery();

            while (rs.next()) {
                String startTimeString = rs.getString(1);
                String endTimeString = rs.getString(2);

                if ((startTimeString != null) && (startTimeString.trim().length() > 0) &&
                        (endTimeString != null) && (endTimeString.trim().length() > 0)) {
                    long startLong = Long.parseLong(startTimeString);
                    long endLong = Long.parseLong(endTimeString);

                    totalWorkgroupChatTime += endLong - startLong;
                }
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return totalWorkgroupChatTime;
    }

    /**
     * Returns the number of request made to a workgroup between
     * specified dates.
     *
     * @param workgroupName the workgroup to search
     * @param startDate the time to begin the search from.
     * @param endDate the time to end the search.
     * @return the total number of requests
     */
    public static int getNumberOfRequestsForWorkgroup(String workgroupName, Date startDate, Date endDate) {
        Workgroup workgroup = getWorkgroup(workgroupName);
        if (workgroup == null) {
            return 0;
        }

        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(WORKGROUP_REQUEST_COUNT);
            pstmt.setLong(1, workgroup.getID());
            pstmt.setString(2, StringUtils.dateToMillis(startDate));
            pstmt.setString(3, StringUtils.dateToMillis(endDate));

            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return count;
    }

    /**
     * Returns the number of canceled requests.
     *
     * @param workgroupName the workgroup to search
     * @param startDate the time to begin the search from.
     * @param endDate the time to end the search.
     * @return the total number of requests
     */
    public static int getNumberOfRequestsCancelledByUser(String workgroupName, Date startDate, Date endDate) {
        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        if (workgroup == null) {
            return 0;
        }

        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(WORKGROUP_STATE_REQUEST_COUNT);
            pstmt.setLong(1, workgroup.getID());
            // Set the state the cancelled requests.
            pstmt.setInt(2, 0);
            pstmt.setString(3, StringUtils.dateToMillis(startDate));
            pstmt.setString(4, StringUtils.dateToMillis(endDate));

            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return count;
    }

    /**
     * Returns an iterator of all sessionID's in the system.
     *
     * @return an iterator of sessionID's.
     */
    public static Iterator<String> getSessionIDs() {
        final List<String> sessionList = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_SESSIONS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                sessionList.add(rs.getString(1));
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return sessionList.iterator();
    }

    /**
     * Returns the number of canceled requests.
     *
     * @param workgroupName the workgroup to search
     * @param startDate the time to begin the search from.
     * @param endDate the time to end the search.
     * @return the total number of requests
     */
    public static int getNumberOfRequestsNeverPickedUp(String workgroupName, Date startDate, Date endDate) {
        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        if (workgroup == null) {
            return 0;
        }

        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(WORKGROUP_STATE_REQUEST_COUNT);
            pstmt.setLong(1, workgroup.getID());
            // Set the state the ignored requests.
            pstmt.setInt(2, 1);
            pstmt.setString(3, StringUtils.dateToMillis(startDate));
            pstmt.setString(4, StringUtils.dateToMillis(endDate));

            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return count;
    }

    /**
     * Returns the total number of chats that have occured within a workgroup.
     *
     * @param workgroupName the jid of the workgroup.
     * @return the total number of chats that have occured within a workgroup.
     */
    public static int getNumberOfChatsForWorkgroup(String workgroupName) {
        Workgroup workgroup = null;

        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }

        int count = 0;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()){
            count += requestQueue.getTotalChatCount();
        }

        return count;
    }

    /**
     * Returns the average wait time in the system.
     *
     * @return the average wait time.
     */
    public static String getAverageWaitTimeForServer() {
        int totalRequests = getTotalRequestCountForSystem();
        long totalWaitTime = getTotalWaitTimeForServer();
        if (totalRequests == 0 ) {
            return "0 sec.";
        }

        long averageWaitTime = totalWaitTime / totalRequests;

        return getDateFromLong(averageWaitTime);
    }

    /**
     * Returns the average wait time for a specified workgroup between two dates.
     *
     * @param workgroupName the Live Assistant Workgroup.
     * @param startDate     the startDate.
     * @param endTime       the end date.
     * @return the average wait time for this workgroup.
     */
    public static long getAverageWaitTimeForWorkgroup(String workgroupName, Date startDate, Date endTime) {
        int totalRequests = getNumberOfRequestsForWorkgroup(workgroupName, startDate, endTime);
        long waitTime = getTotalWaitTimeForWorkgroup(workgroupName, startDate, endTime);

        if (totalRequests == 0) {
            return 0;
        }

       return waitTime / totalRequests;
    }

    /**
     * Returns the total number of chats.
     *
     * @return the total number of chats.
     */
    public static int getTotalChatsInSystem() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_CHATS_COUNT);
            rs = pstmt.executeQuery();
            rs.next();
            count = rs.getInt(1);
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    /**
     * Retruns the total number of requests.
     *
     * @return the total number of requests.
     */
    public static int getTotalRequestCountForSystem() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_REQUESTS_COUNT);
            rs = pstmt.executeQuery();
            rs.next();
            count = rs.getInt(1);
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    /**
     * Returns the total waitTime for all incoming requests.
     *
     * @return the total wait time.
     */
    public static long getTotalWaitTimeForServer() {
        int totalWaitTime = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(TOTAL_WAIT_TIME);
            rs = pstmt.executeQuery();
            rs.next();
            totalWaitTime = rs.getInt(1);
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return totalWaitTime;
    }

    /**
     * Returns the number of canceled requests.
     *
     * @param workgroupName the workgroup to search
     * @param startDate the time to begin the search from.
     * @param endDate the time to end the search.
     * @return the total number of requests
     */
    public static long getTotalWaitTimeForWorkgroup(String workgroupName, Date startDate, Date endDate) {
        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        if (workgroup == null) {
            return 0;
        }

        int waitTime = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(WORKGROUP_WAIT_TIME);
            pstmt.setLong(1, workgroup.getID());
            // Set the state the ignored requests.
            pstmt.setInt(2, 1);
            pstmt.setString(2, StringUtils.dateToMillis(startDate));
            pstmt.setString(3, StringUtils.dateToMillis(endDate));

            rs = pstmt.executeQuery();
            rs.next();
            waitTime = rs.getInt(1);
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return waitTime;
    }

    /**
     * Returns a formatted string to display minutes and seconds.
     *
     * @param time the number of milliseconds.
     * @return a formatted string.
     */
    public static String getDateFromLong(long time) {
        int aTime;
        int minutes;
        int seconds;
        String displayString;

        if (time > 0) {
            aTime = (int) (time) / 1000;
            minutes = aTime / 60;
            seconds = aTime % 60;

            if (minutes != 0) {
                displayString = minutes + " min, " + seconds + " sec.";
            }
            else {
                displayString = seconds + " seconds";
            }
        }
        else {
            return "0 seconds";
        }

        return displayString;
    }

    /**
     * Returns a <code>Workgroup</code> based on it's full jid.
     * @param workgroupJID the full jid of the workgroup (ex. demo@workgroup.jivesoftware.com)
     * @return the Workgroup
     */
    public static  Workgroup getWorkgroup(String workgroupJID) {
        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupJID));
        }
        catch (UserNotFoundException e) {
            ComponentManagerFactory.getComponentManager().getLog().error("Error retrieving Workgroup", e);
        }
        return workgroup;
    }
}