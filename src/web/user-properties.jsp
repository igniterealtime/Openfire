<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.user.*,
                 java.text.DateFormat,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*"
%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="userData" class="org.jivesoftware.messenger.user.spi.UserPrivateData" />



<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    boolean email = request.getParameter("email") != null;
    boolean password = request.getParameter("password") != null;
    long userID = ParamUtils.getLongParameter(request,"userID",-1L);
    String username = ParamUtils.getParameter(request,"username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a delete
    if (delete) {
        response.sendRedirect("user-delete.jsp?userID=" + userID);
        return;
    }

    // Handle an email
    if (email) {
        response.sendRedirect("user-email.jsp?userID=" + userID);
        return;
    }

    // Handle an email
    if (password) {
        response.sendRedirect("user-password.jsp?userID=" + userID);
        return;
    }

    // Load the user object
    User user = null;
    try {
        user = admin.getUserManager().getUser(userID);
    }
    catch (UserNotFoundException unfe) {
        user = admin.getUserManager().getUser(username);
    }

  

    // Date formatter for dates
    DateFormat formatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
%>
<%
 // Get a private data manager //
  final PrivateStore privateStore = admin.getPrivateStore();
  userData.setState( user.getID(), privateStore );
  String nickname = userData.getProperty( "nickname" );
    if(nickname == null){
        nickname = "";
    }
%>
<c:set var="sbar" value="users" scope="page" />



<!-- Define BreadCrumbs -->
<c:set var="title" value="User Properties"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="User Summary" value="user-summary.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="user-properties.jsp?userID=${param.userID}" />
<c:set var="tab" value="props" />
<jsp:include page="top.jsp" flush="true" />

<%@ include file="user-tabs.jsp" %>
<br/>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center">User Properties For <%= user.getUsername() %></td></tr>
<tr><td class="text" colspan="2">
Below is a summary of user properties. Use the tabs above to do things like edit user properties,
send the user a message (if they're online) or delete the user.
</td></tr>
<tr class="jive-even">
    <td wrap width="1%">
        User ID:
    </td>
    <td>
        <%= user.getID() %>
    </td>
</tr>
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

            <%   if (user.getInfo().isNameVisible()) { %>

                <%= user.getInfo().getName() %>

            <%  } else { %>

                <i><%= user.getInfo().getName() %></i>

            <%  } %>

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

            <%  if (user.getInfo().isEmailVisible()) { %>

                <a href="mailto:<%= user.getInfo().getEmail() %>"><%= user.getInfo().getEmail() %></a>

            <%  } else { %>

                <i><a href="mailto:<%= user.getInfo().getEmail() %>"><%= user.getInfo().getEmail() %></a></i>

            <%  } %>

        <%  } %>
    </td>
</tr>
<tr class="jive-odd">
    <td wrap>
        Privacy:
    </td>
    <td>
        Show Name: <b><%= ((user.getInfo().isNameVisible()) ? "Yes" : "No") %></b>,
        Show Email: <b><%= ((user.getInfo().isEmailVisible()) ? "Yes" : "No") %></b>
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
