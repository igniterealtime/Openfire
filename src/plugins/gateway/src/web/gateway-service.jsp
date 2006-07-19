<%@ page import="java.util.*,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    boolean aimEnabled = ParamUtils.getBooleanParameter(request, "aimEnabled");
    boolean icqEnabled = ParamUtils.getBooleanParameter(request, "icqEnabled");
    boolean yahooEnabled = ParamUtils.getBooleanParameter(request, "yahooEnabled");
    boolean msnEnabled = ParamUtils.getBooleanParameter(request, "msnEnabled");
    String serverName = XMPPServer.getInstance().getServerInfo().getName();

    GatewayPlugin plugin = (GatewayPlugin)XMPPServer.getInstance().getPluginManager().getPlugin("gateway");

    // Handle a save
    if (save) {
        //plugin.setPresencePublic(presencePublic);
        if (aimEnabled) {
            plugin.enableService("aim");
        }
        else {
            plugin.disableService("aim");
        }
        if (icqEnabled) {
            plugin.enableService("icq");
        }
        else {
            plugin.disableService("icq");
        }
        if (yahooEnabled) {
            plugin.enableService("yahoo");
        }
        else {
            plugin.disableService("yahoo");
        }
        if (msnEnabled) {
            plugin.enableService("msn");
        }
        else {
            plugin.disableService("msn");
        }
        response.sendRedirect("gateway-service.jsp?success=true");
        return;
    }

    aimEnabled = plugin.serviceEnabled("aim");
    icqEnabled = plugin.serviceEnabled("icq");
    yahooEnabled = plugin.serviceEnabled("yahoo");
    msnEnabled = plugin.serviceEnabled("msn");
%>

<html>
    <head>
        <title>IM Gateway Settings</title>
        <meta name="pageID" content="gateway-service"/>
    </head>
    <body>

<p>
Use the form below to enable or disable any of the available gateways.  By
default, all of the gateways are turned off.  Gateways will answer as the
JID provided in each corresponding section.
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Gateway services successfully updated.
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>

<form action="gateway-service.jsp?save" method="post">

<fieldset>
    <legend>AIM Gateway</legend>
    <div>
    <p>
    This gateway provides a mechanism for users to access the AIM network.
    Users will be able to register with the JID specified below, specifying
    their AIM screen name and password.<br />
    <br />
    JID: aim.<%= serverName %>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="aimEnabled" value="true" id="rb01"
             <%= ((aimEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b> - AIM gateway is available.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="aimEnabled" value="false" id="rb02"
             <%= ((!aimEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Disabled</b> - AIM gateway is not available.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>ICQ Gateway</legend>
    <div>
    <p>
    This gateway provides a mechanism for users to access the ICQ network.
    Users will be able to register with the JID specified below, specifying
    their ICQ UIN (user identification number) and password.<br />
    <br />
    JID: icq.<%= serverName %>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="icqEnabled" value="true" id="rb03"
             <%= ((icqEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b> - ICQ gateway is available.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="icqEnabled" value="false" id="rb04"
             <%= ((!icqEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Disabled</b> - ICQ gateway is not available.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Yahoo Gateway</legend>
    <div>
    <p>
    This gateway provides a mechanism for users to access the YIM network.
    Users will be able to register with the JID specified below, specifying
    their YIM username and password.<br />
    <br />
    JID: yahoo.<%= serverName %>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="yahooEnabled" value="true" id="rb03"
             <%= ((yahooEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b> - Yahoo gateway is available.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="yahooEnabled" value="false" id="rb04"
             <%= ((!yahooEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Disabled</b> - Yahoo gateway is not available.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>MSN Gateway</legend>
    <div>
    <p>
    This gateway provides a mechanism for users to access the MSN network.
    Users will be able to register with the JID specified below, specifying
    their MSN username and password.<br />
    <br />
    JID: msn.<%= serverName %>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="msnEnabled" value="true" id="rb03"
             <%= ((msnEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b> - MSN gateway is available.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="msnEnabled" value="false" id="rb04"
             <%= ((!msnEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Disabled</b> - MSN gateway is not available.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Settings">
</form>

</body>
</html>
