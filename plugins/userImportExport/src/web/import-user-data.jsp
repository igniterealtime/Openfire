<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.net.MalformedURLException,
                 java.util.*,
                 org.dom4j.DocumentException,
                 org.apache.commons.fileupload.DiskFileUpload,
                 org.apache.commons.fileupload.FileItem,
                 org.jivesoftware.openfire.plugin.ImportExportPlugin,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ParamUtils"
%>

<%
    boolean importUsers = request.getParameter("importUsers") != null;
    boolean xep227Support = request.getParameter("xep227support") != null;
   
    ImportExportPlugin plugin = (ImportExportPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userimportexport");
    List<String> duplicateUsers = new ArrayList<String>();
   
    Map<String, String> errors = new HashMap<String, String>();
    if (importUsers) {
        DiskFileUpload dfu = new DiskFileUpload();

        String previousDomain = null;
        xep227Support = false;
        FileItem file = null; // the file.

        List<FileItem> fileItems = dfu.parseRequest(request);
        for ( FileItem fileItem : fileItems )
        {
            if ( fileItem.isFormField() )
            {
                switch ( fileItem.getFieldName() )
                {
                    case "xep227support":
                        xep227Support = Boolean.parseBoolean( fileItem.getString() );
                        break;
                    case "previousDomain":
                        previousDomain = fileItem.getString();
                        break;
                }
            }
            else
            {
                // the fileItem is an actual file.
                file = fileItem;
            }
        }
        
        if (plugin.validateImportFile(file, xep227Support)) {
            try {
                if (isEmpty(previousDomain)) {
                    duplicateUsers.addAll(plugin.importUserData(file, null, xep227Support));
                }
                else if (!isEmpty(previousDomain)) {
                    duplicateUsers.addAll(plugin.importUserData(file, previousDomain, xep227Support));
                }
                else {
                    errors.put("missingDomain", "missingDomain");
                }
              
                if (duplicateUsers.size() == 0) {
                    response.sendRedirect("import-user-data.jsp?success=true");
                    return;
                }
                
                errors.put("invalidUser", "invalidUser");
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

<html>
    <head>
        <title>Import User Data</title>
        <meta name="pageID" content="import-export-selection"/>
    </head>
    <body>

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
            <% } else if (errors.containsKey("invalidUser")) { %>
                
                <% if (plugin.isUserProviderReadOnly()) { %>
                   The following users did not exist in the system or have invalid username so their roster was not loaded:<br>
                <% } else { %>
                   The following users already exist in the system or have invalid username and were not loaded:<br>
               <% } %>
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
            <% if (plugin.isUserProviderReadOnly()) { %>
               <td class="jive-icon-label">User roster data added successfully.</td>
            <% } else { %>
               <td class="jive-icon-label">All users added successfully.</td>
            <% } %>
        </tr>
        </tbody>
    </table>
    </div>
    <br>

<% } %>

Use the form below to import a user data XML file.


<form action="import-user-data.jsp?importUsers" method="post" enctype="multipart/form-data">

<div class="jive-contentBoxHeader">Import</div>
<div class="jive-contentBox">
    <p>
    Choose a file to import:</p>
    <input type="file" name="thefile">

    <br><br><br>
   
    <p>
    <b>Optional</b> - Use the field below to replace the domain name of user roster entries with the current hostname.
    See the migration section of the <a href="../../plugin-admin.jsp?plugin=userimportexport&showReadme=true&decorator=none">readme</a> for details.
    </p>
    Replace Domain: <input type="text" size="20" maxlength="150" name="previousDomain" value=""/>
    
    <p>
    <input type="checkbox" name="xep227support" <%= xep227Support ? "checked" : "" %> value="true" id="rb03"/> <label for="rb03"><b>XEP-0227</b></label> - Import using format defined in <a href="http://www.xmpp.org/extensions/xep-0227.html" target="_blank">XEP-0227</a>.
    
</div>
<input type="submit" value="Import">

</form>

</body>
</html>

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
