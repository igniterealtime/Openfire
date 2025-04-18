<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.util.*,
                 java.net.URLEncoder,
                 javax.mail.*,
                 javax.mail.internet.*"
    errorPage="error.jsp"
%>
<%@ page import="java.text.SimpleDateFormat"%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% // Get paramters
    boolean doTest = request.getParameter("test") != null;
    boolean cancel = request.getParameter("cancel") != null;
    boolean sent = ParamUtils.getBooleanParameter(request, "sent");
    boolean success = ParamUtils.getBooleanParameter(request, "success");
    String from = ParamUtils.getParameter(request, "from");
    String to = ParamUtils.getParameter(request, "to");
    String subject = ParamUtils.getParameter(request, "subject");
    String body = ParamUtils.getParameter(request, "body");

    // Cancel if requested
    if (cancel) {
        response.sendRedirect("system-email.jsp");
        return;
    }

    // Variable to hold messaging exception, if one occurs
    Exception mex = null;

    // Validate input
    Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (doTest) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            doTest = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (doTest) {
        if (from == null) {
            errors.put("from", "");
        }
        if (to == null) {
            errors.put("to", "");
        }
        if (subject == null) {
            errors.put("subject", "");
        }
        if (body == null) {
            errors.put("body", "");
        }

        EmailService service = EmailService.getInstance();

        // Validate host - at a minimum, it needs to be set:
        String host = service.getHost();
        if (host == null) {
            errors.put("host", "");
        }

        // if no errors, continue
        if (errors.isEmpty()) {
            // Create a message
            MimeMessage message = service.createMimeMessage();
            // Set the date of the message to be the current date
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",
                    java.util.Locale.US);
            format.setTimeZone(JiveGlobals.getTimeZone());
            message.setHeader("Date", format.format(new Date()));

            // Set to and from.
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to, null));
            message.setFrom(new InternetAddress(from, null));
            message.setSubject(subject);
            message.setText(body);
            // Send the message, wrap in a try/catch:
            try {
                service.sendMessagesImmediately(Collections.singletonList(message));
                // success, so indicate this:
                response.sendRedirect("system-emailtest.jsp?sent=true&success=true");
                return;
            }
            catch (MessagingException me) {
                me.printStackTrace();
                mex = me;
            }
        }
    }

    // Set var defaults
    Collection<JID> jids = webManager.getXMPPServer().getAdmins();
    User user = null;
    if (!jids.isEmpty()) {
        for (JID jid : jids) {
            if (webManager.getXMPPServer().isLocal(jid)) {
                user = webManager.getUserManager().getUser(jid.getNode());
                if (user.getEmail() != null) {
                    break;
                }
            }
        }
    }
    if (from == null) {
        from = user.getEmail();
    }
    if (to == null) {
        to = user.getEmail();
    }
    if (subject == null) {
        subject = "Test email sent via Openfire";
    }
    if (body == null) {
        body = "This is a test message.";
    }
%>

<html>
    <head>
        <title><fmt:message key="system.emailtest.title"/></title>
        <meta name="pageID" content="system-email"/>
    </head>
    <body>

<script>
let clicked = false;
function checkClick() {
    if (!clicked) {
        clicked = true;
        return true;
    }
    return false;
}
</script>

<p>
<fmt:message key="system.emailtest.info" />
</p>

<%  if (JiveGlobals.getProperty("mail.smtp.host") == null) { %>

    <admin:infoBox type="error">
        <fmt:message key="system.emailtest.no_host">
            <fmt:param value="<a href=\"system-email.jsp\">"/>
            <fmt:param value="</a>"/>
        </fmt:message>
    </admin:infoBox>

<%  } %>

<%  if (doTest || sent) { %>

    <%  if (success) { %>

        <admin:infoBox type="success">
            <fmt:message key="system.emailtest.success" />
        </admin:infoBox>

    <%  } else { %>

        <%  if (mex != null) { %>
        <%  if (mex instanceof AuthenticationFailedException) { %>
            <admin:infoBox type="error">
                <fmt:message key="system.emailtest.failure_authentication" />
            </admin:infoBox>
            <%  } else {
                pageContext.setAttribute("authFailText", "(Message: "+mex.getMessage()+")");%>
            <admin:infoBox type="error">
                <c:out value="${authFailText}"/>
            </admin:infoBox>
            <%  } %>
        <%  } %>
    <%  } %>
<%  } %>

<form action="system-emailtest.jsp" method="post" name="f" onsubmit="return checkClick(this);">
        <input type="hidden" name="csrf" value="${csrf}">

<table>
<tbody>
    <tr>
        <td>
            <fmt:message key="system.emailtest.mail_server" />:
        </td>
        <td>
            <%  String host = JiveGlobals.getProperty("mail.smtp.host");
                if (host == null) {
            %>
                <i><fmt:message key="system.emailtest.host_not_set" /></i>
            <%
                } else {
            %>
                <%= StringUtils.escapeHTMLTags(host) %>:<%= JiveGlobals.getIntProperty("mail.smtp.port", 25)  %>

                <%  if (JiveGlobals.getBooleanProperty("mail.smtp.ssl", false)) { %>

                    (<fmt:message key="system.emailtest.ssl" />)

                <%  } %>
            <%  } %>
        </td>
    </tr>
    <tr>
        <td>
            <fmt:message key="system.emailtest.from" />:
        </td>
        <td>
            <input type="hidden" name="from" value="<%= StringUtils.escapeForXML(from) %>">
            <%= StringUtils.escapeHTMLTags(from) %>
            <span class="jive-description">
            (<a href="user-edit-form.jsp?username=<%= URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8)%>"><fmt:message key="system.emailtest.update-address" /></a>)
            </span>
        </td>
    </tr>
    <tr>
        <td>
            <label for="to"><fmt:message key="system.emailtest.to" />:</label>
        </td>
        <td>
            <input type="text" id="to" name="to" value="<%= ((to != null) ? StringUtils.escapeForXML(to) : "") %>"
             size="40" maxlength="100">
        </td>
    </tr>
    <tr>
        <td>
            <label for="subject"><fmt:message key="system.emailtest.subject" />:</label>
        </td>
        <td>
            <input type="text" id="subject" name="subject" value="<%= ((subject != null) ? StringUtils.escapeForXML(subject) : "") %>"
             size="40" maxlength="100">
        </td>
    </tr>
    <tr>
        <td>
            <label for="body"><fmt:message key="system.emailtest.body" />:</label>
        </td>
        <td>
            <textarea id="body" name="body" cols="45" rows="5" wrap="virtual"><%= StringUtils.escapeHTMLTags(body) %></textarea>
        </td>
    </tr>
    <tr>
        <td colspan="2">
            <br>
            <input type="submit" name="test" value="<fmt:message key="system.emailtest.send" />">
            <input type="submit" name="cancel" value="<fmt:message key="system.emailtest.cancel" />">
        </td>
    </tr>
</tbody>
</table>

</form>

    </body>
</html>
