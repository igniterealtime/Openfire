<%@ page import="java.io.IOException,
				 java.util.*,				 
				 org.jivesoftware.admin.AdminPageBean,
				 org.jivesoftware.messenger.plugin.ImportExportPlugin,
				 org.jivesoftware.messenger.XMPPServer,
				 org.jivesoftware.util.ParamUtils"
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<% 
    boolean exportUsers = request.getParameter("exportUsers") != null;
    boolean success = request.getParameter("success") != null;
    
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
    
    Map errors = new HashMap();
    if (exportUsers) {
		String file = ParamUtils.getParameter(request, "exportFile");
		if ((file == null)  || (file.length() <= 0)) {
			errors.put("missingFile","missingFile");
        
        }
        else {
	        try {
	        	//todo this could take some, redirect to a progress page?
				if (plugin.exportUserData(file)) {
					response.sendRedirect("export-user-data.jsp?success=true");
					return;
				}
				else {
					errors.put("fileNotCreated","fileNotCreated");
				}       				
			}
			catch (IOException e) {
               	errors.put("IOException","IOException");               	
	        }		
       	}
    }
%>


<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Export User Data";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "export-user-data.jsp"));
    pageinfo.setPageID("import-export-selection");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<% if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
	        <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
	        <td class="jive-icon-label">
	        <% 	if (errors.containsKey("missingFile")) { %>
	        	Missing or bad file name.
	        <% } else if (errors.containsKey("IOException") || errors.containsKey("fileNotCreated")) { %>
	        	Couldn't create export file.
	        <% } %>
	        </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } else if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
	        <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
	        <td class="jive-icon-label">User data successfully exported.</td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>
    
<% } %>

<form action="export-user-data.jsp?exportUsers" method="post">
<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
	<thead>
	    <tr>
	        <th>Export Properties</th>
	    </tr>
	</thead>
    <tr class="jive-even">
        <td style="border-right:1px #ccc solid;">Export Location:</td>
    </tr>
    <tr class="jive-odd">
        <td style="border-right:1px #ccc solid;"><%= plugin.exportDirectory() %></td>     
    </tr>
    <tr class="jive-even">
        <td style="border-right:1px #ccc solid;">Export File Name:</td>
    </tr>
    <tr class="jive-odd">
        <td style="border-right:1px #ccc solid;">
        	<input type="text" size="30" maxlength="150" name="exportFile">
        </td>        
    </tr>
</table>
</div>

<br><br>

<input type="submit" value="Export">
</form>

<jsp:include page="bottom.jsp" flush="true" />