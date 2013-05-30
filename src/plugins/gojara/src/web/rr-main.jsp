<%@ page import="org.jivesoftware.openfire.plugin.gojara.database.DatabaseManager"%>
<%@ page import="org.jivesoftware.openfire.plugin.gojara.permissions.PermissionManager"%>
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
	String persistentRosterParam = request.getParameter("persistentEnabled");
	boolean persistentRoster = persistentRosterParam == null? false : persistentRosterParam.equals("true");

	String sparkdiscoParam = request.getParameter("sparkDiscoInfo");
	boolean sparkDiscoInfo = sparkdiscoParam == null ? false : sparkdiscoParam.equals("true");
	
	String iqLastFilterPram = request.getParameter("iqLastFilter");
	boolean iqLastFilter = iqLastFilterPram == null ? false : iqLastFilterPram.equals("true");
	
	String mucFilterParam = request.getParameter("mucFilter");
	boolean mucFilter = mucFilterParam == null ? false : mucFilterParam.equals("true");
	
	String[] componentsEnabled = request.getParameterValues("enabledComponents[]");
	PermissionManager _pmanager = new PermissionManager();
	DatabaseManager _db;

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
		JiveGlobals.setProperty("plugin.remoteroster.iqLastFilter", (iqLastFilter ? "true" : "false"));
		JiveGlobals.setProperty("plugin.remoteroster.mucFilter", (mucFilter ? "true" : "false"));
		response.sendRedirect("rr-main.jsp?success=true");
		return;
	}

	// Get the session manager
	SessionManager sessionManager = webManager.getSessionManager();

	Collection<ComponentSession> sessions = sessionManager.getComponentSessions();

	_db = DatabaseManager.getInstance();
%>

<html>
<head>
<title><fmt:message key="rr.summary.title" /></title>
<link href="./css/rr.css" rel="stylesheet" type="text/css">
<script src="./js/http.js" type="text/javascript"></script>
<script src="./js/jquery.js" type="text/javascript"></script>
<script src="./js/rr.js" type="text/javascript"></script>
<script src="./js/jquery.sparkline.js" type="text/javascript"></script>
<script src="./js/jquery.horiz-bar-graph.js" type="text/javascript"></script>
<!--[if lte IE 8]><script language="javascript" type="text/javascript" src="./js/excanvas.min.js"></script><![endif]-->
<script language="javascript" type="text/javascript" src="./js/jquery.flot.js"></script>
<script language="javascript" type="text/javascript" src="./js/jquery.flot.pie.js"></script>

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

					long incoming = componentSession.getNumClientPackets();
					long outgoing = componentSession.getNumServerPackets();
					long both = incoming + outgoing;
					int incomingPercent = (int) (incoming * 100 / both);
					int outgoingPercent = (int) (outgoing * 100 / both);
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
						<td class="gatewayIcons"><img src="images/log-16x16.png" onclick="slideToggle('#logs<%=i%>')"><img
							src="images/permissions-16x16.png" id="showPermissions" onclick="slideToggle('#permission<%=i%>')"><img
							src="images/info-16x16.png" id="showConfig" onclick="slideToggle('#config<%=i%>')"></td>
					</tr>
				</tbody>
			</table>
			<div id="config<%=i%>" class="slider">
				<div class="sildeHeader">Information</div>
				<table class="configTable">
					<tbody>
						<tr id="logodd">
							<td width="200px">Domain:</td>
							<td><%=componentSession.getExternalComponent().getInitialSubdomain()%></td>
						</tr>
						<tr id="logeven">
							<td>Status:</td>
							<td>Online</td>
						</tr>
						<tr id="logodd">
							<td>Packages Send/Received:</td>
							<td><dl class="browser-data" title="">
									<dt>Incoming</dt>
									<dd><%=incomingPercent%></dd>
									<dt>Outgoing</dt>
									<dd><%=outgoingPercent%></dd>
								</dl></td>
						</tr>
					</tbody>
				</table>
			</div>
			<div id="permission<%=i%>" class="slider">
				<div class="sildeHeader">Access control</div>
				<table class="groupTable">
					<tbody>
						<tr id="loghead">
							<td colspan="3">You can limit the access to the external component to an existing group</td>
						</tr>
						<tr>
							<td class="permissionTableColumn">Groupname:</td>
							<td><input class="groupInput" type="text" id="groupSearch<%=i%>"
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
			<div id="logs<%=i%>" class="slider">
				<%
					int iqs = _db.getPacketCount(componentSession.getExternalComponent().getInitialSubdomain(),
								Class.forName("org.xmpp.packet.IQ"));
						int msgs = _db.getPacketCount(componentSession.getExternalComponent().getInitialSubdomain(),
								Class.forName("org.xmpp.packet.Message"));
						int rosters = _db.getPacketCount(componentSession.getExternalComponent().getInitialSubdomain(),
								Class.forName("org.xmpp.packet.Roster"));
						int presences = _db.getPacketCount(componentSession.getExternalComponent().getInitialSubdomain(),
								Class.forName("org.xmpp.packet.Presence"));
				%>
				<div class="sildeHeader">Logs & Statistics</div>

				<table class="logtable">
					<tfoot>
						<tr id="logfoot">
							<td colspan="2">Packages being logged for <%=JiveGlobals.getIntProperty("plugin.remoteroster.log.cleaner.minutes", 60)%>
								minutes
							</td>
							<td><a style="float: right;"
								onClick="window.open('liveStats.jsp?component=<%=componentSession.getExternalComponent().getInitialSubdomain()%>','mywindow','width=1200,height=700')">Show
									realtime Log</a>
						</tr>
					</tfoot>
					<tbody>
						<tr id="loghead">
							<td width="200px">Paket type</td>
							<td width="100px">Number</td>
							<td></td>
						</tr>
						<tr id="logodd">
							<td>IQ</td>
							<td id="logiq<%=i%>"><%=iqs%></td>
							<td rowspan="5"><div id="pie<%=i%>" class="graph"></div></td>
						</tr>
						<tr id="logeven">
							<td>Messages</td>
							<td id="logmsg<%=i%>"><%=msgs%></td>
						</tr>
						<tr id="logodd">
							<td>Roster</td>
							<td id="logroster<%=i%>"><%=rosters%></td>
						</tr>
						<tr id="logeven">
							<td>Presence</td>
							<td id="logpresence<%=i%>"><%=presences%></td>
						</tr>
						<tr id="logodd">
							<td><span style="font-weight: bold;">Total:</span></td>
							<td><span style="font-weight: bold;"><%=iqs + msgs + rosters + presences%></span></td>
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
		
		
<div class="jive-contentBoxHeader">General Options</div>
<div class="jive-contentBox">
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
   <tbody>
   <tr valign="top">
       <td width="100%">
           <table cellpadding="0" cellspacing="0" border="0">
           <tbody>
				<tr>
					<td><input type="checkbox" name="persistentEnabled" id="GO1" value="true"
						<%=JiveGlobals.getBooleanProperty("plugin.remoteroster.persistent", false) ? "checked=\"checked\"" : ""%> />

					</td>
					<td><label for="GO1">Enable persistent Roster</label></td>
				</tr>
				<tr>
					<td />
					<td align="left" style="font-size: -3; color: grey">When Persistent-Roster is enabled, contacts will be saved to database and
					no contacts will be deleted	by GoJara automatically.<br>					
					When Persistent-Roster is disabled, contacts will not be saved to databse and 
					GoJara will automatically delete all Legacy-RosterItems from the OF-Roster of a User upon logout. </td>
				</tr>
				<tr>
					<td><input type="checkbox" name="mucFilter" id="GO2" value="true"
						<%=JiveGlobals.getBooleanProperty("plugin.remoteroster.mucFilter", false) ? "checked=\"checked\"" : ""%> />
					</td>
					<td><label for="GO2">Only allow internal Jabber Conferences</label></td>
				</tr>
				<tr>
					<td />
					<td align="left" style="font-size: -3; color: grey">Spectrum might add MUC(Multi User Chat) to supported features
					 of some Transports. If this should not be allowed, because only internal Jabber Conferences should be used, GoJara
					 can remove these.</td>
				</tr>
           </tbody>
           </table>
       </td>
   </tr>
   </tbody>
   </table>
</div>

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
										<td><label for="SDI"> Support jabber:iq:registered feature</label></td>
									</tr>
									<tr>
										<td />
										<td align="left" style="font-size: -3; color: grey">If you use Spark clients within your network, it
											might be necessary to modify the service discovery packets between Spark and the external component. If you
											check this RemoteRoster will add the feature "jabber:iq:registered" to the disco#info to indicate that the
											Client is registered with the external component.</td>
									</tr>
									<tr>
										<td><input type="checkbox" name="iqLastFilter" id="SDI2" value="true"
											<%=JiveGlobals.getBooleanProperty("plugin.remoteroster.iqLastFilter", false) ? "checked=\"checked\""
					: ""%> />

										</td>
										<td><label for="SDI">Reply to jabber:iq:last </label></td>
									</tr>
									<tr>
										<td />
										<td align="left" style="font-size: -3; color: grey">Some clients try to check how long a contact is already offline.
										 This feature is not supported by spectrum so it won't response to this IQ stanza. To prevent the client from waiting
										 for a response we could answer with a service-unavailable message as described in XEP-12.</td>
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
