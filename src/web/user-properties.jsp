<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.text.DateFormat,
                 java.util.Iterator,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.*"
%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="userData" class="org.jivesoftware.messenger.user.spi.UserPrivateData" />



<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    boolean email = request.getParameter("email") != null;
    boolean password = request.getParameter("password") != null;
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a delete
    if (delete) {
        response.sendRedirect("user-delete.jsp?username=" + username);
        return;
    }

    // Handle an email
    if (email) {
        response.sendRedirect("user-email.jsp?username=" + username);
        return;
    }

    // Handle an email
    if (password) {
        response.sendRedirect("user-password.jsp?username=" + username);
        return;
    }

    // Load the user object
    User user = null;
    try {
        user = webManager.getUserManager().getUser(username);
    }
    catch (UserNotFoundException unfe) {
        user = webManager.getUserManager().getUser(username);
    }



    // Date formatter for dates
    DateFormat formatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>
<%
 // Get a private data manager //
  final PrivateStore privateStore = webManager.getPrivateStore();
  userData.setState( user.getUsername(), privateStore );
  String nickname = userData.getProperty( "nickname" );
    if(nickname == null){
        nickname = "";
    }
%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "User Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "user-properties.jsp"));
    pageinfo.setSubPageID("user-properties");
    pageinfo.setExtraParams("username="+username);
%>
<c:set var="tab" value="props" />
<%@ include file="top.jsp" %>
<jsp:include page="title.jsp" flush="true" />

<%@ include file="user-tabs.jsp" %>
<br/>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr><td class="text" colspan="2">
Below is a summary of user properties. Use the tabs above to do things like edit user properties,
send the user a message (if they're online) or delete the user.
</td></tr>
<tr class="jive-odd">
    <td wrap width="1%">
        Username:
    </td>
    <td>
        <%= user.getUsername() %>
    </td>
</tr>

<tr class="jive-even">
    <td wrap width="1%">
        Online Status:
    </td>
    <td>
        <%  if (admin.getPresenceManager().isAvailable(user)) {
                // Get the user's session (or the first one available if there are multiple)
                Iterator sessions = admin.getSessionManager().getSessions(user.getUsername());
                // Should be at least one session, so grab first one. Just need to pass in one
                // of the user's sessions to the session details page - that page will display
                // all that exist.
                Session sess = (Session)sessions.next();
        %>

            <img src="images/online.gif" width="13" height="17" border="0" hspace="3"
             title="User is online.">
            (Online - see <a href="session-details.jsp?jid=<%= sess.getAddress().toString() %>">user session(s)</a>.)

        <%  } else { %>

            <img src="images/offline.gif" width="8" height="17" border="0" hspace="3"
             title="User is offline.">
            (Offline)

        <%  } %>
    </td>
</tr>
<tr class="jive-odd">
    <td wrap width="1%">
        Name:
    </td>
    <td>
        <%  if (user.getInfo().getName() == null || "".equals(user.getInfo().getName())) { %>

            <span style="color:#999">
            <i>Not set.</i>
            </span>

        <%  } else { %>

                <i><%= user.getInfo().getName() %></i>

        <%  } %>
    </td>
</tr>
<tr class="jive-even">
    <td wrap width="1%">
        Email:
    </td>
    <td>
        <%  if (user.getInfo().getEmail() == null || "".equals(user.getInfo().getEmail())) { %>

            <span style="color:#999">
            <i>Not set.</i>
            </span>

        <%  } else { %>

                <i><a href="mailto:<%= user.getInfo().getEmail() %>"><%= user.getInfo().getEmail() %></a></i>

        <%  } %>
    </td>
</tr>
<tr class="jive-even">
    <td wrap width="1%">
        Registered:
    </td>
    <td>
        <%= formatter.format(user.getInfo().getCreationDate()) %>
    </td>
</tr>
</table>
</div>

<br>

<form action="user-summary.jsp">
<input type="submit" value="User Summary">
</form>

<%@ include file="footer.jsp" %>
