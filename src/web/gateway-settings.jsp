<%@ page import="javax.servlet.jsp.JspWriter,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin,
                 org.jivesoftware.wildfire.gateway.TransportType"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    GatewayPlugin plugin = (GatewayPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("gateway");

    class GatewaySettings {
        String description = null;
        TransportType gatewayType = null;
        boolean gwEnabled = false;
        JspWriter out = null;

        GatewaySettings(JspWriter out, GatewayPlugin plugin, TransportType gatewayType, String desc) {
            this.description = desc;
            this.gatewayType = gatewayType;
            this.gwEnabled = plugin.serviceEnabled(gatewayType.toString());
            this.out = out;
        }

        void printSettingsDialog() {
            try {
%>

	<!-- BEGIN gateway - <%= this.gatewayType.toString().toUpperCase() %> -->
	<div <%= ((!this.gwEnabled) ? " class='jive-gateway jive-gatewayDisabled'" : "class='jive-gateway'") %> id="jive<%= this.gatewayType.toString().toUpperCase() %>">
		<label for="jive<%= this.gatewayType.toString().toUpperCase() %>checkbox">
			<input type="checkbox" name="gateway" value="<%= this.gatewayType.toString().toLowerCase() %>" id="jive<%= this.gatewayType.toString().toUpperCase() %>checkbox" <%= ((this.gwEnabled) ? "checked" : "") %> onClick="toggleGW('<%= this.gatewayType.toString().toLowerCase() %>','jive<%= this.gatewayType.toString().toUpperCase() %>checkbox'); checkToggle(jive<%= this.gatewayType.toString().toUpperCase() %>); return true"> 
			<img src="images/<%= this.gatewayType.toString().toLowerCase() %>.gif" alt="" border="0">
			<strong><%= this.description %></strong>
		</label>
		<div class="jive-gatewayButtons">
			<a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options,jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %> style="display:none">Options</a>
			<a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms,jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>permsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %> style="display:none">Permissions</a>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>options" style="display: none;">
		<div>
		<form action="">
			<!-- <input type="checkbox" name="filetransfer" value="enabled"> Enable file transfer<br> -->
			<input type="checkbox" name="reconnect" value="enabled"> Reconnect on disconnect<br>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options,jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options,jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>perms" style="display: none;">
		<div>
		<form action="">
			<input type="radio" name="userreg" value="all" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific"> These users and/or groups can register<br>
			<input type="radio" name="userreg" value="manual"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms,jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms,jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway 1 - <%= this.gatewayType.toString().toUpperCase() %> -->

<%
            }
            catch (Exception e) {
                // hrm
            }
        }
    }

    GatewaySettings aimSettings = new GatewaySettings(out, plugin, TransportType.aim, "AOL Instant Messenger");
    GatewaySettings icqSettings = new GatewaySettings(out, plugin, TransportType.icq, "ICQ");
    GatewaySettings msnSettings = new GatewaySettings(out, plugin, TransportType.msn, "MSN Messenger");
    GatewaySettings yahooSettings = new GatewaySettings(out, plugin, TransportType.yahoo, "Yahoo! Messenger");
%>


<html>
<head>
<title>Gateway Settings</title>

<meta name="pageID" content="gateway-settings">

<style type="text/css">
<!--	@import url("style/gateways.css");    -->
</style>

<script language="JavaScript" type="text/javascript" src="scripts/gateways.js"></script>

</head>
<body>


<p>Select which gateways will be allowed, what features are available, and who can connect to each gateway service. Checking a gateway enables the service.</p>


<form action="" name="gatewayForm">

<% aimSettings.printSettingsDialog(); %>
<% icqSettings.printSettingsDialog(); %>
<% msnSettings.printSettingsDialog(); %>
<% yahooSettings.printSettingsDialog(); %>

</form>


<br clear="all">


</body>
</html>
