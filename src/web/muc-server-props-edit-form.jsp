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
                 org.jivesoftware.messenger.XMPPServerInfo,
                 org.jivesoftware.messenger.muc.MultiUserChatServer,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.JiveGlobals"
%>
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

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Edit MultiUser Chat Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-server-props-edit-form.jsp"));
    pageinfo.setPageID("muc-server-props");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />


<%  // Get parameters
    boolean save = ParamUtils.getBooleanParameter(request,"save");
    boolean success = false;
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
            success = true;
        }
           name = admin.getServerInfo().getName() == null ? "" : admin.getServerInfo().getName();
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
%>


<br>

<%  if (success) { %>

    <p class="jive-success-text">
    Server properties edited successfully. You must restart the server in order for
    the changes to take effect (see <a href="server-status.jsp">Server Status</a>).
    </p>

<%  } %>

<p>
Use the form below to edit Multi-User Chat server properties.
</p>

<form action="muc-server-props-edit-form.jsp">
<input type="hidden" name="save" value="true">

<div class="jive-table">
<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr>
    <td class="jive-label">
        Server name:
    </td>
    <td>
    <%= name %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Multi User Chat server name:
    </td>
    <td>
    <input type="text" size="30" maxlength="150" name="mucname"  value="<%= muc %>">.<%=name%>

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

<input type="submit" value="Save Properties">
<input type="submit" name="cancel" value="Cancel">

</form>

<jsp:include page="bottom.jsp" flush="true" />