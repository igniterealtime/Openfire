<%@ taglib uri="core" prefix="c"%>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.MultiUserChatServer,
                 java.util.Iterator"
%>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Users allowed to create rooms";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "muc-create-permission.jsp"));
    pageinfo.setPageID("muc-perms");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />



<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);
    String userJID = ParamUtils.getParameter(request,"userJID");
    boolean add = ParamUtils.getBooleanParameter(request,"add");
    boolean delete = ParamUtils.getBooleanParameter(request,"delete");

	// Get muc server
    MultiUserChatServer mucServer = (MultiUserChatServer)admin.getServiceLookup().lookup(MultiUserChatServer.class);

    // Get the total allowed users count:
    int userCount = mucServer.getUsersAllowedToCreate().size();

    // Handle a save
    Map errors = new HashMap();
    if (add) {
        // do validation
        if (userJID == null || userJID.indexOf('@') == -1) {
            errors.put("userJID","userJID");
        }
        if (errors.size() == 0) {
            mucServer.addUserAllowedToCreate(userJID);
            response.sendRedirect("muc-create-permission.jsp?addsuccess=true");
            return;
        }
    }

    if (delete) {
        // Remove the user from the allowed list
        mucServer.removeUserAllowedToCreate(userJID);
        // done, return
        response.sendRedirect("muc-create-permission.jsp?deletesuccess=true");
        return;
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)userCount/(double)range);
    int curPage = (start/range) + 1;
%>

<table  cellpadding="3" cellspacing="1" border="0" width="600">
<tr><td colspan="8">
Below is a list of users allowed to create rooms. An empty list means that <b>anyone</b> can create
a room.</td></tr>
</table>


<table cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeader"><td colspan="8" align="left">User Summary</td></tr>

<tr><td colspan="8" class="text">
Total Users: <%= userCount %>.
<%  if (numPages > 1) { %>

    Showing <%= (start+1) %>-<%= (start+range) %>.

<%  } %>
</td></tr>

<%  if (numPages > 1) { %>

  <tr><td colspan="8" class="text">
    Pages:
    [
    <%  for (int pageIndex=0; pageIndex<numPages; pageIndex++) {
            String sep = ((pageIndex+1)<numPages) ? " " : "";
            boolean isCurrent = (pageIndex+1) == curPage;
    %>
        <a href="muc-create-permission.jsp?start=<%= (pageIndex*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (pageIndex+1) %></a><%= sep %>

    <%  } %>
    ]
  </td></tr>
<%  } %>
</table>


<%  if ("true".equals(request.getParameter("deletesuccess"))) { %>

    <p class="jive-success-text">
    User removed from the list successfully.
    </p>

<%  } %>

<%  if ("true".equals(request.getParameter("addsuccess"))) { %>

  <p class="jive-success-text">
    User added to the list successfully.
    </p>

<%  } %>

<table class="jive-table" cellpadding="3" cellspacing="1" border="0" width="400">
<tr class="tableHeaderBlue">
    <th nowrap align="left">User</th>
    <th>Delete</th>
</tr>
<%  // Print the list of users
    int max = start + range;
    max = (max > mucServer.getUsersAllowedToCreate().size() ? mucServer.getUsersAllowedToCreate().size() : max);
    Iterator users = mucServer.getUsersAllowedToCreate().subList(start, max).iterator();
    if (!users.hasNext()) {
%>
    <tr>
        <td align="center" colspan="2">
            <br>
            Anyone is allowed to create a room since the list is empty.
            <br><br>
        </td>
    </tr>

<%
    }
    int i = start;
    while (users.hasNext()) {
        userJID = (String)users.next();
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="90%" align="left">
            <%= userJID %>
        </td>
        <td width="10%" align="center">
            <a href="muc-create-permission.jsp?userJID=<%= userJID %>&delete=true"
             title="Click to delete..."
             onclick="return confirm('Are you sure you want to remove this user from the list?');"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
        </td>
    </tr>
<%
    }
%>
</table>
</div>
<%  if (numPages > 1) { %>

    <p>
    Pages:
    [
    <%  for (int pageIndex=0; pageIndex<numPages; pageIndex++) {
            String sep = ((pageIndex+1)<numPages) ? " " : "";
            boolean isCurrent = (pageIndex+1) == curPage;
    %>
        <a href="muc-create-permission.jsp?start=<%= (pageIndex*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (pageIndex+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<form action="muc-create-permission.jsp">
<input type="hidden" name="add" value="true">

<table cellpadding="3" cellspacing="1" border="0" width="400">
<tr>
    <td class="c1">
        Enter bare JID of user to add:
    </td>
    <td>
    <input type="text" size="30" maxlength="150" name="userJID">

    <%  if (errors.get("userJID") != null) { %>

        <span class="jive-error-text">
        Please enter a valid bare JID (e.g. johndoe@company.org).
        </span>

    <%  } %>
    </td>
</tr>
</table>

<br>

<input type="submit" value="Add">
<jsp:include page="bottom.jsp" flush="true" />
