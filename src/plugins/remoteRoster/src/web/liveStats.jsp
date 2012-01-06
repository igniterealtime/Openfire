<%@page import="org.jivesoftware.openfire.plugin.PermissionManager"%>
<%@ page import="org.dom4j.tree.DefaultElement"%>
<%@ page import="org.jivesoftware.openfire.group.GroupManager"%>
<%@ page import="org.jivesoftware.openfire.group.Group"%>
<%@ page import="org.jivesoftware.openfire.session.ComponentSession"%>
<%@ page import="java.util.Collection"%>
<%@ page import="org.jivesoftware.openfire.SessionManager"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.List"%>
<%@ page import="org.jivesoftware.openfire.plugin.database.DatabaseManager"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>


<%
	// webManager.init(request, response, session, application, out);
	boolean componentSet = request.getParameter("component") != null;
	String component = "";
	if (componentSet) {
		component = request.getParameter("component");
	}
%>


<html>
<head>
<title>Live logs <%=componentSet ? "for " + component : ""%></title>
<meta name="decorator" content="none" />


<link href="./css/liveStats.css" rel="stylesheet" type="text/css">
<script src="./js/http.js" type="text/javascript"></script>
<script src="./js/jquery.js" type="text/javascript"></script>
<script src="./js/liveStats.js" type="text/javascript"></script>
<script language="javascript" type="text/javascript" src="./js/jquery.flot.js"></script>
</head>
<body>
	<div class="div-main">
		<div class="header">
			Live statistics for
			<%=componentSet ? component : "NOT SET"%></div>
		<div class="graph">Here should appear your stats</div>

		<table id="logTable">
			<thead>
				<tr>
					<th>Date</th>
					<th>Type</th>
					<th>From</th>
					<th>To</th>
				</tr>
			</thead>
			<tbody class="tableBegin">
			</tbody>
			<tfoot>
				<tr>
					<td colspan="4">Live logging since <span id="logSince"><%=System.currentTimeMillis()%></span> Last poll <span
						id="lastPoll"></span></td>
				</tr>
			</tfoot>
		</table>
	</div>

</body>




</html>