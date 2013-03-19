<%@ page import="org.jivesoftware.openfire.plugin.ImportExportPlugin,
                 org.jivesoftware.openfire.XMPPServer"
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

The import and export functions allow you to read data into and write user
data from your Openfire installation.

<ul>
    <li><a href="import-user-data.jsp">Import User Data</a></li>
    <li><a href="export-user-data.jsp">Export User Data</a></li>    
</ul>

<% if (plugin.isUserProviderReadOnly()) { %>

   Note: because you are using a read-only user data store such as LDAP or POP3 you will only be able to import user roster data, not users themselves.
   Please see the <a href="../../plugin-admin.jsp?plugin=userimportexport&showReadme=true&decorator=none">readme</a> for details.

<% } %>
</p>

</body>
</html>
