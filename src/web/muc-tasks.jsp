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
                 org.jivesoftware.messenger.muc.MultiUserChatServer,
                 java.util.Iterator"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean kickEnabled = ParamUtils.getBooleanParameter(request,"kickEnabled");
    String idletime = ParamUtils.getParameter(request,"idletime");
    String logfreq = ParamUtils.getParameter(request,"logfreq");
    String logbatchsize = ParamUtils.getParameter(request,"logbatchsize");
    boolean kickSettings = request.getParameter("kickSettings") != null;
    boolean logSettings = request.getParameter("logSettings") != null;
    boolean kickSettingSuccess = request.getParameter("kickSettingSuccess") != null;
    boolean logSettingSuccess = request.getParameter("logSettingSuccess") != null;

	// Get muc server
    MultiUserChatServer mucServer = admin.getMultiUserChatServer();

    Map errors = new HashMap();
    // Handle an update of the kicking task settings
    if (kickSettings) {
        if (!kickEnabled) {
            // Disable kicking users by setting a value of -1
            mucServer.setUserIdleTime(-1);
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
            mucServer.setUserIdleTime(idle);
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
            mucServer.setLogConversationsTimeout(frequency);
            mucServer.setLogConversationBatchSize(batchSize);
            response.sendRedirect("muc-tasks.jsp?logSettingSuccess=true");
            return;
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Other Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Other Settings", "muc-tasks.jsp"));
    pageinfo.setPageID("muc-tasks");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the forms below to configure settings for kicking idle users from group chat rooms and to
configure the task that logs room conversations to the database.
</p>

<%  if (kickSettingSuccess || logSettingSuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <%  if (kickSettingSuccess) { %>

            Idle user settings updated successfully.

        <%  } else if (logSettingSuccess) { %>

            Conversation logging settings updated successfully.

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<% if (errors.size() != 0) {  %>

   <table class="jive-error-message" cellpadding="3" cellspacing="0" border="0" width="350"> <tr valign="top">
    <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
    <td width="99%" class="jive-error-text">

		<% if (errors.get("idletime") != null) { %>
                Please enter a valid number for max idle minutes.
        <% }
           else if (errors.get("logfreq") != null) { %>
                Please enter a valid number for the frequency.
        <%  }
            else if (errors.get("logbatchsize") != null) { %>
                Please enter a valid number for the batch size.
            <%  } %>
    </td>
    </tr>
    </table><br>

<% } %>

<form action="muc-tasks.jsp?kickSettings" method="post">

<fieldset>
    <legend>Idle User Settings</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="kickEnabled" value="false" id="rb01"
                 <%= ((mucServer.getUserIdleTime() < 0) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">Never kick idle users.</label>
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="kickEnabled" value="true" id="rb02"
                 <%= ((mucServer.getUserIdleTime() > -1) ? "checked" : "") %>>
            </td>
            <td width="99%">
                    <label for="rb02">Kick users after they have been idle for</label>
                     <input type="text" name="idletime" size="5" maxlength="5"
                         onclick="this.form.kickEnabled[1].checked=true;"
                         value="<%= mucServer.getUserIdleTime() == -1 ? 30 : mucServer.getUserIdleTime() / 1000 / 60 %>">
                     minutes.
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Settings">

</form>

<br>

<form action="muc-tasks.jsp?logSettings" method="post">

<fieldset>
    <legend>Conversation Logging</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tr valign="middle">
        <td width="1%" nowrap class="c1">
            Flush interval (seconds):
        </td>
        <td width="99%">
            <input type="text" name="logfreq" size="15" maxlength="50"
             value="<%= mucServer.getLogConversationsTimeout() / 1000 %>">
        </td>
    </tr>
    <tr valign="middle">
        <td width="1%" nowrap class="c1">
            Batch size:
        </td>
        <td width="99%">
            <input type="text" name="logbatchsize" size="15" maxlength="50"
             value="<%= mucServer.getLogConversationBatchSize() %>">
        </td>
    </tr>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
