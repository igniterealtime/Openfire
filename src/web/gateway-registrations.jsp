<%@ page import="java.util.*,
                 org.xmpp.packet.Presence,
                 org.jivesoftware.wildfire.ClientSession,
                 org.jivesoftware.wildfire.SessionManager,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin,
                 org.jivesoftware.wildfire.gateway.Registration,
                 org.jivesoftware.wildfire.gateway.RegistrationManager"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
    webManager.init(request, response, session, application, out);

    GatewayPlugin plugin = (GatewayPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("gateway");

    RegistrationManager registrationManager = new RegistrationManager();
    Collection<Registration> registrations = registrationManager.getRegistrations();
    int regCount = registrations.size();

    // Get the user manager
    SessionManager sessionManager = webManager.getSessionManager();
%>

<html>
<head>
<title>Gateway Registrations</title>

<meta name="pageID" content="gateway-registrations">

<style type="text/css">
<!--	@import url("style/gateways.css");    -->
</style>

<script language="JavaScript" type="text/javascript" src="scripts/gateways.js"></script>

</head>
<body>


<p>Below is a list of all gateway service registrations. To filter by active sessions and/or specific gateways select the options 
below and update the view.</p>


<!-- BEGIN add registration -->
<div class="jive-gateway-addregBtn" id="jiveAddRegButton">
	<a href="#" onClick="toggleAdd(); return false" id="jiveAddRegLink">Add a new registration</a>
</div>
<div class="jive-gateway-addreg" id="jiveAddRegPanel" style="display: none;">
	<div class="jive-gateway-addregPad">
		<form action="" name="jive-addRegistration">
		<div class="jive-registrations-addJid">
			<input type="text" name="gatewayJID" size="12" maxlength="50" value=""><br>
			<strong>user (JID)</strong>
		</div>
		<div class="jive-registrations-addGateway">
			<select name="gateway" size="1">
			<option value="0" SELECTED> -- select -- </option>
			<option value="aim">AIM</option>
			<option value="icq">ICQ</option>
			<option value="msn">MSN</option>
			<option value="yahoo">Yahoo</option>
			</select><br>
			<strong>gateway</strong>
		</div>
		<div class="jive-registrations-addUsername">
			<input type="text" name="gatewayUser" size="12" maxlength="50" value=""><br>
			<strong>username</strong>
		</div>
		<div class="jive-registrations-addPassword">
			<input type="password" name="gatewayPass" size="12" maxlength="50" value=""><br>
			<strong>password</strong>
		</div>
		<div class="jive-registrations-addButtons">
			<input type="submit" name="Submit" value="Add" class="savechanges" onClick="toggleAdd();"> &nbsp;
			<input type="reset" name="reset" value="Cancel" class="cancel" onClick="toggleAdd();">
		</div>
		</form>
	</div>
</div>
<!-- END add registration -->




<!-- BEGIN registrations table -->
<div class="jive-registrations">


	<!-- BEGIN results -->
	<div class="jive-registrations-results">
		Registrations: <strong>1-15</strong> of <strong><%= regCount %></strong>
	</div>
	<!-- END results -->


	<!-- BEGIN results size (num per page) -->
	<div class="jive-registrations-resultsSize">
		<select name="numPerPage" id="numPerPage" size="1">
		<option value="1" SELECTED>15</option>
		<option value="2">30</option>
		<option value="3">50</option>
		<option value="4">100</option>
		</select>
		<span>per page</span>
	</div>
	<!-- END results size -->


	<!-- BEGIN pagination -->
	<div class="jive-registrations-pagination">
		<strong>Page:</strong> &nbsp; 
		<a href="#"><strong>1</strong></a> 
		<a href="#">2</a> 
		<a href="#">3</a> 
		<a href="#">4</a> -
		<a href="#"><strong>Next &gt;</strong></a>
	</div>
	<!-- END pagination -->

	
	
	
	<!-- BEGIN gateway filter -->
	<form action="" name="jive-filterForm">
	<div class="jive-gateway-filter" id="jiveGatewayFilters">
		<div>
		<strong>Filter by:</strong>
		<label for="filterAIMcheckbox">
			<input type="checkbox" name="filter[]" value="aim" checked id="filterAIMcheckbox"> 
			<img src="images/aim.gif" alt="" border="0"> 
			<span>AIM</span>
		</label>
		<label for="filterICQcheckbox">
			<input type="checkbox" name="filter[]" value="icq" checked id="filterICQcheckbox"> 
			<img src="images/icq.gif" alt="" border="0"> 
			<span>ICQ</span>
		</label>
		<label for="filterMSNcheckbox">
			<input type="checkbox" name="filter[]" value="msn" checked id="filterMSNcheckbox"> 
			<img src="images/msn.gif" alt="" border="0"> 
			<span>MSN</span>
		</label>
		<label for="filterYAHOOcheckbox">
			<input type="checkbox" name="filter[]" value="yahoo" checked id="filterYAHOOcheckbox"> 
			<img src="images/yahoo.gif" alt="" border="0"> 
			<span>Yahoo</span>
		</label>
		<label for="filterActiveOnly">
			<input type="checkbox" name="filter[]" value="signedon" id="filterActiveOnly"> 
			<span>Signed on only</span>
		</label>	
		<input type="submit" name="submit" value="Update" class="filterBtn"> 
		</div>
	</div>
	</form>
	<!-- END gateway filter -->
	
	

	<!-- BEGIN registrations table -->
	<table cellpadding="0" cellspacing="0">
	<thead>
		<tr>
			<th width="20" class="border-left">&nbsp;</th>
			<th width="25%">User</th>
			<th>Service/Username</th>
			<th>Last Login</th>
			<th width="1%"><div align="center">Edit</div></th>
			<th width="1%" class="border-right">Remove</th>
		</tr>
	</thead>
	<tbody>
		
<%
    for (Registration registration : registrations) {
        long id = registration.getRegistrationID();
        String status = "unavailable";
        String linestatus = "offline";
    	try {
            ClientSession clientSession = (ClientSession)sessionManager.getSessions(registration.getJID().getNode()).toArray()[0];
            if (clientSession != null) {
                Presence presence = clientSession.getPresence();
                if (presence == null) {
                    // not logged in, leave alone
                }
                else if (presence.getShow() == Presence.Show.xa) {
                    status = "away";
                    linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.away) {
                    status = "away";
                    linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.chat) {
                    status = "free_chat";
                    linestatus = "online";
                }
                else if (presence.getShow() == Presence.Show.dnd) {
                    status = "dnd";
                    linestatus = "online";
                }
                else if (presence.isAvailable()) {
                    status = "available";
                    linestatus = "online";
                }
            }
        }
        catch (Exception e) {
        }

        Date lastLogin = registration.getLastLogin();
        String lastLoginStr = ((lastLogin != null) ? lastLogin.toString() : "<i>never</i>");

        boolean sessionActive = false;
        try {
            plugin.getTransportInstance(registration.getTransportType().toString()).getTransport().getSessionManager().getSession(registration.getJID());
            sessionActive = true;
        }
        catch (Exception e) {
            sessionActive = false;
            Log.error("what the crap?", e);
        }
%>
		<tr id="jiveRegistration<%= id %>">
			<td align="center">
			<img src="/images/im_<%= status %>.gif" alt="<%= linestatus %>" border="0"></td>
			<td><%= registration.getJID() %></td>
			<td><span class="jive-gateway-<%= linestatus %> jive-gateway-<%= registration.getTransportType().toString().toUpperCase() %><%= ((sessionActive) ? "on" : "off") %>"><%= registration.getUsername() %></span></td>
			<td><%= lastLoginStr %></td>
			<td align="center"><a href="#" onClick="toggleEdit(<%= id %>); return false"><img src="/images/edit-16x16.gif" alt="" border="0"></a></td>
			<td align="center"><a href="#" onClick="alert('Are you sure you want to delete this registration?'); return false"><img src="/images/delete-16x16.gif" alt="" border="0"></a></td>
		</tr>
		<tr id="jiveRegistrationEdit<%= id %>" style="display: none;">
			<td align="center">
			<img src="/images/im_<%= status %>.gif" alt="<%= status %>" border="0"></td>
			<td><%= registration.getJID() %></td>
			<td colspan="4">
			<span class="jive-gateway-<%= linestatus %> jive-gateway-<%= registration.getTransportType().toString().toUpperCase() %>on">
				<div class="jive-registrations-editUsername">
				<input type="text" name="username" size="12" maxlength="50" value="<%= registration.getUsername() %>"><br>
				<strong>username</strong>
				</div>
				<div class="jive-registrations-editPassword">
				<input type="password" name="password" size="12" maxlength="50" value="*********"><br>
				<strong>password</strong>
				</div>
				<div class="jive-registrations-editButtons">
				<input type="submit" name="Submit" value="Save Changes" class="savechanges" onClick="toggleEdit(<%= id %>);"> &nbsp;
				<input type="reset" name="reset" value="Cancel" class="cancel" onClick="toggleEdit(<%= id %>);">
				</div>
			</span>
			</td>
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
		<a href="#"><strong>1</strong></a> 
		<a href="#">2</a> 
		<a href="#">3</a> 
		<a href="#">4</a> -
		<a href="#"><strong>Next &gt;</strong></a>
	</div>
	<!-- END pagination -->


</div>
<!-- END registrations table -->


<br clear="all">


</body>
</html>
