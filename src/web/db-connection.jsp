<%@ taglib uri="core" prefix="c"%><%
/**
 *	$RCSfile$
 *	$Revision$
 *	$Date$
 */
%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.database.DbConnectionManager,
                 java.sql.*,
                 org.jivesoftware.database.DbConnectionManager" %>
                 
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="DB Connection Info"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set var="sbar" value="database" scope="page" />
<c:set target="${breadcrumbs}" property="${title}" value="db-connection.jsp" />
<jsp:include page="top.jsp" flush="true" />


<%  // Get metadata about the database
    Connection con = null;
    try {
        con = DbConnectionManager.getConnection();
        DatabaseMetaData metaData = con.getMetaData();
%>

<p>
Below is a brief summary of your database connection information.
</p>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center">Database Connection Info</td></tr>
<tr>
    <td class="jive-label">
        Database and Version:
    </td>
    <td>
        <%= metaData.getDatabaseProductName() %>
        <%= metaData.getDatabaseProductVersion() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        JDBC Driver:
    </td>
    <td>
        <%= metaData.getDriverName() %>
    </td>
</tr>
<tr>
<td class="jive-label">
     JDBC Driver Version:
</td>
<td><%= metaData.getDriverVersion() %>
</td>
</tr>
<tr>
    <td class="jive-label">
        DB Connection URL:
    </td>
    <td>
        <%= metaData.getURL() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        DB User:
    </td>
    <td>
        <%= metaData.getUserName() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Transaction Support:
    </td>
    <td>
        <%= (metaData.supportsTransactions()) ? "Yes" : "No" %>
    </td>
</tr>
<%  if (metaData.supportsTransactions()) { %>
    <tr>
        <td class="jive-label">
            Transaction Isolation Level
        </td>
        <td>
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
    <td class="jive-label">
        Supports multiple connections<br>open at once:
    </td>
    <td>
        <%= (metaData.supportsMultipleTransactions()) ? "Yes" : "No" %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        In read-only mode:
    </td>
    <td>
        <%= (metaData.isReadOnly()) ? "Yes" : "No" %>
    </td>
</tr>
</table>
</div>

<%  }
    finally {
        try { if (con != null) { con.close(); } }
        catch (SQLException e) { Log.error(e); }
    }
%>
<jsp:include page="bottom.jsp" flush="true" />
