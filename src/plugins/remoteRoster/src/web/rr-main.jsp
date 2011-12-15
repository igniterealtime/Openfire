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
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
	webManager.init(request, response, session, application, out);
	boolean save = request.getParameter("save") != null;
	boolean success = request.getParameter("success") != null;
	//boolean persistentRoster = ParamUtils.getBooleanAttribute(request, "persistentEnabled");
	boolean persistentRoster = true;
	String sparkdiscoParam = request.getParameter("sparkDiscoInfo");
	boolean sparkDiscoInfo = sparkdiscoParam == null ? false : sparkdiscoParam.equals("true");
	String[] componentsEnabled = request.getParameterValues("enabledComponents[]");
	PermissionManager _pmanager = new PermissionManager();

	Map<String, String> errors = new HashMap<String, String>();
	if (save) {
		for (String property : JiveGlobals.getPropertyNames("plugin.remoteroster.jids")) {
			JiveGlobals.deleteProperty(property);
		}
		if (componentsEnabled != null) {
			for (int i = 0; i < componentsEnabled.length; i++) {
				JiveGlobals.setProperty("plugin.remoteroster.jids." + componentsEnabled[i], "true");
				String group = request.getParameter("input_group." + componentsEnabled[i]);
				if (group != null) {
					_pmanager.setGroupForGateway(componentsEnabled[i], group);
				}
			}
		}
		JiveGlobals.setProperty("plugin.remoteroster.persistent", (persistentRoster ? "true" : "false"));
		JiveGlobals.setProperty("plugin.remoteroster.sparkDiscoInfo", (sparkDiscoInfo ? "true" : "false"));
		response.sendRedirect("rr-main.jsp?success=true");
		return;
	}

	// Get the session manager
	SessionManager sessionManager = webManager.getSessionManager();

	Collection<ComponentSession> sessions = sessionManager.getComponentSessions();
%>

<html>
<head>
<title><fmt:message key="rr.summary.title" /></title>
<link href="rr.css" rel="stylesheet" type="text/css">
<script src="http.js" type="text/javascript"></script>
<script src="jquery.js" type="text/javascript"></script>
<script src="rr.js" type="text/javascript"></script>
<script src="jquery.sparkline.js" type="text/javascript"></script>


<meta name="pageID" content="remoteRoster" />
<meta name="helpPage" content="" />

</head>
<body>

	<p>Any components configured here will allow the external component associated with them full control over their
		domain within any user's roster. Before enabling Remote Roster Management support for an external component, first
		connect it like you would any external component. Once it has connected and registered with Openfire, it's JID should
		show up below and you can enable Remote Roster support.</p>

	<%
		if (success) {
	%>

	<div class="jive-success">
		<table cellpadding="0" cellspacing="0" border="0">
			<tbody>
				<tr>
					<td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
					<td class="jive-icon-label">Settings saved!</td>
				</tr>
			</tbody>
		</table>
	</div>
	<br>

	<%
		} else if (errors.size() > 0) {
	%>

	<div class="jive-error">
		<table cellpadding="0" cellspacing="0" border="0">
			<tbody>
				<tr>
					<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
					<td class="jive-icon-label">Error saving settings!</td>
				</tr>
			</tbody>
		</table>
	</div>
	<br>

	<%
		}
	%>

	<form action="rr-main.jsp?save" method="post">

		<div class="jive-contentBoxHeader">Connected Gateway Components</div>
		<div class="jive-contentBox">

			<p>Select which components you want to enable remote roster on:</p>
			<%
				boolean gatewayFound = false;
				int i = 0;
				for (ComponentSession componentSession : sessions) {
					if (!componentSession.getExternalComponent().getCategory().equals("gateway")) {
						continue;
					}
					gatewayFound = true;
			%>
			<table class="gatewayHeader">
				<tbody>
					<tr>
						<td class="gatewayCheckbox"><input type="checkbox" name="enabledComponents[]"
							value="<%=componentSession.getExternalComponent().getInitialSubdomain()%>"
							<%=JiveGlobals.getBooleanProperty("plugin.remoteroster.jids."
						+ componentSession.getExternalComponent().getInitialSubdomain(), false) ? "checked=\"checked\""
						: ""%> />
						</td>
						<td class="gatewayName"><%=componentSession.getExternalComponent().getName()%></td>
						<td class="gatewayIcons"><img src="images/info-16x16.png" id="showConfig"
							onclick="slideToggle('#config<%=i%>')"> <img src="images/permissions-16x16.png" id="showPermissions"
							onclick="slideToggle('#permission<%=i%>')"> <img src="images/log-16x16.png"></td>
					</tr>
				</tbody>
			</table>
			<div id="config<%=i%>" class="slider">
				<table class="configTable">
					<tbody>
						<tr>
							<td class="configTable1Column">Domain:</td>
							<td><%=componentSession.getExternalComponent().getInitialSubdomain()%></td>
						</tr>
						<tr>
							<td class="configTable1Column">Status:</td>
							<td>Online</td>
						</tr>
						<tr>
							<td class="configTable1Column">Packages Send/Received:</td>
							<td><%=componentSession.getNumServerPackets()%> / <%=componentSession.getNumClientPackets()%><div id="inlinesparkline<%=i%>">1,4,4,7,5,9,10</div></td>
						</tr>
					</tbody>
				</table>
			</div>
			<div id="permission<%=i%>" class="slider">
				> <span class="permissionTitle">You can limit the access to the external component to an existing group</span>)
				<table class="groupTable">
					<tbody>
						<tr>
							<td class="permissionTableColumn">Groupname:</td>
							<td><input type="text" id="groupSearch<%=i%>"
								name="input_group.<%=componentSession.getExternalComponent().getInitialSubdomain()%>" alt="Find Groups"
								onkeyup="searchSuggest('<%=i%>');" autocomplete="off"
								value="<%=_pmanager.getGroupForGateway(componentSession.getExternalComponent().getInitialSubdomain())%>">
								<div id="search_suggest<%=i%>"></div></td>
							<td style="vertical-align: top;">
								<div class="ajaxloading" id="ajaxloading<%=i%>"></div>
							</td>

						</tr>
					</tbody>
				</table>
			</div>




			<%
				++i;
				}
			%>
			<%
				if (!gatewayFound) {
			%>
			<span style="font-weight: bold">No connected external gateway components found.</span>
			<%
				}
			%>
		</div>
		<!--  DISABLED PERSISTENT ROSTER UNTIL SPECTRUM SUPPORTS IT
		
<div class="jive-contentBoxHeader">Options</div>
<div class="jive-contentBox">
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
   <tbody>
   <tr valign="top">
       <td width="1%" nowrap class="c1">
           Persistent Roster:
       </td>
       <td width="99%">
           <table cellpadding="0" cellspacing="0" border="0">
           <tbody>
               <tr>
                   <td>
                       <input type="radio" name="persistentEnabled" value="true" checked id="PER01">
                   </td>
                   <td><label for="PER01">Enabled (remote rosters are saved into the user's stored roster)</label></td>
               </tr>
               <tr>
                   <td>
                       <input type="radio" name="persistentEnabled" value="false" id="PER02">
                   </td>
                   <td><label for="PER02">Disabled (remote rosters exist only in memory)</label></td>
               </tr>
           </tbody>
           </table>
       </td>
   </tr>
   </tbody>
   </table>
</div>

 -->



		<br /> <br />
		<div class="jive-contentBoxHeader">Client specific options</div>
		<div class="jive-contentBox">
			<table cellpadding="3" cellspacing="0" border="0" width="100%">
				<tbody>
					<tr valign="top">
						<td width="1%" nowrap class="c1">Spark:</td>
						<td width="99%">
							<table cellpadding="0" cellspacing="0" border="0">
								<tbody>
									<tr>
										<td><input type="checkbox" name="sparkDiscoInfo" id="SDI" value="true"
											<%=JiveGlobals.getBooleanProperty("plugin.remoteroster.sparkDiscoInfo", false) ? "checked=\"checked\""
					: ""%> />

										</td>
										<td><label for="SDI"> Support jabber:iq:registered feature*</label></td>
									</tr>
									<tr>
										<td />
										<td align="left" style="font-size: -3; color: grey">*If you use Spark clients within your network, it
											might be necessary to modify the service discovery packets between Spark and the external component. If you
											check this RemoteRoster will add the feature "jabber:iq:registered" to the disco#info to indicate that the
											Client is registered with the external component.</td>
									</tr>
								</tbody>
							</table>
						</td>
					</tr>
				</tbody>
			</table>
		</div>


		<input type="submit" name="save" value="Save Settings" />
	</form>

</body>
</html>
