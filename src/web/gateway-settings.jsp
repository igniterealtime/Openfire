<%@ page import="javax.servlet.jsp.JspWriter,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin,
                 org.jivesoftware.wildfire.gateway.TransportType"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>

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
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>tests); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>testsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Tests</a>
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Options</a>
			<a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>permsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Permissions</a>
		</div>
	</div>
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>tests" style="display: none;">
        <div>
            <span style="font-weight: bold">Connect to host:</span> <span id="testhost">ninja</span><br />
            <span style="font-weight: bold">Connect to port:</span> <span id="testport">1234</span><br />
        <form action="">
            <input type="submit" name="submit" value="Test Connection" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>tests,jive<%= this.gatewayType.toString().toUpperCase() %>tests); return false" class="jive-formButton">
        </form>
        </div>
    </div>
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>options" style="display: none;">
		<div>
        <form action="">
            <table border="0" cellpadding="0" cellspacing="0">
                <tr valign="top">
                    <td align="left" width="50%">
                        <table border="0" cellpadding="1" cellspacing="2">
                            <tr valign="middle">
                                <td width="1%"><input type="checkbox" name="filetransfer" value="enabled"></td>
                                <td>Enable file transfer</td>
                            </tr>
                            <tr valign="middle">
                                <td width="1%"><input type="checkbox" name="reconnect" value="enabled"></td>
                                <td>Reconnect on disconnect</td>
                            </tr>
                            <tr valign="middle">
                                <td width="1%">&nbsp;</td>
                                <td>Reconnect Attemps: <input type="text" style="margin: 0.0px; padding: 0.0px" name="reconnect_attempts" size="4" maxlength="4" value="10" /></td>
                            </tr>
                        </table>
                    </td>
                    <td align="left" width="50%">
                        <table border="0">
                            <tr valign="middle">
                                <td align="right" width="1%">Host:</td>
                                <td><input type="text" name="host" value="blar" onChange="getElementById('testhost').innerHTML = this.value" /></td>
                            </tr>
                            <tr valign="middle">
                                <td align="right" width="1%">Port:</td>
                                <td><input type="text" name="host" value="1234" onChange="getElementById('testport').innerHTML = this.value" /></td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
			<input type="submit" name="submit" value="Save Options" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options,jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" class="jive-formButton">
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options,jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>perms" style="display: none;">
		<div>
        <form action="">
			<input type="radio" name="userreg" value="all" onClick="getElementById('userreg_specific').style.display = 'none'" checked> All users can register<br>
			<input type="radio" name="userreg" value="specific" onClick="getElementById('userreg_specific').style.display = 'block'"> These users and/or groups can register<br>
            <div id="userreg_specific" style="display: none; margin: 0; padding: 0; font-size: 80%">
                <table border="0" cellpadding="0" cellspacing="0" style="padding-left: 30.0px">
                    <tr valign="top">
                        <td align="left">
                            <span style="font-weight: bold">Users</span> <a href="">(Modify Users)</a><br />
                            (none selected)
                        </td>
                        <td align="left" style="padding-left: 30.0px">
                            <span style="font-weight: bold">Groups</span> <a href="">(Modify Groups)</a><br />
                            (none selected)
                        </td>
                    </tr>
                </table>
            </div>
            <input type="radio" name="userreg" value="manual" onClick="getElementById('userreg_specific').style.display = 'none'"> Manual registration only (see the Registrations section to manage)<br>
			<input type="submit" name="submit" value="Save Permissions" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms,jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" class="jive-formButton"> 
			<input type="reset" name="cancel" value="Cancel" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms,jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" class="jive-formButton">
		</form>
		</div>
	</div>
	<!-- END gateway - <%= this.gatewayType.toString().toUpperCase() %> -->

<%
            }
            catch (Exception e) {
                // hrm
            }
        }
    }

    GatewaySettings aimSettings = new GatewaySettings(out, plugin, TransportType.aim, LocaleUtils.getLocalizedString("gateway.aim.service", "gateway"));
    GatewaySettings icqSettings = new GatewaySettings(out, plugin, TransportType.icq, LocaleUtils.getLocalizedString("gateway.icq.service", "gateway"));
    GatewaySettings ircSettings = new GatewaySettings(out, plugin, TransportType.irc, LocaleUtils.getLocalizedString("gateway.irc.service", "gateway"));
    GatewaySettings msnSettings = new GatewaySettings(out, plugin, TransportType.msn, LocaleUtils.getLocalizedString("gateway.msn.service", "gateway"));
    GatewaySettings yahooSettings = new GatewaySettings(out, plugin, TransportType.yahoo, LocaleUtils.getLocalizedString("gateway.yahoo.service", "gateway"));
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


<p><fmt:message key="gateway.web.settings.instructions" /></p>


<form action="" name="gatewayForm">

<% aimSettings.printSettingsDialog(); %>
<% icqSettings.printSettingsDialog(); %>
<% ircSettings.printSettingsDialog(); %>    
<% msnSettings.printSettingsDialog(); %>
<% yahooSettings.printSettingsDialog(); %>

</form>


<br clear="all">


</body>
</html>
