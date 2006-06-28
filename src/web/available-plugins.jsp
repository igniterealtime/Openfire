<%--
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.update.AvailablePlugin,
                 org.jivesoftware.wildfire.update.UpdateManager,
                 java.util.Collections"
        %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %><%@ page import="org.jivesoftware.util.ByteFormat"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>

<%
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
    List<AvailablePlugin> plugins = updateManager.getNotInstalledPlugins();

    // Sort plugins alphabetically
    Collections.sort(plugins, new Comparator() {
        public int compare(Object o1, Object o2) {
            return ((AvailablePlugin)o1).getName().compareTo(((AvailablePlugin)o2).getName());
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
.content {
    border-color: #bbb;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
}

.textfield {
    font-size: 11px;
    font-family: verdana;
    padding: 3px 2px;
    background: #efefef;
}

.text {
    font-size: 11px;
    font-family: verdana;
}

.small-label {
    font-size: 11px;
    font-weight: bold;
    font-family: verdana;
}

.small-label-link {
    font-size: 11px;
    font-weight: bold;
    font-family: verdana;
    text-decoration: underline;
}

.light-gray-border {
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
}

.light-gray-border-bottom {
    border-color: #bbb;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
}

.table-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 0px 1px 0px;
    padding: 5px;
}

.row-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.table-header-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 0px 1px 1px;
    padding: 5px;

}

.table-header-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #bbb;
    border-style: solid;
    border-width: 1px 1px 1px 0px;
    padding: 5px;
}

.update-top {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;

    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 1px 0px 0px 0px;
    padding: 5px;
}

.update-bottom {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
    padding: 5px;
}

.update-top-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 1px 0px 0px 1px;
    padding: 5px;
}

.update-bottom-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 0px 0px 1px 1px;
    padding: 5px;
}

.update-bottom-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 0px 1px 1px 0px;
    padding: 5px;
}

.update-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    background: #E7FBDE;
    border-color: #73CB73;
    border-style: solid;
    border-width: 1px 1px 0px 0px;
    padding: 5px;
}

.line-bottom-border {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;
    border-color: #bbb;
    border-style: solid;
    border-width: 0px 0px 1px 0px;
    padding: 5px;
}


</style>

    <script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>
    <script type="text/javascript">
          function downloadPlugin(url, id) {
             document.getElementById(id+"-image").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
             document.getElementById(id).style.background = "#FFFFF7";
             downloader.installPlugin(downloadComplete, url, id);
          }

          function downloadComplete(id) {
              document.getElementById(id).style.display = 'none';
              document.getElementById(id + "-row").style.display = '';
              setTimeout("fadeIt('"+id+"')", 3000);
          }

          function fadeIt(id){
              Effect.Fade(id+"-row");
          }
    </script>
    </head>
    <body>

<p>
<fmt:message key="plugin.available.info"/>
</p>
<p>

<div class="light-gray-border" style="padding:10px;">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr style="background:#F7F7FF;">
        <td class="table-header-left">&nbsp;</td>
        <td nowrap colspan="2" class="table-header">Open Source Plugins</td>
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
        <td width="15%" nowrap valign="top" class="line-bottom-border">
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
            <span id="<%= plugin.hashCode() %>-image"><a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><img src="images/add-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="plugin.available.download" />"></a></span>

            <% } %>
        </td>
    </tr>
   <tr id="<%= plugin.hashCode()%>-row" style="display:none;">
         <td width="1%" class="line-bottom-border">
             <img src="<%= plugin.getIcon()%>" width="16" height="16" />
         </td>
         <td nowrap class="line-bottom-border"><%= plugin.getName()%> plugin installed successfully!</td>
         <td colspan="5" class="line-bottom-border">&nbsp;</td>
         <td class="line-bottom-border" align="center">
             <img src="images/success-16x16.gif" height="16" width="16" />
         </td>
    </tr>
<%
    }
%>
    <tr><td><br/></td></tr>
  <tr style="background:#F7F7FF;">
         <td class="table-header-left">&nbsp;</td>
        <td nowrap colspan="7" class="row-header">Commercial Plugins</td>
    </tr>
    <%
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

            <span id="<%= plugin.hashCode() %>-image"><a href="javascript:downloadPlugin('<%=updateURL%>', '<%= plugin.hashCode()%>')"><img src="images/add-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="plugin.available.download" />"></a></span>
            <% } %>
        </td>
    </tr>
    <tr id="<%= plugin.hashCode()%>-row" style="display:none;">
         <td width="1%" class="line-bottom-border">
             <img src="<%= plugin.getIcon()%>" width="16" height="16" />
         </td>
         <td class="line-bottom-border"><%= plugin.getName()%> plugin installed successfully!</td>
         <td colspan="5" class="line-bottom-border">&nbsp;</td>
         <td class="line-bottom-border">
             <img src="images/success-16x16.gif" height="16" width="16" />
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