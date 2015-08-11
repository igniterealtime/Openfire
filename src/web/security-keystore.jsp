<%@ page errorPage="error.jsp" %>

<%@ page import="org.jivesoftware.util.CertificateManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.net.SSLConfig" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.KeyStore" %>
<%@ page import="java.security.PrivateKey" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.container.PluginManager" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="java.io.IOException" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="now" class="java.util.Date"/>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init(request, response, session, application, out); %>

<% // Get parameters:
    boolean generate = ParamUtils.getBooleanParameter(request, "generate");
    boolean delete = ParamUtils.getBooleanParameter(request, "delete");
    boolean importReply = ParamUtils.getBooleanParameter(request, "importReply");
    String alias = ParamUtils.getParameter(request, "alias");
    Map<String, String> errors = new HashMap<String, String>();
    KeyStore keyStore = null;
    KeyStore s2sTrustStore = null;
    KeyStore c2sTrustStore = null;

    try {
        keyStore = SSLConfig.getKeyStore();
        s2sTrustStore = SSLConfig.gets2sTrustStore();
        c2sTrustStore = SSLConfig.getc2sTrustStore();
    } catch (IOException e) {
        e.printStackTrace();
        errors.put("ioerror", e.getMessage());
    }

    if (generate) {
        String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        try {
            if (errors.containsKey("ioerror") && keyStore == null) {
                keyStore = SSLConfig.initializeKeyStore();
            }
            if (errors.containsKey("ioerror") || !CertificateManager.isDSACertificate(keyStore, domain)) {
                CertificateManager
                        .createDSACert(keyStore, SSLConfig.getKeyPassword(), domain + "_dsa", "cn=" + domain, "cn=" + domain, "*." + domain);
            }
            if (errors.containsKey("ioerror") || !CertificateManager.isRSACertificate(keyStore, domain)) {
                CertificateManager
                        .createRSACert(keyStore, SSLConfig.getKeyPassword(), domain + "_rsa", "cn=" + domain, "cn=" + domain, "*." + domain);
            }
            // Save new certificates into the key store
            SSLConfig.saveStores();
            // Log the event
            webManager.logEvent("generated SSL self-signed certs", null);
            response.sendRedirect("security-keystore.jsp");
            return;
        } catch (Exception e) {
            e.printStackTrace();
            errors.put("generate", e.getMessage());
        }
    }
    if (delete) {
        if (alias != null) {
            try {
                CertificateManager.deleteCertificate(keyStore, alias);
                SSLConfig.saveStores();
                // Log the event
                webManager.logEvent("deleted SSL cert with alias " + alias, null);
                response.sendRedirect("security-keystore.jsp");
                return;
            } catch (Exception e) {
                e.printStackTrace();
                errors.put("delete", e.getMessage());
            }
        }
    }
    if (importReply) {
        String reply = ParamUtils.getParameter(request, "reply");
        if (alias != null && reply != null && reply.trim().length() > 0) {
            try {
                CertificateManager.installReply(keyStore, s2sTrustStore,
                        SSLConfig.getKeyPassword(), alias, new ByteArrayInputStream(reply.getBytes()), true, true);
                SSLConfig.saveStores();
                // Log the event
                webManager.logEvent("imported SSL certificate with alias " + alias, null);
                response.sendRedirect("security-keystore.jsp");
                return;
            } catch (Exception e) {
                e.printStackTrace();
                errors.put("importReply", e.getMessage());
            }
        }
    }

    PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
%>

<html>
<head>
    <title><fmt:message key="ssl.certificates.keystore.title"/></title>
    <meta name="pageID" content="security-keystore"/>
</head>
<body>

<% if (((AdminConsolePlugin) pluginManager.getPlugin("admin")).isRestartNeeded()) { %>
<admin:infobox type="warning">
    <fmt:message key="ssl.certificates.keystore.restart_server">
        <fmt:param value="<a href='server-restart.jsp?page=security-keystore.jsp'>"/>
        <fmt:param value="</a>"/>
    </fmt:message>
</admin:infobox>
<% } %>

<% pageContext.setAttribute("errors", errors); %>
<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'ioerror'}">
                <fmt:message key="ssl.certificates.keystore.io_error"/>
            </c:when>
            <c:when test="${err.key eq 'importReply'}">
                <fmt:message key="ssl.certificates.keystore.error_importing-reply"/>
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
<%
    if (keyStore != null) {
        if (keyStore.size() > 1 && !CertificateManager.isRSACertificate(keyStore, XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
%>
<admin:infobox type="warning"><fmt:message key="index.certificate-warning"/></admin:infobox>
<%  } else if (keyStore.size() < 2) { %>
<admin:infobox type="warning">
    <fmt:message key="ssl.certificates.keystore.no_installed">
        <fmt:param value="<a href='security-keystore.jsp?generate=true'>"/>
        <fmt:param value="</a>"/>
        <fmt:param value="<a href='import-keystore-certificate.jsp'>"/>
        <fmt:param value="</a>"/>
    </fmt:message>
</admin:infobox>
<% }} %>

<c:if test="${param.addupdatesuccess}"><admin:infobox type="success"><fmt:message key="ssl.certificates.added_updated"/></admin:infobox></c:if>
<c:if test="${param.generatesuccess}"><admin:infobox type="success"><fmt:message key="ssl.certificates.generated"/></admin:infobox></c:if>
<c:if test="${param.deletesuccess}"><admin:infobox type="success"><fmt:message key="ssl.certificates.deleted"/></admin:infobox></c:if>
<c:if test="${param.issuerUpdated}"><admin:infobox type="success"><fmt:message key="ssl.certificates.keystore.issuer-updated"/></admin:infobox></c:if>
<c:if test="${param.importsuccess}"><admin:infobox type="success"><fmt:message key="ssl.certificates.keystore.ca-reply-imported"/></admin:infobox></c:if>

<!-- BEGIN 'Installed Certificates' -->
<p>
    <fmt:message key="ssl.certificates.keystore.intro"/>
</p>

<p>
    <fmt:message key="ssl.certificates.general-usage"/>
</p>

<p>
    <fmt:message key="ssl.certificates.keystore.info">
        <fmt:param value="<a href='import-keystore-certificate.jsp'>"/>
        <fmt:param value="</a>"/>
    </fmt:message>
</p>

<table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
    <thead>
    <tr>
        <th>
            <fmt:message key="ssl.certificates.identity"/>
            <small>(<fmt:message key="ssl.certificates.alias"/>)</small>
        </th>
        <th width="20%">
            <fmt:message key="ssl.certificates.valid-between"/>
        </th>
        <th colspan="2">
            <fmt:message key="ssl.certificates.status"/>
        </th>
        <th>
            <fmt:message key="ssl.certificates.algorithm"/>
        </th>
        <th width="1%">
            <fmt:message key="global.delete"/>
        </th>
    </tr>
    </thead>
    <tbody>

    <% int i = 0;
        boolean offerUpdateIssuer = false;
        Map<String, String> signingRequests = new LinkedHashMap<String, String>();
        if (keyStore != null && keyStore.aliases().hasMoreElements()) {
            for (Enumeration aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
                i++;
                String a = (String) aliases.nextElement();
                X509Certificate c = (X509Certificate) keyStore.getCertificate(a);
                StringBuffer identities = new StringBuffer();
                for (String identity : CertificateManager.getServerIdentities(c)) {
                    identities.append(identity).append(", ");
                }
                if (identities.length() > 0) {
                    identities.setLength(identities.length() - 2);
                }
                // Self-signed certs are certs generated by Openfire whose IssueDN equals SubjectDN
                boolean isSelfSigned = CertificateManager.isSelfSignedCertificate(keyStore, a);
                // Signing Request pending = not self signed certs whose chain has only 1 cert (the same cert)
                boolean isSigningPending = CertificateManager.isSigningRequestPending(keyStore, a);

                offerUpdateIssuer = offerUpdateIssuer || isSelfSigned || isSigningPending;
                if (isSigningPending) {
                    // Generate new signing request for certificate
                    PrivateKey privKey = (PrivateKey) keyStore.getKey(a, SSLConfig.getKeyPassword().toCharArray());
                    if (privKey != null) {
                        signingRequests.put(a, CertificateManager.createSigningRequest(c, privKey));
                    }
                }
                pageContext.setAttribute("identities", identities);
                pageContext.setAttribute("alias", a);
                pageContext.setAttribute("certificate", c);
    %>
    <tr valign="top">
        <td>
            <a href="security-certificate-details.jsp?type=server&alias=${alias}"
               title="<fmt:message key='session.row.cliked'/>">
                <c:out value="${identities}"/>
            </a>
            <small>(<c:out value="${alias}"/>)</small>
        </td>
        <td>
            <c:choose>
                <c:when test="${certificate.notAfter lt now or certificate.notBefore gt now}">
                  <span style="color: red;">
                      <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notBefore}"/>
                      -
                      <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notAfter}"/>
                  </span>
                </c:when>
                <c:otherwise>
                  <span>
                      <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notBefore}"/>
                      -
                      <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notAfter}"/>
                  </span>
                </c:otherwise>
            </c:choose>
        </td>
        <% if (isSelfSigned && !isSigningPending) { %>
        <td width="1%"><img src="images/certificate_warning-16x16.png" width="16" height="16" border="0"
                            alt="<fmt:message key="ssl.certificates.keystore.self-signed.info"/>"
                            title="<fmt:message key="ssl.certificates.keystore.self-signed.info"/>"></td>
        <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.self-signed"/>
        </td>
        <% } else if (isSigningPending) { %>
        <td width="1%"><img src="images/certificate_warning-16x16.png" width="16" height="16" border="0"
                            alt="<fmt:message key="ssl.certificates.keystore.signing-pending.info"/>"
                            title="<fmt:message key="ssl.certificates.keystore.signing-pending.info"/>"></td>
        <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.signing-pending"/>
        </td>
        <% } else { %>
        <td width="1%"><img src="images/certificate_ok-16x16.png" width="16" height="16" border="0"
                            alt="<fmt:message key="ssl.certificates.keystore.ca-signed.info"/>"
                            title="<fmt:message key="ssl.certificates.keystore.ca-signed.info"/>"></td>
        <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.ca-signed"/>
        </td>
        <% } %>
        <td width="2%">
            <c:out value="${certificate.publicKey.algorithm}"/>
        </td>
        <td width="1" align="center">
            <a href="security-keystore.jsp?alias=${alias}&delete=true"
               title="<fmt:message key="global.click_delete"/>"
               onclick="return confirm('<fmt:message key="ssl.certificates.confirm_delete"/>');"
                    ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
        </td>
    </tr>
    <% if (isSigningPending) { %>
    <form action="security-keystore.jsp" method="post">
        <input name="importReply" type="hidden" value="true">
        <input name="alias" type="hidden" value="${alias}">
        <tr id="pk<%=i%>">
            <td colspan="5">
          <span class="jive-description">
          <fmt:message key="ssl.certificates.truststore.ca-reply"/>
          </span>
                <textarea name="reply" cols="40" rows="3" style="width:100%;font-size:8pt;" wrap="virtual"></textarea>
            </td>
            <td valign="bottom">
                <input type="submit" name="install" value="<fmt:message key="global.save"/>">
            </td>
        </tr>
    </form>
    <% } } } else { %>
    <tr valign="top">
        <td colspan="5"><em>(<fmt:message key="global.none"/>)</em></td>
    </tr>
    <% } %>

    </tbody>
</table>
<!-- END 'Installed Certificates' -->
<!-- BEGIN 'Signing request' -->
<% if (offerUpdateIssuer || !signingRequests.isEmpty()) { %>
<br>

<div class="jive-contentBoxHeader">
    <fmt:message key="ssl.signing-request.title"/>
</div>
<div class="jive-contentBox">
    <% if (offerUpdateIssuer) { %>
    <p>
        <fmt:message key="ssl.signing-request.offer-issuer-information">
            <fmt:param value="<a href='ssl-signing-request.jsp'>"/>
            <fmt:param value="</a>"/>
        </fmt:message>
    </p>
    <% } %>
    <% if (!signingRequests.isEmpty()) { %>
    <p>
        <fmt:message key="ssl.signing-request.requests_info"/>
    </p>
    <table cellpadding="3" cellspacing="2" border="0">
        <thead>
        <tr>
            <th>
                <fmt:message key="ssl.signing-request.alias"/>
            </th>
            <th>
                <fmt:message key="ssl.signing-request.signing-request"/>
            </th>
        </tr>
        </thead>
        <tbody>
        <% for (Map.Entry<String, String> entry : signingRequests.entrySet()) { %>
        <tr>
            <td valign="top">
                <%= entry.getKey() %>
            </td>
            <td style="font-family: monospace;">
                <%= StringUtils.escapeHTMLTags(entry.getValue()) %>
            </td>
        </tr>
        <% } %>
        </tbody>
    </table>
    <% } %>
</div>
<% } %>
<!-- END 'Signing request' -->
</body>
</html>
