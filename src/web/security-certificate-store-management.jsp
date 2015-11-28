<%@ page errorPage="error.jsp"%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.keystore.CertificateStoreManager" %>
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
    <title>Certificate Stores</title>
    <meta name="pageID" content="security-certificate-store-management"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'template'}">
                An unexpected error occurred.
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
    Certificates are used (through TLS and SSL protocols) to establish secure connections between servers and clients.
    When a secured connection is being created, parties can retrieve a certificate from the other party and (amongst
    others) examine the issuer of those certificates. If the issuer is trusted, a secured layer of communication can be
    established.
</p>
<p>
    Certificates are kept in specialized repositories, or 'stores'. Openfire provides two types of stores:
    <ul>
        <li><em>Identity stores</em> are used to store certificates that identify this instance of Openfire. On request,
            they certificates from these stores are transmitted to other parties which use them to identify your server.
        </li>
        <li><em>Trust stores</em> contain certificates that identify parties that you choose to trust. Trust stores often do
            not include the certificate from the remote party directly, but instead holds certificates from organizations
            that are trusted to identify the certificate of the remote party. Such organizations are commonly referred to as
            "Certificate Authorities".
        </li>
    </ul>
</p>
<p>
    This section of the admin panel is dedicated to management of the various key and trust stores that act as
    repositories for sets of security certificates. By default, a small set of stores is re-used for various purposes,
    but Openfire allows you to configure a distinct set of stores for each connection type.
</p>

<c:forEach items="${connectionTypes}" var="connectionType">

    <c:set var="title">
        <c:choose>
            <c:when test="${connectionType eq 'SOCKET_C2S'}">XMPP Client Stores</c:when>
            <c:when test="${connectionType eq 'SOCKET_S2S'}">Server Federation Stores</c:when>
            <c:when test="${connectionType eq 'BOSH_C2S'}">BOSH (HTTP Binding) Stores</c:when>
            <c:when test="${connectionType eq 'WEBADMIN'}">Admin Console Stores</c:when>
            <c:when test="${connectionType eq 'COMPONENT'}">External Component Stores</c:when>
            <c:when test="${connectionType eq 'CONNECTION_MANAGER'}">Connection Manager Stores</c:when>
        </c:choose>
    </c:set>

    <c:set var="description">
        <c:choose>
            <c:when test="${connectionType eq 'SOCKET_C2S'}">
                These stores are used for regular, TCP-based client-to-server XMPP communication. Two stores are provided:
                one identity store and a trust store. Openfire ships with an empty trust store, as in typical
                environments, certificate-based authentication of clients is not required.
            </c:when>
            <c:when test="${connectionType eq 'SOCKET_S2S'}">
                These stores are used for erver-to-server XMPP communication, which establishes server federation.
                Two stores are provided: one identity store and a trust store. Openfire ships with a trust store filled
                with certificates of generally accepted certificate authorities.
            </c:when>
            <c:when test="${connectionType eq 'BOSH_C2S'}">
                These stores are used for BOSH-based XMPP communication. Two stores are provided: an identity store
                and a client trust store.
            </c:when>
            <c:when test="${connectionType eq 'WEBADMIN'}">
                These stores are used for the web-based admin console (you're looking at it right now!). Again, two stores are
                provided an identity store and a trust store (used for optional authentication of browsers that use the admin
                panel).
            </c:when>
            <c:when test="${connectionType eq 'COMPONENT'}">
                These stores are used to establish connections with external components.
            </c:when>
            <c:when test="${connectionType eq 'CONNECTION_MANAGER'}">
                These stores are used to establish connections with Openfire Connection Managers.
            </c:when>
        </c:choose>
    </c:set>

    <admin:contentBox title="${title}">
        <p>
            <c:out value="${description}"/>
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
            <tr>
                <td><label for="loc-key-socket">Identity Store:</label></td>
                <td><input id="loc-key-socket" name="loc-key-socket" type="text" size="80" readonly value="${certificateStoreManager.getIdentityStore(connectionType).configuration.file}"/></td>
                <td><a href="security-keystore.jsp?connectionType=${connectionType}">Manage Store Contents</a></td>
            </tr>
            <tr>
                <td><label for="loc-trust-socket-c2s">Trust Store:</label></td>
                <td><input id="loc-trust-socket-c2s" name="loc-trust-socket-c2s" type="text" size="80" readonly value="${certificateStoreManager.getTrustStore(connectionType).configuration.file}"/></td>
                <td><a href="security-truststore.jsp?connectionType=${connectionType}">Manage Store Contents</a></td>
            </tr>
            </tbody>
        </table>

    </admin:contentBox>

</c:forEach>

</body>
</html>
