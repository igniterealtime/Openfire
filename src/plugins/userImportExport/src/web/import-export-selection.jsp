<%@ page import="org.jivesoftware.wildfire.plugin.ImportExportPlugin,
                 org.jivesoftware.wildfire.XMPPServer"
%>

<html>
    <head>
        <title>Import/Export Selection</title>
        <meta name="pageID" content="import-export-selection"/>
    </head>
    <body>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
%>

<p>

<% if (plugin.isUserProviderReadOnly()) { %>

    Sorry, because you are using LDAP as your user store, this plugin will not work with your Wildfire installation.

<% } else { %>

The import and export functions allow you to read data into and write user
data from your Wildfire installation.

<ul>
    <li><a href="import-user-data.jsp">Import User Data</a></li>
    <li><a href="export-user-data.jsp">Export User Data</a></li>    
</ul>

<% } %>

</body>
</html>
