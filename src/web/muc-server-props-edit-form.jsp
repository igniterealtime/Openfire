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
                 java.text.DateFormat,
                 org.jivesoftware.messenger.XMPPServerInfo,
                 org.jivesoftware.messenger.muc.MultiUserChatServer,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.JiveGlobals,
                 java.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<%
   // Handle a cancel
    if (request.getParameter("cancel") != null) {
      response.sendRedirect("muc-server-props-edit-form.jsp");
      return;
    }
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String name = ParamUtils.getParameter(request,"servername");
    String muc = ParamUtils.getParameter(request,"mucname");

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        // do validation
        if (muc == null  || muc.indexOf('.') >= 0) {
            errors.put("mucname","mucname");
        }
        if (errors.size() == 0) {
            admin.getMultiUserChatServer().setServiceName(muc);
            response.sendRedirect("muc-server-props-edit-form.jsp?success=true");
            return;
        }
    }
    else {
        name = admin.getServerInfo().getName() == null ? "" : admin.getServerInfo().getName();
        muc = admin.getMultiUserChatServer().getServiceName() == null  ? "" : admin.getMultiUserChatServer().getServiceName();
        // Remove the server address part from the MUC domain name.
        int index = muc.indexOf("." + name);
        if (index > 0) {
            muc = muc.substring(0, index);
        }
    }

    name = admin.getServerInfo().getName();
    if (errors.size() == 0) {
        muc = admin.getMultiUserChatServer().getServiceName();
        int pos = muc.lastIndexOf("." + name);
        muc = muc.substring(0, pos);
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Chat Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-server-props-edit-form.jsp"));
    pageinfo.setPageID("muc-server-props");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to edit group chat service settings. Note, any changes will require
a server restart.
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Server properties edited successfully. You must <b>restart</b> the server in order for
            the changes to take effect.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error setting the service name.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-server-props-edit-form.jsp?save" method="post">

<fieldset>
    <legend>Set Conflict Policy</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0">
    <tr>
        <td class="c1">
            Server name:
        </td>
        <td>
        <%= name %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            Group chat service name:
        </td>
        <td>
        <input type="text" size="30" maxlength="150" name="mucname"  value="<%= (muc != null ? muc : "") %>">.<%=name%>

        <%  if (errors.get("mucname") != null) { %>

            <span class="jive-error-text">
            <br>Please enter a valid name.
            </span>

        <%  } %>
        </td>
    </tr>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">

</form>

<jsp:include page="bottom.jsp" flush="true" />