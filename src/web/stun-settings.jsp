<%--
  - Copyright (C) 2007 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.stun.STUNService" %>
<%@ page import="org.jivesoftware.openfire.stun.StunServerAddress" %>
<%@ page import="java.net.InetAddress" %>
<%@ page import="java.util.List" %>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<script type="text/javascript">

    function checkAndSubmit() {

        var ip1 = document.settings.primaryAddress.value;
        var ip2 = document.settings.secondaryAddress.value;
        var port1 = document.settings.primaryPort.value;
        var port2 = document.settings.secondaryPort.value;

        var msg = "";

        if (ip1 == ip2) {
            msg += "<fmt:message key="stun.settings.alert.notvalidip" />";
        }
        if (port1 == port2) {
            if (msg != "") msg += "\n";
            msg += "<fmt:message key="stun.settings.alert.notvalidip" />";
        }

        if (msg == "") {
            document.settings.save.value = "Change";
            document.settings.submit();
        }
        else alert(msg);

    }

</script>

<%

    STUNService stunService = XMPPServer.getInstance().getSTUNService();

    boolean save = request.getParameter("save") != null;
    boolean add = request.getParameter("add") != null;
    int remove = ParamUtils.getIntParameter(request, "remove", -1);
    boolean success = false;
    boolean enabled = true;
    boolean localEnabled = false;

    String primaryAddress;
    String secondaryAddress;
    int primaryPort = 3478;
    int secondaryPort = 3479;

    if (save) {
        primaryPort = ParamUtils.getIntParameter(request, "primaryPort", primaryPort);
        JiveGlobals.setProperty("stun.port.primary", String.valueOf(primaryPort));

        secondaryPort = ParamUtils.getIntParameter(request, "secondaryPort", secondaryPort);
        JiveGlobals.setProperty("stun.port.secondary", String.valueOf(secondaryPort));

        primaryAddress = ParamUtils.getParameter(request, "primaryAddress", true);
        JiveGlobals.setProperty("stun.address.primary", primaryAddress);

        secondaryAddress = ParamUtils.getParameter(request, "secondaryAddress", true);
        JiveGlobals.setProperty("stun.address.secondary", secondaryAddress);

        enabled = JiveGlobals.getBooleanProperty("stun.enabled", enabled);

        localEnabled = ParamUtils.getBooleanParameter(request, "localEnabled", localEnabled);
        JiveGlobals.setProperty("stun.local.enabled", String.valueOf(localEnabled));

        stunService.stop();
        stunService.initialize(XMPPServer.getInstance());
        if (!enabled) localEnabled = false;
        stunService.setEnabled(enabled, localEnabled);

        success = stunService.isEnabled() == enabled && stunService.isLocalEnabled() == localEnabled;

    } else if (remove > -1) {
        stunService.removeExternalServer(remove);
        success = true;
    } else if (add) {

        String server = ParamUtils.getParameter(request, "externalServer", true);
        String port = ParamUtils.getParameter(request, "externalPort", true);

        if (server != null && port != null)
            if (!server.equals("") && !port.equals("")) {
                if (server.indexOf(';') == -1 && server.indexOf(',') == -1 && server.indexOf('@') == -1) {
                    if (port.indexOf(';') == -1 && port.indexOf(',') == -1 && port.indexOf('@') == -1) {
                        stunService.addExternalServer(server, port);
                        success = true;
                    }
                }
            }
    }

%>
<html>
<head>
    <title>
        <fmt:message key="stun.settings.title"/>
    </title>
    <meta name="pageID" content="stun-settings"/>
</head>
<body>

<p>
    <fmt:message key="stun.settings.desc"/>
</p>

<% if (success) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16"
                                           border="0" alt="Success"></td>
                <td class="jive-icon-label">
                    <fmt:message key="stun.settings.success"/>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } else if (save) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16"
                                           border="0" alt=""></td>
                <td class="jive-icon-label">
                    <fmt:message key="stun.settings.error"/>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } else if (add) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16"
                                           border="0" alt=""></td>
                <td class="jive-icon-label">
                    <fmt:message key="stun.external.error"/>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>

<% } %>


<form action="" method="post" name="settings">
    <div class="jive-contentBoxHeader">
        <fmt:message key="stun.settings.title"/>
    </div>
    <div class="jive-contentBox">

        <table cellpadding="3" cellspacing="5" border="0">
            <tbody>
                <tr>
                    <td align="left" colspan="2">
                        <fmt:message key="stun.settings.localenabled"/>
                        :&nbsp<input type="checkbox"
                                     name="localEnabled"
                    <%=stunService.isLocalEnabled()?"checked":""%>
                                     align="left">
                    </td>
                </tr>
                <tr>
                    <td align="left">
                        <fmt:message key="stun.settings.primaryaddress"/>
                        :
                    </td>
                    <td>
                        <select size="1" name="primaryAddress">
                            <option value="CHOOSE">-- Select Address --</option>
                            <%


                                List<InetAddress> addresses = stunService.getAddresses();
                                for (InetAddress iaddress : addresses) {
                                    String hostAddress = iaddress.getHostAddress();
                                    boolean isPrimaryAddress = hostAddress.equals(stunService.getPrimaryAddress());


                            %>
                            <option value="<%= hostAddress %>" <% if (isPrimaryAddress) { %>
                                    selected <% } %> ><%= hostAddress %>
                            </option>
                            <% } %>
                    </td>
                </tr>
                <tr>
                    <td align="left">
                        <fmt:message key="stun.settings.secondaryaddress"/>
                        :
                    </td>
                    <td>
                        <select size="1" name="secondaryAddress">
                            <option value="CHOOSE">-- Select Address --</option>
                            <%
                                for (InetAddress iaddress : addresses) {
                                    String hostAddress = iaddress.getHostAddress();
                                    boolean isSecondaryAddress = hostAddress.equals(stunService.getSecondaryAddress());
                            %>
                            <option value="<%= hostAddress %>" <% if (isSecondaryAddress) { %>
                                    selected <% } %> ><%= hostAddress %>
                            </option>
                            <% } %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td align="left">
                        <fmt:message key="stun.settings.primaryport"/>
                        :
                    </td>
                    <td>
                        <input type="text" size="6"
                               maxlength="10"
                               name="primaryPort"
                               value="<%=stunService.getPrimaryPort()%>"
                               align="left">
                    </td>
                </tr>
                <tr>
                    <td align="left">
                        <fmt:message key="stun.settings.secondaryport"/>
                        :
                    </td>
                    <td>
                        <input type="text" size="6"
                               maxlength="10"
                               name="secondaryPort"
                               value="<%=stunService.getSecondaryPort()%>"
                               align="left">
                    </td>
                </tr>
                <tr>
                    <td>
                        <input type="hidden" name="save">
                        <input type="button" name="set" value="<fmt:message key="global.save_settings" />"
                               onclick="checkAndSubmit()">
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</form>
<form action="" method="post" name="add">
    <div class="jive-contentBoxHeader">
        <fmt:message key="stun.external.title"/>
    </div>
    <div class="jive-contentBox">
        <p>
            <fmt:message key="stun.external.comment"/>
        </p>

        <table cellpadding="3" cellspacing="0" border="0" width="300">
            <thead>
                <tr>
                    <th nowrap align="left">
                        <fmt:message key="stun.external.server"/>
                    </th>
                    <th nowrap align="left">
                        <fmt:message key="stun.external.port"/>
                    </th>
                    <th nowrap align="left">
                        <fmt:message key="global.delete"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <%
                    int i = 0;
                    for (StunServerAddress stunServerAddress : stunService.getExternalServers()) {
                %>
                <tr>
                    <td align="left">
                        <%=stunServerAddress.getServer()%>
                    </td>
                    <td align="left">
                        <%=stunServerAddress.getPort()%>
                    </td>
                    <td align="center">
                        <a href="#" onclick="document.add.remove.value=<%=i++%>;document.add.submit();">
                            <img src="images/delete-16x16.gif" width="16" height="16" border="0"
                                 alt="<fmt:message key="global.click_delete" />">
                        </a>
                    </td>
                </tr>
                <%
                    }
                %>
                <input type="hidden" name="remove" value="">
                <tr>
                    <td align="left">
                        <input type="text" name="externalServer" size="20" maxlength="50">
                    </td>
                    <td align="left">
                        <input type="text" name="externalPort" size="6" maxlength="6">
                    </td>
                </tr>
                <tr>
                    <td>
                        &nbsp;
                    </td>
                </tr>
                <tr>
                    <td>
                        <input type="submit" name="add" value="<fmt:message key="global.add"/>">
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

</form>
</body>
</html>