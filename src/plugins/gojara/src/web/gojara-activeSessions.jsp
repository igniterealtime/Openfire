<%@ page import="org.jivesoftware.openfire.plugin.gojara.permissions.TransportSessionManager"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%
	TransportSessionManager transportManager = TransportSessionManager.getInstance();
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
					<th nowrap>User Name</th>
					<th nowrap>Resource</th>
					<th nowrap>Login Time</th>
				</tr>
			</thead>
			<tbody>
				<%
					for (String transport : sessions.keySet()) {
				%>
				<%
					for (String user : sessions.get(transport).keySet()) {
				%>
				<tr class="jive-odd">
					<td><a href="gojara-sessionDetails.jsp?username=<%=user%>"><%=user%></a></td>
					<td><%=transport%></td>
					<td><%=sessions.get(transport).get(user)%></td>
				</tr>
				<%
					}
				%>
				<%
					}
				%>
			</tbody>
		</table>
	</div>


</body>
</html>