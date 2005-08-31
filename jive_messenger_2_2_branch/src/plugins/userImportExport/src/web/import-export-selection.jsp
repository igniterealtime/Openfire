<%@ page import="org.jivesoftware.admin.AdminPageBean,
				 org.jivesoftware.messenger.plugin.ImportExportPlugin,
				 org.jivesoftware.messenger.XMPPServer"
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Import/Export Selection";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "../../index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "import-export-selection.jsp"));
    pageinfo.setPageID("import-export-selection");
    
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>

<% if (plugin.isUserProviderReadOnly()) { %>

	Sorry, because you are using LDAP as your user store this plugin will not work with your Messenger installation.

<% } else { %>

The import and export functions allow you to read data into and write user
data from your Jive Messenger installation.

<ul>
	<li><a href="import-user-data.jsp">Import User Data</a></li>
    <li><a href="export-user-data.jsp">Export User Data</a></li>    
</ul>

<% } %>

<jsp:include page="bottom.jsp" flush="true" />