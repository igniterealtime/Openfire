<%@ page import="org.ifsoft.rayo.RayoPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.xmpp.jnodes.nio.LocalIPResolver" %>
<%@ page import="java.net.InetAddress" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.xmpp.jnodes.nio.PublicIPResolver" %>
<%@ page import="java.net.InetSocketAddress" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.

--%>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the Monitoring plugin
    RayoPlugin plugin = (RayoPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("rayo");

    if (update) {
        String overrideIP = request.getParameter("overrideip");
        if (overrideIP != null) {
            overrideIP = overrideIP.trim();
            try {
                InetAddress.getByName(overrideIP);
                LocalIPResolver.setOverrideIp(overrideIP);
                JiveGlobals.setProperty(RayoPlugin.JN_PUB_IP_PROPERTY, overrideIP);
                plugin.verifyNetwork();
            } catch (Exception e) {
                errorMessage = LocaleUtils.getLocalizedString("rayo.settings.invalid.publicip", "rayo");
            }
        }
    }

    String publicIP = "none";
    if (!plugin.hasPublicIP()) {
        final InetSocketAddress addr = PublicIPResolver.getPublicAddress("stun.xten.com", 3478);
        if (addr != null) {
            publicIP = addr.getAddress().getHostAddress();
        }
    }
%>
<html>
<head>
    <title><fmt:message key="rayo.settings.title"/></title>
    <meta name="pageID" content="rayo"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
    <form action="rayo.jsp" method="post">
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th colspan="2"><fmt:message key="rayo.settings.title"/></th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td><label class="jive-label"><fmt:message key="rayo.verified.ip"/>:</label><br></td>
                <td align="left"><% if (plugin.hasPublicIP()) { %>
                    <img src="/images/check.gif" width="17" height="17" border="0">
                    <% } else { %>
                    <img src="/images/x.gif" width="17" height="17" border="0"><i>&nbsp;<fmt:message
                            key="rayo.verified.ip.warning"/></i>&nbsp;<b><%=publicIP%></b>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="rayo.active.channels"/>:</label><br>
                </td>
                <td align="left"><%=plugin.getActiveChannelCount()%>
                </td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="rayo.settings.overrideip"/>:</label><br>
                </td>
                <td align="left">
                    <input name="overrideip" type="text" maxlength="15" size="15"
                           value="<%=LocalIPResolver.getLocalIP()%>"/>
                </td>
            </tr>
            <tr>
                <th colspan="2"><input type="submit" name="update"
                                       value="<fmt:message key="rayo.settings.update.settings" />"></th>
            </tr>
            </tbody>
        </table>
    </form>
</div>

</body>
</html>
