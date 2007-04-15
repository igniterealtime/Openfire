<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.gateway.GatewayPlugin,
                 org.jivesoftware.openfire.gateway.Registration,
                 org.jivesoftware.openfire.gateway.RegistrationManager,
                 org.jivesoftware.openfire.session.ClientSession,
                 org.xmpp.packet.JID"
    errorPage="error.jsp"
%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.xmpp.packet.Presence" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
    final GatewayPlugin plugin =
            (GatewayPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("gateway");
    HashMap<String,Boolean> trEnabled = new HashMap<String,Boolean>();
    trEnabled.put("aim", plugin.getTransportInstance("aim").isEnabled());
    trEnabled.put("gtalk", plugin.getTransportInstance("gtalk").isEnabled());
    trEnabled.put("icq", plugin.getTransportInstance("icq").isEnabled());
    trEnabled.put("irc", plugin.getTransportInstance("irc").isEnabled());
    trEnabled.put("msn", plugin.getTransportInstance("msn").isEnabled());
    trEnabled.put("xmpp", plugin.getTransportInstance("xmpp").isEnabled());
    trEnabled.put("yahoo", plugin.getTransportInstance("yahoo").isEnabled());

    webManager.init(request, response, session, application, out);

    RegistrationManager registrationManager = new RegistrationManager();
    Collection<Registration> registrations = registrationManager.getRegistrations();

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Lets gather what information we are going to display
    class regResult {
        public JID jid = null;
        public long id = -1;
        public String type = null;
        public String username = null;
        public String nickname = null;
        public String status = "unavailable";
        public String linestatus = "offline";
        public String lastLogin = null;
        public boolean sessionActive = false;
    }
    Collection<regResult> regResults = new ArrayList<regResult>();

    ArrayList<String> filteropts = new ArrayList<String>();
    if (ParamUtils.getParameter(request, "filter[]") != null) {
        String[] optlist = ParamUtils.getParameters(request, "filter[]");
        for (String opt : optlist) {
            filteropts.add(opt);
        }
    }
    else if (webManager.getPageProperty("gateway-registrations", "filterSET", 0) != 0) {
        if (webManager.getPageProperty("gateway-registrations", "filterAIM", 0) != 0) { filteropts.add("aim"); }
        if (webManager.getPageProperty("gateway-registrations", "filterICQ", 0) != 0) { filteropts.add("icq"); }
        if (webManager.getPageProperty("gateway-registrations", "filterMSN", 0) != 0) { filteropts.add("msn"); }
        if (webManager.getPageProperty("gateway-registrations", "filterYAHOO", 0) != 0) { filteropts.add("yahoo"); }
        if (webManager.getPageProperty("gateway-registrations", "filterIRC", 0) != 0) { filteropts.add("irc"); }
        if (webManager.getPageProperty("gateway-registrations", "filterXMPP", 0) != 0) { filteropts.add("xmpp"); }
        if (webManager.getPageProperty("gateway-registrations", "filterGTALK", 0) != 0) { filteropts.add("gtalk"); }
        if (webManager.getPageProperty("gateway-registrations", "filterSIGNEDON", 0) != 0) { filteropts.add("signedon"); }
    }
    else {
        filteropts.add("aim");
        filteropts.add("icq");
        filteropts.add("msn");
        filteropts.add("yahoo");
        filteropts.add("irc");
        filteropts.add("gtalk");
        filteropts.add("xmpp");
    }

    webManager.setPageProperty("gateway-registrations", "filterSET", 1);
    webManager.setPageProperty("gateway-registrations", "filterAIM", filteropts.contains("aim") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterICQ", filteropts.contains("icq") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterMSN", filteropts.contains("msn") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterYAHOO", filteropts.contains("yahoo") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterIRC", filteropts.contains("irc") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterGTALK", filteropts.contains("gtalk") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterXMPP", filteropts.contains("xmpp") ? 1 : 0);
    webManager.setPageProperty("gateway-registrations", "filterSIGNEDON", filteropts.contains("signedon") ? 1 : 0);

    int resCount = 0;
    for (Registration registration : registrations) {
        regResult res = new regResult();
        res.id = registration.getRegistrationID();
        res.jid = registration.getJID();
        res.username = registration.getUsername();
        res.nickname = registration.getNickname();
        res.type = registration.getTransportType().toString();
        if (!filteropts.contains(res.type)) { continue; }

    	try {
            ClientSession clientSession = (ClientSession)sessionManager.getSessions(res.jid.getNode()).toArray()[0];
            if (clientSession != null) {
                Presence presence = clientSession.getPresence();
                if (presence == null) {
                    // not logged in, leave alone
                }
                else if (presence.getShow() == Presence.Show.xa) {
                    res.status = "away";
                    res.linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.away) {
                    res.status = "away";
                    res.linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.chat) {
                    res.status = "free_chat";
                    res.linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.dnd) {
                    res.status = "dnd";
                    res.linestatus = "online";
                }
                else if (presence.isAvailable()) {
                    res.status = "available";
                    res.linestatus = "online";
                }
            }
        }
        catch (Exception e) {
        }

        if (res.linestatus.equals("offline") && filteropts.contains("signedon")) { continue; }

        Date lastLogin = registration.getLastLogin();
        res.lastLogin = ((lastLogin != null) ? lastLogin.toString() : "<i>"+LocaleUtils.getLocalizedString("gateway.web.registrations.never", "gateway")+"</i>");

        res.sessionActive = false;
        try {
            plugin.getTransportInstance(res.type).getTransport().getSessionManager().getSession(res.jid);
            res.sessionActive = true;
        }
        catch (Exception e) {
            res.sessionActive = false;
        }
        resCount++;
        regResults.add(res);
    }

    final int DEFAULT_RANGE = 15;
    final int[] RANGE_PRESETS = {15, 30, 50, 100};

    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",webManager.getRowsPerPage("gateway-registrations", DEFAULT_RANGE));

    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("gateway-registrations", range);
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)resCount/(double)range);
    int curPage = (start/range) + 1;

    int topRange = ((start+range) < resCount) ? (start+range) : resCount;
%>




<html>

<head>
<title><fmt:message key="gateway.web.registrations.title" /></title>
<meta name="pageID" content="gateway-registrations">
<style type="text/css">
<!--	@import url("style/gateways.css");    -->
</style>
<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/ConfigManager.js" type="text/javascript"></script>
<script src="dwr/interface/ConnectionTester.js" type="text/javascript"></script>
<script language="JavaScript" type="text/javascript" src="scripts/gateways.js"></script>
<script type="text/javascript" >
    DWREngine.setErrorHandler(handleError);
    window.onerror = handleError;

    function handleError(error) {
        // swallow errors
    }

    var lastRegistrationID;

    function deleteRegistration(registrationID) {
        lastRegistrationID = registrationID;
        ConfigManager.deleteRegistration(registrationID, cb_deleteRegistration);
    }

    function cb_deleteRegistration(statusMsg) {
        Effect.Fade("jiveRegistration"+lastRegistrationID);
        document.getElementById("regStatusMsg").style.display = "";
        if (statusMsg == null) {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-success'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.registrations.deletesuccess' /></div>";
        }
        else {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-error'><img src='images/error-16x16.gif' align='absmiddle' />"+statusMsg+"</div>";
        }
        setTimeout("to_statusMessage()", 5000);
    }

    function updateRegistration(registrationID) {
        var usernameEntry = DWRUtil.getValue("gatewayUsername"+registrationID);
        var passwordEntry = DWRUtil.getValue("gatewayPassword"+registrationID);
        if (passwordEntry == "********") {
            passwordEntry = null;
        }
        var nicknameEntry = DWRUtil.getValue("gatewayNickname"+registrationID);
        lastRegistrationID = registrationID;
        ConfigManager.updateRegistration(registrationID, usernameEntry, passwordEntry, nicknameEntry, cb_updateRegistration);
    }

    function cb_updateRegistration(statusMsg) {
        toggleEdit(lastRegistrationID);
        var usernameEntry = DWRUtil.getValue("gatewayUsername"+lastRegistrationID);
        document.getElementById("registrationUsername"+lastRegistrationID).innerHTML = usernameEntry;
        document.getElementById("regStatusMsg").style.display = "";
        if (statusMsg == null) {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-success'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.registrations.updatesuccess' /></div>";
        }
        else {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-error'><img src='images/error-16x16.gif' align='absmiddle' />"+statusMsg+"</div>";
        }
        setTimeout("to_statusMessage()", 5000);
    }

    function addRegistration() {
        var userEntry = DWRUtil.getValue("newRegistrationUser");
        var typeEntry = DWRUtil.getValue("newRegistrationType");
        var legacyUsernameEntry = DWRUtil.getValue("newRegistrationLegacyUsername");
        var legacyPasswordEntry = DWRUtil.getValue("newRegistrationLegacyPassword");
        var legacyNicknameEntry = DWRUtil.getValue("newRegistrationLegacyNickname");
        ConfigManager.addRegistration(userEntry, typeEntry, legacyUsernameEntry, legacyPasswordEntry, legacyNicknameEntry, cb_addRegistration);
    }

    function cb_addRegistration(statusMsg) {
        toggleAdd();
        document.getElementById("regStatusMsg").style.display = "";
        if (statusMsg == null) {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-success'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.registrations.addsuccess' /></div>";
        }
        else {
            document.getElementById("regStatusMsg").innerHTML = "<div class='jive-error'><img src='images/error-16x16.gif' align='absmiddle' />"+statusMsg+"</div>";
        }
        setTimeout("to_statusMessage()", 5000);
    }

    function to_statusMessage() {
        Effect.Fade("regStatusMsg");
    }

    function pingSession() {
        ConnectionTester.pingSession();
        setTimeout("pingSession()", 60000); // Every minute
    }

    setTimeout("pingSession()", 60000); // One minute after first load
</script>
</head>

<body>
<p><fmt:message key="gateway.web.registrations.instructions" /></p>

<div id="regStatusMsg" style="display: none"></div>

<!-- BEGIN add registration -->
<div class="jive-gateway-addregBtn" id="jiveAddRegButton">
	<a href="" onClick="toggleAdd(); return false" id="jiveAddRegLink"><fmt:message key="gateway.web.registrations.addnewreg" /></a>
</div>
<div class="jive-gateway-addreg" id="jiveAddRegPanel" style="display: none;">
	<div class="jive-gateway-addregPad">
		<form action="" name="jive-addRegistration" onSubmit="return false">
        <input type="hidden" name="action" value="add" />
		<div class="jive-registrations-addJid">
			<input type="text" name="newRegistrationUser" id="newRegistrationUser" size="12" maxlength="50" value=""><br>
			<strong><fmt:message key="gateway.web.registrations.jid" /></strong>
		</div>
		<div class="jive-registrations-addGateway">
			<select name="newRegistrationType" id="newRegistrationType" size="1">
			<option value="0" SELECTED> -- select -- </option>
			<% if (trEnabled.get("aim")) { %> <option value="aim"><fmt:message key="gateway.aim.shortservice" /></option> <% } %>
            <% if (trEnabled.get("gtalk")) { %> <option value="gtalk"><fmt:message key="gateway.gtalk.shortservice" /></option> <% } %>
            <% if (trEnabled.get("icq")) { %> <option value="icq"><fmt:message key="gateway.icq.shortservice" /></option> <% } %>
			<% if (trEnabled.get("irc")) { %> <option value="irc"><fmt:message key="gateway.irc.shortservice" /></option> <% } %>
            <% if (trEnabled.get("msn")) { %> <option value="msn"><fmt:message key="gateway.msn.shortservice" /></option> <% } %>
            <% if (trEnabled.get("xmpp")) { %> <option value="xmpp"><fmt:message key="gateway.xmpp.shortservice" /></option> <% } %>
            <% if (trEnabled.get("yahoo")) { %> <option value="yahoo"><fmt:message key="gateway.yahoo.shortservice" /></option> <% } %>
			</select><br>
			<strong><fmt:message key="gateway.web.registrations.gateway" /></strong>
		</div>
		<div class="jive-registrations-addUsername">
			<input type="text" name="newRegistrationLegacyUsername" id="newRegistrationLegacyUsername" size="12" maxlength="50" value=""><br>
			<strong><fmt:message key="gateway.web.registrations.username" /></strong>
		</div>
		<div class="jive-registrations-addPassword">
			<input type="password" name="newRegistrationLegacyPassword" id="newRegistrationLegacyPassword" size="12" maxlength="50" value=""><br>
			<strong><fmt:message key="gateway.web.registrations.password" /></strong>
		</div>
            <div class="jive-registrations-addNickname">
                <input type="text" name="newRegistrationLegacyNickname" id="newRegistrationLegacyNickname" size="12" maxlength="50" value=""><br>
                <strong><fmt:message key="gateway.web.registrations.nickname" /></strong>
            </div>
        <div class="jive-registrations-addButtons">
			<input type="submit" name="Submit" value="<fmt:message key="global.add" />" class="savechanges" onClick="addRegistration(); return false"> &nbsp;
			<input type="reset" name="reset" value="<fmt:message key="global.cancel" />" class="cancel" onClick="toggleAdd();">
		</div>
		</form>
	</div>
</div>
<!-- END add registration -->



<!-- BEGIN registrations table -->
<div class="jive-registrations">


	<!-- BEGIN results -->
	<div class="jive-registrations-results">
		<fmt:message key="gateway.web.registrations.registrations" />: <strong><%= (start+1) %>-<%= topRange %></strong> of <strong><%= resCount %></strong>
	</div>
	<!-- END results -->


	<!-- BEGIN results size (num per page) -->
	<div class="jive-registrations-resultsSize"><form action="gateway-registrations.jsp" method="get">
		<select name="range" id="range" size="1" onchange="this.form.submit()">
                <%  for (int rangePreset : RANGE_PRESETS) { %>

                    <option value="<%= rangePreset %>"<%= (rangePreset== range ? "selected" : "") %>><%= rangePreset %></option>

                <%  } %>
		</select>
		<span><fmt:message key="gateway.web.registrations.perpage" /></span>
	</form></div>
	<!-- END results size -->


	<!-- BEGIN pagination -->
	<div class="jive-registrations-pagination">
		<strong><fmt:message key="gateway.web.registrations.page" />:</strong> &nbsp;
            <%
                if (numPages > 1 && ((curPage) > 1)) {
            %>
                    <a href="gateway-registrations.jsp?start=<%= ((curPage-2)*range) %>">&lt; <fmt:message key="gateway.web.registrations.prev" /></a>
            <%
                }
                for (int i=0; i<numPages; i++) {
                    boolean isCurrent = (i+1) == curPage;
                    if (isCurrent) {
            %>
                        <strong><%= (i+1) %></strong> 
            <%
                    }
                    else {
            %>
                        <a href="gateway-registrations.jsp?start=<%= (i*range) %>"><%= (i+1) %></a> 
            <%
                    }
                }
                if (numPages > 1 && ((curPage) < numPages)) {
            %>
                    <a href="gateway-registrations.jsp?start=<%= (curPage*range) %>"><fmt:message key="gateway.web.registrations.next" /> &gt;</a>
            <%
                }
            %>
	</div>
	<!-- END pagination -->
	
	
	<!-- BEGIN gateway filter -->
	<form action="gateway-registrations.jsp" name="jive-filterForm">
	<div class="jive-gateway-filter" id="jiveGatewayFilters">
		<div>
		<strong>Filter by:</strong>
		<label for="filterAIMcheckbox">
			<input type="checkbox" name="filter[]" value="aim" <%= ((filteropts.contains("aim")) ? "checked" : "") %> id="filterAIMcheckbox"> 
			<img src="images/aim.gif" border="0" alt="<fmt:message key="gateway.aim.shortservice" />">
			<!--<span><fmt:message key="gateway.aim.shortservice" /></span>-->
		</label>
        <label for="filterGTALKcheckbox">
            <input type="checkbox" name="filter[]" value="gtalk" <%= ((filteropts.contains("gtalk")) ? "checked" : "") %> id="filterGTALKcheckbox">
            <img src="images/gtalk.gif" border="0" alt="<fmt:message key="gateway.gtalk.shortservice" />">
            <!--<span><fmt:message key="gateway.gtalk.shortservice" /></span>-->
        </label>
        <label for="filterICQcheckbox">
			<input type="checkbox" name="filter[]" value="icq" <%= ((filteropts.contains("icq")) ? "checked" : "") %> id="filterICQcheckbox"> 
			<img src="images/icq.gif" border="0" alt="<fmt:message key="gateway.icq.shortservice" />">
			<!--<span><fmt:message key="gateway.icq.shortservice" /></span>-->
		</label>
        <label for="filterIRCcheckbox">
            <input type="checkbox" name="filter[]" value="irc" <%= ((filteropts.contains("irc")) ? "checked" : "") %> id="filterIRCcheckbox">
            <img src="images/irc.gif" border="0" alt="<fmt:message key="gateway.irc.shortservice" />">
            <!--<span><fmt:message key="gateway.irc.shortservice" /></span>-->
        </label>
        <label for="filterMSNcheckbox">
			<input type="checkbox" name="filter[]" value="msn" <%= ((filteropts.contains("msn")) ? "checked" : "") %> id="filterMSNcheckbox"> 
			<img src="images/msn.gif" border="0" alt="<fmt:message key="gateway.msn.shortservice" />">
			<!--<span><fmt:message key="gateway.msn.shortservice" /></span>-->
		</label>
        <label for="filterXMPPcheckbox">
            <input type="checkbox" name="filter[]" value="xmpp" <%= ((filteropts.contains("xmpp")) ? "checked" : "") %> id="filterXMPPcheckbox">
            <img src="images/xmpp.gif" border="0" alt="<fmt:message key="gateway.xmpp.shortservice" />">
            <!--<span><fmt:message key="gateway.xmpp.shortservice" /></span>-->
        </label>
        <label for="filterYAHOOcheckbox">
			<input type="checkbox" name="filter[]" value="yahoo" <%= ((filteropts.contains("yahoo")) ? "checked" : "") %> id="filterYAHOOcheckbox"> 
			<img src="images/yahoo.gif" border="0" alt="<fmt:message key="gateway.yahoo.shortservice" />"> 
			<!--<span><fmt:message key="gateway.yahoo.shortservice" /></span>-->
		</label>
		<label for="filterActiveOnly">
			<input type="checkbox" name="filter[]" value="signedon" <%= ((filteropts.contains("signedon")) ? "checked" : "") %> id="filterActiveOnly"> 
			<span><fmt:message key="gateway.web.registrations.signedon" /></span>
		</label>	
		<input type="submit" name="submit" value="<fmt:message key="gateway.web.registrations.update" />" class="filterBtn">
		</div>
	</div>
	</form>
	<!-- END gateway filter -->


    <!-- BEGIN registrations table -->
	<table cellpadding="0" cellspacing="0">
	<thead>
		<tr>
			<th width="20" class="border-left">&nbsp;</th>
			<th width="25%"><fmt:message key="gateway.web.registrations.user" /></th>
			<th><fmt:message key="gateway.web.registrations.serviceusername" /></th>
			<th><fmt:message key="gateway.web.registrations.lastlogin" /></th>
			<th width="1%"><div align="center"><fmt:message key="gateway.web.registrations.edit" /></div></th>
			<th width="1%" class="border-right"><fmt:message key="gateway.web.registrations.remove" /></th>
		</tr>
	</thead>
	<tbody>
		
<%
    int cnt = 0;
    for (regResult result : regResults) {
        cnt++;
        if (cnt < (start+1)) { continue; }
        if (cnt > (start+range)) { continue; }
%>
		<tr id="jiveRegistration<%= result.id %>">
			<td align="center">
			<img src="images/im_<%= result.status %>.gif" alt="<%= result.linestatus %>" border="0"></td>
			<td><%= result.jid %></td>
			<td><span class="jive-gateway-<%= result.linestatus %> jive-gateway-<%= result.type.toUpperCase() %><%= ((result.sessionActive) ? "on" : "off") %>"><span id="registrationUsername<%= result.id %>"><%= result.username %></span></span></td>
			<td><%= result.lastLogin %></td>
			<td align="center"><a href="javascript:noop()" onClick="<% if (!trEnabled.get(result.type)) { %>alert('You must enable this transport to modify registrations.'); return false;<% } else { %>toggleEdit(<%= result.id %>); return false<% } %>"><img src="images/edit-16x16.gif" alt="<fmt:message key="global.edit" />" border="0"></a></td>
            <td align="center"><a href="javascript:noop()" onClick="<% if (!trEnabled.get(result.type)) { %>alert('You must enable this transport to delete registrations.'); return false;<% } else { %>if (confirm('<fmt:message key="gateway.web.registrations.confirmdelete" />')) { deleteRegistration('<%= result.id %>'); return false; } else { return false; }<% } %>"><img src="images/delete-16x16.gif" alt="<fmt:message key="global.delete" />" border="0"></a></td>
		</tr>
		<tr id="jiveRegistrationEdit<%= result.id %>" style="display: none">
			<td align="center"><img src="images/im_<%= result.status %>.gif" alt="<%= result.status %>" border="0"></td>
			<td><%= result.jid %></td>
			<td colspan="4"><form method="post" id="editRegistration<%= result.id %>" name="editRegistration<%= result.id %>" action="" onSubmit="return false">
			<span class="jive-gateway-<%= result.linestatus %> jive-gateway-<%= result.type.toUpperCase() %>on">
				<div class="jive-registrations-editUsername">
				<input type="text" name="gatewayUsername<%= result.id %>" id="gatewayUsername<%= result.id %>"size="12" maxlength="50" value="<%= result.username %>"><br>
				<strong><fmt:message key="gateway.web.registrations.username" /></strong>
				</div>
				<div class="jive-registrations-editPassword">
				<input type="password" name="gatewayPassword<%= result.id %>" id="gatewayPassword<%= result.id %>"size="12" maxlength="50" value="********"><br>
				<strong><fmt:message key="gateway.web.registrations.password" /></strong>
				</div>
                <div class="jive-registrations-editNickname">
                <input type="text" name="gatewayNickname<%= result.id %>%>" id="gatewayNickname<%= result.id %>" size="12" maxlength="50" value="<%= result.nickname %>"><br>
                <strong><fmt:message key="gateway.web.registrations.nickname" /></strong>
                </div>
                <div class="jive-registrations-editButtons">
				<input type="submit" name="Submit" value="<fmt:message key="global.save_changes" />" class="savechanges" onClick="updateRegistration('<%= result.id %>'); return false" /> &nbsp;
				<input type="reset" name="reset" value="<fmt:message key="global.cancel" />" class="cancel" onClick="toggleEdit(<%= result.id %>);" />
				</div>
			</span>
			</form></td>
		</tr>
<%
    }
%>
	</tbody>
	</table>
	<!-- BEGIN registrations table -->


	<!-- BEGIN pagination -->
	<div class="jive-registrations-pagination">
		<strong>Page:</strong> &nbsp; 
            <%
                if (numPages > 1 && ((curPage) > 1)) {
            %>
                    <a href="gateway-registrations.jsp?start=<%= ((curPage-2)*range) %>">&lt; Prev</a> 
            <%
                }
                for (int i=0; i<numPages; i++) {
                    boolean isCurrent = (i+1) == curPage;
                    if (isCurrent) {
            %>
                        <strong><%= (i+1) %></strong> 
            <%
                    }
                    else {
            %>
                        <a href="gateway-registrations.jsp?start=<%= (i*range) %>"><%= (i+1) %></a> 
            <%
                    }
                }
                if (numPages > 1 && ((curPage) < numPages)) {
            %>
                    <a href="gateway-registrations.jsp?start=<%= (curPage*range) %>">Next &gt;</a>
            <%
                }
            %>
	</div>
	<!-- END pagination -->


</div>
<!-- END registrations table -->


<br clear="all" />
</body>

</html>
