<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.HashMap,
                 java.util.Map" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="User Search"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="user-search.jsp" />
<c:set var="sbar" value="users" />
<jsp:include page="top.jsp" flush="true" />

<% 

    // Get parameters
    boolean search = ParamUtils.getBooleanParameter(request,"search");
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);
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
            user = admin.getUserManager().getUser(userID);
        }
        catch (UserNotFoundException e1) {
            errors.put("userID","userID");
            try {
                user = admin.getUserManager().getUser(username);
            }
            catch (Exception e2) {
                errors.put("username","username");
            }
        }
        if (user != null) {
            // found the user, so redirect to the user properties page:
            response.sendRedirect("user-properties.jsp?userID=" + user.getID());
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
<tr class="tableHeader"><td colspan="2" align="left">Search For User</td></tr>
<tr><td class="text" colspan="2">
Use the form below to search for users in the system. Currently, searches are keyed off userID
or username.



<input type="hidden" name="search" value="true">

<tr class="jive-even">
    <td>
        User ID:
    </td>
    <td>
        <input type="text" name="userID" value="<%= ((userID!=-1L) ? ""+userID : "") %>" size="5" maxlength="10">
    </td>
</tr>
<tr class="">
    <td align="center">
        or
    </td>
    <td>
        &nbsp;
    </td>
</tr>
<tr class="jive-even">
    <td class="jive-label">
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
