<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.user.*,
                 org.jivesoftware.messenger.*,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map"
%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="userData" class="org.jivesoftware.messenger.user.spi.UserPrivateData" />

<%  // Get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);
    String name = ParamUtils.getParameter(request,"name");
    String email = ParamUtils.getParameter(request,"email");
    boolean nameVisible = ParamUtils.getBooleanParameter(request,"nameVisible");
    boolean emailVisible = ParamUtils.getBooleanParameter(request,"emailVisible");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-properties.jsp?userID=" + userID);
        return;
    }

    // Load the user object
    User user = admin.getUserManager().getUser(userID);
  
    // Get a private data manager //
    final PrivateStore privateStore = admin.getPrivateStore();
    userData.setState( user.getID(), privateStore );

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
            user.getInfo().setNameVisible(nameVisible);
            user.getInfo().setEmailVisible(emailVisible);
            user.saveInfo();

            // Changes good, so redirect
            response.sendRedirect("user-edit-form.jsp?success=true&userID=" + userID);
            return;
        }
    }
%>




<c:set var="sbar" value="users" scope="page" />



<!-- Define BreadCrumbs -->
<c:set var="title" value="Edit User Properties"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />  
<c:set target="${breadcrumbs}" property="User Summary" value="user-summary.jsp" />
<c:set target="${breadcrumbs}" property="User Properties" value="user-properties.jsp?userID=${param.userID}" />
<c:set var="tab" value="edit" />
<jsp:include page="top.jsp" flush="true" />
<%@ include file="user-tabs.jsp" %>

<br>
<%  if (success) { %>

    <p class="jive-success-text">
    User edited successfully.
    </p>

<%  } %>

<form action="user-edit-form.jsp">
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center">Edit User Properties for <%= user.getUsername() %></td></tr>
<tr><td class="text" colspan="2">
Use the form below to edit user properties.
</td></tr>

<input type="hidden" name="userID" value="<%= userID %>">
<input type="hidden" name="save" value="true">


<tr class="jive-even">
    <td class="jive-label">
        User ID:
    </td>
    <td>
        <%= user.getID() %>
    </td>
</tr>
<tr class="jive-odd">
    <td>
        Username:
    </td>
    <td>
        <%= user.getUsername() %>
    </td>
</tr>
<tr class="jive-odd">
    <td class="jive-label">
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
<tr class="jive-even">
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
<tr class="jive-odd" valign="top">
    <td class="jive-label">
        Privacy:
    </td>
    <td>
        <table style="border-width:0px;" cellpadding="2" cellspacing="0" border="0" width="100%">
        <tr>
            <td width="1%" nowrap>
                Show Name:
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="nameVisible" value="true" <%= ((user.getInfo().isNameVisible()) ? "checked" : "") %> id="nv01">
                <label for="nv01">Yes</label> &nbsp;
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="nameVisible" value="false" <%= ((!user.getInfo().isNameVisible()) ? "checked" : "") %> id="nv02">
                <label for="nv02">No</label> &nbsp;
            </td>
            <td width="97%">
                &nbsp;
            </td>
        </tr>
        <tr>
            <td width="1%" nowrap>
                Show Email:
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="emailVisible" value="true" <%= ((user.getInfo().isEmailVisible()) ? "checked" : "") %> id="ev01">
                <label for="ev01">Yes</label> &nbsp;
            </td>
            <td width="1%" nowrap>
                <input type="radio" name="emailVisible" value="false" <%= ((!user.getInfo().isEmailVisible()) ? "checked" : "") %> id="ev02">
                <label for="ev02">No</label> &nbsp;
            </td>
            <td width="97%">
                &nbsp;
            </td>
        </td>
        </table>
    </td>
</tr>
</table>
<br>

<input type="submit" value="Save User Properties">
<input type="submit" name="cancel" value="Cancel">

</form>

<%@ include file="footer.jsp" %>