<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page import="java.text.*"
         errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.database.DbConnectionManager"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.database.ProfiledConnection"%>
<%@ page import="org.jivesoftware.database.ProfiledConnectionEntry"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

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
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    boolean csrf_check = true;

    if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
        csrf_check = false;
        doClear = false;
    }
    csrfParam = StringUtils.randomString(16);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Var for the alternating colors
    int rowColor = 0;

    // Clear the statistics
    if (doClear) {
        ProfiledConnection.resetStatistics();
        // Reload the page without params.
        response.sendRedirect("server-db-stats.jsp");
    }

    if (csrf_check) {
        // Enable/disable stats
        if ("true".equals(enableStats) && ! DbConnectionManager.isProfilingEnabled()) {
            DbConnectionManager.setProfilingEnabled(true);
            // Log the event
            webManager.logEvent("enabled db profiling", null);
        }
        else if ("false".equals(enableStats) && DbConnectionManager.isProfilingEnabled()) {
            DbConnectionManager.setProfilingEnabled(false);
            // Log the event
            webManager.logEvent("disabled db profiling", null);
        }
    }

    // Number intFormat for pretty printing of large number values and decimals:
    NumberFormat intFormat = NumberFormat.getInstance(JiveGlobals.getLocale());
    DecimalFormat decFormat = new DecimalFormat("#,##0.00");

    pageContext.setAttribute("refresh", refresh);
    pageContext.setAttribute("profilingEnabled", DbConnectionManager.isProfilingEnabled());
    pageContext.setAttribute("selectList", ProfiledConnection.getSortedQueries(ProfiledConnection.Type.select, doSortByTime));
    pageContext.setAttribute("insertList", ProfiledConnection.getSortedQueries(ProfiledConnection.Type.insert, doSortByTime));
    pageContext.setAttribute("updateList", ProfiledConnection.getSortedQueries(ProfiledConnection.Type.update, doSortByTime));
    pageContext.setAttribute("deleteList", ProfiledConnection.getSortedQueries(ProfiledConnection.Type.delete, doSortByTime));
%>

<html>
<head>
    <title><fmt:message key="server.db_stats.title" /></title>
    <meta name="pageID" content="server-db"/>
    <%  // Enable refreshing if specified
        if (refresh >= 10) {
    %>
    <meta http-equiv="refresh" content="${admin:escapeHTMLTags(refresh)};URL=server-db-stats.jsp?refresh=${admin:escapeHTMLTags(refresh)}">

    <%  } %>
    <style>
        td.numVal {
            text-align: right;
            margin-right: 0.5em;
        }

        table.queries {
            width: 100%;
            background-color: #aaaaaa;
            border: 0;
        }
        table.queries td,th {
            padding: 3px;
        }
        table.queries > thead > tr > th {
            background-color: #ffffff;
            text-align: center;
        }
        table.queries > thead > tr > th:first-of-type {
            text-align: left;
        }
        table.queries > tbody > tr:nth-child(even) {
            background-color: #ffffff;
        }
        table.queries > tbody > tr:nth-child(odd) {
            background-color: #efefef;
        }
    </style>
</head>
<body>

<p>
    <fmt:message key="server.db_stats.description" />
</p>

<div class="jive-contentBox jive-contentBoxGrey" style="width: 732px;">
    <h3><fmt:message key="server.db_stats.status" /></h3>

    <form action="server-db-stats.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <table cellpadding="3" cellspacing="1">
            <tr>
                <td>
                    <input type="radio" name="enableStats" value="true" id="rb01" ${profilingEnabled ? "checked" : ""}>
                    <label for="rb01" style="font-weight: ${profilingEnabled ? 'strong' : 'normal'}">
                        <fmt:message key="server.db_stats.enabled" />
                    </label>
                </td>
                <td>
                    <input type="radio" name="enableStats" value="false" id="rb02" ${profilingEnabled ? "" : "checked"}>
                    <label for="rb02" style="font-weight: ${profilingEnabled ? 'normal' : 'strong'}">
                        <fmt:message key="server.db_stats.disabled" />
                    </label>
                </td>
                <td>
                    <input type="submit" name="" value="<fmt:message key="server.db_stats.update" />">
                </td>
            </tr>
        </table>
    </form>

    <c:if test="${profilingEnabled}">
    <br>
    <h3><fmt:message key="server.db_stats.settings" /></h3>

    <form action="server-db-stats.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <table cellpadding="3" cellspacing="5">
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
                <td>|</td>
                <td>
                    <input type="submit" name="" value="<fmt:message key="server.db_stats.refresh_now" />">
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

    <table bgcolor="#aaaaaa" width="600">
        <tr><td>
            <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" style="width: 100%">
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.operations" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.select)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_time" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.select).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.avg_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.select).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.select)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.queries" /></td>
                    <td bgcolor="#ffffff">
                        <c:choose>
                            <c:when test="${empty selectList}">
                                <fmt:message key="server.db_stats.no_queries" />
                            </c:when>
                            <c:otherwise>
                                &nbsp;
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </td></tr>
    </table>

    <c:if test="${not empty selectList}">
        <br />

        <table bgcolor="#aaaaaa" style="width: 100%">
            <tr><td>
                <table bgcolor="#aaaaaa" style="width: 100%">
                    <tr bgcolor="#ffffff"><td>
                        <table class="queries">
                            <thead>
                            <tr>
                                <th align=\"left\"><b><fmt:message key="server.db_stats.query"/></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.count" /></a></b></th>
                                <th nowrap style="width: 1%"><b><fmt:message key="server.db_stats.time" /></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.average_time" /></a></b></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="pce" items="${selectList}" end="20">
                                <tr>
                                    <td><c:out value="${pce.sql}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.count}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.totalTime.toMillis()}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.averageTime.toMillis()}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </td></tr>
                </table>
            </td></tr>
        </table>
    </c:if>
</ul>

<b><fmt:message key="server.db_stats.insert_stats" /></b>

<ul>

    <table bgcolor="#aaaaaa" width="600">
        <tr><td>
            <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" style="width: 100%">
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.operations" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.insert)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_time" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.insert).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.avg_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.insert).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.insert)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.queries" /></td>
                    <td bgcolor="#ffffff">
                        <c:choose>
                            <c:when test="${empty insertList}">
                                <fmt:message key="server.db_stats.no_queries" />
                            </c:when>
                            <c:otherwise>
                                &nbsp;
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </td></tr>
    </table>

    <c:if test="${not empty insertList}">
        <br />

        <table bgcolor="#aaaaaa" style="width: 100%">
            <tr><td>
                <table bgcolor="#aaaaaa" style="width: 100%">
                    <tr bgcolor="#ffffff"><td>
                        <table class="queries">
                            <thead>
                            <tr>
                                <th align=\"left\"><b><fmt:message key="server.db_stats.query"/></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.count" /></a></b></th>
                                <th nowrap style="width: 1%"><b><fmt:message key="server.db_stats.time" /></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.average_time" /></a></b></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="pce" items="${insertList}" end="20">
                                <tr>
                                    <td><c:out value="${pce.sql}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.count}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.totalTime.toMillis()}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.averageTime.toMillis()}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </td></tr>
                </table>
            </td></tr>
        </table>
    </c:if>
</ul>

<b><fmt:message key="server.db_stats.update_stats" /></b>

<ul>

    <table bgcolor="#aaaaaa" width="600">
        <tr><td>
            <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" style="width: 100%">
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.operations" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.update)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_time" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.update).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.avg_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.update).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.update)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.queries" /></td>
                    <td bgcolor="#ffffff">
                        <c:choose>
                            <c:when test="${empty updateList}">
                                <fmt:message key="server.db_stats.no_queries" />
                            </c:when>
                            <c:otherwise>
                                &nbsp;
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </td></tr>
    </table>

    <c:if test="${not empty updateList}">
        <br />

        <table bgcolor="#aaaaaa" style="width: 100%">
            <tr><td>
                <table bgcolor="#aaaaaa" style="width: 100%">
                    <tr bgcolor="#ffffff"><td>
                        <table class="queries">
                            <thead>
                            <tr>
                                <th align=\"left\"><b><fmt:message key="server.db_stats.query"/></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.count" /></a></b></th>
                                <th nowrap style="width: 1%"><b><fmt:message key="server.db_stats.time" /></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.average_time" /></a></b></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="pce" items="${updateList}" end="20">
                                <tr>
                                    <td><c:out value="${pce.sql}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.count}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.totalTime.toMillis()}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.averageTime.toMillis()}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </td></tr>
                </table>
            </td></tr>
        </table>
    </c:if>
</ul>

<b><fmt:message key="server.db_stats.delete_stats" /></b>

<ul>

    <table bgcolor="#aaaaaa" width="600">
        <tr><td>
            <table bgcolor="#aaaaaa" cellpadding="3" cellspacing="1" style="width: 100%">
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.operations" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getQueryCount(ProfiledConnection.Type.delete)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_time" /></td>
                    <td><%= intFormat.format(ProfiledConnection.getTotalQueryTime(ProfiledConnection.Type.delete).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.avg_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getAverageQueryTime(ProfiledConnection.Type.delete).toMillis()) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.total_rate" /></td>
                    <td><%= decFormat.format(ProfiledConnection.getQueriesPerSecond(ProfiledConnection.Type.delete)) %></td>
                </tr>
                <tr bgcolor="#ffffff">
                    <td><fmt:message key="server.db_stats.queries" /></td>
                    <td bgcolor="#ffffff">
                        <c:choose>
                            <c:when test="${empty deleteList}">
                                <fmt:message key="server.db_stats.no_queries" />
                            </c:when>
                            <c:otherwise>
                                &nbsp;
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </td></tr>
    </table>

    <c:if test="${not empty deleteList}">
        <br />

        <table bgcolor="#aaaaaa" style="width: 100%">
            <tr><td>
                <table bgcolor="#aaaaaa" style="width: 100%">
                    <tr bgcolor="#ffffff"><td>
                        <table class="queries">
                            <thead>
                            <tr>
                                <th align=\"left\"><b><fmt:message key="server.db_stats.query"/></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=false&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.count" /></a></b></th>
                                <th nowrap style="width: 1%"><b><fmt:message key="server.db_stats.time" /></b></th>
                                <th nowrap style="width: 1%"><b><a href="javascript:location.href='server-db-stats.jsp?doSortByTime=true&refresh=${admin:escapeHTMLTags(refresh)};'"><fmt:message key="server.db_stats.average_time" /></a></b></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="pce" items="${deleteList}" end="20">
                                <tr>
                                    <td><c:out value="${pce.sql}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.count}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.totalTime.toMillis()}"/></td>
                                    <td class="numVal"><fmt:formatNumber value="${pce.averageTime.toMillis()}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </td></tr>
                </table>
            </td></tr>
        </table>
    </c:if>

</ul>

</c:if>

</body></html>
