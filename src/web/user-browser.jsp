<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 java.util.Iterator"
    errorPage="error.jsp"
%>

<%@ include file="global.jsp" %>

<%  // Check to see if the server is down.
    if (xmppServer == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",10);
    String formName = ParamUtils.getParameter(request,"formName");
    String elName = ParamUtils.getParameter(request,"elName");

    String panel = ParamUtils.getParameter(request,"panel");
    if (panel == null) {
        panel = "frameset";
    }
%>

<%  if ("frameset".equals(panel)) { %>

    <html>
    <title>User Browser</title>
    <script language="JavaScript" type="text/javascript">
    var users = new Array();
    function getUserListDisplay() {
        var display = "";
        var sep = ", ";
        for (var i=0; i<users.length; i++) {
            if ((i+1) == users.length) {
                sep = "";
            }
            display += (users[i] + sep);
        }
        return display;
    }
    function printUsers(theForm) {
        theForm.users.value = getUserListDisplay();
    }
    function addUser(theForm, username) {
        users[users.length] = username;
        printUsers(theForm);
    }
    function closeWin() {
        window.close();
    }
    function done() {
        closeWin();
    }
    </script>
    <frameset rows="*,105">
        <frame name="main" src="user-browser.jsp?panel=main"
                marginwidth="5" marginheight="5" scrolling="auto" frameborder="0">
        <frame name="bottom" src="user-browser.jsp?panel=bottom&formName=<%= formName %>&elName=<%= elName %>"
                marginwidth="5" marginheight="5" scrolling="no" frameborder="0">
    </frameset>
    </html>

<%  } else if ("bottom".equals(panel)) { %>

    <%@ include file="header.jsp" %>

    <style type="text/css">
    .mybutton {
        width : 100%;
    }
    </style>

    <form name="f" onsubmit="return false;">

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr>
        <td width="99%">

            <textarea rows="4" cols="40" style="width:100%;" name="users" wrap="virtual"></textarea>

        </td>
        <td width="1%" nowrap>

            <table cellpadding="0" cellspacing="0" border="0" width="75">
            <tr>
                <td>
                    <input type="submit" name="" value="Done" class="mybutton"
<%--                     onclick="alert(parent.opener.document.f.action);parent.done();return false;"--%>
                     onclick="if(parent.getUserListDisplay()!=''){parent.opener.document.<%= formName %>.<%= elName %>.value=parent.getUserListDisplay();}parent.done();return false;">
                </td>
            </tr>
            <tr>
                <td>
                    <input type="submit" name="" value="Cancel" class="mybutton"
                     onclick="parent.closeWin();return false;">
                </td>
            </tr>
            </table>

        </td>
    </tr>
    </table>

    </form>

    <%@ include file="footer.jsp" %>

<%  } else if ("main".equals(panel)) { %>

    <%  // Get the user manager
        int userCount = userManager.getUserCount();

        // paginator vars
        int numPages = (int)Math.ceil((double)userCount/(double)range);
        int curPage = (start/range) + 1;
    %>

    <%@ include file="header.jsp" %>

    <%  // Title of this page and breadcrumbs
        String title = "User Browser";
        String[][] breadcrumbs = {
            { "Home", "main.jsp" },
            { title, "user-browser.jsp" }
        };
    %>
    <%@ include file="title-browser.jspf" %>

    <p>
    Total Users: <%= userManager.getUserCount() %>,
    <%  if (numPages > 1) { %>

        Showing <%= (start+1) %>-<%= (start+range) %>,

    <%  } %>
    Sorted by User ID. Click "Add User" to add a user to the list box below. When you're done
    click "Done" or "Cancel".
    </p>

    <%  if (numPages > 1) { %>

        <p>
        Pages:
        [
        <%  for (int i=0; i<numPages; i++) {
                String sep = ((i+1)<numPages) ? " " : "";
                boolean isCurrent = (i+1) == curPage;
        %>
            <a href="user-browser.jsp?panel=main&start=<%= (i*range) %>"
             class="<%= ((isCurrent) ? "jive-current" : "") %>"
             ><%= (i+1) %></a><%= sep %>

        <%  } %>
        ]
        </p>

    <%  } %>

    <table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">
    <tr class="tableHeaderBlue">
        <th>&nbsp;</th>
        <th>Username</th>
        <th>Name</th>
        <th align="center">Add</th>
    </tr>
    <%  // Print the list of users
        Iterator users = userManager.users(start, range);
        if (!users.hasNext()) {
    %>
        <tr>
            <td align="center" colspan="4">
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
            <td width="60%">
                <%= user.getUsername() %>
            </td>
            <td width="50%">
                <%= user.getInfo().getName() %>
            </td>
            <td width="1%" align="center">
                <input type="submit" name="" value="Add User" class="jive-sm-button"
                 onclick="parent.addUser(parent.frames['bottom'].document.f,'<%= user.getUsername() %>');">
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
            <a href="user-browser.jsp?panel=main&start=<%= (i*range) %>"
             class="<%= ((isCurrent) ? "jive-current" : "") %>"
             ><%= (i+1) %></a><%= sep %>

        <%  } %>
        ]
        </p>

    <%  } %>

    <%@ include file="footer.jsp" %>

<%  } %>
