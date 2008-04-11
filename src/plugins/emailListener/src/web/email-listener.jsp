<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.openfire.plugin.emailListener.EmailListener,
				 org.jivesoftware.util.ParamUtils"
%>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // get parameters
    String host = ParamUtils.getParameter(request,"host");
    int port = ParamUtils.getIntParameter(request,"port",0);
    String username = ParamUtils.getParameter(request,"server_username");
    String password = ParamUtils.getParameter(request,"server_password");
    boolean ssl = ParamUtils.getBooleanParameter(request,"ssl");
    String folder = ParamUtils.getParameter(request,"folder");
    int frequency = ParamUtils.getIntParameter(request,"frequency",0);
    String users = ParamUtils.getParameter(request,"users");
    boolean save = request.getParameter("save") != null;
    boolean test = request.getParameter("test") != null;
    boolean success = ParamUtils.getBooleanParameter(request,"success");
    boolean testSuccess = false;

    EmailListener emailListener = EmailListener.getInstance();
    Map<String, String> errors = new HashMap<String, String>();
    if (test || save) {
        if (host == null || host.trim().length() == 0) {
            errors.put("host","");
        }
        if (username == null || username.trim().length() == 0) {
            errors.put("username","");
        }
        if (password == null || password.trim().length() == 0) {
            errors.put("password","");
        }
        if (folder == null || folder.trim().length() == 0) {
            errors.put("folder","");
        }
        if (frequency <= 0) {
            errors.put("frequency","");
        }
        if (port <= 0) {
            errors.put("port","");
        }
        if (users == null || users.trim().length() == 0) {
            errors.put("users","");
        }

        // Get hash value of existing password
        String existingHashPassword = "";
        if (emailListener.getPassword() != null) {
            existingHashPassword = StringUtils.hash(emailListener.getPassword());
        }

        // Check if the new password was changed. If it wasn't changed, then it is the original hashed password
        // NOTE: if the new PLAIN password equals the previous HASHED password this fails, but is unlikely.
        if (!existingHashPassword.equals(password)) {
            // Hash the new password since it was changed
            String newHashPassword = "";
            if (password != null) {
                newHashPassword = StringUtils.hash(password);
            }
            // Change password if hash values are different
            if (!existingHashPassword.equals(newHashPassword)) {
                //password = password;
            }
        }
        else {
            password = emailListener.getPassword();
        }

    }

    // Handle a test request
    if (test && errors.isEmpty()) {
        testSuccess = EmailListener.testConnection(host, port, ssl, username, password, folder);
    }
    else {
        // Save the email settings if requested
        if (save) {
            if (errors.isEmpty()) {
                emailListener.setHost(host);
                emailListener.setPort(port);
                emailListener.setSSLEnabled(ssl);
                emailListener.setUser(username);
                emailListener.setPassword(password);
                emailListener.setFolder(folder);
                emailListener.setFrequency(frequency);
                emailListener.setUsers(StringUtils.stringToCollection(users));

                // Restart the email listener service
                emailListener.stop();
                emailListener.start();

                response.sendRedirect("email-listener.jsp?success=true");
            }
        }

        host = emailListener.getHost();
        port = emailListener.getPort();
        ssl = emailListener.isSSLEnabled();
        username = emailListener.getUser();
        password = emailListener.getPassword();
        folder = emailListener.getFolder();
        frequency = emailListener.getFrequency();
        users = StringUtils.collectionToString(emailListener.getUsers());
    }
%>

<html>
    <head>
        <title>Email Listener</title>
        <meta name="pageID" content="email-listener"/>
    </head>
    <body>

<p>
Configure the email listener service with the following form. The email listener service
connects to an email server using IMAP and listens for new messages. Specified users are then alerted by
IM when new messages were detected. Messages are not deleted from the mail server.    
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Settings updated successfully.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%  } %>

<%  if (test && testSuccess && errors.isEmpty()) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Test was successful.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%  } else if (test && !testSuccess && errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">Test failed.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%  } %>

<%  if (errors.containsKey("host")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the SMTP server to use.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("port")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the port to use to connect to the SMTP server.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("username")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the user to use to connect to the SMTP server.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("password")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the password to use to connect to the SMTP server.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("folder")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the folder to use in the SMTP server.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("frequency")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify the frequency to check for new messages.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} else if (errors.containsKey("users")) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="/images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Please specify one or more users that will get the notifications.</td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} %>

<p>

<!-- BEGIN SMTP settings -->
<form action="email-listener.jsp" name="f" method="post">

	<div class="jive-contentBoxHeader">
		Email listener settings
	</div>
	<div class="jive-contentBox">
		<table width="80%" cellpadding="3" cellspacing="0" border="0">
		<tr>
			<td width="30%" nowrap>
				Mail Host:
			</td>
			<td nowrap>
				<input type="text" name="host" value="<%= (host != null)?host:"" %>" size="40" maxlength="150">
			</td>
		</tr>
        <tr>
            <td nowrap>
                Mail Port:
            </td>
            <td nowrap>
                <input type="text" name="port" value="<%= (port > 0) ? String.valueOf(port) : "" %>" size="10" maxlength="15">
            </td>
        </tr>
        <tr>
            <td nowrap>
                Use SSL (Optional):
            </td>
            <td nowrap>
                <input type="checkbox" name="ssl"<%= (ssl) ? " checked" : "" %>>
            </td>
        </tr>
		<tr>
			<td nowrap>
				Server Username:
			</td>
			<td nowrap>
				<input type="text" name="server_username" value="<%= (username != null) ? username : "" %>" size="40" maxlength="150">
			</td>
		</tr>
		<tr>                               
			<td nowrap>
				Server Password:
			</td>
			<td nowrap>
				<input type="password" name="server_password" value="<%= (password != null) ? StringUtils.hash(password) : "" %>" size="40" maxlength="150">
			</td>
		</tr>
		<tr>
			<td nowrap>
				Folder:
			</td>
			<td nowrap>
                <input type="text" name="folder" value="<%= (folder != null) ? folder : "Inbox" %>" size="40" maxlength="150">
			</td>
		</tr>
        <tr>
            <td nowrap>
                Check Frequency (millis):
            </td>
            <td nowrap>
                <input type="text" name="frequency" value="<%= (frequency > 0) ? String.valueOf(frequency) : "" %>" size="10" maxlength="15">
            </td>
        </tr>
        <tr>
            <td nowrap>
                JID of users to notify:<br>
                <i>(comma delimited)</i>
            </td>
            <td nowrap>
                <textarea name="users" cols="40" rows="3" wrap="virtual"><%= users%></textarea>
            </td>
        </tr>
		</table>
	</div>

<input type="submit" name="save" value="Save">
<input type="submit" name="test" value="Test Settings">
</form>
<!-- END SMTP settings -->

</body>
</html>