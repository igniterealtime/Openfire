<%@ page import="org.jinglenodes.JingleNodesPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.openfire.container.Plugin" %>
<%--
  -	$Revision: $
  -	$Date: $
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
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%

    // Get handle on the Monitoring plugin
    JingleNodesPlugin plugin = (JingleNodesPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("jinglenodes");

%>
<html>
<head>
    <title><fmt:message key="jn.settings.title"/></title>
    <meta name="pageID" content="jingle-nodes"/>
</head>
<body>

<div class="jive-table">
    <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th colspan="2"><fmt:message key="jn.settings.title"/></th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td><label class="jive-label"><fmt:message key="jn.verified.ip"/>:</label><br></td>
            <td align="left"><% if (plugin.hasPublicIP()) { %>
                <img src="/images/check.gif" width="17" height="17" border="0">
                <% } else { %>
                <img src="/images/x.gif" width="17" height="17" border="0"><i>&nbsp;<fmt:message key="jn.verified.ip.warning"/></i>
                <% } %>
            </td>
        </tr>
        <tr>
            <td><label class="jive-label"><fmt:message key="jn.active.channels"/>:</label><br>
            </td>
            <td align="left"><%=plugin.getActiveChannelCount()%>
            </td>
        </tr>
        </tbody>
    </table>
</div>

</body>
</html>