<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.database.DbConnectionManager,
                 java.sql.*"
    errorPage="error.jsp"
%>

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