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
    pageContext.setAttribute( "certificateStoreManager", XMPPServer.getInstance().getCertificateStoreManager());
%>
<html>
<head>
    <title>Certificate Stores</title>
    <meta name="pageID" content="security-certificate-store-management"/>
</head>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <!--Use the template below for specific error messages. -->
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

    <c:set var="trustStore" value="${certificateStoreManager.
    <admin:contentBox title="XMPP Client Connection Stores">
        <p>
            These stores are used for regular, TCP-based client-to-server XMPP communication. Two stores are provided:
            one identity store and a trust store. Openfire ships with an empty client trust store, as in typical
            environments, certificate-based authentication of clients is not required.
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
            <tr>
                <td><label for="loc-key-socket">Identity Store:</label></td>
                <td><input id="loc-key-socket" name="loc-key-socket" type="text" size="40" value="${locKeySocket}"/></td>
                <td><a href="security-keystore.jsp?connectionType=${connectionType}">Manage Store Contents</a></td>
            </tr>
            <tr>
                <td><label for="loc-trust-socket-c2s">Trust Store:</label></td>
                <td><input id="loc-trust-socket-c2s" name="loc-trust-socket-c2s" type="text" size="40" value="${locTrustSocketC2S}"/></td>
                <td><a href="security-truststore.jsp?storeConnectionType=${connectionType}">Manage Store Contents</a></td>
            </tr>
            </tbody>
        </table>

    </admin:contentBox>

</c:forEach>

    <div class="jive-contentBoxHeader">
        BOSH (HTTP Binding) connection Stores
    </div>
    <div class="jive-contentBox">
        <p>
            These stores are used for BOSH-based XMPP communication. Two stores are provided: an identity store
            and a client trust store (a server trust store is not provided, as BOSH-based server federation is
            unsupported by Openfire).
        </p>
        <p>
            Openfire ships with an empty client trust store, as in typical environments, certificate-based authentication of
            clients is not required.
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td><label for="loc-key-bosh">Identity Store:</label></td>
                    <td><input id="loc-key-bosh" name="loc-key-bosh" type="text" size="40" value="${locKeyBosh}"/></td>
                    <td><a href="security-keystore.jsp?storeConnectionType=BOSHBASED_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-bosh-c2s">Client Trust Store:</label></td>
                    <td><input id="loc-trust-bosh-c2s" name="loc-trust-bosh-c2s" type="text" size="40" value="${locTrustBoshC2S}"/></td>
                    <td><a href="security-truststore.jsp?storeConnectionType=BOSHBASED_C2S_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
            </tbody>
        </table>
    </div>

    <div class="jive-contentBoxHeader">
        Admin Panel Stores
    </div>
    <div class="jive-contentBox">
        <p>
            These stores are used for the web-based admin panel (you're looking at it right now!). Again, two stores are
            provided an identity store and a trust store (used for optional authentication of browsers that use the admin
            panel).
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td><label for="loc-key-webadmin">Identity Store:</label></td>
                    <td><input id="loc-key-webadmin" name="loc-key-webadmin" type="text" size="40" value="${locKeyWebadmin}"/></td>
                    <td><a href="security-keystore.jsp?storeConnectionType=WEBADMIN_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-webadmin">Trust Store:</label></td>
                    <td><input id="loc-trust-webadmin" name="loc-trust-webadmin" type="text" size="40" value="${locTrustWebadmin}"/></td>
                    <td><a href="security-keystore.jsp?storeConnectionType=WEBADMIN_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
            </tbody>
        </table>
    </div>

    <div class="jive-contentBoxHeader">
        Administrative Stores
    </div>
    <div class="jive-contentBox">
        <p>
            These stores are used in communication with external servers that serves administrative purposes (such as user
            providers or databases).
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td><label for="loc-key-administrative">Identity Store:</label></td>
                    <td><input id="loc-key-administrative" name="loc-key-administrative" type="text" size="40" value="${locKeyAdministrative}"/></td>
                    <td><a href="security-keystore.jsp?storeConnectionType=ADMINISTRATIVE_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-administrative">Trust Store:</label></td>
                    <td><input id="loc-trust-administrative" name="loc-trust-administrative" type="text" size="40" value="${locTrustAdministrative}"/></td>
                    <td><a href="security-truststore.jsp?storeConnectionType=ADMINISTRATIVE_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
            </tbody>
        </table>
    </div>

</form>
-->

</body>
</html>
