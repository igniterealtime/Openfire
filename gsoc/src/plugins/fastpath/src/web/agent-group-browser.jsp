<%--
  -	$RCSfile$
  -	$Revision: 32691 $
  -	$Date: 2006-07-27 23:27:17 -0700 (Thu, 27 Jul 2006) $
--%>

<%@ page
import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.xmpp.workgroup.WorkgroupManager"%>
<%@ page import="org.jivesoftware.openfire.group.GroupManager"%>
<%@ page import="org.jivesoftware.openfire.group.Group"%>

<%
    GroupManager groupManager = GroupManager.getInstance();
%>

<% // Get parameters
    int              start = ParamUtils.getIntParameter(request, "start", 0);
    int              range = ParamUtils.getIntParameter(request, "range", 10);
    String           formName = ParamUtils.getParameter(request, "formName");
    String           elName = ParamUtils.getParameter(request, "elName");

    String           panel = ParamUtils.getParameter(request, "panel");
    if (panel == null) {
        panel = "frameset";
    }
%>

<%
    if ("frameset".equals(panel)) {
%>

        <html>
        <head>
            <title>Group Browser</title>

            <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1"/>
            <meta name="decorator" content="none"/>

            <link rel="stylesheet" type="text/css" href="/style/global.css">

            <script language="JavaScript" type="text/javascript">
                var users = new Array();

                function getUserListDisplay() {
                    var display = "";
                    var sep = ", ";

                    for (var i = 0; i < users.length; i++) {
                        if ((i + 1) == users.length) {
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
        </head>

        <frameset rows="*,105">
            <frame name="main"      src="agent-group-browser.jsp?panel=main" marginwidth="5" marginheight="5"
                   scrolling="auto" frameborder="0">
                <frame name="bottom"
                       src="agent-group-browser.jsp?panel=bottom&formName=<%= formName %>&elName=<%= elName %>"
                       marginwidth="5"
                       marginheight="5"
                       scrolling="no"
                       frameborder="0">
        </frameset>
        </html>

<%
    }
    else if ("bottom".equals(panel)) {
%>

        <html>
        <head>
            <title><fmt:message key="title" /> <fmt:message key="header.admin" /></title>
            <meta http-equiv="content-type" content="text/html; charset=">
            <meta name="decorator" content="none"/>

            <link rel="stylesheet" href="style/global.css" type="text/css">
        </head>

        <body>
        <style type="text/css">
            .mybutton
            {
             width: 100%;
            }
        </style>

        <form name="f" onsubmit="return false;">
            <table cellpadding="3" cellspacing="0" border="0" width="100%">
                <tr>
                    <td width="99%">
                        <textarea rows="4" cols="40" style="width:100%;" name="users" wrap="virtual">
                        </textarea>
                    </td>

                    <td width="1%" nowrap>
                        <table cellpadding="0" cellspacing="0" border="0" width="75">
                            <tr>
                                <td>
                                    <script language="javascript">
                                        var currentValue = parent.opener.document.<%= formName %>.<%= elName %>.value;

                                        if (currentValue.length > 0) {
                                            currentValue = "," + currentValue;
                                        }
                                    </script>

                                    <input type="submit"
                                           name=""
                                           value="Done"
                                           class="mybutton"
                                           onclick="if(parent.getUserListDisplay()!=''){parent.opener.document.<%= formName %>.<%= elName %>.value=parent.getUserListDisplay()+currentValue;}parent.done();return false;">
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

        </body>
    </html>
<%
    }
    else if ("main".equals(panel)) {
%>

<% // Get the user manager
        int userCount = groupManager.getGroupCount();

        // paginator vars
        int numPages = (int) Math.ceil((double) userCount / (double) range);
        int curPage = (start / range) + 1;
%>

        <html>
        <head>
            <title>Agent Browser</title>

            <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
            <meta name="decorator" content="none"/>

            <link rel="stylesheet" type="text/css" href="/style/global.css">
        </head>

        <body class="jive-body">
            <p>
                Total Groups: <%= groupManager.getGroupCount() %>,

<%
                if (numPages > 1) {
%>

                            Showing <%= (start + 1) %>-<%= (start + range) %>,

<%
                }
%>

                        Sorted by Group ID
            </p>

            <p>
                Viewing page <%= curPage %>
            </p>

            <p>
                Click "Add Group" to add a group to the list box below. When you are finished, click "Done".
            </p>

<%
            if (numPages > 1) {
%>

                    <p>
                    Pages: [

<%
                    for (int i = 0; i < numPages; i++) {
                        String sep = ((i + 1) < numPages) ? " " : "";
                        boolean isCurrent = (i + 1) == curPage;
%>

                            <a href="agent-group-browser.jsp?panel=main&start=<%= (i * range) %>"
                               class="<%= ((isCurrent) ? "jive-current" : "") %>"><%= (i + 1) %></a><%= sep %>

<%
                            }
%>

                            ]
                    </p>

<%
            }
%>

            <fieldset>
                <legend>
                    Possible Groups to Add
                </legend>

                <table class="jive-table" cellpadding="3" cellspacing="1" border="0" width="100%">
                    <th>
                        &nbsp;
                    </th>

                    <th>
                        Name/Description
                    </th>

                    <th align="center">
                        Add
                    </th>

<%
                        if (groupManager.getGroupCount() == 0) {
%>

                            <tr>
                                <td align="center" colspan="3">
                                    No groups in the system.
                                </td>
                            </tr>

<%
                        }
                        else{
%>

<%
                            // Print the list of users
                            int i = start;
                            for(Group group : groupManager.getGroups()){
                                i++;
%>

                                <tr class="jive-<%= (((i % 2) == 0) ? "even" : "odd") %>">
                                    <td width="1%">
                                        <%= i %>
                                    </td>

                                    <td>
                                        <%= group.getName() %>
                                    </td>

                                    <td width="1%" align="center">
                                        <input type="submit"
                                               name=""
                                               value="Add Group"
                                               class="jive-sm-button"
                                               onclick="parent.addUser(parent.frames['bottom'].document.f,'<%= group.getName() %>');">
                                    </td>
                                </tr>

<%
                            }
                        }
%>
                </table>
            </fieldset>

            </body>
        </html>
<%
    }
%>
