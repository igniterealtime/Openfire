<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 java.io.StringWriter,
                 java.io.StringWriter,
                 java.io.IOException,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 java.io.PrintStream,
                 org.dom4j.xpath.DefaultXPath,
                 org.dom4j.*,
                 org.jivesoftware.messenger.group.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<jsp:useBean id="errors" class="java.util.HashMap" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean create = request.getParameter("create") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String name = ParamUtils.getParameter(request,"name");
    String description = ParamUtils.getParameter(request,"description");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("group-summary.jsp");
        return;
    }

    // Handle a request to create a group:
    if (create) {
        // Validate
        if (name == null) {
            errors.put("name","");
        }

        // do a create if there were no errors
        if (errors.size() == 0) {
            try {
                Group newGroup = webManager.getGroupManager().createGroup(name);
                if (description != null) {
                    newGroup.setDescription(description);
                }

                // Successful, so redirect
                response.sendRedirect("group-properties.jsp?success=true&group=" + newGroup.getName());
                return;
            }
            catch (GroupAlreadyExistsException e) {
                e.printStackTrace();
                errors.put("groupAlreadyExists","");
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("general","");
                Log.error(e);
            }
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Create Group";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "group-create.jsp"));
    pageinfo.setPageID("group-create");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<c:set var="submit" value="${param.create}" />
<c:set var="errors" value="${errors}" />

<%  if (errors.get("general") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error creating the group. Please check your error logs.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<form name="f" action="group-create.jsp" method="post">
<tr><td class="text" colspan="2">
Use the form below to create a new group in the system.
</td></tr>

<tr class="jive-even">
    <td>
        Group: *
    </td>
    <td>
        <input type="text" name="name" size="30" maxlength="75"
         value="<%= ((name!=null) ? name : "") %>">

        <%  if (errors.get("name") != null) { %>

            <span class="jive-error-text">
            Invalid name.
            </span>

        <%  } else if (errors.get("groupAlreadyExists") != null) { %>

            <span class="jive-error-text">
            Group already exists - please choose a different name.
            </span>

        <%  } %>
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Description:
    </td>
    <td>
        <input type="text" name="description" size="30" maxlength="75"
         value="<%= ((description!=null) ? description : "") %>">

        <%  if (errors.get("description") != null) { %>

            <span class="jive-error-text">
            Invalid description.
            </span>

        <%  } %>
    </td>
</tr>
</table>
</div>

<p>
* Required fields
</p>

<input type="submit" name="create" value="Create Group">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.name.focus();

function checkFields() {
  
}
</script>

<jsp:include page="bottom.jsp" flush="true" />
