<%--
  - Copyright (C) 2005-2007 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.*,
                 org.jivesoftware.openfire.update.UpdateManager,
                 org.jivesoftware.openfire.update.AvailablePlugin,
                 org.jivesoftware.openfire.container.PluginManager"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // It's possible for the page to still be displayed after Enterprise
    // has been downloaded, like when the user has not done a page refresh.
    // Detect that case and do a re-direct.
    PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
    boolean pluginExists = pluginManager != null && pluginManager.getPlugin("enterprise") != null;
    if (pluginExists) {
        // Redirect to the main Enterprise page.
        response.sendRedirect("/plugins/enterprise/stats-dashboard.jsp");
        return;
    }

    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    boolean disable = request.getParameter("disable") != null;
    if(disable){
        JiveGlobals.setProperty("enterpriseInfoEnabled", "false");
        response.sendRedirect("/index.jsp");
        return;
    }

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    List<AvailablePlugin> plugins = updateManager.getNotInstalledPlugins();

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
<title>Try Openfire Enterprise</title>
<meta name="pageID" content="enterprise-info"/>

<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>
<script type="text/javascript">

    var downloading;
    function downloadPlugin(url, id) {
        downloading = true;
        document.getElementById("install-button").style.display = 'none';
        document.getElementById("installing-button").style.display = '';
        document.getElementById("installed").style.display = 'none';
        setTimeout("startDownload('" + url + "','" + id + "')", 5000);
    }

    function startDownload(url, id) {
        downloader.installPlugin(downloadComplete, url, id);
    }

    function downloadComplete(status) {
        downloading = false;
        if (!status.successfull) {
            document.getElementById("install-button").style.display = '';
            document.getElementById("installing-button").style.display = 'none';
            document.getElementById("installed").style.display = 'none';
            document.getElementById("error-message").style.display = '';
        }
        else {
            document.getElementById("install-button").style.display = 'none';
            document.getElementById("installing-button").style.display = 'none';
            document.getElementById("installed").style.display = '';
            setTimeout("gotoEnterprise()", 5000);
        }
    }

    function gotoEnterprise(){
        window.location.href = "plugins/enterprise/stats-dashboard.jsp";
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

    <img src="images/enterprise.png" alt="Openfire Enterprise" hspace="10" vspace="10" width="200" height="197" border="0" align="right" />

    <h3>What is it?</h3>

    <ul>
        <li>A commercial edition of Openfire that provides practical and valuable features along with professional support.</li>
        <li>Delivered as a plugin that works with the Open Source edition.</li>
        <li>Low per-user pricing.</li>
    </ul>

    <h3>Why use it?</h3>
    <ul>
        <li><b>Reporting:</b> What manager wouldn't love graphs like the one pictured to the right?</li>
        <li><b>Client Control:</b> Stop the client madness -- take control of versions, features and more.</li>
        <li><b>Archiving:</b> If "compliance" is a word your organization uses, you need this feature.</li>
        <li><b>Customer Chat:</b> Add click-to-chat to your website to generate leads, close sales,
            route questions -- your sales and support people will love this feature.</li>
        <li><b>Support.</b> Professional support by the hard-working and friendly Jive Software team.</li>
        <li>Best of all, by using Enterprise, you're directly supporting the Open Source project.</li>

        <p>Interested? See the full <a href="http://www.jivesoftware.com/products/openfire/features/enterprise.jsp">feature overview</a>.</p>
    </ul>



    <h3>How to get started:</h3>
    <ol>
        <li>Install the Enterprise edition via the plugin (no re-start necessary).</li>
        <li>Get an evaluation license file (you'll be prompted after install).</li>
    </ol>


</span>


<%if(plugins.size() == 0){ %>
<div style="padding:10px;background:#FFEBB5;border:1px solid #DEB24A;width:75%;">
    <fmt:message key="plugin.available.no.plugin" />&nbsp;<span id="reloaderID"><a href="javascript:updatePluginsList();"><fmt:message key="plugin.available.list" /></a></span>
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


<%
    for (AvailablePlugin plugin : plugins) {
        String pluginName = plugin.getName();
        String pluginVersion = plugin.getLatestVersion();
        ByteFormat byteFormat = new ByteFormat();
        String fileSize = byteFormat.format(plugin.getFileSize());

        if (!plugin.isCommercial() || !pluginName.equals("Openfire Enterprise")) {
            continue;
        }
%>
        <%
            String updateURL = plugin.getURL();
            if (updateManager.isPluginDownloaded(updateURL)) {
        %>
        &nbsp;
        <%  }
        else { %>

        <div id="error-message" class="error" style="display:none;">
           <fmt:message key="plugin.enterprise.download.error" />
        </div>


        <div id="install-button" class="jive-enterprise-info-install">
            <a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')" class="jive-enterprise-info-install-btn">Install Enterprise Plugin</a>
            <p>Version <%= pluginVersion%> - <%= fileSize%><br>
            <a href="<%=plugin.getReadme()%>" target=_blank>Readme</a> &nbsp;|&nbsp; <a href="<%= plugin.getChangelog()%>" target=_blank>ChangeLog</a></p>
            <div class="jive-enterprise-info-close"><a href="enterprise-info.jsp?disable=true"><fmt:message key="plugin.enterprise.dont.show" /></a></div>
            </div>


        <div id="installing-button" class="jive-enterprise-info-wait" style="display:none;">
           <img src="images/wait24trans.gif" alt="" align="left">    <strong><fmt:message key="plugin.enterprise.installing" /></strong>
        </div>

         <div id="installed" class="jive-enterprise-info-success" style="display:none;">
          <strong><fmt:message key="plugin.enterprise.installed" /></strong>
         </div>

        <% } } } %>
</body>
</html>