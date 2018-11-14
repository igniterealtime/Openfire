<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
                 java.util.*"
    errorPage="error.jsp"
%>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% // Get paramters
    boolean doTest = request.getParameter("test") != null;
    boolean cancel = request.getParameter("cancel") != null;
    String recipient = ParamUtils.getParameter(request, "recipient");
    String message = ParamUtils.getParameter(request, "message");

    // Cancel if requested
    if (cancel) {
        response.sendRedirect("system-sms.jsp");
        return;
    }

    // Validate input
    Map<String, String> errors = new HashMap<String, String>();
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

    // See if the service has basic configuration.
    if ( JiveGlobals.getProperty( "sms.smpp.host" ) == null ) {
        errors.put( "host", "cannot be missing or empty." );
    }
    if ( JiveGlobals.getProperty( "sms.smpp.systemId" ) == null )
    {
        errors.put( "systemId", "cannot be missing or empty." );
    }
    if (JiveGlobals.getProperty( "sms.smpp.password" ) == null )
    {
        errors.put( "password", "cannot be missing or empty.");
    }

    if (doTest) {
        if (recipient == null || recipient.trim().isEmpty() ) {
            errors.put("recipient", "Recipient cannot be missing or empty.");
        }
        if (message == null || message.trim().isEmpty() ) {
            errors.put("message", "Message cannot be missing or empty.");
        }

        if (errors.isEmpty())
        {
            final SmsService service = SmsService.getInstance();
            try
            {
                service.sendImmediately( message, recipient );
                response.sendRedirect("system-smstest.jsp?sent=true&success=true");
                return;
            }
            catch ( Exception e )
            {
                errors.put( "sendfailed", StringUtils.escapeHTMLTags(SmsService.getDescriptiveMessage( e ), true) );
            }
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "recipient", recipient );
    pageContext.setAttribute( "message", message );

%>

<html>
    <head>
        <title><fmt:message key="system.smstest.title"/></title>
        <meta name="pageID" content="system-sms"/>
    </head>
    <body>
        <c:if test="${not empty errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${ not empty errors['host'] or not empty errors['systemId'] or not empty errors['password']}">
                        <fmt:message key="system.smstest.invalid-service-config">
                            <fmt:param value="<a href=\"system-sms.jsp\">"/>
                            <fmt:param value="</a>"/>
                        </fmt:message>
                    </c:when>
                    <c:when test="${ not empty errors['sendfailed']}">
                        <fmt:message key="system.smstest.failure">
                            <fmt:param value="${errors['sendfailed']}"/>
                        </fmt:message>
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="system.sms.config_failure" />
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:if>

        <c:if test="${empty errors and param.success}">
            <admin:infobox type="success"><fmt:message key="system.smstest.success" /></admin:infobox>
        </c:if>

        <p>
            <fmt:message key="system.smstest.info" />
        </p>


        <form action="system-smstest.jsp" method="post">

            <table cellpadding="3" cellspacing="0" border="0">
                <tbody>
                    <tr>
                        <td>
                            <fmt:message key="system.smstest.recipient" />:
                        </td>
                        <td>
                            <input type="text" name="recipient" value="${fn:escapeXml(recipient)}"size="40" maxlength="100">
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['recipient']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.smstest.valid_recipient" />
                            </td>
                        </tr>
                    </c:if>

                    <tr valign="top">
                        <td>
                            <fmt:message key="system.smstest.message" />:
                        </td>
                        <td>
                            <textarea name="message" cols="45" rows="5" maxlength="140" wrap="virtual"><c:out value="${message}"/></textarea>
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['message']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.smstest.valid_message" />
                            </td>
                        </tr>
                    </c:if>

                    <tr>
                        <td colspan="2">
                            <br>
                            <input type="hidden" name="csrf" value="${csrf}">
                            <input type="submit" name="test" value="<fmt:message key="system.smstest.send" />" ${ not empty errors['host'] or not empty errors['systemId'] or not empty errors['password'] ? 'disabled' : ''}>
                            <input type="submit" name="cancel" value="<fmt:message key="system.smstest.cancel" />">
                        </td>
                    </tr>
                </tbody>
            </table>

        </form>

    </body>
</html>
