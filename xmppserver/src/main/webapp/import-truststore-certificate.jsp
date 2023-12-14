<%--
  -
  - Copyright (C) 2018-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page errorPage="error.jsp"%>
<%@ page import="org.jivesoftware.openfire.keystore.TrustStore"%>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.CookieUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="java.time.Duration" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%  webManager.init(request, response, session, application, out ); %>

<%  boolean save             = ParamUtils.getParameter(request, "save") != null;
    final String alias             = ParamUtils.getParameter(request, "alias");
    final String certificate       = ParamUtils.getParameter(request, "certificate");
    final String storePurposeText  = ParamUtils.getParameter(request, "connectionType");

    final Map<String, String> errors = new HashMap<>();
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

    ConnectionType connectionType;
    try
    {
        connectionType = ConnectionType.valueOf( storePurposeText );
    } catch (RuntimeException ex) {
        errors.put( "connectionType", ex.getMessage() );
        connectionType = null;
    }

    pageContext.setAttribute( "connectionType", connectionType );

    if (save && errors.isEmpty())
    {
        final TrustStore trustStoreConfig = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore( connectionType );

        if (alias == null || "".equals(alias))
        {
            errors.put("missingalias", "missingalias");
        }
        else if (trustStoreConfig.getStore().containsAlias( alias ))
        {
            // Verify that the provided alias is not already available
            errors.put("existingalias", "existingalias");
        }
        if (certificate == null || "".equals(certificate))
        {
            errors.put("certificate", "certificate-missing");
        }

        if (errors.isEmpty())
        {
            try
            {
                // When updating certificates through the admin console, do not immediately restart the website, as that
                // is very likely to lock out the administrator that is performing the changes.
                ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).pauseAutoRestartEnabled(Duration.ofMinutes(5));

                // Import certificate
                trustStoreConfig.installCertificate( alias, certificate );

                // Log the event
                webManager.logEvent("imported SSL certificate in trust store "+ storePurposeText, "alias = "+alias);

                response.sendRedirect( "security-truststore.jsp?connectionType=" + storePurposeText + "&importsuccess=true" );
                return;
            }
            catch (Throwable e)
            {
                e.printStackTrace();
                errors.put("import", e.getMessage());
            }
        }
    }
%>

<html>
<head>
    <title><fmt:message key="ssl.import.certificate.truststore.boxtitle"/></title>
    <meta name="pageID" content="security-certificate-store-management"/>
    <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-trust-store"/>
</head>
<body>

<% pageContext.setAttribute("errors", errors); %>
<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'connectionType'}">
                <fmt:message key="ssl.import.certificate.truststore.error.connection-type"/>
            </c:when>

            <c:when test="${err.key eq 'missingalias'}">
                <fmt:message key="ssl.import.certificate.truststore.error.alias-missing"/>
            </c:when>

            <c:when test="${err.key eq 'existingalias'}">
                <fmt:message key="ssl.import.certificate.truststore.error.alias-exists"/>
            </c:when>

            <c:when test="${err.key eq 'certificate'}">
                <fmt:message key="ssl.import.certificate.truststore.error.certificate"/>
            </c:when>

            <c:when test="${err.key eq 'import'}">
                <fmt:message key="ssl.import.certificate.truststore.error.import"/>
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

<c:if test="${not empty connectionType}">
    <p>
        <fmt:message key="ssl.import.certificate.truststore.intro"/>
    </p>

    <!-- BEGIN 'Import Certificate' -->
    <form action="import-truststore-certificate.jsp?connectionType=${connectionType}" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <fmt:message key="ssl.import.certificate.truststore.boxtitle" var="title"/>
        <admin:contentBox title="${title}">
            <table>
                <tr>
                    <td style="width: 1%; white-space: nowrap" class="c1">
                        <label for="alias"><fmt:message key="ssl.signing-request.alias"/></label>
                    </td>
                    <td>
                        <input type="text" size="30" maxlength="100" name="alias" id="alias" value="<c:out value='${param.alias}'/>">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap" class="c1">
                        <label for="certificate"><fmt:message key="ssl.import.certificate.keystore.certificate"/></label>
                    </td>
                    <td>
                        <textarea name="certificate" id="certificate" cols="80" rows="20" wrap="virtual"><c:if test="${not empty param.certificate}"><c:out value="${param.certificate}"/></c:if></textarea>
                    </td>
                </tr>
            </table>
        </admin:contentBox>

        <input type="submit" name="save" value="<fmt:message key="global.save"/>">
    </form>
    <!-- END 'Import Certificate' -->
</c:if>

</body>
</html>
