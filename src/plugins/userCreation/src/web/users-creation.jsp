<%@page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.TaskEngine,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.plugin.UserCreationPlugin,
                 java.util.HashMap,
                 java.util.Map"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
    <head>
        <title>Quick Users Creation</title>
        <meta name="pageID" content="users-creation"/>
    </head>
    <body>

<%
    String prefix = ParamUtils.getParameter(request, "prefix");
    String from = ParamUtils.getParameter(request, "from");
    String total = ParamUtils.getParameter(request, "total");
    String usersPerRoster = ParamUtils.getParameter(request, "usersPerRoster");
    boolean generateMessages =  request.getParameter("messageGenerate") != null;
    
    Map<String, String> errors = new HashMap<String, String>();

    boolean running = false;

    if (prefix != null) {
        final String userPrefix = prefix;
        final int intFrom = Integer.parseInt(from);
        final int maxUsers = Integer.parseInt(total);
        final int usersRoster = Integer.parseInt(usersPerRoster) + 1;
        final boolean boolGenerateMessages = generateMessages;

        if (maxUsers % usersRoster != 0 || maxUsers <= usersRoster) {
            errors.put("arguments", ""); 
        }

        if (errors.isEmpty()) {
            final UserCreationPlugin plugin =
                    (UserCreationPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("usercreation");
            TaskEngine.getInstance().submit(new Runnable() {
                public void run() {
                    plugin.createUsers(userPrefix, intFrom, maxUsers);
                    plugin.populateRosters(userPrefix, intFrom, maxUsers, usersRoster);
                    plugin.createVCards(userPrefix, intFrom, maxUsers);
                    if (boolGenerateMessages) {
                        plugin.generateMessages();
                    }
                    
                }
            });
            running = true;
        }
    }
%>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0"/></td>
            <td class="jive-icon-label">

            <% if (errors.get("arguments") != null) { %>
                Number of users per roster should be greater than total number of users. Number of users per roster <b>plus one</b> should also be a multiple of total number of users. 
            <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
    <br>

<%  } else if (running) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="/images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Users being created in background and getting their rosters populated. Check the stdout for more information.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form name="f" action="users-creation.jsp">
    <fieldset>
        <legend>Creation Form</legend>
        <div>
        <table cellpadding="3" cellspacing="1" border="0" width="600">
        <tr class="c1">
            <td width="1%" colspan="2" nowrap>
                User prefix:
                &nbsp;<input type="text" name="prefix" value="<%=(prefix != null ? prefix : "user") %>" size="30" maxlength="75"/>
            </td>
        </tr>
        <tr class="c1">
            <td width="1%" colspan="2" nowrap>
                From index:
                &nbsp;<input type="text" name="from" value="<%=(from != null ? from : "0") %>" size="5" maxlength="15"/>
            </td>
        </tr>
        <tr class="c1">
            <td width="1%" colspan="2" nowrap>
                Total users:
                &nbsp;<input type="text" name="total" value="<%=(total != null ? total : "20") %>" size="5" maxlength="15"/>
            </td>
        </tr>
        <tr class="c1">
            <td width="1%" colspan="2" nowrap>
                Contacts in roster:
                &nbsp;<input type="text" name="usersPerRoster" value="<%=(usersPerRoster != null ? usersPerRoster : "9") %>" size="5" maxlength="15"/>
            </td>
        </tr>
        <tr>
            <td colspan="2" width="90%"><label class="jive-label" for="messageGenerate">Generate chat messages:</label><br>
            Generates dummy chat messages between users. Useful for testing message archiving.</td>
            <td><input type="checkbox" id="messageGenerate" name="messageGenerate" <%= "checked" %> /></td>
        </tr>	
        <tr class="c1">
            <td width="1%" colspan="2" nowrap>
                <input type="submit" name="Create"/>
            </td>
        </tr>
        </table>
        </div>
    </fieldset>
</form>

</body>
</html>
