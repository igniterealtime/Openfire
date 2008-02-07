<%--
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page errorPage="error.jsp" import="org.jivesoftware.util.ByteFormat,
                                       org.jivesoftware.util.Version,
                                       org.jivesoftware.openfire.XMPPServer,
                                       org.jivesoftware.openfire.container.Plugin"
    %>
<%@ page import="org.jivesoftware.openfire.container.PluginManager" %>
<%@ page import="org.jivesoftware.openfire.update.AvailablePlugin" %>
<%@ page import="org.jivesoftware.openfire.update.UpdateManager" %>
<%@ page import="java.io.File" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="java.util.Date"%>
<%@ page import="java.text.SimpleDateFormat"%>
<%@ page import="org.jivesoftware.util.WebManager"%><%@ page import="org.jivesoftware.util.LocaleUtils"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    WebManager webManager = new WebManager();
%>

<%
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    List<AvailablePlugin> plugins = updateManager.getNotInstalledPlugins();

    String time = JiveGlobals.getProperty("update.lastCheck");
    // Sort plugins alphabetically
    Collections.sort(plugins, new Comparator<AvailablePlugin>() {
        public int compare(AvailablePlugin o1, AvailablePlugin o2) {
            return o1.getName().compareTo(o2.getName());
        }
    });

    if (downloadRequested) {
        // Download and install new plugin
        updateManager.downloadPlugin(url);
    }

%>

<html>
<head>
<title><fmt:message key="plugin.available.title"/></title>
<meta name="pageID" content="available-plugins"/>

<style type="text/css">

.light-gray-border {
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
	-moz-border-radius: 3px;
}



.table-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0px 1px 0px;
    padding: 5px;
}

.table-header-align-right {
    text-align: right;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0px 1px 0px;
    padding: 5px;
}

.row-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.table-header-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0px 1px 1px;
    padding: 5px;

}

.table-header-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.line-bottom-border {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;
    border-color: #e3e3e3;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
    padding: 5px;
}


</style>

<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>
<script type="text/javascript">

    var downloading;
    function downloadPlugin(url, id) {
        downloading = true;
        document.getElementById(id + "-image").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        document.getElementById(id).style.background = "#FFFFCC";
        setTimeout("startDownload('" + url + "','" + id + "')", 5000);
    }

    function startDownload(url, id) {
        downloader.installPlugin(downloadComplete, url, id);
    }

    function downloadComplete(status) {
        downloading = false;
        if (!status.successfull) {
            document.getElementById(status.hashCode + "-image").innerHTML = '<img src="images/add-16x16.gif" border="0"/>';
            document.getElementById(status.hashCode).style.background = "#FFFFFF";
            document.getElementById("errorMessage").style.display = '';
            document.getElementById(status.hashCode).style.display = '';
            document.getElementById(status.hashCode + "-row").style.display = 'none';
            setTimeout("closeErrorMessage()", 5000);
        }
        else {
            document.getElementById(status.hashCode).style.display = 'none';
            document.getElementById(status.hashCode + "-row").style.display = '';
            setTimeout("fadeIt('" + status.hashCode + "')", 3000);
        }
    }

    function closeErrorMessage(){
        Effect.Fade("errorMessage");
    }

    function fadeIt(id) {
        Effect.Fade(id + "-row");
    }


    DWREngine.setErrorHandler(handleError);

    function handleError(error) {
    }

    // Handle leaving of page validation.
    window.onbeforeunload = function (evt) {
        if (!downloading) {
            return;
        }
        var message = '<fmt:message key="plugin.available.cancel.redirect" />';
        if (typeof evt == 'undefined') {
            evt = window.event;
        }
        if (evt) {
            evt.returnValue = message;
        }
        return message;
    }

    function updatePluginsList(){
        document.getElementById("reloaderID").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        downloader.updatePluginsList(pluginsListUpdated);
    }

    function updatePluginsListNow(){
        document.getElementById("reloader2").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        downloader.updatePluginsList(pluginsListUpdated);
    }

    function pluginsListUpdated(){
        window.location.href = "available-plugins.jsp";
    }


</script>
</head>

<body>

<p>
    <fmt:message key="plugin.available.info"/>
</p>

<p>

<%if(time == null){ %>
<div style="padding:10px;background:#FFEBB5;border:1px solid #DEB24A;width:75%;">
    <fmt:message key="plugin.available.no.list" />&nbsp;<span id="reloaderID"><a href="javascript:updatePluginsList();"><fmt:message key="plugin.available.list" /></a></span>
</div>
<br/>
<div style="width:75%;">
    <p>
   <fmt:message key="plugin.available.no.list.description" />
</p>

<% if(!updateManager.isServiceEnabled()){ %>
<fmt:message key="plugin.available.auto.update.currently" /> <b><fmt:message key="plugin.available.auto.update.currently.disabled" /></b>. <a href="manage-updates.jsp"><fmt:message key="plugin.available.click.here" /></a> <fmt:message key="plugin.available.change" />
<% } %>
</div>
<% } else {%>


<div id="errorMessage" class="error" style="display:none;">
    <fmt:message key="plugin.available.error.downloading" />
</div>


<div class="light-gray-border" style="padding:10px;">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr style="background:#eee;">
        <td class="table-header-left">&nbsp;</td>
        <td nowrap colspan="2" class="table-header"><fmt:message key="plugin.available.open_source"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.description"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.version"/></td>
        <td nowrap class="table-header"><fmt:message key="plugin.available.author"/></td>
        <td nowrap class="table-header">File Size</td>
        <td nowrap class="table-header-right"><fmt:message key="plugin.available.install"/></td>
    </tr>
</thead>
<tbody>

<%
    // If only the admin plugin is installed, show "none".
    if (plugins.isEmpty()) {
%>
<tr>
    <td align="center" colspan="8"><fmt:message key="plugin.available.no_plugin"/></td>
</tr>
<%
    }

    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (plugin.isCommercial()) {
            continue;
        }
%>
<tr id="<%= plugin.hashCode()%>">
    <td width="1%" class="line-bottom-border">
        <% if (plugin.getIcon() != null) { %>
        <img src="<%= plugin.getIcon() %>" width="16" height="16" alt="Plugin">
        <% }
        else { %>
        <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
        <% } %>
    </td>
    <td width="20%" nowrap class="line-bottom-border">
        <%= (pluginName != null ? pluginName : "") %> &nbsp;
    </td>
    <td nowrap valign="top" class="line-bottom-border">
        <% if (plugin.getReadme() != null) { %>
        <a href="<%= plugin.getReadme() %>"
            ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
        <% }
        else { %> &nbsp; <% } %>
        <% if (plugin.getChangelog() != null) { %>
        <a href="<%= plugin.getChangelog() %>"
            ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
        <% }
        else { %> &nbsp; <% } %>
    </td>
    <td width="60%" class="line-bottom-border">
        <%= pluginDescription != null ? pluginDescription : "" %>
    </td>
    <td width="5%" align="center" valign="top" class="line-bottom-border">
        <%= pluginVersion != null ? pluginVersion : "" %>
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border" align="right">
        <%= fileSize %>
    </td>
    <td width="1%" align="center" valign="top" class="line-bottom-border">
        <%
            String updateURL = plugin.getURL();
            if (updateManager.isPluginDownloaded(updateURL)) {
        %>
        &nbsp;
        <%  }
        else { %>
        <%

        %>
        <a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><span id="<%= plugin.hashCode() %>-image"><img src="images/add-16x16.gif" width="16" height="16" border="0"
                                                                                                                                        alt="<fmt:message key="plugin.available.download" />"></span></a>

        <% } %>
    </td>
</tr>
<tr id="<%= plugin.hashCode()%>-row" style="display:none;background: #E7FBDE;">
    <td width="1%" class="line-bottom-border">
        <img src="<%= plugin.getIcon()%>" width="16" height="16"/>
    </td>
    <td colspan="6" nowrap class="line-bottom-border"><%= plugin.getName()%> <fmt:message key="plugin.available.installation.success" /></td>
    <td class="line-bottom-border" align="center">
        <img src="images/success-16x16.gif" height="16" width="16"/>
    </td>
</tr>
<%
    }
%>
<tr><td><br/></td></tr>
<tr style="background:#eee;">
    <td class="table-header-left">&nbsp;</td>
    <td nowrap colspan="7" class="row-header"><fmt:message key="plugin.available.commercial_plugins" /></td>
</tr>
<%
    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (!plugin.isCommercial()) {
            continue;
        }
%>
<tr id="<%= plugin.hashCode()%>">
    <td width="1%" class="line-bottom-border">
        <% if (plugin.getIcon() != null) { %>
        <img src="<%= plugin.getIcon() %>" width="16" height="16" alt="Plugin">
        <% }
        else { %>
        <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
        <% } %>
    </td>
    <td width="20%" nowrap class="line-bottom-border">
        <%= (pluginName != null ? pluginName : "") %> &nbsp;
    </td>
    <td nowrap valign="top" class="line-bottom-border">
        <% if (plugin.getReadme() != null) { %>
        <a href="<%= plugin.getReadme() %>"
            ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
        <% }
        else { %> &nbsp; <% } %>
        <% if (plugin.getChangelog() != null) { %>
        <a href="<%= plugin.getChangelog() %>"
            ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
        <% }
        else { %> &nbsp; <% } %>
    </td>
    <td width="60%" class="line-bottom-border">
        <%= pluginDescription != null ? pluginDescription : "" %>
    </td>
    <td width="5%" align="center" valign="top" class="line-bottom-border">
        <%= pluginVersion != null ? pluginVersion : "" %>
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
    </td>
    <td width="15%" nowrap valign="top" class="line-bottom-border">
        <%= fileSize  %>
    </td>
    <td width="1%" align="center" valign="top" class="line-bottom-border">
        <%
            String updateURL = plugin.getURL();
            if (updateManager.isPluginDownloaded(updateURL)) {
        %>
        &nbsp;
        <%  }
        else { %>

        <span id="<%= plugin.hashCode() %>-image"><a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><img src="images/add-16x16.gif" width="16" height="16" border="0"
                                                                                                                                        alt="<fmt:message key="plugin.available.download" />"></a></span>
        <% } %>
    </td>
</tr>
<tr id="<%= plugin.hashCode()%>-row" style="display:none;background: #E7FBDE;">
     <td width="1%" class="line-bottom-border">
        <img src="<%= plugin.getIcon()%>" width="16" height="16"/>
    </td>
    <td colspan="6" nowrap class="line-bottom-border"><%= plugin.getName()%> <fmt:message key="plugin.available.installation.success" /></td>
    <td class="line-bottom-border" align="center">
        <img src="images/success-16x16.gif" height="16" width="16"/>
    </td>
</tr>
<%
    }
%>

</tbody>
</table>

</div>

<br/>


<%
    final XMPPServer server = XMPPServer.getInstance();
    Version version = server.getServerInfo().getVersion();
    List<Plugin> outdatedPlugins = new ArrayList<Plugin>();
    for (Plugin plugin : server.getPluginManager().getPlugins()) {
        String pluginVersion = server.getPluginManager().getMinServerVersion(plugin);
        if (pluginVersion != null && pluginVersion.compareTo(version.getVersionString()) > 0) {
            outdatedPlugins.add(plugin);
        }
    }

    if (outdatedPlugins.size() > 0) {
%>
    <div class="light-gray-border" style="padding:10px;">
    <p><fmt:message key="plugin.available.outdated" /><a href="http://www.igniterealtime.org/projects/openfire/" target="_blank"><fmt:message key="plugin.available.outdated.update" /></a></p>
    <table cellpadding="0" cellspacing="0" border="0" width="100%">


        <%
            PluginManager pluginManager = server.getPluginManager();
            for (Plugin plugin : outdatedPlugins) {
                String pluginName = pluginManager.getName(plugin);
                String pluginDescription = pluginManager.getDescription(plugin);
                String pluginAuthor = pluginManager.getAuthor(plugin);
                String pluginVersion = pluginManager.getVersion(plugin);
                File pluginDir = pluginManager.getPluginDirectory(plugin);
                File icon = new File(pluginDir, "logo_small.png");
                boolean readmeExists = new File(pluginDir, "readme.html").exists();
                boolean changelogExists = new File(pluginDir, "changelog.html").exists();
                if (!icon.exists()) {
                    icon = new File(pluginDir, "logo_small.gif");
                }
        %>
        <tr>
            <td class="line-bottom-border" width="1%">
                <% if (icon.exists()) { %>
                <img src="/geticon?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showIcon=true&decorator=none" width="16" height="16" alt="Plugin">
                <% }
                else { %>
                <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
                <% } %>
            </td>
            <td class="line-bottom-border" width="1%" nowrap>
                <%= pluginName%>
            </td>
            <td nowrap class="line-bottom-border">
                <p><% if (readmeExists) { %>
                    <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showReadme=true&decorator=none"
                        ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
                    <% }
                    else { %> &nbsp; <% } %>
                    <% if (changelogExists) { %>
                    <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showChangelog=true&decorator=none"
                        ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                    <% }
                    else { %> &nbsp; <% } %></p>
            </td>
            <td class="line-bottom-border">
                <%= pluginDescription %>
            </td>
            <td class="line-bottom-border">
                <%= pluginVersion%>
            </td>
            <td class="line-bottom-border">
                <%= pluginAuthor%>
            </td>
        </tr>
        <% }%>
  </table>

        <%} %>

</div>
<br/>
 <%
        if(time != null){
            Date date = new Date(Long.parseLong(time));
            time = JiveGlobals.formatDate(date);
        }
    %>
       <p>
           <% if(time != null) { %>
        <fmt:message key="plugin.available.autoupdate" /> <%= time%>.
           <% } %>
           <% if(updateManager.isServiceEnabled()){%>
              <fmt:message key="plugin.available.autoupdate.on" />
           <% } else { %>
                <fmt:message key="plugin.available.autoupdate.off" />
           <% } %>
           &nbsp;<span id="reloader2"><a href="javascript:updatePluginsListNow()"><fmt:message key="plugin.available.manual.update" /></a></span>
        </p>
           <% } %>

</body>
</html>