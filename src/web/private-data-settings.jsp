<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*"
%>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Private Data";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "private-data-settings.jsp"));
    pageinfo.setPageID("server-data-settings");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />



<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean privateEnabled = ParamUtils.getBooleanParameter(request,"privateEnabled");

    // Get an audit manager:
    PrivateStore privateStore = admin.getPrivateStore();

    Map errors = new HashMap();
    if( update ) {
      privateStore.setEnabled(privateEnabled);
    %>
     <p class="jive-success-text">
    Settings updated.
    </p>
    <%
    
    }

    // Set page vars
    if (errors.size() == 0) {
        privateEnabled = privateStore.isEnabled();
    }
%>


<table cellpadding="3" cellspacing="1" border="0" width="600">
<form action="private-data-settings.jsp">
<tr><td colspan=2 class="text">
Private data storage allows XMPP clients to store settings, bookmarks, etc. on the server. Users
can log into their account and their settings will follow them around (as opposed to having the
clients store the settings on the local computer where their settings will not follow them). You
may enable or disable this feature.



<tr valign="top" class="">
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
</table>

<br>

<input type="submit" name="update" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
