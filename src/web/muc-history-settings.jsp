<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,                  
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.muc.HistoryStrategy,
                 org.jivesoftware.messenger.muc.MultiUserChatServer"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt" prefix="fmt"%>

<%!  // Global vars and methods:

    // Strategy definitions:
    static final int ALL = 1;
    static final int NONE = 2;
    static final int NUMBER = 3;
%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"/>
<%  admin.init(request, response, session, application, out ); %>

<%   // Get parameters:
    boolean update = request.getParameter("update") != null;
    int policy = ParamUtils.getIntParameter(request,"policy",-1);
    int numMessages = ParamUtils.getIntParameter(request,"numMessages",0);

	// Get muc history
    MultiUserChatServer mucServer = admin.getMultiUserChatServer();
    HistoryStrategy historyStrat = mucServer.getHistoryStrategy();

    Map errors = new HashMap();
    if (update) {
        if (policy != ALL && policy != NONE && policy != NUMBER) {
            errors.put("general", "Please choose a valid chat history policy.");
        }
        else {
            if (policy == NUMBER && numMessages <= 0) {
                errors.put("numMessages", "Please enter a valid number of messages.");
            }
        }
        if (errors.size() == 0) {
            if (policy == ALL) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.all);
            }
            else if (policy == NONE) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.none);
            }
            else if (policy == NUMBER) {
                // Update MUC history strategy
                historyStrat.setType(HistoryStrategy.Type.number);
                historyStrat.setMaxNumber(numMessages);
            }
            // All done, redirect
            response.sendRedirect("muc-history-settings.jsp?success=true");
            return;
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        if (historyStrat.getType() == HistoryStrategy.Type.all) {
            policy = ALL;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.none) {
            policy = NONE;
        }
        else if (historyStrat.getType() == HistoryStrategy.Type.number) {
            policy = NUMBER;
        }
        numMessages = historyStrat.getMaxNumber();
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Group Chat History Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Group Chat History", "muc-history-settings.jsp"));
    pageinfo.setPageID("muc-history");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Group chat rooms can replay conversation histories to provide context to new members joining a room.
There are several options for controlling how much history to store for each room.
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="muc-history-settings.jsp" method="post">

<fieldset>
    <legend>History Settings</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="top" class="">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= NONE %>" id="rb01"  <%= ((policy==NONE) ? "checked" : "") %> />
            </td>
            <td width="99%">
                <label for="rb01">
                <b>Don't Show History</b>
                </label>- Do not show a chat history to users joining a room.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= ALL %>" id="rb02"  <%= ((policy==ALL) ? "checked" : "") %>/>
            </td>
            <td width="99%">
                <label for="rb02">
                <b>Show Entire Chat History</b>
                </label>- Show the entire chat history to users joining a room.
            </td>
        </tr>
        <tr valign="top">
            <td width="1%" nowrap>
                <input type="radio" name="policy" value="<%= NUMBER %>" id="rb03"  <%= ((policy==NUMBER) ? "checked" : "") %> />
            </td>
            <td width="99%">
                <label for="rb03">
                <b>Show a Specific Number of Messages</b>
                </label>- Show a specific number of the most recent messages in the chat. Use the box below to specify that number.
            </td>
        </tr>
        <tr valign="top" class="">
            <td width="1%" nowrap>&nbsp;</td>
            <td width="99%">
                <input type="text" name="numMessages" size="5" maxlength="10" onclick="this.form.policy[2].checked=true;" value="<%= ((numMessages > 0) ? ""+numMessages : "") %>"/> messages
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="update" value="Save Settings"/>

</form>

<jsp:include page="bottom.jsp" flush="true" />