<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.*,
                 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
    // get parameters
    String host = ParamUtils.getParameter(request,"host");
    int port = ParamUtils.getIntParameter(request,"port",0);
    String systemId = ParamUtils.getParameter(request,"systemId");
    String password = ParamUtils.getParameter(request,"password");
    String systemType = ParamUtils.getParameter(request,"systemType");
    boolean save = request.getParameter("save") != null;
    boolean test = request.getParameter("test") != null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    final Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a test request
    if (test) {
        response.sendRedirect("system-smstest.jsp");
        return;
    }

    if ( !save )
    {
        // Not trying to save new values? Use the existing values.
        host = JiveGlobals.getProperty( "sms.smpp.host", "localhost" );
        port = JiveGlobals.getIntProperty( "sms.smpp.port", 2775 );
        systemId = JiveGlobals.getProperty( "sms.smpp.systemId" );
        password = JiveGlobals.getProperty( "sms.smpp.password" );
        systemType = JiveGlobals.getProperty( "sms.smpp.systemType" );
    }

    if ( host == null || host.isEmpty() )
    {
        errors.put( "host", "cannot be missing or empty." );
    }
    try {
        JID.domainprep(host);
    } catch (Exception e) {
        errors.put("host", "Invalid hostname");
    }
    if ( port < 0 || port > 65535 )
    {
        errors.put( "port", "must be a number between 0 and 65535." );
    }
    if ( systemId == null || systemId.isEmpty() )
    {
        errors.put( "systemId", "cannot be missing or empty." );
    }
    if ( password == null || password.isEmpty() )
    {
        errors.put( "password", "cannot be missing or empty." );
    }

    if (errors.isEmpty() && save)
    {
        JiveGlobals.setProperty( "sms.smpp.host", host );
        JiveGlobals.setProperty( "sms.smpp.port", Integer.toString(port) );
        JiveGlobals.setProperty( "sms.smpp.systemId", systemId );
        JiveGlobals.setProperty( "sms.smpp.password", password );
        JiveGlobals.setProperty( "sms.smpp.systemType", systemType );

        // Log the event
        webManager.logEvent("updated sms service settings", "host = "+host+"\nport = "+port+"\nusername = "+ systemId +"\nsystemType = "+systemType);
        JiveGlobals.setProperty("sms.configured", "true");
        response.sendRedirect("system-sms.jsp?success=true");
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "host",  host );
    pageContext.setAttribute( "port", port);
    pageContext.setAttribute( "systemId", systemId );
    pageContext.setAttribute( "password", password );
    pageContext.setAttribute( "systemType", systemType );
%>

<html>
    <head>
        <title><fmt:message key="system.sms.title"/></title>
        <meta name="pageID" content="system-sms"/>
    </head>
    <body>
        <c:if test="${not empty errors}">
            <admin:infobox type="error">
                <fmt:message key="system.sms.config_failure" />
            </admin:infobox>
        </c:if>
        <c:if test="${empty errors and param.success}">
            <admin:infobox type="success"><fmt:message key="system.sms.update_success" /></admin:infobox>
        </c:if>
        <p>
            <fmt:message key="system.sms.info" />
        </p>

        <form action="system-sms.jsp" method="post">
            <fmt:message key="system.sms.name" var="plaintextboxtitle"/>
            <admin:contentBox title="${plaintextboxtitle}">

                <table width="80%" cellpadding="3" cellspacing="0" border="0">
                    <tr>
                        <td width="30%" nowrap>
                            <fmt:message key="system.sms.host" />:
                        </td>
                        <td nowrap>
                            <input type="text" name="host" value="${fn:escapeXml(host)}" size="40" maxlength="150">
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['host']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.sms.valid_host" />
                            </td>
                        </tr>
                    </c:if>

                    <tr>
                        <td nowrap>
                            <fmt:message key="system.sms.port" />:
                        </td>
                        <td nowrap>
                            <input type="text" name="port" value="${fn:escapeXml(port)}" size="10" maxlength="15">
                        </td>
                    </tr>
                    <c:if test="${ not empty errors['port']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.sms.valid_port" />
                            </td>
                        </tr>
                    </c:if>

                    <!-- spacer -->
                    <tr><td colspan="2">&nbsp;</td></tr>

                    <tr>
                        <td width="30%" nowrap>
                            <fmt:message key="system.sms.systemId" />:
                        </td>
                        <td nowrap>
                            <input type="text" name="systemId" value="${fn:escapeXml(systemId)}" size="40" maxlength="150">
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['systemId']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.sms.valid_systemId" />
                            </td>
                        </tr>
                    </c:if>

                    <tr>
                        <td width="30%" nowrap>
                            <fmt:message key="system.sms.password" />:
                        </td>
                        <td nowrap>
                            <input type="password" name="password" value="${fn:escapeXml(password)}" size="40" maxlength="8">
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['password']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.sms.valid_password" />
                            </td>
                        </tr>
                    </c:if>

                    <!-- spacer -->
                    <tr><td colspan="2">&nbsp;</td></tr>

                    <tr>
                        <td width="30%" nowrap>
                            <fmt:message key="system.sms.systemType" />:
                        </td>
                        <td nowrap>
                            <input type="text" name="systemType" value="${fn:escapeXml(systemType)}" size="40" maxlength="150">
                        </td>
                    </tr>

                    <c:if test="${ not empty errors['systemType']}">
                        <tr>
                            <td nowrap>
                                &nbsp;
                            </td>
                            <td nowrap class="jive-error-text">
                                <fmt:message key="system.sms.valid_systemType" />
                            </td>
                        </tr>
                    </c:if>
                </table>

            </admin:contentBox>

            <input type="hidden" name="csrf" value="${csrf}"/>
            <input type="submit" name="save" value="<fmt:message key="system.sms.save" />">
            <input type="submit" name="test" value="<fmt:message key="system.sms.send_test" />" ${ not empty errors ? 'disabled' : ''}>
        </form>

    </body>
</html>
