<%--
  -
  - Copyright (C) 2007-2008 Jive Software, 2018-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.keystore.IdentityStore" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="java.time.Duration" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"  %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters:
    boolean save            = ParamUtils.getParameter(request, "save") != null;
    final String privateKey       = ParamUtils.getParameter(request, "privateKey");
    final String passPhrase       = ParamUtils.getParameter(request, "passPhrase");
    final String certificate      = ParamUtils.getParameter(request, "certificate");
    final String storePurposeText = ParamUtils.getParameter(request, "connectionType");

    final Map<String, String> errors = new HashMap<>();

    ConnectionType connectionType;
    try
    {
        connectionType = ConnectionType.valueOf( storePurposeText );
    } catch (RuntimeException ex) {
        errors.put( "connectionType", ex.getMessage() );
        connectionType = null;
    }
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (save) {
        if (privateKey == null || privateKey.trim().isEmpty() ) {
            errors.put("privateKey", "privateKey");
        }
        if (certificate == null || certificate.trim().isEmpty() ) {
            errors.put("certificate", "certificate");
        }
        if (errors.isEmpty()) {
            try {
                // When updating certificates through the admin console, do not immediately restart the website, as that
                // is very likely to lock out the administrator that is performing the changes.
                XMPPServer.getInstance().getPluginManager().getPluginByCanonicalName("admin")
                    .ifPresent(plugin -> ((AdminConsolePlugin) plugin).pauseAutoRestartEnabled(Duration.ofMinutes(5)));

                final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( connectionType );

                // Import certificate
                final String alias = identityStore.installCertificate( certificate, privateKey, passPhrase);

                // Log the event
                webManager.logEvent("imported SSL certificate in identity store "+ connectionType, "alias = "+alias);

                response.sendRedirect("security-keystore.jsp?connectionType="+connectionType+"&addupdatesuccess=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("import", e.getMessage());
            }
        }
    }

    pageContext.setAttribute( "connectionType", connectionType );
    pageContext.setAttribute( "errors", errors );
%>

<html>
<head>
      <title><fmt:message key="ssl.import.certificate.keystore.boxtitle"/></title>
      <meta name="pageID" content="security-certificate-store-management"/>
      <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-identity-store"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'privateKey'}">
                <fmt:message key="ssl.import.certificate.keystore.error.private-key"/>
            </c:when>

            <c:when test="${err.key eq 'certificate'}">
                <fmt:message key="ssl.import.certificate.keystore.error.certificate"/>
            </c:when>

            <c:when test="${err.key eq 'import'}">
                <fmt:message key="ssl.import.certificate.keystore.error.import"/>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
            </c:when>

            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<p>
    <fmt:message key="ssl.import.certificate.keystore.info">
        <fmt:param value="<a href='http://java.sun.com/javase/downloads/index.jsp'>" />
        <fmt:param value="</a>" />
    </fmt:message>
</p>

<!-- BEGIN 'Import Private Key and Certificate' -->
<form action="import-keystore-certificate.jsp?connectionType=${connectionType}" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <c:set var="title"><fmt:message key="ssl.import.certificate.keystore.private-key.title"/></c:set>
    <admin:contentBox title="${title}">
        <p><fmt:message key="ssl.import.certificate.keystore.private-key.info"/></p>
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap" class="c1">
                    <label for="passPhrase"><fmt:message key="ssl.import.certificate.keystore.pass-phrase" /></label>
                </td>
                <td>
                    <input type="text" size="60" maxlength="200" name="passPhrase" id="passPhrase" value="<c:out value="${param.passPhrase}"/>">
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap" class="c1">
                    <label for="privateKey"><fmt:message key="ssl.import.certificate.keystore.private-key" /></label>
                </td>
                <td>
                    <textarea name="privateKey" id="privateKey" cols="80" rows="15" wrap="virtual"><c:if test="${not empty param.privateKey}"><c:out value="${param.privateKey}"/></c:if></textarea>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <c:set var="title"><fmt:message key="ssl.import.certificate.keystore.certificate.title"/></c:set>
    <admin:contentBox title="${title}">
        <p><fmt:message key="ssl.import.certificate.keystore.certificate.info"/></p>
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap" class="c1">
                    <label for="certificate"><fmt:message key="ssl.import.certificate.keystore.certificate" /></label>
                </td>
                <td>
                    <textarea name="certificate" id="certificate" cols="80" rows="15" wrap="virtual"><c:if test="${not empty param.certificate}"><c:out value="${param.certificate}"/></c:if></textarea>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" name="save" value="<fmt:message key="global.save" />">
</form>
<!-- END 'Import Private Key and Certificate' -->

</body>
</html>
