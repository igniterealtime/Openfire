<%@ page import="java.io.IOException,
				 java.util.*,				 
				 org.jivesoftware.admin.AdminPageBean,
				 org.jivesoftware.messenger.plugin.ImportExportPlugin,
				 org.jivesoftware.messenger.XMPPServer,
				 org.jivesoftware.util.ParamUtils"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% 
	admin.init(request, response, session, application, out);

    boolean exportUsers = request.getParameter("exportUsers") != null;
    boolean success = request.getParameter("success") != null;
    boolean exportToFile = ParamUtils.getBooleanParameter(request, "exporttofile", true);
    
    ImportExportPlugin plugin = (ImportExportPlugin) admin.getXMPPServer().getPluginManager().getPlugin("userimportexport");

    String exportText = "";
    
    Map errors = new HashMap();
    if (exportUsers) {
        if (exportToFile) {
			String file = ParamUtils.getParameter(request, "exportFile");
			if ((file == null)  || (file.length() <= 0)) {
				errors.put("missingFile","missingFile");	        
	        }
	        else {
		        try {
		        	//todo this could take some, redirect to a progress page?
					if (plugin.exportUsersToFile(file)) {
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
        else {
            try {
        		exportText = plugin.exportUsersToString();
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
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "../../index.jsp"));
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

<% } else if (ParamUtils.getBooleanParameter(request, "success")) { %>

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

<fieldset>
    <legend>Export Options</legend>
	<div>
	
	<table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            	<input type="radio" name="exporttofile" value="true" <%= exportToFile ? "checked" : "" %> id="rb01">
            </td>
            <td width="99%">
                <label for="rb01"><b>To File</b></label> - Save user data to the specified file location.
            </td>
            <tr>
	            <td width="1%">&nbsp;</td>
		        <td width="99%">
		        	<%= plugin.exportDirectory() %>
		        </td>
	        </tr>
	        <tr>
		        <td width="1%">&nbsp;</td>
		        <td width="99%">Export File Name:&nbsp;<input type="text" size="30" maxlength="150" name="exportFile"></td>
		    </tr>
        </tr>
        <tr>
            <td width="1%">
            	<input type="radio" name="exporttofile" value="false" <%= !exportToFile ? "checked" : "" %> id="rb02">
            </td>
            <td width="99%">
                <label for="rb02"><b>To Screen</b></label> - Display user data in the text area below.
            </td>            
        </tr>
        <tr>
        	<td width="1%">&nbsp;</td>
	        <td width="99%">
	        	<textarea cols="80" rows="20" wrap=off><%=exportText %></textarea>
	        </td>
        </tr>
    </tbody>
    </table>
	</div>
</fieldset>
<br><br>

<input type="submit" value="Export">
</form>

<jsp:include page="bottom.jsp" flush="true" />