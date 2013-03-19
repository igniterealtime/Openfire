<%@ page import="java.io.IOException,
                 java.util.*,
                 org.jivesoftware.openfire.plugin.ImportExportPlugin,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ParamUtils"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean exportUsers = request.getParameter("exportUsers") != null;
    boolean success = request.getParameter("success") != null;
    boolean exportToFile = ParamUtils.getBooleanParameter(request, "exporttofile", true);
    
    ImportExportPlugin plugin = (ImportExportPlugin)XMPPServer.getInstance().getPluginManager(
            ).getPlugin("userimportexport");

    String exportText = "";
    
    Map<String, String> errors = new HashMap<String, String>();
    if (exportUsers) {
        if (exportToFile) {
            String file = ParamUtils.getParameter(request, "exportFile");
            if ((file == null) || (file.length() <= 0)) {
                errors.put("missingFile","missingFile");
            }
            else {
                response.sendRedirect("export-file.jsp?fileName="+file);
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

<html>
    <head>
        <title>Export User Data</title>
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
            <% if (errors.containsKey("missingFile")) { %>
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

<div class="jive-contentBoxHeader">Export Options</div>
<div class="jive-contentBox">
    <p>
    Select the radio button next to the desired export option and then click on the Export button.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%"><input type="radio" name="exporttofile" value="true" <%= exportToFile ? "checked" : "" %> id="rb01"></td>
            <td width="99%"><label for="rb01"><b>To File</b></label> - Save user data to the specified file location.</td>
        </tr>
        <tr>
            <td width="1%">&nbsp;</td>
            <td width="99%">Export File Name:&nbsp;<input type="text" size="30" maxlength="150" name="exportFile"></td>
        </tr>
        <tr>
            <td width="1%"><input type="radio" name="exporttofile" value="false" <%= !exportToFile ? "checked" : "" %> id="rb02"></td>
            <td width="99%"><label for="rb02"><b>To Screen</b></label> - Display user data in the text area below.</td>            
        </tr>
        <tr>
            <td width="1%">&nbsp;</td>
            <td width="99%"><textarea cols="80" rows="20" wrap=off><%=exportText %></textarea></td>
        </tr>
    </tbody>
    </table>
</div>

<input type="submit" value="Export">
</form>

</body>
</html>