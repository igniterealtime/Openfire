<%--
  -
  - Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.keystore.CertificateStoreManager" %>
<%@ page import="org.jivesoftware.openfire.keystore.CertificateStoreConfiguration" %>
<%@ page import="java.io.File" %>
<%@ page import="org.jivesoftware.admin.CertificateStorePasswordField" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.*" %>
<%@ page import="static org.jivesoftware.openfire.spi.ConnectionType.SOCKET_C2S" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();

    final Map<String, String> errors = new HashMap<>();
    pageContext.setAttribute( "errors", errors );
    // Store files that could not be opened with the password the administrator entered.
    final Set<String> wrongPasswordFiles = new LinkedHashSet<>();
    pageContext.setAttribute( "wrongPasswordFiles", wrongPasswordFiles );
    // Store files that could not be opened with the current password, when no new password was entered (typically
    // after a change of location to a file with a different password).
    final Set<String> currentPasswordFailedFiles = new LinkedHashSet<>();
    pageContext.setAttribute( "currentPasswordFailedFiles", currentPasswordFailedFiles );

    boolean showAll = certificateStoreManager.usesDistinctConfigurationForEachType();
    if ( !showAll )
    {
        // Allow 'showAll' to be overridden, even when the current configuration does not call for an 'expanded' view.
        showAll = ParamUtils.getBooleanParameter( request, "showAll", false );
    }

    // OF-1415: Show distinct boxes for all connection types, but only when their configuration differs!
    pageContext.setAttribute( "showAll", showAll );
    pageContext.setAttribute( "connectionTypes", ConnectionType.values() );
    pageContext.setAttribute( "defaultClientConnectionType", SOCKET_C2S );
    pageContext.setAttribute( "defaultServerConnectionType", ConnectionType.SOCKET_S2S );
    pageContext.setAttribute( "certificateStoreManager", certificateStoreManager );

    boolean update = request.getParameter("update") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if ( update ) {
        final String cType = request.getParameter( "connectionType" );
        final boolean updateAll = "all".equals( cType );

        Set<ConnectionType> toUpdate = new HashSet<>();
        if ( updateAll )
        {
            toUpdate.addAll( Arrays.asList( ConnectionType.values() ) );
        }
        else
        {
            try
            {
                toUpdate.add( ConnectionType.valueOf( cType ) );
            }
            catch ( IllegalArgumentException ex )
            {
                errors.put( "connectionType", ex.getMessage() );
            }
        }

        final String locKey = request.getParameter( "loc-key" );
        final String pwdKey = request.getParameter( "pwd-key" );
        final String locTrustS2S = request.getParameter( "loc-trust-s2s" );
        final String pwdTrustS2S = request.getParameter( "pwd-trust-s2s" );
        final String locTrustC2S = request.getParameter( "loc-trust-c2s" );
        final String pwdTrustC2S = request.getParameter( "pwd-trust-c2s" );

        // The page never sends a store's current password to the browser. A password field is submitted only when the
        // administrator revealed it with its "Update password" link, so a missing or empty field keeps the current
        // password of each store (see CertificateStorePasswordField). Only the locations are mandatory.
        if ( locKey == null || locKey.isEmpty() ) {
            errors.put( "locKey", "Identity Store location must be defined." );
        }

        for ( final ConnectionType connectionType : toUpdate )
        {
            if ( connectionType.isClientOriented() )
            {
                if ( locTrustC2S == null || locTrustC2S.isEmpty() )
                {
                    errors.put( "locTrustC2S", "Trust Store (C2S) location must be defined." );
                }
            }
            else
            {
                if ( locTrustS2S == null || locTrustS2S.isEmpty() )
                {
                    errors.put( "locTrustS2S", "Trust Store (S2S) location must be defined." );
                }
            }
        }

        if ( errors.isEmpty() ) {
            for ( final ConnectionType connectionType : toUpdate )
            {
                String storeBeingReplaced = null;
                boolean passwordEntered = false;
                try
                {
                    final String locTrust;
                    final String pwdTrust;
                    if ( connectionType.isClientOriented() )
                    {
                        locTrust = locTrustC2S;
                        pwdTrust = pwdTrustC2S;
                    }
                    else
                    {
                        locTrust = locTrustS2S;
                        pwdTrust = pwdTrustS2S;
                    }

                    final char[] keyPassword = CertificateStorePasswordField.passwordToSave( pwdKey, certificateStoreManager.getIdentityStoreConfiguration( connectionType ).getPassword() );
                    final char[] trustPassword = CertificateStorePasswordField.passwordToSave( pwdTrust, certificateStoreManager.getTrustStoreConfiguration( connectionType ).getPassword() );

                    final File backupKey = new File( CertificateStoreManager.getIdentityStoreBackupDirectory( connectionType ) );
                    final File backupTrust = new File( CertificateStoreManager.getTrustStoreBackupDirectory( connectionType ) );

                    final CertificateStoreConfiguration configKey = new CertificateStoreConfiguration( CertificateStoreManager.getIdentityStoreType(connectionType), new File( locKey ), keyPassword, backupKey );
                    final CertificateStoreConfiguration configTrust = new CertificateStoreConfiguration( CertificateStoreManager.getTrustStoreType(connectionType), new File( locTrust ), trustPassword, backupTrust );
                    storeBeingReplaced = locKey;
                    passwordEntered = pwdKey != null && !pwdKey.isEmpty();
                    certificateStoreManager.replaceIdentityStore( connectionType, configKey, false );
                    storeBeingReplaced = locTrust;
                    passwordEntered = pwdTrust != null && !pwdTrust.isEmpty();
                    certificateStoreManager.replaceTrustStore( connectionType, configTrust, false );
                }
                catch ( Exception ex )
                {
                    // A store that fails to load leaves the manager unchanged, so its previous settings stay in use.
                    if ( storeBeingReplaced != null && CertificateStorePasswordField.isWrongPassword( ex ) ) {
                        ( passwordEntered ? wrongPasswordFiles : currentPasswordFailedFiles ).add( storeBeingReplaced );
                    } else {
                        errors.put( "update", ex.getMessage() );
                    }
                }
            }

            // Log the event
            webManager.logEvent("Updated certificate store configuration", Arrays.toString( toUpdate.toArray() ) );

            // Redirect only on success: a store that fails to load (for example a wrong password) must be reported,
            // not followed by a success message.
            if ( errors.isEmpty() && wrongPasswordFiles.isEmpty() && currentPasswordFailedFiles.isEmpty() ) {
                response.sendRedirect("security-certificate-store-management.jsp?updated=true&showAll=" + showAll );
                return;
            }

            // A partial failure can leave the connection types with different stores, so decide the view again from
            // the configuration as it is now.
            showAll = showAll || certificateStoreManager.usesDistinctConfigurationForEachType();
            pageContext.setAttribute( "showAll", showAll );
        }
    }
%>
<html>
<head>
    <title><fmt:message key="ssl.certificates.store-management.title"/></title>
    <meta name="pageID" content="security-certificate-store-management"/>
    <style>
        /* The console aligns table labels to text-bottom, which lifts "Password:" above the link beside it, and
           centres cells, which drops it below the field once the hint adds a line. Align both on the first line. */
        .jive-contentBox table td.store-password-label label { vertical-align: baseline; }
        .jive-contentBox table td.store-password-label, .jive-contentBox table td.store-password { vertical-align: baseline; }
        .store-password-hint { display: block; color: #666; }
    </style>
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

<c:forEach var="wrongPasswordFile" items="${wrongPasswordFiles}">
    <admin:infobox type="error">
        <fmt:message key="ssl.certificates.store-management.password_wrong">
            <fmt:param><c:out value="${wrongPasswordFile}"/></fmt:param>
        </fmt:message>
    </admin:infobox>
</c:forEach>

<c:forEach var="currentPasswordFailedFile" items="${currentPasswordFailedFiles}">
    <admin:infobox type="error">
        <fmt:message key="ssl.certificates.store-management.password_current_wrong">
            <fmt:param><c:out value="${currentPasswordFailedFile}"/></fmt:param>
        </fmt:message>
    </admin:infobox>
</c:forEach>

<!-- Display success report, but only if there were no errors. -->
<c:if test="${param.updated and empty errors and empty wrongPasswordFiles and empty currentPasswordFailedFiles}">
    <admin:infoBox type="success">
        <fmt:message key="ssl.certificates.store-management.saved_successfully"/>
    </admin:infoBox>
</c:if>

<p>
    <fmt:message key="ssl.certificates.store-management.info-1"/>
</p>
<p>
    <fmt:message key="ssl.certificates.store-management.info-2"/>
</p>
<p>
    <fmt:message key="ssl.certificates.store-management.info-3"/>
    <c:if test="${not showAll}">
        <fmt:message key="ssl.certificates.store-management.info-4"/>
    </c:if>
</p>

<c:choose>
    <c:when test="${showAll}">
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

            <form action="security-certificate-store-management.jsp" method="post">
                <input type="hidden" name="csrf" value="${csrf}">
                <input type="hidden" name="connectionType" value="${connectionType}"/>

                <admin:contentBox title="${title}">
                    <p>
                        <c:out value="${description}"/>
                    </p>

                    <admin:identityStoreConfig connectionType="${connectionType}"/>

                    <br/>

                    <admin:trustStoreConfig connectionType="${connectionType}"/>

                    <br/>

                    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

                </admin:contentBox>

            </form>

        </c:forEach>

    </c:when>
    <c:otherwise>

        <c:set var="title">
            <fmt:message key="ssl.certificates.store-management.combined-stores.title"/>
        </c:set>

        <c:set var="description">
            <fmt:message key="ssl.certificates.store-management.combined-stores.info"/>
        </c:set>

        <form action="security-certificate-store-management.jsp" method="post">
            <input type="hidden" name="csrf" value="${csrf}">
            <input type="hidden" name="connectionType" value="all"/>

            <admin:contentBox title="${title}">
                <p>
                    <c:out value="${description}"/>
                </p>

                <c:set var="connectionType" value="SOCKET_S2S"/>
                <admin:identityStoreConfig connectionType="${connectionType}"/>

                <br/>

                <c:set var="connectionType" value="SOCKET_S2S"/>
                <admin:trustStoreConfig connectionType="${connectionType}"/>

                <br/>

                <c:set var="connectionType" value="SOCKET_C2S"/>
                <admin:trustStoreConfig connectionType="${connectionType}"/>

                <br/>

                <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

            </admin:contentBox>

        </form>
    </c:otherwise>
</c:choose>

<script>
    // Each store's new-password field starts hidden and disabled, so it is not submitted and the current password is
    // kept. "Update password" reveals and enables it; "Cancel" clears, disables and hides it again. Elements are found
    // per table cell rather than by id, because the per-connection-type view repeats the same ids in every form.
    document.querySelectorAll( 'td.store-password' ).forEach( function ( cell ) {
        const status = cell.querySelector( '.store-password-status' );
        const edit = cell.querySelector( '.store-password-edit' );
        const field = edit.querySelector( 'input[type="password"]' );
        const change = status.querySelector( '.store-password-change' );
        const cancel = edit.querySelector( '.store-password-cancel' );

        const show = function ( editing ) {
            field.value = '';
            field.disabled = !editing;
            status.hidden = editing;
            edit.hidden = !editing;
            ( editing ? field : change ).focus();
        };

        // These links act as buttons: Enter activates a link natively, Space needs handling.
        [ [ change, true ], [ cancel, false ] ].forEach( function ( pair ) {
            pair[0].addEventListener( 'click', function ( event ) {
                event.preventDefault();
                show( pair[1] );
            } );
            pair[0].addEventListener( 'keydown', function ( event ) {
                if ( event.key === ' ' ) {
                    event.preventDefault();
                    show( pair[1] );
                }
            } );
        } );
    } );
</script>
</body>
</html>
