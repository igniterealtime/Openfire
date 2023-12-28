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
<%@page import="org.jivesoftware.util.StringUtils"%>
<%@page import="org.jivesoftware.util.CertificateManager"%>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page errorPage="error.jsp" %>

<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="org.jivesoftware.openfire.keystore.IdentityStore" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.*" %>
<%@ page import="java.time.Duration" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="now" class="java.util.Date"/>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init(request, response, session, application, out); %>

<% // Get parameters:
    boolean generate          = ParamUtils.getBooleanParameter(request, "generate");
    boolean delete            = ParamUtils.getBooleanParameter(request, "delete");
    boolean importReply       = ParamUtils.getBooleanParameter(request, "importReply");
    final String alias              = ParamUtils.getParameter( request, "alias" );
    final String connectionTypeText = ParamUtils.getParameter( request, "connectionType" );

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (generate |  delete | importReply) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            generate = false;
            delete = false;
            importReply = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    ConnectionType connectionType = null;
    IdentityStore identityStore = null;
    try
    {
        connectionType = ConnectionType.valueOf( connectionTypeText );
        identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( connectionType );
        if ( identityStore == null )
        {
            errors.put( "identityStore", "Unable to get an instance." );
        }
    }
    catch (RuntimeException ex)
    {
        errors.put( "connectionType", ex.getMessage() );
    }

    if ( errors.isEmpty() )
    {
        pageContext.setAttribute( "connectionType", connectionType );
        pageContext.setAttribute( "identityStore", identityStore );

        final Set<ConnectionType> sameStoreConnectionTypes = Collections.emptySet(); // TODO FIXME: SSLConfig.getInstance().getOtherPurposesForSameStore( connectionType );
        pageContext.setAttribute( "sameStoreConnectionTypes", sameStoreConnectionTypes );

        final Map<String, X509Certificate> certificates = identityStore.getAllCertificates();
        pageContext.setAttribute( "certificates", certificates );

        pageContext.setAttribute( "validCert", identityStore.containsAllIdentityCertificate() );
        pageContext.setAttribute( "allIDCert", identityStore.containsAllIdentityCertificate() );

        if ( delete )
        {
            if ( alias == null )
            {
                errors.put( "alias", "The alias has not been specified." );
            }
            else
            {
                try
                {
                    // When updating certificates through the admin console, do not cause changes to restart the website, as
                    // that is very likely to log out the administrator that is performing the changes. As the keystore change
                    // event is async, this line disables restarting the plugin for a few minutes.
                    ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).pauseAutoRestartEnabled(Duration.ofMinutes(5));

                    identityStore.delete( alias );

                    // Log the event
                    webManager.logEvent( "deleted SSL cert from " + connectionType + " with alias " + alias, null );
                    response.sendRedirect( "security-keystore.jsp?connectionType=" + connectionType+ "&deletesuccess=true" );
                    return;
                }
                catch ( Exception e )
                {
                    errors.put( "delete", e.getMessage() );
                }
            }
        }
    }

    pageContext.setAttribute( "errors", errors );


    if (generate) {
        try {
            // When updating certificates through the admin console, do not cause changes to restart the website, as
            // that is very likely to log out the administrator that is performing the changes. As the keystore change
            // event is async, this line disables restarting the plugin for a few minutes.
            ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).pauseAutoRestartEnabled(Duration.ofMinutes(5));

            if (!identityStore.containsAllIdentityCertificate()) {
                identityStore.addSelfSignedDomainCertificate();

                // Save new certificates into the key store
                identityStore.persist();

                // Log the event
                webManager.logEvent("generated SSL self-signed cert", null);
            }

            response.sendRedirect("security-keystore.jsp?connectionType="+connectionType);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            errors.put("generate", e.getMessage());
        }
    }

    if (importReply) {
        String reply = ParamUtils.getParameter(request, "reply");
        if (alias != null && reply != null && reply.trim().length() > 0) {
            try {
                // When updating certificates through the admin console, do not cause changes to restart the website, as
                // that is very likely to log out the administrator that is performing the changes. As the keystore change
                // event is async, this line disables restarting the plugin for a few minutes.
                ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).pauseAutoRestartEnabled(Duration.ofMinutes(5));

                identityStore.installCSRReply(alias, reply);
                identityStore.persist();
                // Log the event
                webManager.logEvent( "imported SSL certificate with alias " + alias, null );
                response.sendRedirect("security-keystore.jsp?connectionType="+connectionType);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                errors.put("importReply", e.getMessage());
            }
        }
    }

    final boolean restartNeeded = ( (AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin( "admin" ) ).isRestartNeeded();
    pageContext.setAttribute( "restartNeeded", restartNeeded );

    boolean offerUpdateIssuer = false;
    Map<String, String> signingRequests = new LinkedHashMap<>();
%>

<html>
    <head>
        <title><fmt:message key="ssl.certificates.keystore.title"/></title>
        <meta name="pageID" content="security-certificate-store-management"/>
        <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-identity-store"/>
    </head>
    <body>
        <c:if test="${restartNeeded}">
            <admin:infobox type="warning">
                <fmt:message key="ssl.certificates.keystore.restart_server">
                    <fmt:param value="<a href='server-restart.jsp?page=security-keystore.jsp&connectionType=${connectionType}'>"/>
                    <fmt:param value="</a>"/>
                </fmt:message>
            </admin:infobox>
        </c:if>

        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
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

        <c:choose>
            <c:when test="${not validCert}">
                <admin:infobox type="warning">
                    <fmt:message key="ssl.certificates.keystore.no_installed">
                        <fmt:param value="<a href='security-keystore.jsp?csrf=${csrf}&generate=true&connectionType=${connectionType}'>"/>
                        <fmt:param value="</a>"/>
                        <fmt:param value="<a href='import-keystore-certificate.jsp?connectionType=${connectionType}'>"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </admin:infobox>
            </c:when>
            <c:when test="${not allIDCert}">
                <admin:infobox type="info">
                    <fmt:message key="ssl.certificates.keystore.no_complete_installed">
                        <fmt:param value="<a href='security-keystore.jsp?csrf=${csrf}&generate=true&connectionType=${connectionType}'>"/>
                        <fmt:param value="</a>"/>
                        <fmt:param value="<a href='import-keystore-certificate.jsp?connectionType=${connectionType}'>"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </admin:infobox>
            </c:when>

        </c:choose>

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
                <fmt:param value="<a href='import-keystore-certificate.jsp?connectionType=${connectionType}'>"/>
                <fmt:param value="</a>"/>
            </fmt:message>
        </p>

        <table class="jive-table" style="width: 100%">
            <thead>
                <tr>
                    <th>
                        <fmt:message key="ssl.certificates.identity"/>
                        <small>(<fmt:message key="ssl.certificates.alias"/>)</small>
                    </th>
                    <th style="width: 20%">
                        <fmt:message key="ssl.certificates.valid-between"/>
                    </th>
                    <th colspan="2">
                        <fmt:message key="ssl.certificates.status"/>
                    </th>
                    <th>
                        <fmt:message key="ssl.certificates.algorithm"/>
                    </th>
                    <th style="width: 1%">
                        <fmt:message key="global.delete"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <c:choose>
                    <c:when test="${empty certificates}">
                        <tr>
                            <td colspan="5"><em>(<fmt:message key="global.none"/>)</em></td>
                        </tr>
                    </c:when>
                    <c:otherwise>
                        <c:forEach var="certificateEntry" items="${certificates}">
                            <c:set var="certificate" value="${certificateEntry.value}"/>
                            <c:set var="alias" value="${certificateEntry.key}"/>
                            <c:set var="identities" value="${admin:serverIdentities(certificateEntry.value)}"/>
                            <%
                            String rowAlias = (String) pageContext.getAttribute("alias");
                            X509Certificate certificate = (X509Certificate) pageContext.getAttribute("certificate");
                            
                              boolean isSelfSigned = CertificateManager.isSelfSignedCertificate(certificate);
                              boolean isSigningPending = CertificateManager.isSigningRequestPending(certificate);

                              offerUpdateIssuer = offerUpdateIssuer || isSelfSigned || isSigningPending;

                              if (isSigningPending) {
                                  // Generate new signing request for certificate
                                  signingRequests.put(rowAlias, identityStore.generateCSR(rowAlias));
                              }

                            %>
                            <tr>
                                <td>
                                    <a href="security-certificate-details.jsp?connectionType=${connectionType}&alias=${alias}&isTrustStore=false" title="<fmt:message key='session.row.click'/>">
                                        <c:forEach items="${identities}" var="currentItem" varStatus="stat">
                                            <c:out value="${stat.first ? '' : ','} ${currentItem}"/>
                                        </c:forEach>
                                        (<c:out value="${alias}"/>)
                                    </a>
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
                                <td style="width: 1%"><img src="images/certificate_warning-16x16.png"
                                                    alt="<fmt:message key="ssl.certificates.keystore.self-signed.info"/>"
                                                    title="<fmt:message key="ssl.certificates.keystore.self-signed.info"/>"></td>
                                <td style="width: 1%; white-space: nowrap">
                                    <fmt:message key="ssl.certificates.self-signed"/>
                                </td>
                                <% } else if (isSigningPending) { %>
                                <td style="width: 1%"><img src="images/certificate_warning-16x16.png"
                                                    alt="<fmt:message key="ssl.certificates.keystore.signing-pending.info"/>"
                                                    title="<fmt:message key="ssl.certificates.keystore.signing-pending.info"/>"></td>
                                <td style="width: 1%; white-space: nowrap">
                                    <fmt:message key="ssl.certificates.signing-pending"/>
                                </td>
                                <% } else { %>
                                <td style="width: 1%"><img src="images/certificate_ok-16x16.png"
                                                    alt="<fmt:message key="ssl.certificates.keystore.ca-signed.info"/>"
                                                    title="<fmt:message key="ssl.certificates.keystore.ca-signed.info"/>"></td>
                                <td style="width: 1%; white-space: nowrap">
                                    <fmt:message key="ssl.certificates.ca-signed"/>
                                </td>
                                <% } %>
                                <td style="width: 2%">
                                    <c:out value="${certificate.publicKey.algorithm}"/>
                                </td>
                                <td style="width: 1%; text-align: center">
                                    <a href="security-keystore.jsp?csrf=${csrf}&alias=${alias}&connectionType=${connectionType}&delete=true"
                                       title="<fmt:message key="global.click_delete"/>"
                                       onclick="return confirm('<fmt:message key="ssl.certificates.confirm_delete"/>');"
                                            ><img src="images/delete-16x16.gif" alt=""></a>
                                </td>
                            </tr>

                            <% if (isSigningPending) { %>
                            <form action="security-keystore.jsp?connectionType=${connectionType}" method="post">
                                <input type="hidden" name="csrf" value="${csrf}">
                                <input type="hidden" name="importReply" value="true">
                                <input type="hidden" name="alias" value="${alias}">
                                <tr>
                                    <td colspan="5">
                                  <span class="jive-description">
                                  <label for="reply"><fmt:message key="ssl.certificates.truststore.ca-reply"/></label>
                                  </span>
                                        <textarea id="reply" name="reply" rows="8" style="width:100%;font-size:8pt;" wrap="virtual"></textarea>
                                    </td>
                                    <td style="vertical-align: bottom">
                                        <input type="submit" name="install" value="<fmt:message key="global.save"/>">
                                    </td>
                                </tr>
                            </form>
                            <% } %>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
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
            <fmt:param value="<a href='security-keystore-signing-request.jsp?connectionType=${connectionType}'>"/>
            <fmt:param value="</a>"/>
        </fmt:message>
    </p>
    <% } %>
    <% if (!signingRequests.isEmpty()) { %>
    <p>
        <fmt:message key="ssl.signing-request.requests_info"/>
    </p>
    <table>
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
            <td style="vertical-align: top">
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
<form action="/security-certificate-store-management.jsp">
    <input type="submit" name="done" value="<fmt:message key="global.done" />">
</form>
    </body>
</html>
