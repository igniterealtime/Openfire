<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
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
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.filetransfer.proxy.FileTransferProxy" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init( request, response, session, application, out ); %>

<%
    Map<String, String> errors = new HashMap<String, String>();
    FileTransferProxy transferProxy = XMPPServer.getInstance().getFileTransferProxy();

    boolean isUpdated = request.getParameter( "update" ) != null;
    boolean isProxyEnabled = ParamUtils.getBooleanParameter( request, "proxyEnabled" );
    String hardcodedAddress = ParamUtils.getParameter( request, "hardcodedAddress" );
    int port = ParamUtils.getIntParameter( request, "port", 0 );
    Cookie csrfCookie = CookieUtils.getCookie( request, "csrf" );
    String csrfParam = ParamUtils.getParameter( request, "csrf" );

    if ( isUpdated )
    {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) )
        {
            isUpdated = false;
            errors.put( "csrf", "CSRF Failure!" );
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie( request, response, "csrf", csrfParam, -1 );
    pageContext.setAttribute( "csrf", csrfParam );

    if ( isUpdated )
    {
        if ( hardcodedAddress != null && !hardcodedAddress.matches( "^[A-Za-z0-9-\\.]+$" ) )
        {
            errors.put( "address", "" );
        }

        if ( port <= 0 )
        {
            errors.put( "port", "" );
        }

        if ( errors.isEmpty() )
        {
            JiveGlobals.setProperty( "xmpp.proxy.externalip", hardcodedAddress );
            if ( isProxyEnabled )
            {
                transferProxy.setProxyPort( port );
            }
            transferProxy.enableFileTransferProxy( isProxyEnabled );

            // Log the event
            webManager.logEvent( "edited file transfer proxy settings", "port = " + port + "\nhardcodedAddress = " + hardcodedAddress + "\nenabled = " + isProxyEnabled );
        }
    }

    if ( errors.isEmpty() )
    {
        port = transferProxy.getProxyPort();
    }
    else
    {
        isUpdated = false;
        if ( port == 0 )
        {
            port = transferProxy.getProxyPort();
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "isUpdated", isUpdated );
    pageContext.setAttribute( "port", port );
    pageContext.setAttribute( "hardcodedAddress", JiveGlobals.getProperty( "xmpp.proxy.externalip" ) );
    pageContext.setAttribute( "fileTransferProxy", XMPPServer.getInstance().getFileTransferProxy() );
%>

<html>
<head>
    <title><fmt:message key="filetransferproxy.settings.title"/></title>
</head>
<meta name="pageID" content="server-transfer-proxy"/>
<body>

<p>
    <fmt:message key="filetransferproxy.settings.info"/>
</p>

<c:forEach var="err" items="${errors}" varStatus="varStatus">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'port'}">
                <fmt:message key="filetransferproxy.settings.valid.port"/>
            </c:when>

            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
    <c:if test="${not varStatus.last}">
        <br/>
    </c:if>
</c:forEach>

<c:if test="${isUpdated}">
    <admin:infobox type="success">
        <fmt:message key="filetransferproxy.settings.confirm.updated"/>
    </admin:infobox>
</c:if>
<br/>

<!-- BEGIN 'Proxy Service' -->
<c:set var="title"><fmt:message key="filetransferproxy.settings.enabled.legend"/></c:set>
<form action="file-transfer-proxy.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <admin:contentBox title="${title}">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="proxyEnabled" value="true" id="rb02" ${fileTransferProxy.proxyEnabled ? 'checked' : ''}>
                </td>
                <td width="99%">
                    <label for="rb02"><b><fmt:message key="filetransferproxy.settings.label_enable"/></b>- <fmt:message key="filetransferproxy.settings.label_enable_info"/></label>
                    <table border="0">
                        <tr>
                            <td><label for="port"><fmt:message key="filetransferproxy.settings.label_port"/></label></td>
                            <td><input type="text" size="5" maxlength="10" id="port" name="port" value="${port}"></td>
                        </tr>
                        <tr>
                            <td><label for="hardcodedAddress"><fmt:message key="filetransferproxy.settings.label_hardcoded_address"/></label></td>
                            <td>
                                <input type="text" size="40" maxlength="255" id="hardcodedAddress" name="hardcodedAddress" value="${hardcodedAddress}"> <fmt:message key="filetransferproxy.settings.label_hardcoded_optionality"/>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="proxyEnabled" value="false" id="rb01" ${fileTransferProxy.proxyEnabled ? '' : 'checked'}>
                </td>
                <td width="99%">
                    <label for="rb01"><b><fmt:message key="filetransferproxy.settings.label_disable"/></b> - <fmt:message key="filetransferproxy.settings.label_disable_info"/></label>
                </td>
            </tr>
            </tbody>
        </table>
    </admin:contentBox>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Proxy Service' -->


</body>
</html>
