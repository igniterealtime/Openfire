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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.messenger.*,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 java.util.HashMap,
                 java.util.Map"
%>

<%@ taglib uri="core" prefix="c"%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="userData" class="org.jivesoftware.messenger.user.spi.UserPrivateData" />

<%  // Get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    String username = ParamUtils.getParameter(request,"username");
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-properties.jsp?username=" + username);
        return;
    }

    // Load the user object
    User user = webManager.getUserManager().getUser(username);
  
    // Get a private data manager //
    final PrivateStore privateStore = webManager.getPrivateStore();
    userData.setState( user.getUsername(), privateStore );

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        // do validation
        if (name == null) {
            errors.put("name","name");
        }
        if (email == null) {
            errors.put("email","email");
        }
        if (errors.size() == 0) {
            user.getInfo().setEmail(email);
            user.getInfo().setName(name);
            user.saveInfo();

            // Changes good, so redirect
            response.sendRedirect("user-edit-form.jsp?success=true&username=" + username);
            return;
        }
    }
%>




<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Edit User";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-summary.jsp"));
    pageinfo.setSubPageID("user-edit");
    pageinfo.setExtraParams("username="+username);
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />
<c:set var="tab" value="edit" />
<%@ include file="user-tabs.jsp" %>

<%  if (success) { %>

    <p class="jive-success-text">
    User edited successfully.
    </p>

<%  } %>


<table cellpadding="3" cellspacing="1" border="0" width="600">
<form action="user-edit-form.jsp">
<tr><td colspan="2">
Use the form below to edit user properties.
</td></tr>
</table>

<table  cellpadding="3" cellspacing="1" border="0">

<input type="hidden" name="username" value="<%= username %>">
<input type="hidden" name="save" value="true">


<tr>
    <td>
        Username:
    </td>
    <td>
        <%= user.getUsername() %>
    </td>
</tr>
<tr>
    <td>
        Name:
    </td>
    <td>
        <input type="text" size="30" maxlength="150" name="name"
         value="<%= ((user.getInfo().getName()!=null) ? user.getInfo().getName() : "") %>">

        <%  if (errors.get("name") != null) { %>

            <span class="jive-error-text">
            Please enter a valid name.
            </span>

        <%  } %>
    </td>
</tr>
<tr>
    <td>
        Email:
    </td>
    <td>
        <input type="text" size="30" maxlength="150" name="email"
         value="<%= ((user.getInfo().getEmail()!=null) ? user.getInfo().getEmail() : "") %>">

        <%  if (errors.get("email") != null) { %>

            <span class="jive-error-text">
            Please enter a valid email address.
            </span>

        <%  } %>
    </td>
</tr>
</table>
<br>

<input type="submit" value="Save User Properties">
<input type="submit" name="cancel" value="Cancel">

</form>

<%@ include file="footer.jsp" %>