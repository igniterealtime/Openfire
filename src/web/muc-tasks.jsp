<%--
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
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean kickEnabled = ParamUtils.getBooleanParameter(request,"kickEnabled");
    String idletime = ParamUtils.getParameter(request,"idletime");
    String logfreq = ParamUtils.getParameter(request,"logfreq");
    String logbatchsize = ParamUtils.getParameter(request,"logbatchsize");
    boolean kickSettings = request.getParameter("kickSettings") != null;
    boolean logSettings = request.getParameter("logSettings") != null;
    boolean kickSettingSuccess = request.getParameter("kickSettingSuccess") != null;
    boolean logSettingSuccess = request.getParameter("logSettingSuccess") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get muc server
    MultiUserChatService mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    Map<String, String> errors = new HashMap<String, String>();
    // Handle an update of the kicking task settings
    if (kickSettings) {
        if (!kickEnabled) {
            // Disable kicking users by setting a value of -1
            mucService.setUserIdleTime(-1);
            // Log the event
            webManager.logEvent("disabled muc idle kick timeout for service "+mucname, null);
            response.sendRedirect("muc-tasks.jsp?kickSettingSuccess=true");
            return;
        }
        // do validation
        if (idletime == null) {
            errors.put("idletime","idletime");
        }
        int idle = 0;
        // Try to obtain an int from the provided strings
        if (errors.size() == 0) {
            try {
                idle = Integer.parseInt(idletime) * 1000 * 60;
            }
            catch (NumberFormatException e) {
                errors.put("idletime","idletime");
            }
            if (idle < 0) {
                errors.put("idletime","idletime");
            }
        }

        if (errors.size() == 0) {
            mucService.setUserIdleTime(idle);
            // Log the event
            webManager.logEvent("edited muc idle kick timeout for service "+mucname, "timeout = "+idle);
            response.sendRedirect("muc-tasks.jsp?kickSettingSuccess=true");
            return;
        }
    }

    // Handle an update of the log conversations task settings
    if (logSettings) {
        // do validation
        if (logfreq == null) {
            errors.put("logfreq","logfreq");
        }
        if (logbatchsize == null) {
            errors.put("logbatchsize","logbatchsize");
        }
        int frequency = 0;
        int batchSize = 0;
        // Try to obtain an int from the provided strings
        if (errors.size() == 0) {
            try {
                frequency = Integer.parseInt(logfreq) * 1000;
            }
            catch (NumberFormatException e) {
                errors.put("logfreq","logfreq");
            }
            try {
                batchSize = Integer.parseInt(logbatchsize);
            }
            catch (NumberFormatException e) {
                errors.put("logbatchsize","logbatchsize");
            }
        }

        if (errors.size() == 0) {
            mucService.setLogConversationsTimeout(frequency);
            mucService.setLogConversationBatchSize(batchSize);
            // Log the event
            webManager.logEvent("edited muc conversation log settings for service "+mucname, "timeout = "+frequency+"\nbatchSize = "+batchSize);
            response.sendRedirect("muc-tasks.jsp?logSettingSuccess=true");
            return;
        }
    }
%>

<html>
<head>
<title><fmt:message key="muc.tasks.title"/></title>
<meta name="subPageID" content="muc-tasks"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="edit_idle_user_settings.html"/>
</head>
<body>

<p>
<fmt:message key="muc.tasks.info" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= mucname %></a></b>
</p>

<%  if (kickSettingSuccess || logSettingSuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (kickSettingSuccess) { %>

            <fmt:message key="muc.tasks.update" />

        <%  } else if (logSettingSuccess) { %>

            <fmt:message key="muc.tasks.log" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<% if (errors.size() != 0) {  %>

   <table class="jive-error-message" cellpadding="3" cellspacing="0" border="0" width="350"> <tr valign="top">
    <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
    <td width="99%" class="jive-error-text">

		<% if (errors.get("idletime") != null) { %>
                <fmt:message key="muc.tasks.valid_idel_minutes" />
        <% }
           else if (errors.get("logfreq") != null) { %>
                <fmt:message key="muc.tasks.valid_frequency" />
        <%  }
            else if (errors.get("logbatchsize") != null) { %>
                <fmt:message key="muc.tasks.valid_batch" />
            <%  } %>
    </td>
    </tr>
    </table><br>

<% } %>


<!-- BEGIN 'Idle User Settings' -->
<form action="muc-tasks.jsp?kickSettings" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="muc.tasks.user_setting" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="kickEnabled" value="false" id="rb01"
					 <%= ((mucService.getUserIdleTime() < 0) ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb01"><fmt:message key="muc.tasks.never_kick" /></label>
				</td>
			</tr>
			<tr valign="middle">
				<td width="1%" nowrap>
					<input type="radio" name="kickEnabled" value="true" id="rb02"
					 <%= ((mucService.getUserIdleTime() > -1) ? "checked" : "") %>>
				</td>
				<td width="99%">
						<label for="rb02"><fmt:message key="muc.tasks.kick_user" /></label>
						 <input type="text" name="idletime" size="5" maxlength="5"
							 onclick="this.form.kickEnabled[1].checked=true;"
							 value="<%= mucService.getUserIdleTime() == -1 ? 30 : mucService.getUserIdleTime() / 1000 / 60 %>">
						 <fmt:message key="global.minutes" />.
				</td>
			</tr>
		</tbody>
		</table>
        <br/>
        <input type="submit" value="<fmt:message key="global.save_settings" />">
	</div>
</form>
<!-- END 'Idle User Settings' -->

<br>

<!-- BEGIN 'Conversation Logging' -->
<form action="muc-tasks.jsp?logSettings" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="muc.tasks.conversation.logging" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0" >
		<tr valign="middle">
			<td width="1%" nowrap class="c1">
				<fmt:message key="muc.tasks.flush" />
			</td>
			<td width="99%">
				<input type="text" name="logfreq" size="15" maxlength="50"
				 value="<%= mucService.getLogConversationsTimeout() / 1000 %>">
			</td>
		</tr>
		<tr valign="middle">
			<td width="1%" nowrap class="c1">
				<fmt:message key="muc.tasks.batch" />
			</td>
			<td width="99%">
				<input type="text" name="logbatchsize" size="15" maxlength="50"
				 value="<%= mucService.getLogConversationBatchSize() %>">
			</td>
		</tr>
		</table>
        <br/>
        <input type="submit" value="<fmt:message key="global.save_settings" />">
	</div>
</form>
<!-- END 'Conversation Logging' -->


</body>
</html>