<%@ page import="java.net.MalformedURLException,
                 java.util.*,
                 org.dom4j.DocumentException,
                 org.apache.commons.fileupload.DiskFileUpload,
                 org.apache.commons.fileupload.FileItem,
                 org.jivesoftware.admin.AdminPageBean,
                 org.jivesoftware.messenger.plugin.ImportExportPlugin,
                 org.jivesoftware.util.ParamUtils"
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% 
    admin.init(request, response, session, application, out); 

    boolean importUsers = request.getParameter("importUsers") != null;
    boolean success = request.getParameter("success") != null;
   
    ImportExportPlugin plugin = (ImportExportPlugin) admin.getXMPPServer().getPluginManager().getPlugin("userimportexport");
    List<String> duplicateUsers = new ArrayList<String>();
   
    Map<String, String> errors = new HashMap<String, String>();
    if (importUsers) {
        DiskFileUpload dfu = new DiskFileUpload();
      
        List fileItems = dfu.parseRequest(request);
        Iterator i = fileItems.iterator();
        FileItem fi = (FileItem) i.next();
        FileItem pd = (FileItem) i.next();
        String previousDomain = pd.getString();
        
        if (plugin.validateImportFile(fi)) {
            try {
                if (isEmpty(previousDomain)) {
                    duplicateUsers.addAll(plugin.importUserData(fi, null));
                }
                else if (!isEmpty(previousDomain)) {
                    duplicateUsers.addAll(plugin.importUserData(fi, previousDomain));
                }
                else {
                    errors.put("missingDomain", "missingDomain");
                }
              
                if (duplicateUsers.size() == 0) {
                    response.sendRedirect("import-user-data.jsp?success=true");
                    return;
                }
      			
                errors.put("userAlreadyExists", "userAlreadyExists");
            }
            catch (MalformedURLException e) {
                errors.put("IOException", "IOException");
            }
            catch (DocumentException e) {
                errors.put("DocumentException", "DocumentException");
            }
        }
        else {
            errors.put("invalidUserFile", "invalidUserFile");
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
            <% if (errors.containsKey("missingDomain")) { %>
                You must supply both a existing and new domain name.
            <% } else if (errors.containsKey("IOException")) { %>
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

<% } else if (ParamUtils.getBooleanParameter(request, "success")) { %>

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

<form action="import-user-data.jsp?importUsers" method="post" enctype="multipart/form-data">

<fieldset>
    <legend>Import</legend>
    <div>
    <p>
    Use the Browse button to select the file that conatians the Jive Messenger user data to be imported, then click on the Import button.
    </p>
    <input type="file" name="thefile"><input type="submit" value="Import">
   
    <br><br><br>
   
    <p>
    <b>Optional</b> - Use the field below to replace the domain name of user roster entries with the current hostname.
    See the migration section of the <a href="../../plugin-admin.jsp?plugin=userimportexport&showReadme=true">readme</a> for details.
    </p>
    Existing Domain:<input type="text" size="20" maxlength="150" name="previousDomain" value=""/>
   
    </div>
</fieldset>

</form>

<jsp:include page="bottom.jsp" flush="true" />

<%! 
public boolean isEmpty(String s) {
    if (s == null) {
        return true;
    }
    
    if (s.trim().length() == 0) {
        return true;
    }
    
    return false;
}
%>
