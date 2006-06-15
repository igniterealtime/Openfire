<%--
  - Copyright (C) 2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
				 org.jivesoftware.wildfire.XMPPServer,
				 org.jivesoftware.wildfire.container.Plugin,
                 org.jivesoftware.wildfire.container.PluginManager,
                 org.jivesoftware.wildfire.update.Update,
                 org.jivesoftware.wildfire.update.UpdateManager"
%>
<%@ page import="java.io.BufferedReader"%>
<%@ page import="java.io.File"%>
<%@ page import="java.io.FileReader"%>
<%@ page import="java.io.IOException"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.Collections"%>
<%@ page import="java.util.Comparator"%>
<%@ page import="java.util.List"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
	String deletePlugin = ParamUtils.getParameter(request, "deleteplugin");
	String reloadPlugin = ParamUtils.getParameter(request, "reloadplugin");
    boolean showReadme = ParamUtils.getBooleanParameter(request, "showReadme", false);
    boolean showChangelog = ParamUtils.getBooleanParameter(request, "showChangelog", false);
    boolean showIcon = ParamUtils.getBooleanParameter(request, "showIcon", false);
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");

    final PluginManager pluginManager = webManager.getXMPPServer().getPluginManager();

    List<Plugin> plugins = new ArrayList<Plugin>(pluginManager.getPlugins());

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

    if (plugins != null) {
        Collections.sort(plugins, new Comparator<Plugin>() {
            public int compare(Plugin p1, Plugin p2) {
                return pluginManager.getName(p1).compareTo(pluginManager.getName(p2));
            }
        });
    }
    
    if (downloadRequested) {
        // Download and install new version of plugin
        updateManager.downloadPlugin(url);
    }

    if (deletePlugin != null) {
        File pluginDir = pluginManager.getPluginDirectory(pluginManager.getPlugin(deletePlugin));
		File pluginJar = new File(pluginDir.getParent(), pluginDir.getName() + ".jar");
        // Also try the .war extension.
        if (!pluginJar.exists()) {
            pluginJar = new File(pluginDir.getParent(), pluginDir.getName() + ".war");
        }
        pluginJar.delete();
        pluginManager.unloadPlugin(pluginDir.getName());
        response.sendRedirect("plugin-admin.jsp?deletesuccess=true");
        return;
	}
	
	if (reloadPlugin != null) {
		for (Plugin plugin : plugins) {
            File pluginDir = pluginManager.getPluginDirectory(plugin);
			if (reloadPlugin.equals(pluginDir.getName())) {
				pluginManager.unloadPlugin(reloadPlugin);
				response.sendRedirect("plugin-admin.jsp?reloadsuccess=true");
                return;
			}
		}		
	}
%>

<% if (showReadme) {
       String pluginName = ParamUtils.getParameter(request, "plugin");
       Plugin plugin = pluginManager.getPlugin(pluginName);
       if (plugin != null) {
           File readme = new File(pluginManager.getPluginDirectory(plugin), "readme.html");
           if (readme.exists()) {
               BufferedReader in = null;
               try {
                   in = new BufferedReader(new FileReader(readme));
                   String line;
                   while ((line = in.readLine()) != null) {
%>
                        <%= line %>
<%
                   }
               }
               catch (IOException ioe) {
                   ioe.printStackTrace();
               }
               finally {
                   if (in != null) {
                       try { in.close(); } catch (Exception e) { }
                   }
               }
           }
       }
       return;
   }
%>
<% if (showChangelog) {
       String pluginName = ParamUtils.getParameter(request, "plugin");
       Plugin plugin = pluginManager.getPlugin(pluginName);
       if (plugin != null) {
           File changelog = new File(pluginManager.getPluginDirectory(plugin), "changelog.html");
           if (changelog.exists()) {
               BufferedReader in = null;
               try {
                   in = new BufferedReader(new FileReader(changelog));
                   String line;
                   while ((line = in.readLine()) != null) {
%>
                        <%= line %>
<%
                   }
               }
               catch (IOException ioe) {

               }
               finally {
                   if (in != null) {
                       try { in.close(); } catch (Exception e) { }
                   }
               }
           }
       }
       return;
    }
%>

<html>
    <head>
        <title><fmt:message key="plugin.admin.title"/></title>
        <meta name="pageID" content="plugin-settings"/>
        <meta name="helpPage" content="manage_system_plugins.html"/>
    </head>
    <body>

<% if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label"><fmt:message key="plugin.admin.deleted_success" /></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } else if ("false".equals(request.getParameter("deletesuccess"))) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt="" />></td>
        	<td class="jive-icon-label"><fmt:message key="plugin.admin.deleted_failure" /></td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } %>

<% if ("true".equals(request.getParameter("reloadsuccess"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label"><fmt:message key="plugin.admin.reload_success" /></td></tr>
    </tbody>
    </table>
    </div>
    <br>

<% } %>

<p>
<fmt:message key="plugin.admin.info" />
</p>
<p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th>&nbsp;</th>
        <th nowrap colspan="2"><fmt:message key="plugin.admin.name" /></th>
        <th nowrap><fmt:message key="plugin.admin.description" /></th>
        <th nowrap><fmt:message key="plugin.admin.version" /></th>
        <th nowrap><fmt:message key="plugin.admin.author" /></th>
        <th nowrap><fmt:message key="plugin.admin.restart" /></th>
        <th nowrap><fmt:message key="global.delete" /></th>
    </tr>
</thead>
<tbody>

<%
	// If only the admin plugin is installed, show "none".
    if (plugins.size() == 1) {
%>
    <tr>
        <td align="center" colspan="8"><fmt:message key="plugin.admin.no_plugin" /></td>
    </tr>
<%
    }

    int count = 0;
    for (Plugin plugin : plugins) {
        String dirName = pluginManager.getPluginDirectory(plugin).getName();
        // Skip the admin plugin.
        if (!"admin".equals(dirName)) {
            count++;
            String pluginName = pluginManager.getName(plugin);
            String pluginDescription = pluginManager.getDescription(plugin);
            String pluginAuthor = pluginManager.getAuthor(plugin);
            String pluginVersion = pluginManager.getVersion(plugin);
            File pluginDir = pluginManager.getPluginDirectory(plugin);
            File icon = new File(pluginDir, "logo_small.png");
            if (!icon.exists()) {
                icon = new File(pluginDir, "logo_small.gif");
            }
            // Check if there is an update for this plugin
            Update update = updateManager.getPluginUpdate(pluginName, pluginVersion);
%>

        <tr>
            <td width="1%">
                <% if (icon.exists()) { %>
                <img src="plugin-icon.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showIcon=true&decorator=none" width="16" height="16" alt="Plugin">
                <% } else { %>
	            <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
                <% } %>
	        </td>
	        <td width="20%" nowrap>
	            <%= (pluginName != null ? pluginName : dirName) %> &nbsp;
                <%

                    boolean readmeExists = new File(pluginDir, "readme.html").exists();
                    boolean changelogExists = new File(pluginDir, "changelog.html").exists();
                %>
                </td>
            <td nowrap valign="top">
                <p><% if (readmeExists) { %>
                <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showReadme=true&decorator=none"
                ><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
                <% } else { %> &nbsp; <% } %>
                <% if (changelogExists) { %>
                <a href="plugin-admin.jsp?plugin=<%= URLEncoder.encode(pluginDir.getName(), "utf-8") %>&showChangelog=true&decorator=none"
                ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                <% } else { %> &nbsp; <% } %></p>
                <% if (update != null) { %>
                <p>
                    <%
                        String updateURL = update.getURL();
                        if (updateURL.endsWith(".jar") || updateURL.endsWith(".zip") || updateURL.endsWith(".war")) {
                            // Change it so that the server downloads and installs the new version of the plugin
                            updateURL = "plugin-admin.jsp?download=true&url="+ updateURL;
                        }
                    %>
                    <a href="<%= updateURL %>"
                    ><img src="images/doc-down-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="plugin.admin.download" />"></a>
                    <% if (update.getChangelog() != null) { %>
                    <a href="<%= update.getChangelog()%>"
                    ><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                    <% } else { %> &nbsp; <% } %>
                </p>
                <% } %>
            </td>
	        <td width="60%">
	            <p><%= pluginDescription != null ? pluginDescription : "" %></p>
                <% if (update != null) { %>
                <p><fmt:message key="plugin.admin.update-desc" /></p>
                <% } %>
            </td>
	        <td width="5%" align="center" valign="top">
                <p><%= pluginVersion != null ? pluginVersion : "" %></p>
                <% if (update != null) { %>
	             <p><%= update.getLatestVersion() %></p>
                <% } %>
	        </td>
	        <td width="15%" nowrap valign="top">
	             <%= pluginAuthor != null ? pluginAuthor : "" %>  &nbsp;
	        </td>
	        <td width="1%" align="center" valign="top">
	            <a href="plugin-admin.jsp?reloadplugin=<%= dirName %>"
	             title="<fmt:message key="plugin.admin.click_reload" />"
	             ><img src="images/refresh-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.refresh" />"></a>
	        </td>
	        <td width="1%" align="center" valign="top" style="border-right:1px #ccc solid;">
	            <a href="#" onclick="if (confirm('<fmt:message key="plugin.admin.confirm" />')) { location.replace('plugin-admin.jsp?deleteplugin=<%= dirName %>'); } "
	             title="<fmt:message key="global.click_delete" />"
	             ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.delete" />"></a>
	        </td>
	    </tr>
<%		    
        }
    }
%>
</tbody>
</table>
</div>

    </body>
</html>