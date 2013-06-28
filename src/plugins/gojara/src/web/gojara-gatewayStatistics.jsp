<%@page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.Set"%>
<%
	TransportSessionManager transportSessionManager = TransportSessionManager.getInstance();
	GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();
	gojaraAdminManager.gatherGatewayStatistics();
%>
<html>
<head>
<title>Spectrum2 gateway stats</title>
<meta name="pageID" content="gojaraGatewayStatistics" />
</head>
<body>

	<%
		if (!gojaraAdminManager.areGatewaysConfigured()) {
	%>
	<center>
		<h2 style="color: red">
			Warning: Not all Gateways are configured for admin usage. Affected
			gateways will not show any data.<br /> Please configure admin_jid =
			gojaraadmin@yourdomain in Spectrum2 transport configuration.
		</h2>
	</center>
	<hr />
	<%
		}
	%>
	<br/><br/>
	<div class="jive-table">
		<table cellpadding="0" cellspacing="0" border="0" width="100%">
			<thead>
				<tr>
					<th nowrap>Name</th>
					<th nowrap># Online Users</th>
					<th nowrap># Registrations</th>
					<th nowrap>Uptime</th>
					<th nowrap># Messages received</th>
					<th nowrap># Messages sent</th>
					<th nowrap>Used Memory</th>
					<th nowrap>Avg. Memory per User</th>
				</tr>
			</thead>
			<tbody>
				<%
				Set<String> gateways = gojaraAdminManager.getGatewayStatisticsMap().keySet();
				for (String gateway : gateways) {
				%>	
					<tr class="jive-odd">
					<td><%=gateway %></td>
					<td><%=transportSessionManager.getNumberOfActiveSessionsFor(gateway)%></td>
					<td><%=transportSessionManager.getNumberOfRegistrationsForTransport(gateway)%></td>
					<td><%=gojaraAdminManager.getStatisticsPresentationString(gateway, "uptime")%></td>
					<td><%=gojaraAdminManager.getStatisticsPresentationString(gateway, "messages_from_xmpp")%></td>
					<td><%=gojaraAdminManager.getStatisticsPresentationString(gateway, "messages_to_xmpp") %></td>
					<td><%=gojaraAdminManager.getStatisticsPresentationString(gateway, "used_memory") %></td>
					<td><%=gojaraAdminManager.getStatisticsPresentationString(gateway, "average_memory_per_user") %></td>
					</tr>
				<% } %>
			</tbody>
		</table>
	</div>
</body>
</html>