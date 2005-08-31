<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ page import="org.jivesoftware.util.ParamUtils,
                 java.util.Iterator,
                 org.jivesoftware.messenger.user.User"
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

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
<head>

    <title><fmt:message key="user.browser.title" /></title>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <link rel="stylesheet" type="text/css" href="/style/global.css">
</head>


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

    <jsp:include page="header.jsp" flush="true" />

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
                <script language="javascript">
                  var currentValue = parent.opener.document.<%= formName %>.<%= elName %>.value;
                  if(currentValue.length > 0){
                    currentValue = ","+currentValue;
                  }
                </script>
                    <input type="submit" name="" value="<fmt:message key="global.done" />" class="mybutton"
                     onclick="if(parent.getUserListDisplay()!=''){parent.opener.document.<%= formName %>.<%= elName %>.value=parent.getUserListDisplay()+currentValue;}parent.done();return false;">
                </td>
            </tr>
            <tr>
                <td>
                    <input type="submit" name="" value="<fmt:message key="global.cancel" />" class="mybutton"
                     onclick="parent.closeWin();return false;">
                </td>
            </tr>
            </table>

        </td>
    </tr>
    </table>

    </form>
<jsp:include page="footer.jsp" flush="true" />

<%  } else if ("main".equals(panel)) { %>

    <%  // Get the user manager
        int userCount = webManager.getUserManager().getUserCount();

        // paginator vars
        int numPages = (int)Math.ceil((double)userCount/(double)range);
        int curPage = (start/range) + 1;
    %>

    <html>
<head>

    <title><fmt:message key="user.browser.title" /></title>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <link rel="stylesheet" type="text/css" href="/style/global.css">
</head>
<body class="jive-body">

    <p>
    <fmt:message key="user.summary.total_user" />: <%= webManager.getUserManager().getUserCount() %>,
    <%  if (numPages > 1) { %>

        <fmt:message key="global.showing" /> <%= (start+1) %>-<%= (start+range) %>,

    <%  } %>
    <fmt:message key="user.summary.sorted" />.
    </p>

    <p>
    <fmt:message key="user.browser.viewing_page" /> <%= curPage %>
    </p>

    <p><fmt:message key="user.browser.info" />.
    </p>

    <%  if (numPages > 1) { %>

        <p>
        <fmt:message key="global.pages" />:
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
    <fieldset>
    <legend><fmt:message key="user.browser.legend" /></legend>
    <table class="jive-table" cellpadding="3" cellspacing="1" border="0" width="100%">

        <th>&nbsp;</th>
        <th><fmt:message key="user.browser.username" /></th>
        <th><fmt:message key="user.browser.name" /></th>
        <th align="center"><fmt:message key="global.add" /></th>

    <%  // Print the list of users
        Iterator users = webManager.getUserManager().getUsers(start, range).iterator();
        if (!users.hasNext()) {
    %>
        <tr>
            <td align="center" colspan="4">
                <fmt:message key="user.browser.no_users" />.
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
            <% String name = user.getName();
               if(name == null || name.trim().length() == 0){
                   name = "&nbsp;";
               }
           %>
                <%= name %>
            </td>
            <td width="1%" align="center">
                <input type="submit" name="" value="<fmt:message key="user.browser.add_user" />" class="jive-sm-button"
                 onclick="parent.addUser(parent.frames['bottom'].document.f,'<%= user.getUsername() %>');">
            </td>
        </tr>

    <%
        }
    %>
    </table>
    </fieldset>
    </div>

    <%  if (numPages > 1) { %>

        <p>
        <fmt:message key="global.pages" />:
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
<jsp:include page="footer.jsp" flush="true" />

<%  } %>
