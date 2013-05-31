<%@ page import="org.jivesoftware.openfire.plugin.gojara.permissions.TransportSessionManager"%>
<%@ page import="org.jivesoftware.openfire.plugin.gojara.permissions.GatewaySession"%>
<%@ page import="org.jivesoftware.openfire.plugin.gojara.utils.JspColumnSortingHelper"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%
	TransportSessionManager transportManager = TransportSessionManager.getInstance();
	//Helper object for generation of sorting links, column restriction is done in DatabaseManager
	Map<String, String> sortParams = new HashMap<String, String>();
	if (request.getParameter("sortby") != null && request.getParameter("sortorder") != null) {
		sortParams.put("sortby", request.getParameter("sortby") );
		sortParams.put("sortorder", request.getParameter("sortorder"));
	} else {
		sortParams.put("sortby", "transport");
		sortParams.put("sortorder", "ASC");
	}
%>
<html>
<head>
<title>Gateway Sessions</title>

<meta name="pageID" content="gojaraSessions" />
</head>
<body>
	<center>Please be aware that currently only users that connect
		AFTER GoJara has been started are considered for these Sessions. This
		affects Plugin-restarts.</center>
	<%
		Map<String, Map<String, Date>> sessions = transportManager.getSessions();
	%><br>
	<h4>
		Current number of active Gateway Sessions: &emsp;
		<%=transportManager.getNumberOfActiveSessions()%>
	</h4>
	<br>
	<%
		for (String transport : sessions.keySet()) {
	%>
	<%=transport.substring(0, 10)%>... :
	<b><%=sessions.get(transport).size()%></b> &emsp;
	<%
		}
	%>
	<br>
	<br>
	<div class="jive-table">
		<table cellpadding="0" cellspacing="0" border="0" width="100%">
			<thead>
				<tr>
					<th nowrap><%= JspColumnSortingHelper.sortingHelperSessions("username", sortParams) %></th>
					<th nowrap><%= JspColumnSortingHelper.sortingHelperSessions("transport", sortParams) %></th>
					<th nowrap><%= JspColumnSortingHelper.sortingHelperSessions("loginTime", sortParams)%></th>
				</tr>
			</thead>
			<tbody>
				<%
					for (GatewaySession gwsession : transportManager.getSessionArrayList()) {
				%>

				<tr class="jive-odd">
					<td><a href="gojara-sessionDetails.jsp?username=<%=gwsession.getUsername()%>"><%=gwsession.getUsername()%></a></td>
					<td><%=gwsession.getTransport()%></td>
					<td><%=gwsession.getLastActivity()%></td>
				</tr>
				<%
					}
				%>
			</tbody>
		</table>
	</div>


</body>
</html>