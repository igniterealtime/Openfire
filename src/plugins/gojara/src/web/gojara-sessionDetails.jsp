<%@page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.GatewaySession"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager"%>
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
	GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();
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
	
	<% if (!gojaraAdminManager.areGatewaysConfigured()) {%>
		<h2 align="center"><a href="gojara-gatewayStatistics.jsp">Warning: Not all Gateways are configured for admin usage. This means unregistrations will not be properly executed.<br/>
		 Please configure admin_jid = gojaraadmin@yourdomain in Spectrum2 transport configuration.</a></h2>
	 <% } %>
	
	<%
		if (request.getParameter(username) != null) {
			String[] unregister = request.getParameterValues(username);
	%>
		<br>
		<br>
		<%
			for (String key : unregister) {
		%>
	
		<div align="center"><%=transportManager.removeRegistrationOfUser(key, username)%></div><br>
		<%
			}
		%>
		<br>
		<br>
	
		<%
		}
		ArrayList<GatewaySession> userconnections = transportManager.getConnectionsFor(username);
		if (userconnections.isEmpty()) {
	%>
	<h2 align="center">User has no active sessions</h2>
	<%
		} else {
	%>
		<h1 align="center">Active Sessions:</h1>
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
		<h1 align="center">Associated Registrations:</h1>
	<br>
	<form name="unregister-form" id="gojara-sessDetailsUnregister"
		method="POST">
		<div class="jive-table">
			<table cellpadding="0" cellspacing="0" border="0" width="100%">
				<thead>
					<tr>
						<th nowrap>User Name:</th>
						<th nowrap>Resource:</th>
						<th nowrap>Active?</th>
						<th nowrap>Admin Configured?</th>
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
							 %> <img alt="No" src="/images/error-16x16.gif" title="Sending unregister to inactive transport will result in NOT UNREGISTERING the registration."> <%
							 	}
							 %>
						</td>
						<td>
						<% if (gojaraAdminManager.isGatewayConfigured(registration.getTransport())) { %>
						<img alt="Yes" src="/images/success-16x16.gif"> 
						<% 	} else { %>
						 <img alt="No" src="/images/error-16x16.gif" title="Sending unregister to unconfigured transport will result in NOT UNREGISTERING the registration.">
						  <% }%>
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
		<div align="center"><br>
			<input type="button" value="check/uncheck all" onclick='checkedAll();'>
		<br>
			<input type="submit" value="Unregister">
		</div>
	</form>
</body>
</html>

