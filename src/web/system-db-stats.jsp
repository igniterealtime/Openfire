<%--
  - $Revision: 27964 $
  - $Date: 2006-03-02 09:27:01 -0800 (Thu, 02 Mar 2006) $
  -
  - Copyright (C) 1999-2004 Jive Software. All rights reserved.
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="java.text.*"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.database.DbConnectionManager"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.database.ProfiledConnection"%>
<%@ page import="org.jivesoftware.database.ProfiledConnectionEntry"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>


<%! // Global methods, vars

    // Default refresh values
    static final int[] REFRESHES = {10,30,60,90};
%>

<%
    // Get parameters
    boolean doClear = request.getParameter("doClear") != null;
    String enableStats = ParamUtils.getParameter(request,"enableStats");
    int refresh = ParamUtils.getIntParameter(request,"refresh", -1);
    boolean doSortByTime = ParamUtils.getBooleanParameter(request,"doSortByTime");

    // Var for the alternating colors
    int rowColor = 0;

    // Clear the statistics
    if (doClear) {
        ProfiledConnection.resetStatistics();
        // Reload the page without params.
        response.sendRedirect("system-db-stats.jsp");
    }

    // Enable/disable stats
    if ("true".equals(enableStats)) {
        DbConnectionManager.setProfilingEnabled(true);
    }
    else if ("false".equals(enableStats)) {
        DbConnectionManager.setProfilingEnabled(false);
    }

    boolean showQueryStats = DbConnectionManager.isProfilingEnabled();

    // Number intFormat for pretty printing of large number values and decimals:
    NumberFormat intFormat = NumberFormat.getInstance(JiveGlobals.getLocale());
    DecimalFormat decFormat = new DecimalFormat("#,##0.00");
%>

<html>
    <head>
        <title>Database Query Statistics</title>
        <meta name="pageID" content="system-db-stats"/>
    <%  // Enable refreshing if specified
        if (refresh >= 10) {
    %>
        <meta http-equiv="refresh" content="<%= refresh %>;URL=system-db-stats.jsp?refresh=<%= refresh %>">

    <%  } %>
    <meta name="helpPage" content="enable_or_disable_database_query_statistics.html" />
</head>
<body>

<p>
Enable database query statistics to trace all database queries made by Wildfire. This can
be useful to debug issues and monitor database performance. However, it's not recommended that
you leave query statistics permanently running, as they will cause performance to degrade slightly.
</p>

<p><b>Query Statistics Status</b></p>

<form action="system-db-stats.jsp">
<ul>
    <table cellpadding="3" cellspacing="1" border="0">
    <tr>
        <td>
            <input type="radio" name="enableStats" value="true" id="rb01" <%= ((showQueryStats) ? "checked":"") %>>
            <label for="rb01"><%= ((showQueryStats) ? "<b>Enabled</b>":"Enabled") %></label>
        </td>
        <td>
            <input type="radio" name="enableStats" value="false" id="rb02" <%= ((!showQueryStats) ? "checked":"") %>>
            <label for="rb02"><%= ((!showQueryStats) ? "<b>Disabled</b>":"Disabled") %></label>
        </td>
        <td>
            <input type="submit" name="" value="Update">
        </td>
    </tr>
    </table>
</ul>
</form>

<%  if (showQueryStats) { %>

    <p><b>Query Statistics Settings</b></p>

    <form action="system-db-stats.jsp">
    <ul>
        <table cellpadding="3" cellspacing="1" border="0">
        <tr>
            <td>
                Refresh:
                <select size="1" name="refresh" onchange="this.form.submit();">
                <option value="none">None

                <%  for (int j=0; j<REFRESHES.length; j++) {
                        String selected = ((REFRESHES[j] == refresh) ? " selected" : "");
                %>
                    <option value="<%= REFRESHES[j] %>"<%= selected %>
                     ><%= REFRESHES[j] %> seconds

                <%  } %>
                </select>
            </td>
            <td>
                <input type="submit" name="" value="Set">
            </td>
            <td>|</td>
            <td>
                <input type="submit" name="" value="Update Now">
            </td>
            <td>|</td>
            <td>
                <input type="submit" name="doClear" value="Clear All Stats">
            </td>
        </tr>
        </table>
    </ul>
    </form>

    <br />

    <p>
    <b>SELECT Query Statistics</b>
    </p>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td>Total # of selects</td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Total time for all selects (ms)</td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Average time for all selects (ms)</td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Selects per second</td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>20 Most common selects</td>
        <td bgcolor="#ffffff"><%
                    ProfiledConnectionEntry[] list = ProfiledConnection.getSortedQueries(ProfiledConnection.SELECT, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println("No queries");
                    }
                    else { %>
                &nbsp;
         </td>
    </tr>
    </table>
    </td></tr>
    </table>

    <br />

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr bgcolor="#ffffff"><td>
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>Query</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">Count</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>Total Time</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">Avg. Time</a></b></td></tr>");

            for (int i = 0; i < ((list.length > 20) ? 20 : list.length); i++) {
                ProfiledConnectionEntry pce = list[i];
                out.println("<tr><td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + pce.sql + "</td>");
                out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.count) + "</td>");
                out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime) + "</td>");
                out.println("<td bgcolor=\"" + ((rowColor++%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime/pce.count) + "</td></tr>");
            }
            out.println("</table>");
        }
     %></td>
    </tr>
    </table>
    </td></tr>
    </table>

    </ul>

    <b>INSERT Query Statistics</b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td>Total # of inserts</td>
        <td><%= ProfiledConnection.getQueryCount(ProfiledConnection.INSERT) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Total time for all inserts (ms)</td>
        <td><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.INSERT) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Average time for all inserts (ms)</td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Inserts per second</td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>10 Most common inserts</td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.INSERT, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println("No queries");
                    }
                    else {  %>
                &nbsp;
         </td>
    </tr>
    </table>
    </td></tr>
    </table>

    <br />

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr bgcolor="#ffffff"><td>
    <%
                out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>Query</b></td>");
                out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">Count</a></b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b>Total Time</b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">Avg. Time</a></b></td></tr>");

                rowColor = 0;

                for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                    ProfiledConnectionEntry pce = list[i];
                    out.println("<tr><td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + pce.sql + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.count) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor++%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime/pce.count) + "</td></tr>");
                }
                out.println("</table>");
            }
        %></td>
    </tr>
    </table>
    </td></tr>
    </table>

    </ul>

    <b>UPDATE Query Statistics</b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td>Total # of updates</td>
        <td><%= ProfiledConnection.getQueryCount(ProfiledConnection.UPDATE) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Total time for all updates (ms)</td>
        <td><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.UPDATE) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Average time for all updates (ms)</td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Updates per second</td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>10 Most common updates</td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.UPDATE, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println("No queries");
                    }
                    else { %>
                &nbsp;
         </td>
    </tr>
    </table>
    </td></tr>
    </table>

    <br />

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr bgcolor="#ffffff"><td>
    <%
                out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>Query</b></td>");
                out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">Count</a></b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b>Total Time</b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">Avg. Time</a></b></td></tr>");

                rowColor = 0;

                for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                    ProfiledConnectionEntry pce = list[i];
                    out.println("<tr><td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + pce.sql + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.count) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor++%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime/pce.count) + "</td></tr>");
                }
                out.println("</table>");
            }
        %></td>
    </tr>
    </table>
    </td></tr>
    </table>

    </ul>

    <b>DELETE Query Statistics</b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td>Total # of deletes</td>
        <td><%= ProfiledConnection.getQueryCount(ProfiledConnection.DELETE) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Total time for all deletes (ms)</td>
        <td><%= ProfiledConnection.getTotalQueryTime(ProfiledConnection.DELETE) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Average time for all deletes (ms)</td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>Deletes per second</td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td>10 Most common deletes</td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.DELETE, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println("No queries");
                    }
                    else { %>
                &nbsp;
         </td>
    </tr>
    </table>
    </td></tr>
    </table>

    <br />

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr bgcolor="#ffffff"><td>
    <%
                out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>Query</b></td>");
                out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">Count</a></b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b>Total Time</b></td>");
                out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='system-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">Avg. Time</a></b></td></tr>");

                rowColor = 0;

                for (int i = 0; i < ((list.length > 10) ? 10 : list.length); i++) {
                    ProfiledConnectionEntry pce = list[i];
                    out.println("<tr><td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + pce.sql + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.count) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime) + "</td>");
                    out.println("<td bgcolor=\"" + ((rowColor++%2 == 0) ? "#efefef" : "#ffffff") + "\">" + intFormat.format(pce.totalTime/pce.count) + "</td></tr>");
                }
                out.println("</table>");
            }
        %></td>
    </tr>
    </table>
    </td></tr>
    </table>

    </ul>

<% } %>


</body></html>