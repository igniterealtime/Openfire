<%@page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.GatewaySession"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.database.SessionEntry"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.utils.JspHelper"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.util.ArrayList"%>

<%
	TransportSessionManager transportManager = TransportSessionManager.getInstance();
	String username = request.getParameter("username");
%>
<html>
<head>
<title>GatewaySession Details for: &emsp; <%=username%></title>
<meta name="pageID" content="gojaraSessions" />
<script language='JavaScript'>
	checked = false;
	function checkedAll () {
		if (checked == false){checked = true}else{checked = false}
		for (var i = 0; i < document.getElementById('gojara-sessDetailsUnregister').elements.length; i++) {
			document.getElementById('gojara-sessDetailsUnregister').elements[i].checked = checked;
		}
	}
</script>
</head>
<body>
	<%
		if (request.getParameter(username) != null) {
			String[] unregister = request.getParameterValues(username);
	%>
	<br>
	<br>
	<%
		for (String key : unregister) {
	%>

	<%=transportManager.removeRegistrationOfUser(key, username)%><br>
	<%
		}
	%>
	<br>
	<br>
	<%
		}
		ArrayList<GatewaySession> userconnections = transportManager.getConnectionsFor(username);
		if (userconnections == null) {
	%>
	<h2>
		<center>User has no active sessions</center>
	</h2>
	<%
		} else {
	%>
	<center>
		<h1>Active Sessions:</h1>
	</center>
	<br>
	<div class="jive-table">
		<table cellpadding="0" cellspacing="0" border="0" width="100%">
			<thead>
				<tr>
					<th nowrap>Resource</th>
					<th nowrap>Login Time</th>
				</tr>
			</thead>
			<tbody>
				<%
					for (GatewaySession gws : userconnections) {
				%>
				<tr class="jive-odd">
					<td><%=gws.getTransport()%></td>
					<td title="<%=JspHelper.dateDifferenceHelper(gws.getLastActivity()) %>"><%=gws.getLastActivity()%></td>
				</tr>
				<%
					}
				%>
			</tbody>
		</table>
	</div>

	<%
		}
	%>

	<br>
	<hr>
	<center>
		<h1>Associated Registrations:</h1>
	</center>
	<br>
	<form name="unregister-form" id="gojara-sessDetailsUnregister"
		method="POST">
		<div class="jive-table">
			<table cellpadding="0" cellspacing="0" border="0" width="100%">
				<thead>
					<tr>
						<th nowrap>User Name:</th>
						<th nowrap>Resource:</th>
						<th nowrap>Resource active?</th>
						<th nowrap>Last login was at:</th>
						<th nowrap>Unregister?</th>
					</tr>
				</thead>
				<tbody>
					<%
						ArrayList<SessionEntry> registrations = transportManager.getRegistrationsFor(username);
					%>
					<%
						for (SessionEntry registration : registrations) {
					%>
					<tr class="jive-odd">
						<td><a
							href="/user-properties.jsp?username=<%=registration.getUsername()%>"><%=registration.getUsername()%></a></td>
						<td><%=registration.getTransport()%></td>
						<td>
							<%
								if (transportManager.isTransportActive(registration.getTransport())) {
							%> <img alt="Yes" src="/images/success-16x16.gif"> <%
 	} else {
 %> <img alt="No" src="/images/error-16x16.gif"> <%
 	}
 %>
						</td>
						<td
							title="<%=JspHelper.dateDifferenceHelper(registration.getLast_activityAsDate())%>"><%=registration.getLast_activityAsDate()%></td>
						<td><input type="checkbox"
							name="<%=registration.getUsername()%>"
							value="<%=registration.getTransport()%>"></td>
					</tr>
					<%
						}
					%>
				</tbody>
			</table>
		</div>
		<br>
		<center>
			<input type="button" value="check/uncheck all"
				onclick='checkedAll();'>
		</center>
		<br>
		<center>
			<input type="submit" value="Unregister">
		</center>
	</form>
</body>
</html>

