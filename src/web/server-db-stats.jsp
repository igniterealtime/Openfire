<%--
  - $Revision: 27964 $
  - $Date: 2006-03-02 09:27:01 -0800 (Thu, 02 Mar 2006) $
  -
  - Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

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
        response.sendRedirect("server-db-stats.jsp");
    }

    // Enable/disable stats
    if ("true".equals(enableStats)) {
        DbConnectionManager.setProfilingEnabled(true);
        // Log the event
        webManager.logEvent("enabled db profiling", null);
    }
    else if ("false".equals(enableStats)) {
        DbConnectionManager.setProfilingEnabled(false);
        // Log the event
        webManager.logEvent("disabled db profiling", null);
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




<div class="jive-contentBox jive-contentBoxGrey" style="width: 732px;">
<h3><fmt:message key="server.db_stats.status" /></h3>

<form action="server-db-stats.jsp">
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
</form>

<%  if (showQueryStats) { %>
	<br>
	<h3><fmt:message key="server.db_stats.settings" /></h3>

    <form action="server-db-stats.jsp">
        <table cellpadding="3" cellspacing="5" border="0">
        <tr>
            <td>
                <fmt:message key="server.db_stats.refresh" />:
                <select size="1" name="refresh" onchange="this.form.submit();">
                <option value="none"><fmt:message key="server.db_stats.none" />

                <%  for(int aREFRESHES: REFRESHES){
                        String selected = ((aREFRESHES == refresh) ? " selected" : "");
                %>
                    <option value="<%= aREFRESHES %>"<%= selected %>
                     ><%= aREFRESHES
                            %> <fmt:message key="server.db_stats.seconds" />

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
    </form>

</div>


    <b><fmt:message key="server.db_stats.select_stats" /></b>

    <ul>

    <table bgcolor="#aaaaaa" cellpadding="0" cellspacing="0" border="0" width="600">
    <tr><td>
    <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.operations" /></td>
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.select)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.select)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.select)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    ProfiledConnectionEntry[] list = ProfiledConnection.getSortedQueries(ProfiledConnection.Type.select, doSortByTime);

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
    <%      out.println("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"1\" border=\"0\" bgcolor=\"#aaaaaa\"><tr><td bgcolor=\"#ffffff\" align=\"left\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.query") + "</b></td>");
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.insert)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.insert)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.insert)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.insert)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.Type.insert, doSortByTime);

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
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.update)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.update)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.update)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.update)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.Type.update, doSortByTime);

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
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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
        <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.delete)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_time" /></td>
        <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.delete)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.avg_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.delete)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.total_rate" /></td>
        <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.delete)) %></td>
    </tr>
    <tr bgcolor="#ffffff">
        <td><fmt:message key="server.db_stats.queries" /></td>
        <td bgcolor="#ffffff"><%
                    list = ProfiledConnection.getSortedQueries(ProfiledConnection.Type.delete, doSortByTime);

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
            out.println("<td bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.count") + "</a></b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b>" + LocaleUtils.getLocalizedString("server.db_stats.time") + "</b></td>");
            out.println("<td nowrap bgcolor=\"#ffffff\"><b><a href=\"javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=" + refresh + ";'\">" + LocaleUtils.getLocalizedString("server.db_stats.average_time") + "</a></b></td></tr>");

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