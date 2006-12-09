<%@ page import="javax.servlet.jsp.JspWriter,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.gateway.GatewayPlugin,
                 org.jivesoftware.wildfire.gateway.TransportType"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.dom4j.Element" %>
<%@ page import="org.dom4j.Attribute" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.dom4j.Document" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    final GatewayPlugin plugin =
            (GatewayPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("gateway");

    class GatewaySettings {

        String description = null;
        TransportType gatewayType = null;
        boolean gwEnabled = false;
        JspWriter out = null;
        Integer jsID = 0; // Javascript incrementable id
        String connectHost = null;
        String connectPort = null;

        GatewaySettings(JspWriter out, GatewayPlugin plugin, TransportType gatewayType, String desc) {
            this.description = desc;
            this.gatewayType = gatewayType;
            this.gwEnabled = plugin.serviceEnabled(gatewayType.toString());
            this.out = out;
            getConnectHostAndPort();
        }

        void getConnectHostAndPort() {
            // This assumes that you've chosen to keep the connect host and port in a standard
            // location ... as a "root level" option in the left or right panel.
            Document optConfig = plugin.getOptionsConfig(gatewayType);
            if (optConfig == null) {
                Log.debug("No options config present for transport.");
                return;
            }
            Element leftPanel = optConfig.getRootElement().element("leftpanel");
            Element rightPanel = optConfig.getRootElement().element("rightpanel");
            if (leftPanel != null && leftPanel.nodeCount() > 0) {
                for (Object nodeObj : leftPanel.elements("item")) {
                    Element node = (Element)nodeObj;
                    Attribute type = node.attribute("type");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");
                    if (type == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        continue;
                    }
                    Attribute def = node.attribute("default");
                    String defStr = "";
                    if (def != null) {
                        defStr = def.getText();
                    }
                    if (type.getText().equals("text") && var.getText().equals("host")) {
                        this.connectHost = JiveGlobals.getProperty(sysprop.getText(), defStr);
                    }
                    if (type.getText().equals("text") && var.getText().equals("port")) {
                        this.connectPort = JiveGlobals.getProperty(sysprop.getText(), defStr);
                    }
                }
            }
            if (rightPanel != null && rightPanel.nodeCount() > 0) {
                for (Object nodeObj : rightPanel.elements("item")) {
                    Element node = (Element)nodeObj;
                    Attribute type = node.attribute("type");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");
                    if (type == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        continue;
                    }
                    Attribute def = node.attribute("default");
                    String defStr = "";
                    if (def != null) {
                        defStr = def.getText();
                    }
                    if (type.getText().equals("text") && var.getText().equals("host")) {
                        this.connectHost = JiveGlobals.getProperty(sysprop.getText(), defStr);
                    }
                    if (type.getText().equals("text") && var.getText().equals("port")) {
                        this.connectPort = JiveGlobals.getProperty(sysprop.getText(), defStr);
                    }
                }
            }
        }

        void printConfigNode(Element node) {
            try {
                Attribute type = node.attribute("type");
                if (type.getText().equals("text")) {
                    // Required fields
                    Attribute desc = node.attribute("desc");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");
                    Attribute size = node.attribute("size");
                    Attribute maxlen = node.attribute("maxlength");

                    if (desc == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    String defStr = "";
                    if (def != null) {
                        defStr = def.getText();
                    }

                    String setting = JiveGlobals.getProperty(sysprop.getText(), defStr);

                    String inputId = gatewayType.toString()+var.getText();
                    out.println("<tr valign='middle'>");
                    out.println("<td align='right' width='1%'><label for='" + inputId + "'>" + desc.getText() + "</label>:</td>");
                    out.print("<td><input type='text' id='" + inputId + "' name='" + inputId + "'"+(size != null ? " size='"+size.getText()+"'" : "")+(size != null ? " maxlength='"+maxlen.getText()+"'" : "")+" value='"+setting+"'");
                    if (var.getText().equals("host")) {
                        out.print(" onChange='document.getElementById(\""+gatewayType.toString()+"testhost\").innerHTML = this.value'");
                    }
                    if (var.getText().equals("port")) {
                        out.print(" onChange='document.getElementById(\""+gatewayType.toString()+"testport\").innerHTML = this.value'");
                    }
                    out.println("/></td>");
                    out.println("</tr>");
                }
                else if (type.getText().equals("toggle")) {
                    // Required fields
                    Attribute desc = node.attribute("desc");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");

                    if (desc == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    boolean defBool = false;
                    if (def != null && (def.getText().equals("1") || def.getText().equals("true") || def.getText().equals("enabled") || def.getText().equals("yes"))) {
                        defBool = true;
                    }

                    boolean setting = JiveGlobals.getBooleanProperty(sysprop.getText(), defBool);

                    String jsStr = gatewayType.toString()+(++jsID);
                    String checkId = gatewayType.toString()+var.getText();
                    out.println("<tr valign='top'>");
                    out.println("<td align='right' width='1%'><input type='checkbox' id='" + checkId +"' name='" + checkId + "' value='true' "+(setting ? " checked='checked'" : "")+" onClick='elem = document.getElementById(\""+jsStr+"\"); if (elem) { if (this.checked) { elem.style.display=\"table\"} else { elem.style.display=\"none\"} }'/></td>");
                    out.print("<td><label for='" + checkId + "'>" + desc.getText() + "</label>");
                    for (Object itemObj : node.elements("item")) {
                        Element item = (Element)itemObj;
                        out.println("<table id='"+jsStr+"' width='100%' style='display: "+(defBool ? "table" : "none")+"'>");
                        printConfigNode(item);
                        out.println("</table>");
                    }
                    out.println("</td>");
                    out.println("</tr>");
                }
            }
            catch (Exception e) {
                // Uhm, yeah, that sucks.
                Log.error("Error printing config node:", e);
            }
        }

        void printSettingsDialog() {
            try {
                Document optConfig = plugin.getOptionsConfig(gatewayType);
                Element leftPanel = optConfig.getRootElement().element("leftpanel");
                Element rightPanel = optConfig.getRootElement().element("rightpanel");
%>

	<!-- BEGIN gateway - <%= this.gatewayType.toString().toUpperCase() %> -->
    <div <%= ((!this.gwEnabled) ? " class='jive-gateway jive-gatewayDisabled'" : "class='jive-gateway'") %> id="jive<%= this.gatewayType.toString().toUpperCase() %>">
		<label for="jive<%= this.gatewayType.toString().toUpperCase() %>checkbox">
			<input type="checkbox" name="gateway" value="<%= this.gatewayType.toString().toLowerCase() %>" id="jive<%= this.gatewayType.toString().toUpperCase() %>checkbox" <%= ((this.gwEnabled) ? "checked" : "") %> onClick="ConfigManager.toggleTransport('<%= this.gatewayType.toString().toLowerCase() %>'); checkToggle('jive<%= this.gatewayType.toString().toUpperCase() %>'); return true">
			<img src="images/<%= this.gatewayType.toString().toLowerCase() %>.gif" alt="" border="0">
			<strong><%= this.description %></strong>
		</label>
		<div class="jive-gatewayButtons">
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>tests); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>testsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Tests</a>
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Options</a>
			<a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>permsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>>Permissions</a>
		</div>
	</div>
    <!-- Tests Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>tests" style="display: none;">
        <div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>testsform" action="">
                <span style="font-weight: bold">Connect to host:</span> <span id="<%= this.gatewayType.toString() %>testhost"><%= connectHost %></span><br />
                <span style="font-weight: bold">Connect to port:</span> <span id="<%= this.gatewayType.toString() %>testport"><%= connectPort %></span><br />

                <input type="submit" name="submit" value="Test Connection" onclick="testConnect('<%= this.gatewayType.toString() %>'); return false" class="jive-formButton">
                <span id="<%= this.gatewayType.toString() %>testsresults" class="saveResultsMsg"></span>
            </form>
        </div>
    </div>
    <!-- Options Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>options" style="display: none;">
		<div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsform" action="">
                <table border="0" cellpadding="0" cellspacing="0">
                    <tr valign="top">
                        <td align="left" width="50%">
<%
                if (leftPanel != null && leftPanel.nodeCount() > 0) {
                    out.println("<table border='0' cellpadding='1' cellspacing='2'>");
                    for (Object nodeObj : leftPanel.elements("item")) {
                        Element node = (Element)nodeObj;
                        printConfigNode(node);
                    }
                    out.println("</table");
                }
                else {
                    out.println("&nbsp;");
                }
%>
                        </td>
                        <td align="left" width="50%">
<%
                if (rightPanel != null && rightPanel.nodeCount() > 0) {
                    out.println("<table border='0' cellpadding='1' cellspacing='2'>");
                    for (Object nodeObj : rightPanel.elements("item")) {
                        Element node = (Element)nodeObj;
                        printConfigNode(node);
                    }
                    out.println("</table>");
                }
                else {
                    out.println("&nbsp;");
                }
%>
                        </td>
                    </tr>
                </table>
                <input type="submit" name="submit" value="Save Options" onclick="saveOptions('<%= this.gatewayType.toString() %>'); return false" class="jive-formButton">
                <input type="reset" name="cancel" value="Cancel Changes" class="jive-formButton">
                <span id="<%= this.gatewayType.toString() %>optionsresults" class="saveResultsMsg"></span>
            </form>
		</div>
	</div>
    <!-- Permissions Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>perms" style="display: none;">
		<div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>permsform" action="">
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

                <input type="submit" name="submit" value="Save Permissions" onclick="return false" class="jive-formButton">
                <input type="reset" name="cancel" value="Cancel Changes" class="jive-formButton">
                <span id="<%= this.gatewayType.toString() %>permsresults" class="saveResultsMsg"></span>
            </form>
		</div>
	</div>
	<!-- END gateway - <%= this.gatewayType.toString().toUpperCase() %> -->

<%
            }
            catch (Exception e) {
                // Uhm, yeah, that sucks.
                Log.error("Error printing settings section:", e);                
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
<!-- @import url("style/gateways.css"); -->
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

    var settings;
    // If you add new option types, you to the transport option configs, you will also need
    // to add the option 'var' ids here.
    var optionTypes = new Array(
        "host",
        "port",
        "encoding"
    );
    var testTransportID = null;

    function saveOptions(transportID) {
        var transportSettings = new Object();
        for (var x in optionTypes) {
            var optType = optionTypes[x];
            var optionId = transportID+optType;
            var testoption = document.getElementById(optionId);
            if (testoption != null) {
                transportSettings[optType] = DWRUtil.getValue(optionId);
            }
        }
        ConfigManager.saveSettings(transportID, transportSettings);
        document.getElementById(transportID+"optionsresults").innerHTML = "Settings Saved!";
        setTimeout("to_saveOptions('"+transportID+"')", 5000);
    }

    function to_saveOptions(transportID) {
        document.getElementById(transportID+"optionsresults").innerHTML = "";
    }

    function testConnect(transportID) {
        testTransportID = transportID;
        ConnectionTester.testConnection(DWRUtil.getValue(transportID+"testhost"),
                DWRUtil.getValue(transportID+"testport"), cb_testConnect);
    }

    function cb_testConnect(result) {
        if (result) {
            document.getElementById(testTransportID+"testsresults").innerHTML = "Success!";
        }
        else {
            document.getElementById(testTransportID+"testsresults").innerHTML = "<span style='color: #ff0000'>Failed.</span>";
        }
        setTimeout("to_testConnect('"+testTransportID+"')", 5000);
        testTransportID = null;
    }

    function to_testConnect(transportID) {
        document.getElementById(transportID+"testsresults").innerHTML = "";
    }
</script>
</head>

<body>
<p><fmt:message key="gateway.web.settings.instructions" />
<b>Note:</b> Please be aware that Tests, Options, and Permissions are not yet functional.  They are only present for demonstration.</p>

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
