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
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Private Data";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "private-data-settings.jsp"));
    pageinfo.setPageID("server-data-settings");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean privateEnabled = ParamUtils.getBooleanParameter(request,"privateEnabled");

    // Get an audit manager:
    PrivateStorage privateStorage = admin.getPrivateStore();

    Map errors = new HashMap();
    if (update) {
      privateStorage.setEnabled(privateEnabled);
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%
    
    }

    // Set page vars
    if (errors.size() == 0) {
        privateEnabled = privateStorage.isEnabled();
    }
%>

<p>
Private data storage allows XMPP clients to store settings, bookmarks, etc. on the server. Users
can log into their account and their settings will follow them around (as opposed to having the
clients store the settings on the local computer where their settings will not follow them). You
may enable or disable this feature.
</p>

<form action="private-data-settings.jsp">

<fieldset>
    <legend>Set Private Data Policy</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="privateEnabled" value="true" id="rb01"
                 <%= (privateEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b>Enable Private Data Storage</b> - allow clients to store information on the server.
                </label>
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="privateEnabled" value="false" id="rb02"
                 <%= (!privateEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b>Disable Private Data Storage</b> - do not allow server-side storage.
                </label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
