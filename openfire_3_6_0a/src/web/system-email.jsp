<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="java.util.*,
				 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
    // get parameters
    String host = ParamUtils.getParameter(request,"host");
    int port = ParamUtils.getIntParameter(request,"port",0);
    String username = ParamUtils.getParameter(request,"server_username");
    String password = ParamUtils.getParameter(request,"server_password");
    boolean ssl = ParamUtils.getBooleanParameter(request,"ssl");
    boolean save = request.getParameter("save") != null;
    boolean test = request.getParameter("test") != null;
    boolean debug = ParamUtils.getBooleanParameter(request, "debug");

    // Handle a test request
    if (test) {
        response.sendRedirect("system-emailtest.jsp");
        return;
    }

    EmailService service = EmailService.getInstance();
    // Save the email settings if requested
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        if (host != null) {
            service.setHost(host);
        }
        else {
            errors.put("host","");
        }
        if (port > 0) {
            service.setPort(port);
        }
        else {
            // Default to port 25.
            service.setPort(25);
        }
        service.setUsername(username);
        // Get hash value of existing password
        String existingHashPassword = "";
        if (service.getPassword() != null) {
            existingHashPassword = StringUtils.hash(service.getPassword());
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
                service.setPassword(password);
            }
        }
        
        service.setDebugEnabled(debug);
        service.setSSLEnabled(ssl);

        if (errors.size() == 0) {
            // Log the event
            webManager.logEvent("updated email service settings", "host = "+host+"\nport = "+port+"\nusername = "+username);
            // Set property to specify email is configured
            JiveGlobals.setProperty("mail.configured", "true");
            response.sendRedirect("system-email.jsp?success=true");
        }
    }

    host = service.getHost();
    port = service.getPort();
    username = service.getUsername();
    password = service.getPassword();
    ssl = service.isSSLEnabled();
    debug = service.isDebugEnabled();
%>

<html>
    <head>
        <title><fmt:message key="system.email.title"/></title>
        <meta name="pageID" content="system-email"/>
    </head>
    <body>

<p>
<fmt:message key="system.email.info" />
</p>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label"><fmt:message key="system.email.update_success" /></td>
        </tr>
    </tbody>
    </table>
    </div>

<%  } %>

<%  if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label"><fmt:message key="system.email.update_failure" /></td>
        </tr>
    </tbody>
    </table>
    </div>

<%	} %>

<p>

<!-- BEGIN SMTP settings -->
<form action="system-email.jsp" name="f" method="post">

	<div class="jive-contentBoxHeader">
		<fmt:message key="system.email.name" />
	</div>
	<div class="jive-contentBox">
		<table width="80%" cellpadding="3" cellspacing="0" border="0">
		<tr>
			<td width="30%" nowrap>
				<fmt:message key="system.email.mail_host" />:
			</td>
			<td nowrap>
				<input type="text" name="host" value="<%= (host != null)?host:"" %>" size="40" maxlength="150">
			</td>
		</tr>

		<%  if (errors.containsKey("host")) { %>

			<tr>
				<td nowrap>
					&nbsp;
				</td>
				<td nowrap class="jive-error-text">
					<fmt:message key="system.email.valid_host_name" />
				</td>
			</tr>

		<%  } %>

		<tr>
			<td nowrap>
				<fmt:message key="system.email.server_port" />:
			</td>
			<td nowrap>
				<input type="text" name="port" value="<%= (port > 0) ? String.valueOf(port) : "" %>" size="10" maxlength="15">
			</td>
		</tr>
		<tr>
			<td nowrap>
				<fmt:message key="system.email.mail_debugging" />:
			</td>
			<td nowrap>
				<input type="radio" name="debug" value="true"<%= (debug ? " checked" : "") %> id="rb01"> <label for="rb01">On</label>
				&nbsp;
				<input type="radio" name="debug" value="false"<%= (debug ? "" : " checked") %> id="rb02"> <label for="rb02">Off</label>
				&nbsp; (<fmt:message key="system.email.restart_possible" />)
			</td>
		</tr>

		<%-- spacer --%>
		<tr><td colspan="2">&nbsp;</td></tr>

		<tr>
			<td nowrap>
				<fmt:message key="system.email.server_username" />:
			</td>
			<td nowrap>
				<input type="text" name="server_username" value="<%= (username != null) ? username : "" %>" size="40" maxlength="150">
			</td>
		</tr>
		<tr>
			<td nowrap>
				<fmt:message key="system.email.server_password" />:
			</td>
			<td nowrap>
				<input type="password" name="server_password" value="<%= (password != null) ? StringUtils.hash(password) : "" %>" size="40" maxlength="150">
			</td>
		</tr>

		<tr>
			<td nowrap>
				<fmt:message key="system.email.ssl" />:
			</td>
			<td nowrap>
				<input type="checkbox" name="ssl"<%= (ssl) ? " checked" : "" %>>
			</td>
		</tr>
		</table>
	</div>

<input type="submit" name="save" value="<fmt:message key="system.email.save" />">
<input type="submit" name="test" value="<fmt:message key="system.email.send_test" />">
</form>
<!-- END SMTP settings -->

</body>
</html>