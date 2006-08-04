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
<%@ page import="org.jivesoftware.util.LocaleUtils"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

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
        <title><fmt:message key="server.db_stats.title" /></title>
        <meta name="pageID" content="server-db"/>
    <%  // Enable refreshing if specified
        if (refresh >= 10) {
    %>
        <meta http-equiv="refresh" content="<%= refresh %>;URL=server-db-stats.jsp?refresh=<%= refresh %>">

    <%  } %>
</head>
<body>

<p>
<fmt:message key="server.db_stats.description" />
</p>

<p><b><fmt:message key="server.db_stats.status" /></b></p>

<form action="server-db-stats.jsp">
<ul>
    <table cellpadding="3" cellspacing="1" border="0">
    <tr>
        <td>
            <input type="radio" name="enableStats" value="true" id="rb01" <%= ((showQueryStats) ? "checked":"") %>>
            <label for="rb01"><%= ((showQueryStats) ? "<b>" +
                    LocaleUtils.getLocalizedString("server.db_stats.enabled") + "</b>": LocaleUtils.getLocalizedString("server.db_stats.enabled")) %></label>
        </td>
        <td>
            <input type="radio" name="enableStats" value="false" id="rb02" <%= ((!showQueryStats) ? "checked":"") %>>
            <label for="rb02"><%= ((!showQueryStats) ? "<b>" +
                     LocaleUtils.getLocalizedString("server.db_stats.disabled") + "</b>":  LocaleUtils.getLocalizedString("server.db_stats.disabled")) %></label>
        </td>
        <td>
            <input type="submit" name="" value="<fmt:message key="server.db_stats.update" />">
        </td>
    </tr>
    </table>
</ul>
</form>

<%  if (showQueryStats) { %>

    <p><b><fmt:message key="server.db_stats.settings" /></b></p>

    <form action="server-db-stats.jsp">
    <ul>
        <table cellpadding="3" cellspacing="1" border="0">
        <tr>
            <td>
                <fmt:message key="server.db_stats.refresh" />:
                <select size="1" name="refresh" onchange="this.form.submit();">
                <option value="none"><fmt:message key="server.db_stats.none" />

                <%  for (int j=0; j<REFRESHES.length; j++) {
                        String selected = ((REFRESHES[j] == refresh) ? " selected" : "");
                %>
                    <option value="<%= REFRESHES[j] %>"<%= selected %>
                     ><%= REFRESHES[j] %> <fmt:message key="server.db_stats.seconds" />

                <%  } %>
                </select>
            </td>
            <td>
                <input type="submit" name="" value="<fmt:message key="server.db_stats.set" />">
            </td>
            <td>|</td>
            <td>
                <input type="submit" name="" value="<fmt:message key="server.db_stats.update" />">
            </td>
            <td>|</td>
            <td>
                <input type="submit" name="doClear" value="<fmt:message key="server.db_stats.clear_stats" />">
            </td>
        </tr>
        </table>
    </ul>
    </form>

    <br />

    <p>
    <b><fmt:message key="server.db_stats.select_stats" /></b>
    </p>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.operations" /></td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.SELECT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    ProfiledConnectionEntry[] list = ProfiledConnection.getSortedQueries(ProfiledConnection.SELECT, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println(LocaleUtils.getLocalizedString("server.db_stats.no_queries"));
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
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.query") + "</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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

    <b><fmt:message key="server.db_stats.insert_stats" /></b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.operations" /></td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.INSERT)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.INSERT, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println(LocaleUtils.getLocalizedString("server.db_stats.no_queries"));
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
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.query") + "</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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

    <b><fmt:message key="server.db_stats.update_stats" /></b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.operations" /></td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.UPDATE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.UPDATE, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println(LocaleUtils.getLocalizedString("server.db_stats.no_queries"));
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
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.query") + "</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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

    <b><fmt:message key="server.db_stats.delete_stats" /></b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.operations" /></td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.DELETE)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.DELETE, doSortByTime);

                    if (list == null || list.length < 1) {
                        out.println(LocaleUtils.getLocalizedString("server.db_stats.no_queries"));
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
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"middle\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.query") + "</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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