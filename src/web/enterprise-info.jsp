<%--
  -	$RCSfile$
  -	$Revision: 3195 $
  -	$Date: $
  -
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.wildfire.*, org.jivesoftware.wildfire.update.UpdateManager, org.jivesoftware.wildfire.update.AvailablePlugin, java.net.URLEncoder, java.io.File, org.jivesoftware.wildfire.container.PluginManager, org.jivesoftware.wildfire.container.Plugin"
    errorPage="error.jsp"
%>

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

    // Sort plugins alphabetically
    Collections.sort(plugins, new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((AvailablePlugin) o1).getName().compareTo(((AvailablePlugin) o2).getName());
        }
    });

    if (downloadRequested) {
        // Download and install new plugin
        updateManager.downloadPlugin(url);
    }

%>

<html>
<head>
<title>Try Wildfire Enterprise</title>
<meta name="pageID" content="enterprise-info"/>

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
        window.location.href = "enterprise-info.jsp";
    }


</script>
</head>

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
<body>

<span class="jive-enterprise-info">

    <img src="images/enterprise.png" alt="Wildfire Enterprise" hspace="10" vspace="10" width="200" height="197" border="0" align="right" />

    <h3>What is it?</h3>

    <ul>
        <li>A commercial extension to Wildfire that provides practical and valuable features.</li>
        <li>Delivered as a plugin that works with the Open Source edition.</li>
        <li>Low per-user pricing.</li>
    </ul>

    <h3>Why use it?</h3>
    <ul>
        <li><b>Reporting:</b> What manager wouldn't love graphs like the one pictured to the right?</li>
        <li><b>Client Control:</b> Stop the client madness -- take control of versions, features and more.</li>
        <li><b>Archiving:</b> If "compliance" is a word your organization uses, you need this feature.</li>
        <li><b>Customer Chat:</b> Generate leads, close sales, route questions -- your sales and support people will love this feature.</li>
        <li><b>Support.</b> Professional support by the hard-working and friendly Jive Software team.</li>
        <li>Best of all, by using Enterprise, you're directly supporting the Open Source project.</li>

        <p>Interested? See the full <a href="http://www.jivesoftware.com/products/wildfire/features/enterprise.jsp">feature overview</a>.</p>
    </ul>



    <h3>How to get started:</h3>
    <ol>
        <li>Install the plugin (no re-start necessary).</li>
        <li>Get an evaluation license file (you'll be prompted after install).</li>
    </ol>


</span>


<%if(plugins.size() == 0){ %>
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

<tr style="background:#f3f7fa;">
    <td class="table-header-left">&nbsp;</td>
    <td nowrap colspan="7" class="row-header">Install Enterprise Plugin</td>
</tr>
<%
    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginDescription = plugin.getDescription();
        String pluginAuthor = plugin.getAuthor();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (!plugin.isCommercial() || !pluginName.equals("Wildfire Enterprise")) {
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

</table>

</div>


 <%
        String time = JiveGlobals.getProperty("update.lastCheck");
        if(time != null){
        Date date = new Date(Long.parseLong(time));
        time = JiveGlobals.formatDate(date);
        }
    %>
       <p style="font-size: 8pt; padding: 4px 0px 10px 0px;">
           <% if(time != null) { %>
        Last checked for an updated version <%= time%>.
           <% } %>
          
           &nbsp;<span id="reloader2"><a href="javascript:updatePluginsListNow()"><fmt:message key="plugin.available.manual.update" /></a></span>
        </p>
           <% } %>


    <div class="jive-enterprise-info-install">
    <a href="#" class="jive-enterprise-info-install-btn">Install Enterprise Plugin</a>
    <p>Version 3.0.1 - 3.9 MB<br>
    <a href="#">Readme</a> &nbsp;|&nbsp; <a href="#">Changelog</a></p>
    </div>

    <div class="jive-enterprise-info-close"><a href="#">Don't Show This Again</a></div>

</body>
</html>