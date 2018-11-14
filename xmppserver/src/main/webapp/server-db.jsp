<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.DefaultConnectionProvider"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.JiveConstants" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.DatabaseMetaData" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<html>
    <head>
        <title><fmt:message key="server.db.title"/></title>
        <meta name="pageID" content="server-db"/>
        <meta name="helpPage" content="view_database_connection_properties.html"/>
    </head>
    <body>

<%  // Get metadata about the database
    final Logger Log = LoggerFactory.getLogger("server-db.jsp");
    Connection con = null;
    try {
        con = DbConnectionManager.getConnection();
        DatabaseMetaData metaData = con.getMetaData();
%>

<p>
<fmt:message key="server.db.info" />
</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2"><fmt:message key="server.db.connect_info" /></th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.version" />
        </td>
        <td class="c2">
            <%= metaData.getDatabaseProductName() %>
            <%= metaData.getDatabaseProductVersion() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.jdbc" />
        </td>
        <td class="c2">
            <%= metaData.getDriverName() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
             <fmt:message key="server.db.jdbc_driver" />
        </td>
        <td class="c2">
            <%= metaData.getDriverVersion() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connect_url" />
        </td>
        <td class="c2">
            <%= metaData.getURL() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.user" />
        </td>
        <td class="c2">
            <%= metaData.getUserName() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.transaction" />
        </td>
        <td class="c2">
            <%= (metaData.supportsTransactions()) ? "Yes" : "No" %>
        </td>
    </tr>
    <%  if (metaData.supportsTransactions()) { %>
        <tr>
            <td class="c1">
                <fmt:message key="server.db.transaction_level" />
            </td>
            <td class="c2">
                <%  if (con.getTransactionIsolation() == Connection.TRANSACTION_NONE) { %>

                        TRANSACTION_NONE

                <%  } else if (con.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED) { %>

                        TRANSACTION_READ_COMMITTED

                <%  } else if (con.getTransactionIsolation() == Connection.TRANSACTION_READ_UNCOMMITTED) { %>

                        TRANSACTION_READ_UNCOMMITTED

                <%  } else if (con.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ) { %>

                        TRANSACTION_REPEATABLE_READ

                <%  } else if (con.getTransactionIsolation() == Connection.TRANSACTION_SERIALIZABLE) { %>

                        TRANSACTION_SERIALIZABLE

                <%  } %>
            </td>
        </tr>
    <%  } %>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.multiple_connect" /><br>
            <fmt:message key="server.db.multiple_connect2" />
        </td>
        <td class="c2">
            <%= (metaData.supportsMultipleTransactions()) ? "Yes" : "No" %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.read_only_mode" />
        </td>
        <td class="c2">
            <%= (metaData.isReadOnly()) ? "Yes" : "No" %>
        </td>
    </tr>
</tbody>
</table>
</div>

<%  }
    finally {
        try { if (con != null) { con.close(); } }
        catch (SQLException e) { Log.error("Unable to close connection", e); }
    }

    final ConnectionProvider connectionProvider = DbConnectionManager.getConnectionProvider();
    if (connectionProvider instanceof DefaultConnectionProvider) {
        final DefaultConnectionProvider defaultConnectionProvider = (DefaultConnectionProvider) connectionProvider;
        final int activeConnections = defaultConnectionProvider.getActiveConnections();
        final int idleConnections = defaultConnectionProvider.getIdleConnections();
        final int maxConnections = defaultConnectionProvider.getMaxConnections();
        final int activePercent = 100 * activeConnections / maxConnections;
        final int idlePercent = 100 * idleConnections / maxConnections;
        final int unopenedPercent = 100 - idlePercent - activePercent;
%>

<br/><br/>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2"><fmt:message key="server.db.pool_info" /></th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.house_keeping_sleep" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getTimeBetweenEvictionRunsMillis())%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_idle_time" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getMinIdleTime())%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_lifetime" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime((long) (defaultConnectionProvider.getConnectionTimeout() * JiveConstants.DAY))%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_max_wait_time" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getMaxWaitTime())%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_min" />
        </td>
        <td class="c2">
            <%= defaultConnectionProvider.getMinConnections() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_max" />
        </td>
        <td class="c2">
            <%= maxConnections %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.house_keeping_sql" />
        </td>
        <td class="c2">
            <%= defaultConnectionProvider.getTestSQL() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.test_before_use" />
        </td>
        <td class="c2">
            <%= (defaultConnectionProvider.getTestBeforeUse() ? "Yes" : "No") %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.test_after_use" />
        </td>
        <td class="c2">
            <%= (defaultConnectionProvider.getTestAfterUse() ? "Yes" : "No") %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.test_timeout" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getTestTimeout())%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections" />
        </td>
        <td class="c2">
            <%= activeConnections %> (<fmt:message key="server.db.connections.active"/>),
            <%= idleConnections %> (<fmt:message key="server.db.connections.available"/>),
            <%= maxConnections %> (<fmt:message key="server.db.connections.max"/>)<br/>
            <table border="0" cellspacing="0" cellpadding="0" width="250px" style="margin: 8px; font-size: 50%">
                <tr>
                    <% if (activePercent > 0) { %><td style="border: 1px solid #000000; background-color: #ffffaa" width="<%= activePercent %>%">&nbsp;</td><% } %>
                    <% if (idlePercent > 0) { %><td style="border: 1px solid #000000; background-color: #aaffaa" width="<%= idlePercent %>%">&nbsp;</td><% } %>
                    <td style="border: 1px solid #000000; background-color: #eeeeee" width="<%= unopenedPercent %>%">&nbsp;</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections_served" />
        </td>
        <td class="c2">
            <%= defaultConnectionProvider.getConnectionsServed() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections_refused" />
        </td>
        <td class="c2">
            <%= defaultConnectionProvider.getRefusedCount() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_mean_borrow_time" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getMeanBorrowWaitTime())%>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_max_borrow_time" />
        </td>
        <td class="c2">
            <%=StringUtils.getFullElapsedTime(defaultConnectionProvider.getMaxBorrowWaitTime())%>
        </td>
    </tr>
</tbody>
</table>
</div>
<%
    }
%>

    <br/><br/>
    <table border="0">
        <tr>
            <td valign="center">
                <a href="server-db-stats.jsp"><img src="images/arrow_right_blue.gif" width="24" height="24" border="0" alt="<fmt:message key="server.db_stats.title" />" /></a>
            </td>
            <td valign="center"><a href="server-db-stats.jsp"><fmt:message key="server.db_stats.title" /></a></td>
        </tr>
    </table>

    </body>
</html>
