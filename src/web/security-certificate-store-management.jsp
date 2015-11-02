<%@ page errorPage="error.jsp"%>

<%@ page import="org.jivesoftware.openfire.net.SSLConfig"%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.keystore.Purpose" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init( request, response, session, application, out );

    // Read parameters
    final boolean save                       = request.getParameter("save") != null;
    // TODO actually save something!

    // Pre-update property values
    final Map<String, String> errors = new HashMap<>();

    pageContext.setAttribute( "errors", errors );
%>

<html>
<head>
    <title>Certificate Stores</title>
    <meta name="pageID" content="security-certificate-store-management"/>
</head>
<>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:if test="${not empty err.value}">
            <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
        </c:if>
        (<c:out value="${err.key}"/>)
    </admin:infobox>
</c:forEach>

<c:if test="${param.success}">
    <admin:infobox type="success">Settings Updated Successfully</admin:infobox>
</c:if>
<c:if test="${param.noChange}">
    <admin:infobox type="info">The provided settings were no different than before. Nothing changed.</admin:infobox>
</c:if>

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
    but Openfire allows you to configure a distinct set of stores for each type. To do so, please change the store
    locations below.
</p>

<form action="security-certificate-store-management.jsp" method="post">

    <div class="jive-contentBoxHeader">
        Regular XMPP connection Stores
    </div>
    <div class="jive-contentBox">
        <p>
            These stores are used for regular, TCP-based XMPP communication. Three stores are provided: one identity store
            and two trust stores. One of the trust stores applies to server-to-server federation. The other trust store
            applies to the optional client-based mutual authentication feature in Openfire.
        </p>
        <p>
            Openfire ships with an empty client trust store, as in typical environments, certificate-based authentication of
            clients is not required.
        </p>

        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td><label for="loc-key-socket">Identity Store:</label></td>
                    <td><input id="loc-key-socket" name="loc-key-socket" type="text" size="40" value="${locKeySocket}"/></td>
                    <td><a href="security-keystore.jsp?storePurpose=SOCKETBASED_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-socket-s2s">Server Trust Store:</label></td>
                    <td><input id="loc-trust-socket-s2s" name="loc-trust-socket-s2s" type="text" size="40" value="${locTrustSocketS2S}"/></td>
                    <td><a href="security-truststore.jsp?storePurpose=SOCKETBASED_S2S_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-socket-c2s">Client Trust Store:</label></td>
                    <td><input id="loc-trust-socket-c2s" name="loc-trust-socket-c2s" type="text" size="40" value="${locTrustSocketC2S}"/></td>
                    <td><a href="security-truststore.jsp?storePurpose=SOCKETBASED_C2S_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
            </tbody>
        </table>
    </div>

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
                    <td><a href="security-keystore.jsp?storePurpose=BOSHBASED_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-bosh-c2s">Client Trust Store:</label></td>
                    <td><input id="loc-trust-bosh-c2s" name="loc-trust-bosh-c2s" type="text" size="40" value="${locTrustBoshC2S}"/></td>
                    <td><a href="security-truststore.jsp?storePurpose=BOSHBASED_C2S_TRUSTSTORE">Manage Store Contents</a></td>
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
                    <td><a href="security-keystore.jsp?storePurpose=WEBADMIN_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-webadmin">Trust Store:</label></td>
                    <td><input id="loc-trust-webadmin" name="loc-trust-webadmin" type="text" size="40" value="${locTrustWebadmin}"/></td>
                    <td><a href="security-keystore.jsp?storePurpose=WEBADMIN_TRUSTSTORE">Manage Store Contents</a></td>
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
                    <td><a href="security-keystore.jsp?storePurpose=ADMINISTRATIVE_IDENTITYSTORE">Manage Store Contents</a></td>
                </tr>
                <tr>
                    <td><label for="loc-trust-administrative">Trust Store:</label></td>
                    <td><input id="loc-trust-administrative" name="loc-trust-administrative" type="text" size="40" value="${locTrustAdministrative}"/></td>
                    <td><a href="security-truststore.jsp?storePurpose=ADMINISTRATIVE_TRUSTSTORE">Manage Store Contents</a></td>
                </tr>
            </tbody>
        </table>
    </div>

    <!-- TODO enable me <input type="submit" name="save" value="<fmt:message key="global.save_settings" />"> -->
</form>

</body>
</html>
