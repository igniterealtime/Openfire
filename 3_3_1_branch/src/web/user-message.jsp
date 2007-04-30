
<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.ClientSession,
                 org.jivesoftware.openfire.session.Session,
                 org.jivesoftware.openfire.user.User,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder,
                 java.util.Collection,
                 java.util.HashMap,
                 java.util.Iterator"
    errorPage="error.jsp"
%>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%  // Get parameters
    String username = ParamUtils.getParameter(request,"username");
    boolean send = ParamUtils.getBooleanParameter(request,"send");
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean sendToAll = ParamUtils.getBooleanParameter(request,"sendToAll");
    boolean tabs = ParamUtils.getBooleanParameter(request,"tabs",true);
    String jid = ParamUtils.getParameter(request,"jid");
    String[] jids = ParamUtils.getParameters(request,"jid");
    String sessionID = ParamUtils.getParameter(request,"sessionID");
    String message = ParamUtils.getParameter(request,"message");
%>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(pageContext); %>

<%
    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        if (username == null) {
            response.sendRedirect("session-summary.jsp");
            return;
        }
        else {
            response.sendRedirect("user-properties.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
            return;
        }
    }

    // Get the user - a user might not be passed in if this is a system-wide message
    User user = null;
    if (username != null) {
        user = webManager.getUserManager().getUser(username);
    }

    // Get the session manager
    SessionManager sessionManager = webManager.getSessionManager();

    // Handle the request to send a message:
    Map errors = new HashMap();
    if (send) {
        // Validate the message and jid
        if (jid == null && !sendToAll && user != null) {
            errors.put("jid","jid");
        }
        if (message == null) {
            errors.put("message","message");
        }
        if (errors.size() == 0) {
            // no errors, so continue
            if (user == null) {
                // system-wide message:
                sessionManager.sendServerMessage(null,message);
            }
            else {
                if (sendToAll) {
                    // loop through all sessions based on the user assoc with the JID, send
                    // message to all
                    for (int i=0; i<jids.length; i++) {
                        JID address = new JID(jids[i]);
                        Session s = sessionManager.getSession(address);
                        sessionManager.sendServerMessage(address, null, message);
                    }
                }
                else {
                    sessionManager.sendServerMessage(new JID(jid),null,message);
                }
            }
            if (username != null){
                response.sendRedirect("user-message.jsp?success=true&username=" +
                        URLEncoder.encode(username, "UTF-8") + "&tabs=" + tabs);
            }
            else {
                response.sendRedirect("user-message.jsp?success=true");
            }
            return;
        }
    }

    // Get all sessions associated with this user:
    int numSessions = -1;
    ClientSession sess = null;
    Collection<ClientSession> sessions = null;
    if (user != null) {
        numSessions = sessionManager.getSessionCount(user.getUsername());
        sessions = sessionManager.getSessions(user.getUsername());
        if (numSessions == 1) {
            sess = sessions.iterator().next();
        }
    }
%>


<html>
<head>
<title><fmt:message key="user.message.title"/></title>
<meta name="pageID" content="user-message"/>
<meta name="helpPage" content="send_an_administrative_message_to_users.html"/>
</head>
<body>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="user.message.send" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<script language="JavaScript" type="text/javascript">
function updateSelect(el) {
    if (el.checked) {
        for (var e=0; e<el.form.jid.length; e++) {
            el.form.jid[e].selected = true;
        }
    }
    else {
        for (var e=0; e<el.form.jid.length; e++) {
            el.form.jid[e].selected = false;
        }
    }
    el.form.message.focus();
}
</script>

<form action="user-message.jsp" method="post" name="f">
<% if(username != null){ %>
<input type="hidden" name="username" value="<%= username %>">
<% } %>
<input type="hidden" name="tabs" value="<%= tabs %>">
<input type="hidden" name="send" value="true">
<%  if (sess != null) { %>

    <input type="hidden" name="sessionID" value="<%= sess.getAddress().toString() %>">

<%  } %>

	<!-- BEGIN send message block -->
	<!--<div class="jive-contentBoxHeader">
		<fmt:message key="user.message.send_admin_msg" />
	</div>-->
	<div class="jive-contentBox" style="-moz-border-radius: 3px;">
		<table cellpadding="3" cellspacing="1" border="0" width="600">

		<tr><td colspan=3 class="text" style="padding-bottom: 10px;">
		<%   if (user == null) { %>

			<p><fmt:message key="user.message.info" /></p>

		<%  } else { %>

			<p><fmt:message key="user.message.specified_user_info" /></p>

		<%  } %>
		</td></tr>
		<tr>
			<td class="jive-label">
				<fmt:message key="user.message.to" />:
			</td>
			<td>
				<%  if (user == null) { %>

					<fmt:message key="user.message.all_online_user" />

				<%  } else { %>

					<%  if (sess != null && numSessions == 1) { %>

						<%= sess.getAddress().toString() %>
						<input type="hidden" name="jid" value="<%= sess.getAddress().toString() %>">

					<%  } else { %>

						<select size="2" name="jid" multiple>

						<%   Iterator<ClientSession> iter = sessions.iterator();
							 while (iter.hasNext()) {
								sess = iter.next();
						%>
							<option value="<%= sess.getAddress().toString() %>"><%= sess.getAddress().toString() %></option>

						<%  } %>

						</select>

						<input type="checkbox" name="sendToAll" value="true" id="cb01"
						 onfocus="updateSelect(this);" onclick="updateSelect(this);">
						<label for="cb01"><fmt:message key="user.message.send_session" /></label>

					<%  } %>

					<%  if (errors.get("jid") != null) { %>

						<br>
						<span class="jive-error-text">
						<fmt:message key="user.message.valid_address" />
						</span>

					<%  } %>

				<%  } %>
			</td>
		</tr>
		<tr valign="top">
			<td class="jive-label">
				<fmt:message key="user.message.message" />:
			</td>
			<td>
				<%  if (errors.get("message") != null) { %>

					<span class="jive-error-text">
					<fmt:message key="user.message.valid_message" />
					</span>
					<br>

				<%  } %>
				<textarea name="message" cols="55" rows="5" wrap="virtual"></textarea>
			</td>
		</tr>
		</table>
	</div>
	<!-- END send message block -->

<input type="submit" value="<fmt:message key="user.message.send_message" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">

</form>

<script language="JavaScript" type="text/javascript">
document.f.message.focus();
</script>


</body>
</html>