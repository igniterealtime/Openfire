<%--
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.update.AvailablePlugin,
                 org.jivesoftware.wildfire.update.UpdateManager,
                 java.util.List"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    List<AvailablePlugin> plugins = updateManager.getAvailablePlugins();


    if (downloadRequested) {
        // Download and install new plugin
        updateManager.downloadPlugin(url);
    }

%>

<html>
    <head>
        <title><fmt:message key="plugin.available.title"/></title>
        <meta name="pageID" content="available-plugins"/>
    </head>
    <body>

<p>
<fmt:message key="plugin.available.info" />
</p>
<p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap colspan="2"><fmt:message key="plugin.available.name" /></th>
        <th nowrap><fmt:message key="plugin.available.description" /></th>
        <th nowrap><fmt:message key="plugin.available.version" /></th>
        <th nowrap><fmt:message key="plugin.available.author" /></th>
        <th nowrap><fmt:message key="plugin.available.install" /></th>
    </tr>
</thead>
<tbody>

<%
	// If only the admin plugin is installed, show "none".
    if (plugins.isEmpty()) {
%>
    <tr>
        <td align="center" colspan="8"><fmt:message key="plugin.available.no_plugin" /></td>
    </tr>
<%
    }

    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
%>
    <tr>
        <td width="1%">
            <% if (plugin.getIcon() != null) { %>
            <img src="<%= plugin.getIcon() %>" width="16" height="16" alt="Plugin">
            <% } else { %>
            <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
            <% } %>
        </td>
        <td width="20%" nowrap>
            <%= (pluginName != null ? pluginName : "") %> &nbsp;
            </td>
        <td nowrap valign="top">
            <% if (plugin.getReadme() != null) { %>
            <a href="<%= plugin.getReadme() %>"
            ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
            <% } else { %> &nbsp; <% } %>
            <% if (plugin.getChangelog() != null) { %>
            <a href="<%= plugin.getChangelog() %>"
            ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
            <% } else { %> &nbsp; <% } %>
        </td>
        <td width="60%">
            <%= pluginDescription != null ? pluginDescription : "" %>
        </td>
        <td width="5%" align="center" valign="top">
            <%= pluginVersion != null ? pluginVersion : "" %>
        </td>
        <td width="15%" nowrap valign="top">
             <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
        </td>
        <td width="1%" align="center" valign="top">
            <%
                String updateURL = plugin.getURL();
                if (updateManager.isPluginDownloaded(updateURL)) {
            %>
            &nbsp;
            <%  } else { %>
            <%
                if (updateURL.endsWith(".jar") || updateURL.endsWith(".zip") || updateURL.endsWith(".war")) {
                    // Change it so that the server downloads and installs the new version of the plugin
                    updateURL = "available-plugins.jsp?download=true&url="+ updateURL;
                }
            %>
            <a href="<%= updateURL %>"
            ><img src="images/doc-down-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="plugin.available.download" />"></a>
            <% } %>
        </td>
    </tr>
<%		    
    }
%>
</tbody>
</table>
</div>

    </body>
</html>