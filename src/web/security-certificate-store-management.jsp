<%@ page errorPage="error.jsp"%>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    final Map<String, String> errors = new HashMap<>();
    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "connectionTypes", ConnectionType.values() );
    pageContext.setAttribute( "certificateStoreManager", XMPPServer.getInstance().getCertificateStoreManager() );
%>
<html>
<head>
    <title><fmt:message key="ssl.certificates.store-management.title"/></title>
    <meta name="pageID" content="security-certificate-store-management"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'template'}">
                <fmt:message key="admin.error"/>
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
    <fmt:message key="ssl.certificates.store-management.info-1"/>
</p>
<p>
    <fmt:message key="ssl.certificates.store-management.info-2"/>
</p>
<p>
<fmt:message key="ssl.certificates.store-management.info-3"/></p>

<c:forEach items="${connectionTypes}" var="connectionType">

    <c:set var="title">
        <c:choose>
            <c:when test="${connectionType eq 'SOCKET_C2S'}"><fmt:message key="ssl.certificates.store-management.socket-c2s-stores.title"/></c:when>
            <c:when test="${connectionType eq 'SOCKET_S2S'}"><fmt:message key="ssl.certificates.store-management.socket-s2s-stores.title"/></c:when>
            <c:when test="${connectionType eq 'BOSH_C2S'}"><fmt:message key="ssl.certificates.store-management.bosh-c2s-stores.title"/></c:when>
            <c:when test="${connectionType eq 'WEBADMIN'}"><fmt:message key="ssl.certificates.store-management.admin-console-stores.title"/></c:when>
            <c:when test="${connectionType eq 'COMPONENT'}"><fmt:message key="ssl.certificates.store-management.component-stores.title"/></c:when>
            <c:when test="${connectionType eq 'CONNECTION_MANAGER'}"><fmt:message key="ssl.certificates.store-management.connection-manager-stores.title"/></c:when>
        </c:choose>
    </c:set>

    <c:set var="description">
        <c:choose>
            <c:when test="${connectionType eq 'SOCKET_C2S'}"><fmt:message key="ssl.certificates.store-management.socket-c2s-stores.info"/></c:when>
            <c:when test="${connectionType eq 'SOCKET_S2S'}"><fmt:message key="ssl.certificates.store-management.socket-s2s-stores.info"/></c:when>
            <c:when test="${connectionType eq 'BOSH_C2S'}"><fmt:message key="ssl.certificates.store-management.bosh-c2s-stores.info"/></c:when>
            <c:when test="${connectionType eq 'WEBADMIN'}"><fmt:message key="ssl.certificates.store-management.admin-console-stores.info"/></c:when>
            <c:when test="${connectionType eq 'COMPONENT'}"><fmt:message key="ssl.certificates.store-management.component-stores.info"/></c:when>
            <c:when test="${connectionType eq 'CONNECTION_MANAGER'}"><fmt:message key="ssl.certificates.store-management.connection-manager-stores.info"/></c:when>
        </c:choose>
    </c:set>

    <admin:contentBox title="${title}">
        <p>
            <c:out value="${description}"/>
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
            <tr>
                <td><label for="loc-key-socket"><fmt:message key="ssl.certificates.identity-store"/>:</label></td>
                <td><input id="loc-key-socket" name="loc-key-socket" type="text" size="80" readonly value="${certificateStoreManager.getIdentityStore(connectionType).configuration.file}"/></td>
                <td><a href="security-keystore.jsp?connectionType=${connectionType}"><fmt:message key="ssl.certificates.store-management.manage"/></a></td>
            </tr>
            <tr>
                <td><label for="loc-trust-socket-c2s"><fmt:message key="ssl.certificates.trust-store"/>:</label></td>
                <td><input id="loc-trust-socket-c2s" name="loc-trust-socket-c2s" type="text" size="80" readonly value="${certificateStoreManager.getTrustStore(connectionType).configuration.file}"/></td>
                <td><a href="security-truststore.jsp?connectionType=${connectionType}"><fmt:message key="ssl.certificates.store-management.manage"/></a></td>
            </tr>
            </tbody>
        </table>

    </admin:contentBox>

</c:forEach>

</body>
</html>
