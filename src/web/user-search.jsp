<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.HashMap,
                 org.jivesoftware.admin.*,
                 java.util.Map" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "User Search";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-search.jsp"));
    pageinfo.setPageID("user-search");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<% 

    // Get parameters
    boolean search = ParamUtils.getBooleanParameter(request,"search");
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a search execute:
    Map errors = new HashMap();
    if (search) {
        User user = null;
        try {
            user = admin.getUserManager().getUser(username);
        }
        catch (Exception e2) {
            errors.put("username","username");
        }
        if (user != null) {
            // found the user, so redirect to the user properties page:
            response.sendRedirect("user-properties.jsp?username=" + user.getUsername());
            return;
        }
    }
%>
<%  if (errors.size() > 0) { %>

    <p class="jive-error-text">
    User not found. Please try a different search.
    </p>

<%  } %>
<form name="f" action="user-search.jsp">
<table cellpadding="3" cellspacing="1" border="0" width="600">
<tr><td class="text" colspan="2">
Use the form below to search for users in the system.

<input type="hidden" name="search" value="true">


<tr class="jive-even">
    <td width="1%">
        Username:
    </td>
    <td>
        <input type="text" name="username" value="<%= ((username!=null) ? username : "") %>" size="30" maxlength="75">
    </td>
</tr>
</table>

<br>

<input type="submit" name="search" value="Search!">
<input type="submit" name="cancel" value="Cancel">

</form>

<script language="JavaScript" type="text/javascript">
document.f.username.focus();
</script>

<jsp:include page="bottom.jsp" flush="true" />
