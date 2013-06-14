<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.database.SessionEntry"%>
<%@ page
	import="org.jivesoftware.openfire.plugin.gojara.utils.JspHelper"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Set"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.util.ArrayList"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>

<%
	TransportSessionManager transportManager = TransportSessionManager.getInstance();

	//Helper object for generation of sorting links, column restriction is done in DatabaseManager
	Map<String, String> sortParams = new HashMap<String, String>();
	if (request.getParameter("sortby") != null && request.getParameter("sortorder") != null) {
		sortParams.put("sortby", request.getParameter("sortby"));
		sortParams.put("sortorder", request.getParameter("sortorder"));
	} else {
		sortParams.put("sortby", "username");
		sortParams.put("sortorder", "ASC");
	}
	//pagination
	int current_page = 1;
%>

<html>
<head>
<title>Overview of existing Registrations</title>
<meta name="pageID" content="gojaraRegistrationAdministration" />
</head>
<body>
	<%
		//do unregisters if supplied
		if (request.getParameterMap() != null) {
			String uninteresting_params = "sortorder sortby page";
			for (Object key : request.getParameterMap().keySet()) {
				if (uninteresting_params.contains(key.toString())) {
					continue;
				}
				String[] uservalues = request.getParameterValues(key.toString());
				for (String transport : uservalues) {
	%>
	<ul>
		<%=transportManager.removeRegistrationOfUser(transport, key.toString())%>
	</ul>
	<%
		}
			}
		}
	%>


	<center>
		<h5>Logintime 1970 means User did register but never logged in,
			propably because of invalid credentials.</h5>
	</center>
	<br>
	<%
		//Here we do our nice query
		ArrayList<SessionEntry> registrations = transportManager.getAllRegistrations(sortParams.get("sortby"),
				sortParams.get("sortorder"));
		int numOfSessions = registrations.size();
		int numOfPages = numOfSessions / 100;
		if (request.getParameter("page") != null) {
			//lets check for validity
			try {
				current_page = Integer.parseInt(request.getParameter("page"));
				if (current_page < 1 || current_page > (numOfPages))
					current_page = 1;
			} catch (Exception e) {
			}
		}
		// we now know current_page is in valid range, so set it for computation
		current_page -= 1;
		numOfPages += 1;
		int next_items = (current_page * 100) + 99;
		if (next_items > numOfSessions)
			next_items = numOfSessions;
	%>
	<p>
		Registrations total: <b><%=transportManager.getNumberOfRegistrations()%></b><br>
		<br> Pages: [
		<%
			for (int i = 1; i <= numOfPages; i++) {
		%>
		<%="<a href=\"gojara-RegistrationsOverview.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
						+ sortParams.get("sortorder") + "\" class=\"" + ((current_page + 1) == i ? "jive-current" : "") + "\">" + i
						+ "</a>"%>
		<%
			}
		%>
		]
	</p>
	<form name="unregister-form" id="gojara-RegOverviewUnregister"
		method="POST">
		<div class="jive-table">
			<table cellpadding="0" cellspacing="0" border="0" width="100%">
				<thead>
					<tr>
						<th nowrap><%=JspHelper.sortingHelperRegistrations("username", sortParams)%></th>
						<th nowrap><%=JspHelper.sortingHelperRegistrations("transport", sortParams)%></th>
						<th nowrap>Resource active?</th>
						<th nowrap><%=JspHelper.sortingHelperRegistrations("lastActivity", sortParams)%></th>
						<th nowrap>Unregister?</th>
					</tr>
				</thead>
				<tbody>
					<%
						for (SessionEntry registration : registrations) {
					%>
					<tr class="jive-odd">
						<td><a
							href="gojara-sessionDetails.jsp?username=<%=registration.getUsername()%>"
							title="Session Details for <%=registration.getUsername()%>"><%=registration.getUsername()%></a></td>
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
		<p>
			Pages: [
			<%
			for (int i = 1; i <= numOfPages; i++) {
		%>
			<%="<a href=\"gojara-RegistrationsOverview.jsp?page=" + i + "&sortby=" + sortParams.get("sortby") + "&sortorder="
						+ sortParams.get("sortorder") + "\" class=\"" + ((current_page + 1) == i ? "jive-current" : "") + "\">" + i
						+ "</a>"%>
			<%
				}
			%>
			]
		</p>
		<br>
		<center>
			<input type="submit" value="Unregister">
		</center>
	</form>
</body>
</html>