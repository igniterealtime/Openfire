<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.XMPPServerInfo"
%>
<%
   // Handle a cancel
    if (request.getParameter("cancel") != null) {
      response.sendRedirect("server-props.jsp");
      return;
    }
%>

<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="Edit Server Properties"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="Server Properties" value="server-props.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="server-props-edit-form.jsp" />
<%@ include file="top.jsp" %>



<%  // Get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = false;
    String name = ParamUtils.getParameter(request,"servername");
    String muc = ParamUtils.getParameter(request,"mucname");

    

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        // do validation
        if (name == null) {
            errors.put("servername","servername");
        }
        if (muc == null || muc.indexOf('.') >= 0) {
            errors.put("mucname","mucname");
        }
        if (errors.size() == 0) {
            admin.getMultiUserChatServer().setServiceName(muc);
            admin.getXMPPServer().getServerInfo().setName(name);
            success = true;
        }
    }
    else {
        name = admin.getServerInfo().getName() == null
                ? "" : admin.getServerInfo().getName();
        muc = admin.getMultiUserChatServer().getServiceName() == null
                ? "" : admin.getMultiUserChatServer().getServiceName();
    }
%>


<br>

<%  if (success) { %>

    <p class="jive-success-text">
    Server properties edited successfully. You must restart the server in order for
    the changes to take effect (see <a href="server-status.jsp">Server Status</a>).
    </p>

<%  } %>

<p>
Use the form below to edit server properties.
</p>

<form action="server-props-edit-form.jsp">
<input type="hidden" name="save" value="true">

<div class="jive-table">
<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr>
    <td class="jive-label">
        Server name:
    </td>
    <td>
    <input type="text" size="30" maxlength="150" name="servername"
     value="<%= name %>">

    <%  if (errors.get("servername") != null) { %>

        <span class="jive-error-text">
        Please enter a valid name.
        </span>

    <%  } %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Multi User Chat server name:
    </td>
    <td>
    <input type="text" size="30" maxlength="150" name="mucname"
     value="<%= muc %>">

    <%  if (errors.get("mucname") != null) { %>

        <span class="jive-error-text">
        Please enter a valid name.
        </span>

    <%  } %>
    </td>
</tr>
</table>
</div>

<br>

<input type="submit" value="Save Server Properties">
<input type="submit" name="cancel" value="Cancel">

</form>

<%@ include file="bottom.jsp" %>