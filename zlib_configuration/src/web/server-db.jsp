<%--
  -	$Revision$
  -	$Date$
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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.database.DbConnectionManager,
                 java.sql.*"
    errorPage="error.jsp"
%>
<%@ page import="org.logicalcobwebs.proxool.ConnectionPoolDefinitionIF" %>
<%@ page import="org.logicalcobwebs.proxool.ProxoolFacade" %>
<%@ page import="org.logicalcobwebs.proxool.admin.SnapshotIF" %>
<%@ page import="org.logicalcobwebs.proxool.ConnectionInfoIF" %>
<%@ page import="java.text.SimpleDateFormat" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<html>
    <head>
        <title><fmt:message key="server.db.title"/></title>
        <meta name="pageID" content="server-db"/>
        <meta name="helpPage" content="view_database_connection_properties.html"/>
    </head>
    <body>

<%  // Get metadata about the database
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
        catch (SQLException e) { Log.error(e); }
    }

    if (DbConnectionManager.getConnectionProvider().isPooled()) {
        try {
            // Get metadata about the connection pool
            ConnectionPoolDefinitionIF poolDef = ProxoolFacade.getConnectionPoolDefinition("openfire");
            SnapshotIF poolStats = ProxoolFacade.getSnapshot("openfire", true);
            Integer active = 100 * poolStats.getActiveConnectionCount() / poolStats.getMaximumConnectionCount();
            Integer inactive = 100 * (poolStats.getAvailableConnectionCount() - poolStats.getActiveConnectionCount()) / poolStats.getMaximumConnectionCount();
            Integer notopened = 100 - active - inactive;
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
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
            <%= (poolDef.getHouseKeepingSleepTime() / 1000) %> <fmt:message key="server.db_stats.seconds" />
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_lifetime" />
        </td>
        <td class="c2">
            <%= (poolDef.getMaximumConnectionLifetime() / 1000) %> <fmt:message key="server.db_stats.seconds" />
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_min" />
        </td>
        <td class="c2">
            <%= poolDef.getMinimumConnectionCount() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_max" />
        </td>
        <td class="c2">
            <%= poolDef.getMaximumConnectionCount() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.house_keeping_sql" />
        </td>
        <td class="c2">
            <%= poolDef.getHouseKeepingTestSql() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.test_before_use" />
        </td>
        <td class="c2">
            <%= (poolDef.isTestBeforeUse() ? "Yes" : "No") %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.test_after_use" />
        </td>
        <td class="c2">
            <%= (poolDef.isTestAfterUse() ? "Yes" : "No") %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections" />
        </td>
        <td class="c2">
            <%= poolStats.getActiveConnectionCount() %> (<fmt:message key="server.db.connections.active"/>),
            <%= poolStats.getAvailableConnectionCount() %> (<fmt:message key="server.db.connections.available"/>),
            <%= poolStats.getMaximumConnectionCount() %> (<fmt:message key="server.db.connections.max"/>)<br/>
            <table border="0" cellspacing="0" cellpadding="0" width="250px" style="margin: 8px; font-size: 50%">
                <tr>
                    <% if (active > 0) { %><td style="border: 1.0px solid #000000; background-color: #ffffaa" width="<%= active %>%">&nbsp;</td><% } %>
                    <% if (inactive > 0) { %><td style="border: 1.0px solid #000000; background-color: #aaffaa" width="<%= inactive %>%">&nbsp;</td><% } %>
                    <td style="border: 1.0px solid #000000; background-color: #eeeeee" width="<%= notopened %>%">&nbsp;</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections_served" />
        </td>
        <td class="c2">
            <%= poolStats.getServedCount() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connections_refused" />
        </td>
        <td class="c2">
            <%= poolStats.getRefusedCount() %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="server.db.connection_details" />
        </td>
        <td class="c2">
            <table cellspacing="0">
                <thead>
                    <tr>
                        <th><fmt:message key="server.db.connection_details.id"/></th>
                        <th><fmt:message key="server.db.connection_details.when_created"/></th>
                        <th><fmt:message key="server.db.connection_details.last_used"/></th>
                        <th><fmt:message key="server.db.connection_details.thread"/></th>
                    </tr>
                </thead>
                <tbody>
<%
                        for (ConnectionInfoIF info : poolStats.getConnectionInfos()) {
%>
                    <tr>
                        <td align="center" style="padding: 2px"><%= info.getId() %></td>
                        <td align="center" style="padding: 2px"><%= dateFormat.format(info.getBirthDate()) %></td>
                        <td align="center" style="padding: 2px"><%= info.getTimeLastStartActive() > 0 ? dateFormat.format(new Date(info.getTimeLastStartActive())) : "-" %></td>
                        <td align="center" style="padding: 2px"><%= info.getRequester() != null ? info.getRequester() : "-" %></td>
                    </tr>
<%
                        }
%>
                </tbody>
            </table>
        </td>
    </tr>
</tbody>
</table>
</div>
<%
        }
        catch (Exception e) {
            // Nothing
            Log.error("AdminConsole: Error while displaying connection pool information: ", e);
        }
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