<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.user.UserManager,
                 java.text.DateFormat,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.PresenceManager"
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "User Summary";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-summary.jsp"));
    pageinfo.setPageID("user-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />




<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);

    // Get the user manager
    int userCount = webManager.getUserManager().getUserCount();

    // Get the presence manager
    PresenceManager presenceManager = (PresenceManager)webManager.getServiceLookup().lookup(PresenceManager.class);

    // paginator vars
    int numPages = (int)Math.ceil((double)userCount/(double)range);
    int curPage = (start/range) + 1;

    // Formatter for dates
    DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>



<p>
Below is a list of users in the system.
</p>

<p>
Total Users: <%= webManager.getUserManager().getUserCount() %>,
<%  if (numPages > 1) { %>

    Showing <%= (start+1) %>-<%= (start+range) %>,

<%  } %>
Sorted by User ID
</p>

<%  if (numPages > 1) { %>

    <p>
    Pages:
    [
    <%  for (int i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<table  cellpadding="3" cellspacing="0" border="0" width="600">
<tr class="tableHeader"><td colspan="7" align="left">List Of Users</td></tr>
</table>
<table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="600">
<tr >
    <th>&nbsp;</th>
    <th>Online</th>
    <th>Username</th>
    <th>Name</th>
    <th>Created</th>
    <th>Edit</th>
    <th>Delete</th>
</tr>
<%  // Print the list of users
    Iterator users = webManager.getUserManager().users(start, range);
    if (!users.hasNext()) {
%>
    <tr>
        <td align="center" colspan="7">
            No users in the system.
        </td>
    </tr>

<%
    }
    int i = start;
    while (users.hasNext()) {
        User user = (User)users.next();
        i++;
%>
    <tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
        <td width="1%">
            <%= i %>
        </td>
        <td width="1%" align="center">
            <%  if (presenceManager.isAvailable(user)) { %>

                <img src="images/online.gif" width="13" height="17" border="0">

            <%  } else { %>

                <img src="images/offline.gif" width="8" height="17" border="0">

            <%  } %>
        </td>
        <td width="30%">
            <a href="user-properties.jsp?username=<%= user.getUsername() %>"><%= user.getUsername() %></a>
        </td>
        <td width="40%">
            <%= user.getInfo().getName() %>
        </td>
        <td width="26%">
            <%= dateFormatter.format(user.getInfo().getCreationDate()) %>
        </td>
        <td width="1%" align="center">
            <a href="user-edit-form.jsp?username=<%= user.getUsername() %>"
             title="Click to edit..."
             ><img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
        </td>
        <td width="1%" align="center">
            <a href="user-delete.jsp?username=<%= user.getUsername() %>"
             title="Click to delete..."
             ><img src="images/button_delete.gif" width="17" height="17" border="0"></a>
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
    <%  for (i=0; i<numPages; i++) {
            String sep = ((i+1)<numPages) ? " " : "";
            boolean isCurrent = (i+1) == curPage;
    %>
        <a href="user-summary.jsp?start=<%= (i*range) %>"
         class="<%= ((isCurrent) ? "jive-current" : "") %>"
         ><%= (i+1) %></a><%= sep %>

    <%  } %>
    ]
    </p>

<%  } %>

<jsp:include page="bottom.jsp" flush="true" />
