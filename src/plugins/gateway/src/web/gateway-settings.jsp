<%@ page import="javax.servlet.jsp.JspWriter,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.gateway.GatewayPlugin,
                 org.jivesoftware.openfire.gateway.TransportType"
    errorPage="error.jsp"
%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.dom4j.Element" %>
<%@ page import="org.dom4j.Attribute" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.dom4j.Document" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.gateway.PermissionManager" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Iterator" %>

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
        String userPermText = "[none selected]";
        String groupPermText = "[none selected]";
        String userPermEntry = "";
        String groupPermEntry = "";
        Integer globalPermSetting = 1;

        GatewaySettings(JspWriter out, GatewayPlugin plugin, TransportType gatewayType,
                String desc) {
            this.description = desc;
            this.gatewayType = gatewayType;
            this.gwEnabled = plugin.serviceEnabled(gatewayType.toString());
            this.out = out;
            getConnectHostAndPort();
            getPermissionsList();
        }

        /**
         * Borrowed from http://www.bigbold.com/snippets/posts/show/91
         */
        public String join(Collection s, String delimiter) {
            StringBuffer buffer = new StringBuffer();
            Iterator iter = s.iterator();
            while (iter.hasNext()) {
                buffer.append(iter.next());
                if (iter.hasNext()) {
                    buffer.append(delimiter);
                }
            }
            return buffer.toString();
        }

        void getPermissionsList() {
            PermissionManager permissionManager = new PermissionManager(this.gatewayType);
            ArrayList<String> userList = permissionManager.getAllUsers();
            if (userList.size() > 0) {
                String joinedString = join(userList, " ");
                userPermText = joinedString;
                userPermEntry = joinedString;
            }
            ArrayList<String> groupList = permissionManager.getAllGroups();
            if (groupList.size() > 0) {
                String joinedString = join(groupList, " ");
                groupPermText = joinedString;
                groupPermEntry = joinedString;
            }
            globalPermSetting = JiveGlobals.getIntProperty("plugin.gateway."+this.gatewayType.toString()+".registration", 1);
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
                    Element node = (Element) nodeObj;
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
                    Element node = (Element) nodeObj;
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
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");
                    Attribute size = node.attribute("size");
                    Attribute maxlen = node.attribute("maxlength");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    String defStr = "";
                    if (def != null) {
                        defStr = def.getText();
                    }

                    String descStr = LocaleUtils.getLocalizedString(desckey.getText(), "gateway");
                    String setting = JiveGlobals.getProperty(sysprop.getText(), defStr);

                    String inputId = gatewayType.toString() + var.getText();
                    out.println("<tr valign='middle'>");
                    out.println("<td align='right' width='1%'><label for='" + inputId + "'>" +
                            descStr + "</label>:</td>");
                    out.print("<td><input type='text' id='" + inputId + "' name='" + inputId + "'" +
                            (size != null ? " size='" + size.getText() + "'" : "") +
                            (size != null ? " maxlength='" + maxlen.getText() + "'" : "") +
                            " value='" + setting + "'");
                    if (var.getText().equals("host")) {
                        out.print(" onChange='document.getElementById(\"" + gatewayType.toString() +
                                "testhost\").innerHTML = this.value'");
                    }
                    if (var.getText().equals("port")) {
                        out.print(" onChange='document.getElementById(\"" + gatewayType.toString() +
                                "testport\").innerHTML = this.value'");
                    }
                    out.println("/></td>");
                    out.println("</tr>");
                }
                else if (type.getText().equals("toggle")) {
                    // Required fields
                    Attribute desckey = node.attribute("desckey");
                    Attribute var = node.attribute("var");
                    Attribute sysprop = node.attribute("sysprop");

                    // Optional fields
                    Attribute def = node.attribute("default");

                    if (desckey == null || var == null || sysprop == null) {
                        Log.error("Missing variable from options config.");
                        return;
                    }

                    boolean defBool = false;
                    if (def != null && (def.getText().equals("1") || def.getText().equals("true") ||
                            def.getText().equals("enabled") || def.getText().equals("yes"))) {
                        defBool = true;
                    }

                    String descStr = LocaleUtils.getLocalizedString(desckey.getText(), "gateway");
                    boolean setting = JiveGlobals.getBooleanProperty(sysprop.getText(), defBool);

                    String jsStr = gatewayType.toString() + (++jsID);
                    String checkId = gatewayType.toString() + var.getText();
                    out.println("<tr valign='top'>");
                    out.println("<td align='right' width='1%'><input type='checkbox' id='" +
                            checkId + "' name='" + checkId + "' value='true' " +
                            (setting ? " checked='checked'" : "") +
                            " onClick='elem = document.getElementById(\"" + jsStr +
                            "\"); if (elem) { if (this.checked) { elem.style.display=\"table\"} else { elem.style.display=\"none\"} }'/></td>");
                    out.print("<td><label for='" + checkId + "'>" + descStr + "</label>");
                    for (Object itemObj : node.elements("item")) {
                        Element item = (Element) itemObj;
                        out.println("<table id='" + jsStr + "' width='100%' style='display: " +
                                (defBool ? "table" : "none") + "'>");
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
			<input type="checkbox" name="gateway" value="<%= this.gatewayType.toString().toLowerCase() %>" id="jive<%= this.gatewayType.toString().toUpperCase() %>checkbox" <%= ((this.gwEnabled) ? "checked" : "") %> onClick="ConfigManager.toggleTransport('<%= this.gatewayType.toString().toLowerCase() %>'); checkToggle(jive<%= this.gatewayType.toString().toUpperCase() %>); return true">
			<img src="images/<%= this.gatewayType.toString().toLowerCase() %>.gif" alt="" border="0">
			<strong><%= this.description %></strong>
		</label>
		<div class="jive-gatewayButtons">
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>tests); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>testsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>><%= LocaleUtils.getLocalizedString("gateway.web.settings.tests", "gateway") %></a>
            <a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>options); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>><%= LocaleUtils.getLocalizedString("gateway.web.settings.options", "gateway") %></a>
			<a href="#" onclick="togglePanel(jive<%= this.gatewayType.toString().toUpperCase() %>perms); return false" id="jive<%= this.gatewayType.toString().toUpperCase() %>permsLink" <%= ((!this.gwEnabled) ? "style='display:none'" : "") %>><%= LocaleUtils.getLocalizedString("gateway.web.settings.permissions", "gateway") %></a>
		</div>
	</div>
    <!-- Tests Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>tests" style="display: none;">
        <div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>testsform" action="" onSubmit="return false">
                <span style="font-weight: bold"><%= LocaleUtils.getLocalizedString("gateway.web.settings.connecttohost", "gateway") %>:</span> <span id="<%= this.gatewayType.toString() %>testhost"><%= connectHost %></span><br />
                <span style="font-weight: bold"><%= LocaleUtils.getLocalizedString("gateway.web.settings.connecttoport", "gateway") %>:</span> <span id="<%= this.gatewayType.toString() %>testport"><%= connectPort %></span><br />

                <span id="<%= this.gatewayType.toString() %>testsresults" class="saveResultsMsg"></span>
                <input type="submit" name="submit" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.testconnection", "gateway") %>" onclick="testConnect('<%= this.gatewayType.toString() %>'); return false" class="jive-formButton">
            </form>
        </div>
    </div>
    <!-- Options Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>options" style="display: none;">
		<div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>optionsform" action="" onSubmit="return false">
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

                <span id="<%= this.gatewayType.toString() %>optionsresults" class="saveResultsMsg"></span>
                <input type="submit" name="submit" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.saveoptions", "gateway") %>" onclick="saveOptions('<%= this.gatewayType.toString() %>'); return false" class="jive-formButton">
                <input type="reset" name="cancel" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.cancelchanges", "gateway") %>" onclick="cancelOptions('<%= this.gatewayType.toString() %>'); return true" class="jive-formButton">
            </form>
		</div>
	</div>
    <!-- Permissions Window -->
    <div class="jive-gatewayPanel" id="jive<%= this.gatewayType.toString().toUpperCase() %>perms" style="display: none;">
		<div>
            <form id="jive<%= this.gatewayType.toString().toUpperCase() %>permsform" action=""  onSubmit="return false">
                <input type="radio" name="<%= this.gatewayType.toString() %>userreg" value="all" onClick="hideSpecificChoices('<%= this.gatewayType.toString() %>')" <%= (this.globalPermSetting == 1 ? "checked='checked'" : "") %> /> <%= LocaleUtils.getLocalizedString("gateway.web.settings.registerall", "gateway") %><br>
                <input type="radio" name="<%= this.gatewayType.toString() %>userreg" value="specific" onClick="showSpecificChoices('<%= this.gatewayType.toString() %>')"  <%= (this.globalPermSetting == 2 ? "checked='checked'" : "") %> /> <%= LocaleUtils.getLocalizedString("gateway.web.settings.registersome", "gateway") %><br>
                <div id="<%= this.gatewayType.toString() %>userreg_specific" style="<%= (this.globalPermSetting == 2 ? "" : "display: none; ") %>margin: 0; padding: 0; font-size: 80%">
                    <table border="0" cellpadding="0" cellspacing="0" style="margin-left: 30.0px" width='100%'>
                        <tr valign="top">
                            <td align="left" style="padding-right: 15.0px" width='50%'>
                                <span style="font-weight: bold"><%= LocaleUtils.getLocalizedString("gateway.web.settings.users", "gateway") %></span> <a href="javascript:noop()" onClick="activateModifyUsers('<%= this.gatewayType.toString() %>'); return false">(Modify Users)</a><br />
                                <div id="<%= this.gatewayType.toString() %>userpermtextdiv" style="margin: 0px; padding: 0px" class='permissionListDiv'><span id="<%= this.gatewayType.toString() %>userpermtext"><%= this.userPermText %></span></div>
                                <div id="<%= this.gatewayType.toString() %>userpermentrydiv" style="margin: 0px; padding: 0px; display:none" class='permissionListDiv'><textarea style="margin: 0px" class='permissionListTextArea' rows="5" cols="20" id="<%= this.gatewayType.toString() %>userpermentry" name="<%= this.gatewayType.toString() %>userpermentry"><%= this.userPermEntry %></textarea></div>
                            </td>
                            <td align="left" style="margin-left: 15.0px" width='50%'>
                                <span style="font-weight: bold"><%= LocaleUtils.getLocalizedString("gateway.web.settings.groups", "gateway") %></span> <a href="javascript:noop()" onClick="activateModifyGroups('<%= this.gatewayType.toString() %>'); return false">(Modify Groups)</a><br />
                                <div id="<%= this.gatewayType.toString() %>grouppermtextdiv" style="margin: 0px; padding: 0px" class='permissionListDiv'><span id="<%= this.gatewayType.toString() %>grouppermtext"><%= this.groupPermText %></span></div>
                                <div id="<%= this.gatewayType.toString() %>grouppermentrydiv" style="margin: 0px; padding: 0px; display:none" class='permissionListDiv'><textarea style="margin: 0px" class='permissionListTextArea' rows="5" cols="20" id="<%= this.gatewayType.toString() %>grouppermentry" name="<%= this.gatewayType.toString() %>grouppermentry"><%= this.groupPermEntry %></textarea></div>
                            </td>
                        </tr>
                    </table>
                </div>
                <input type="radio" name="<%= this.gatewayType.toString() %>userreg" value="manual" onClick="hideSpecificChoices('<%= this.gatewayType.toString() %>')" <%= (this.globalPermSetting == 3 ? "checked='checked'" : "") %> /> <%= LocaleUtils.getLocalizedString("gateway.web.settings.registernone", "gateway") %><br>

                <span id="<%= this.gatewayType.toString() %>permsresults" class="saveResultsMsg"></span>
                <input type="submit" name="submit" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.savepermissions", "gateway") %>" onclick="savePermissions('<%= this.gatewayType.toString() %>'); return false" class="jive-formButton">
                <input type="reset" name="cancel" value="<%= LocaleUtils.getLocalizedString("gateway.web.settings.cancelchanges", "gateway") %>" onclick="cancelPermissions('<%= this.gatewayType.toString() %>'); return true" class="jive-formButton">
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
    GatewaySettings gtalkSettings = new GatewaySettings(out, plugin, TransportType.gtalk, LocaleUtils.getLocalizedString("gateway.gtalk.service", "gateway"));
    GatewaySettings icqSettings = new GatewaySettings(out, plugin, TransportType.icq, LocaleUtils.getLocalizedString("gateway.icq.service", "gateway"));
    GatewaySettings ircSettings = new GatewaySettings(out, plugin, TransportType.irc, LocaleUtils.getLocalizedString("gateway.irc.service", "gateway"));
    GatewaySettings msnSettings = new GatewaySettings(out, plugin, TransportType.msn, LocaleUtils.getLocalizedString("gateway.msn.service", "gateway"));
    GatewaySettings xmppSettings = new GatewaySettings(out, plugin, TransportType.xmpp, LocaleUtils.getLocalizedString("gateway.xmpp.service", "gateway"));
    GatewaySettings yahooSettings = new GatewaySettings(out, plugin, TransportType.yahoo, LocaleUtils.getLocalizedString("gateway.yahoo.service", "gateway"));
%>




<html>

<head>
<title><fmt:message key="gateway.web.settings.title" /></title>
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
        document.getElementById(transportID+"optionsresults").style.display = "";
        document.getElementById(transportID+"optionsresults").innerHTML = "<span class='successresults'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.settingssaved' /></span>";
        setTimeout("to_saveOptions('"+transportID+"')", 5000);
    }

    function cancelOptions(transportID) {
        document.getElementById(transportID+"optionsresults").style.display = "";
        document.getElementById(transportID+"optionsresults").innerHTML = "<span class='warningresults'><img src='images/warning-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.cancelledchanges' /></span>";
        setTimeout("to_saveOptions('"+transportID+"')", 5000);
    }

    function to_saveOptions(transportID) {
        Effect.Fade(transportID+"optionsresults");
    }

    function testConnect(transportID) {
        testTransportID = transportID;
        ConnectionTester.testConnection(DWRUtil.getValue(transportID+"testhost"),
                DWRUtil.getValue(transportID+"testport"), cb_testConnect);
    }

    function cb_testConnect(result) {
        document.getElementById(testTransportID+"testsresults").style.display = "";
        if (result) {
            document.getElementById(testTransportID+"testsresults").innerHTML = "<span class='successresults'><img src='images/success-16x16.gif' alt='' align='absmiddle' /><fmt:message key='gateway.web.settings.success' /></span>";
        }
        else {
            document.getElementById(testTransportID+"testsresults").innerHTML = "<span class='failureresults'><img src='images/failure-16x16.gif' alt='' align='absmiddle' /><fmt:message key='gateway.web.settings.failed' /></span>";
        }
        setTimeout("to_testConnect('"+testTransportID+"')", 5000);
        testTransportID = null;
    }

    function to_testConnect(transportID) {
        Effect.Fade(transportID+"testsresults");
    }

    var lastUserList;
    var lastGroupList;
    var lastTransportID;

    function savePermissions(transportID) {
        var userEntry = DWRUtil.getValue(transportID+"userpermentry");
        var groupEntry = DWRUtil.getValue(transportID+"grouppermentry");
        var globalSettingStr = DWRUtil.getValue(transportID+"userreg");
        var globalSetting = 1; // Allow all as default
        if (globalSettingStr == "all") {
            globalSetting = 1;
        }
        else if (globalSettingStr == "specific") {
            globalSetting = 2;
        }
        else if (globalSettingStr == "manual") {
            globalSetting = 3;
        }
        var userList = userEntry.split(/\s+/);
        var groupList = groupEntry.split(/\s+/);
        lastUserList = userList;
        lastGroupList = groupList;
        lastTransportID = transportID;
        ConfigManager.savePermissions(transportID, globalSetting, userList, groupList, cb_savePermissions);
    }

    function cb_savePermissions(errorList) {
        var userList = lastUserList;
        var groupList = lastGroupList;
        var transportID = lastTransportID;
        if (errorList != null && errorList.length > 0) {
            var errUsers = new Array();
            var errGroups = new Array();
            for (i = 0; i < errorList.length; i++) {
                if (errorList[i].charAt(0) == "@") {
                    var grpName = errorList[i].substr(1, (errorList[i].length-1));
                    errGroups.push(grpName);
                    for (j = 0; j < groupList.length; j++) {
                        if (groupList[j] == grpName) {
                            groupList.splice(j, 1);
                            break;
                        }
                    }
                }
                else {
                    var userName = errorList[i];
                    errUsers.push(userName);
                    for (j = 0; j < userList.length; j++) {
                        if (userList[j] == userName) {
                            userList.splice(j, 1);
                            break;
                        }
                    }
                }
            }
            var errMsg = "";
            if (errUsers.length > 0) {
                errMsg = errMsg + "\nThe following users were not valid and were ignored:\n" + errUsers.join("\n") + "\n";
            }
            if (errGroups.length > 0) {
                errMsg = errMsg + "\nThe following groups were not valid and were ignored:\n" + errGroups.join("\n") + "\n";
            }
            alert(errMsg);
        }
        for (i = 0; i < userList.length; i++) {
            var charPos = userList[i].indexOf("@");
            if (charPos >= 0) {
                userList[i] = userList[i].substr(0, charPos);
            }
        }
        document.getElementById(transportID+"userpermtext").innerHTML = (userList.length > 0 ? userList.join(" ") : "[none selected]");
        document.getElementById(transportID+"userpermentry").value = userList.join(" ");
        document.getElementById(transportID+"grouppermtext").innerHTML = (groupList.length > 0 ? groupList.join(" ") : "[none selected]");
        document.getElementById(transportID+"grouppermentry").value = groupList.join(" ");
        deactivateModifyUsers(transportID);
        deactivateModifyGroups(transportID);
        document.getElementById(transportID+"permsresults").style.display = "";
        document.getElementById(transportID+"permsresults").innerHTML = "<span class='successresults'><img src='images/success-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.permissionssaved' /></span>";
        setTimeout("to_savePermissions('"+transportID+"')", 5000);
    }

    function cancelPermissions(transportID) {
        deactivateModifyUsers(transportID);
        deactivateModifyGroups(transportID);
        document.getElementById(transportID+"permsresults").style.display = "";
        document.getElementById(transportID+"permsresults").innerHTML = "<span class='warningresults'><img src='images/warning-16x16.gif' align='absmiddle' /><fmt:message key='gateway.web.settings.cancelledchanges' /></span>";
        setTimeout("to_savePermissions('"+transportID+"')", 5000);
    }

    function to_savePermissions(transportID) {
        Effect.Fade(transportID+"permsresults");
    }

    function activateModifyUsers(transportID) {
        var textElem = document.getElementById(transportID+"userpermtextdiv");
        var entryElem = document.getElementById(transportID+"userpermentrydiv");
        if (textElem.style.display != "none") {
            Effect.SlideUp(textElem,{duration: .4});
        }
        if (entryElem.style.display == "none") {
            Effect.SlideDown(entryElem, {duration: .4, delay: .5});
        }
    }

    function deactivateModifyUsers(transportID) {
        var textElem = document.getElementById(transportID+"userpermtextdiv");
        var entryElem = document.getElementById(transportID+"userpermentrydiv");
        if (entryElem.style.display != "none") {
            Effect.SlideUp(entryElem,{duration: .4});
        }
        if (textElem.style.display == "none") {
            Effect.SlideDown(textElem, {duration: .4, delay: .5});
        }
    }

    function activateModifyGroups(transportID) {
        var textElem = document.getElementById(transportID+"grouppermtextdiv");
        var entryElem = document.getElementById(transportID+"grouppermentrydiv");
        if (textElem.style.display != "none") {
            Effect.SlideUp(textElem,{duration: .4});
        }
        if (entryElem.style.display == "none") {
            Effect.SlideDown(entryElem, {duration: .4, delay: .5});
        }
    }

    function deactivateModifyGroups(transportID) {
        var textElem = document.getElementById(transportID+"grouppermtextdiv");
        var entryElem = document.getElementById(transportID+"grouppermentrydiv");
        if (entryElem.style.display != "none") {
            Effect.SlideUp(entryElem,{duration: .4});
        }
        if (textElem.style.display == "none") {
            Effect.SlideDown(textElem, {duration: .4, delay: .5});
        }
    }

    function hideSpecificChoices(transportID) {
        var targElement = document.getElementById(transportID+"userreg_specific");
        if (targElement.style.display != "none") {
            Effect.toggle(targElement,'slide', {duration: .4});
        }
    }

    function showSpecificChoices(transportID) {
        var targElement = document.getElementById(transportID+"userreg_specific");
        if (targElement.style.display == "none") {
            Effect.toggle(targElement,'slide', {duration: .4});
        }
    }

    function pingSession() {
        ConnectionTester.pingSession();
        setTimeout("pingSession()", 60000); // Every minute
    }

    setTimeout("pingSession()", 60000); // One minute after first load
</script>
</head>

<body>
<p><fmt:message key="gateway.web.settings.instructions" /></p>

<form action="" name="gatewayForm">

<% aimSettings.printSettingsDialog(); %>
<% ircSettings.printSettingsDialog(); %>
<% msnSettings.printSettingsDialog(); %>

<br><br>

<div id="jive-title"><fmt:message key="gateway.web.settings.unstable.title" /></div>

<p><fmt:message key="gateway.web.settings.unstable.notice" /></p>

<% gtalkSettings.printSettingsDialog(); %>
<% icqSettings.printSettingsDialog(); %>
<% xmppSettings.printSettingsDialog(); %>
<% yahooSettings.printSettingsDialog(); %>

</form>
<br clear="all">
</body>

</html>
