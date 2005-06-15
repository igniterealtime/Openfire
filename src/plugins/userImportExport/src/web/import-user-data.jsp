<%@ page import="java.io.IOException,
				 java.net.MalformedURLException,
				 java.util.*,
				 org.dom4j.DocumentException,
				 org.jivesoftware.admin.AdminPageBean,
				 org.jivesoftware.messenger.plugin.ImportExportPlugin,
				 org.jivesoftware.messenger.XMPPServer,
				 org.jivesoftware.util.ParamUtils"
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out); 

    boolean importUsers = request.getParameter("importUsers") != null;
    boolean success = request.getParameter("success") != null;
    
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
    List duplicateUsers = new ArrayList();
    
    Map errors = new HashMap();
    if (importUsers) {
		String file = ParamUtils.getParameter(request, "importFile");
		if ((file == null)  || (file.length() <= 0)) {
			errors.put("badFile", "badFile");
        }
        else {
			try {
				//todo this could take some, redirect to a progress page?				
				if (plugin.validateImportFile(file)) {
					duplicateUsers.addAll(plugin.importUserData(file));	
					if (duplicateUsers.size() == 0) {
						response.sendRedirect("import-user-data.jsp?success=true");
						return;
					}
					
					errors.put("userAlreadyExists", "userAlreadyExists");		       		
	       		}
	       		else {
	       			errors.put("invalidUserFile", "invalidUserFile"); 
	       		}
				
			}
			catch (MalformedURLException e) {
				errors.put("MalformedURLException", "MalformedURLException"); 
			}
			catch (DocumentException e) {
				errors.put("DocumentException", "DocumentException"); 
			}
		}
    }    
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Import User Data";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "../../index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "import-user-data.jsp"));
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
			<% if (errors.containsKey("MalformedURLException") || errors.containsKey("badFile")) { %>
				  Missing or bad file name.
	        <% } else if (errors.containsKey("DocumentException")) { %>
				  Import failed.
	        <% } else if (errors.containsKey("invalidUserFile")) { %>
	        	  The import file does not match the user schema.
	        <% } else if (errors.containsKey("userAlreadyExists")) { %>
	        	  The following users are already exist in the system and were not loaded:<br>
	        <%	        	  
				  Iterator iter = duplicateUsers.iterator();	        	  
	        	  while (iter.hasNext()) {
					String username = (String) iter.next();
	        	  	%><%= username %><%
	        	  	if (iter.hasNext()) {
	        	  		%>,&nbsp;<%
	        	  	} else {
	        	  		%>.<%
	        	  	}
	        	  }
	           } %>
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
        	<td class="jive-icon-label">All users added successfully.</td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<% } %>


<form action="import-user-data.jsp?importUsers" method="post">

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
	<thead>
	    <tr>
	        <th>Import Properties</th>
	    </tr>
	</thead>
    <tr class="jive-even">
        <td style="border-right:1px #ccc solid;">Import Location:</td>
    </tr>
    <tr class="jive-odd">
        <td style="border-right:1px #ccc solid;"><%= plugin.exportDirectory() %></td>     
    </tr>
    <tr class="jive-even">
        <td style="border-right:1px #ccc solid;">Import File Name:</td>
    </tr>
    <tr class="jive-odd">
        <td style="border-right:1px #ccc solid;"><input type="text" size="30" maxlength="150" name="importFile"></td>        
    </tr>
</table>
</div>

<br><br>

<input type="submit" value="Import">
</form>

<jsp:include page="bottom.jsp" flush="true" />