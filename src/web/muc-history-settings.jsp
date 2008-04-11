<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,                  
                 org.jivesoftware.openfire.muc.HistoryStrategy,
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt" prefix="fmt"%>

<%!  // Global vars and methods:

    // Strategy definitions:
    static final int ALL = 1;
    static final int NONE = 2;
    static final int NUMBER = 3;
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%  webManager.init(request, response, session, application, out ); %>

<%   // Get parameters:
    boolean update = request.getParameter("update") != null;
    int policy = ParamUtils.getIntParameter(request,"policy",-1);
    int numMessages = ParamUtils.getIntParameter(request,"numMessages",0);
    String mucname = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get muc server
    MultiUserChatService mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);
    
    HistoryStrategy historyStrat = mucService.getHistoryStrategy();

    Map<String,String> errors = new HashMap<String,String>();
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
            // Log the event
            webManager.logEvent("set MUC history settings for service "+mucname, "type = "+policy+"\nmax messages = "+numMessages);
            // All done, redirect
            response.sendRedirect("muc-history-settings.jsp?success=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
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

<html>
<head>
<title><fmt:message key="groupchat.history.settings.title"/></title>
<meta name="subPageID" content="muc-history"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="edit_group_chat_history_settings.html"/>
</head>
<body>

<p>
<fmt:message key="groupchat.history.settings.introduction" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= mucname %></a></b>
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="groupchat.history.settings.saved_successfully" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<!-- BEGIN 'History Settings' -->
<form action="muc-history-settings.jsp" method="post">
    <input type="hidden" name="mucname" value="<%= mucname %>" />
    <div class="jive-contentBoxHeader">
		<fmt:message key="groupchat.history.settings.legend" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0" >
		<tbody>
			<tr valign="middle" class="">
				<td width="1%" nowrap>
					<input type="radio" name="policy" value="<%= NONE %>" id="rb01"  <%= ((policy==NONE) ? "checked" : "") %> />
				</td>
				<td width="99%">
					<label for="rb01">
					<b><fmt:message key="groupchat.history.settings.label1_no_history" /></b>
					</label><fmt:message key="groupchat.history.settings.label2_no_history" />
				</td>
			</tr>
			<tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="policy" value="<%= ALL %>" id="rb02"  <%= ((policy==ALL) ? "checked" : "") %>/>
				</td>
				<td width="99%">
					<label for="rb02">
					<b><fmt:message key="groupchat.history.settings.label1_entire_history" /></b>
					</label><fmt:message key="groupchat.history.settings.label2_entire_history" />
				</td>
			</tr>
			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="policy" value="<%= NUMBER %>" id="rb03"  <%= ((policy==NUMBER) ? "checked" : "") %> />
				</td>
				<td width="99%">
					<label for="rb03">
					<b><fmt:message key="groupchat.history.settings.label1_number_messages" /></b>
					</label><fmt:message key="groupchat.history.settings.label2_number_messages" />
				</td>
			</tr>
			<tr valign="middle" class="">
				<td width="1%" nowrap>&nbsp;</td>
				<td width="99%">
					<input type="text" name="numMessages" size="5" maxlength="10" onclick="this.form.policy[2].checked=true;" value="<%= ((numMessages > 0) ? ""+numMessages : "") %>"/> <fmt:message key="groupchat.history.settings.messages" />
				</td>
			</tr>
		</tbody>
		</table>
	</div>
    <input type="submit" name="update" value="<fmt:message key="groupchat.history.settings.save" />"/>
</form>
<!-- END 'History Settings' -->


</body>
</html>