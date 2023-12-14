<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2005-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

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

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    Map<String,String> errors = new HashMap<>();

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "CSRF Failure!");
            save = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    // Handle a test request
    if (test) {
        response.sendRedirect("system-emailtest.jsp");
        return;
    }

    EmailService service = EmailService.getInstance();
    // Save the email settings if requested
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

    <admin:infoBox type="success">
        <fmt:message key="system.email.update_success" />
    </admin:infoBox>

<%  } %>

<%  if (errors.size() > 0) { %>

    <admin:infoBox type="error">
        <fmt:message key="system.email.update_failure" />
    </admin:infoBox>

<%	} %>

<p>

<!-- BEGIN SMTP settings -->
<form action="system-email.jsp" name="f" method="post">

    <div class="jive-contentBoxHeader">
        <fmt:message key="system.email.name" />
    </div>
    <div class="jive-contentBox">
        <table style="width: 80%;">
        <tr>
            <td style="width: 30%" nowrap>
                <label for="host"><fmt:message key="system.email.mail_host" />:</label>
            </td>
            <td nowrap>
                <input type="text" id="host" name="host" value="<%= (host != null)? StringUtils.escapeForXML(host):"" %>" size="40" maxlength="150">
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
                <label for="port"><fmt:message key="system.email.server_port" />:</label>
            </td>
            <td nowrap>
                <input type="text" id="port" name="port" value="<%= (port > 0) ? String.valueOf(port) : "" %>" size="10" maxlength="15">
            </td>
        </tr>
        <tr>
            <td nowrap>
                <fmt:message key="system.email.mail_debugging" />:
            </td>
            <td nowrap>
                <input type="radio" name="debug" value="true"<%= (debug ? " checked" : "") %> id="rb01"> <label for="rb01"><fmt:message key="system.email.mail_debugging.enabled" /></label>
                &nbsp;
                <input type="radio" name="debug" value="false"<%= (debug ? "" : " checked") %> id="rb02"> <label for="rb02"><fmt:message key="system.email.mail_debugging.disabled" /></label>
                &nbsp; (<fmt:message key="system.email.restart_possible" />)
            </td>
        </tr>

        <%-- spacer --%>
        <tr><td colspan="2">&nbsp;</td></tr>

        <tr>
            <td nowrap>
                <label for="server_username"><fmt:message key="system.email.server_username" />:</label>
            </td>
            <td nowrap>
                <input type="text" id="server_username" name="server_username" value="<%= (username != null) ? StringUtils.escapeForXML(username) : "" %>" size="40" maxlength="150">
            </td>
        </tr>
        <tr>
            <td nowrap>
                <label for="server_password"><fmt:message key="system.email.server_password" />:</label>
            </td>
            <td nowrap>
                <input type="password" id="server_password" name="server_password" value="<%= (password != null) ? StringUtils.hash(password) : "" %>" size="40" maxlength="150">
            </td>
        </tr>

        <tr>
            <td nowrap>
                <label for="ssl"><fmt:message key="system.email.ssl" />:</label>
            </td>
            <td nowrap>
                <input type="checkbox" id="ssl" name="ssl"<%= (ssl) ? " checked" : "" %>>
            </td>
        </tr>
        </table>
    </div>
<input type="hidden" name="csrf" value="${csrf}"/>
<input type="submit" name="save" value="<fmt:message key="system.email.save" />">
<input type="submit" name="test" value="<fmt:message key="system.email.send_test" />">
</form>
<!-- END SMTP settings -->

</body>
</html>
