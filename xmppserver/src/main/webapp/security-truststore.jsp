<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp"%>
<%@ page import="org.jivesoftware.openfire.keystore.TrustStore"%>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType"%>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    boolean delete          = ParamUtils.getBooleanParameter( request, "delete" );
    final String alias            = ParamUtils.getParameter( request, "alias" );

    final String connectionTypeText = ParamUtils.getParameter( request, "connectionType" );

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    ConnectionType connectionType = null;
    TrustStore trustStore = null;
    try
    {
        connectionType = ConnectionType.valueOf( connectionTypeText );
        trustStore = XMPPServer.getInstance().getCertificateStoreManager().getTrustStore( connectionType );
        if ( trustStore == null )
        {
            errors.put( "trustStore", "Unable to get an instance." );
        }
    }
    catch ( RuntimeException ex )
    {
        errors.put( "connectionType", ex.getMessage() );
    }

    if ( errors.isEmpty() )
    {
        pageContext.setAttribute( "connectionType", connectionType );
        pageContext.setAttribute( "trustStore", trustStore );

        final Set<ConnectionType> sameStoreConnectionTypes = Collections.EMPTY_SET; // TODO FIXME: SSLConfig.getInstance().getOtherPurposesForSameStore( connectionType );
        pageContext.setAttribute( "sameStoreConnectionTypes", sameStoreConnectionTypes );

        final Map<String, X509Certificate> certificates = trustStore.getAllCertificates();
        pageContext.setAttribute( "certificates", certificates );

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
                    trustStore.delete( alias );

                    // Log the event
                    webManager.logEvent( "deleted SSL cert from " + connectionType + " with alias " + alias, null );
                    response.sendRedirect( "security-truststore.jsp?connectionType=" + connectionType+ "&deletesuccess=true" );
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
%>

<html>
    <head>
        <title><fmt:message key="ssl.certificates.truststore.title"/></title>
        <meta name="pageID" content="security-certificate-store-management"/>
        <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-trust-store"/>
    </head>
    <body>
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'type'}">
                        <c:out value="${err.key}"/>
                        <c:if test="${not empty err.value}">
                            : <c:out value="${err.value}"/>
                        </c:if>
                    </c:when>

                    <c:otherwise>
                        <c:out value="${err.key}"/>
                        <c:if test="${not empty err.value}">
                            : <c:out value="${err.value}"/>
                        </c:if>
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>

        <c:if test="${param.deletesuccess}">
            <admin:infobox type="success"><fmt:message key="ssl.certificates.deleted"/></admin:infobox>
        </c:if>
        <c:if test="${param.importsuccess}">
            <admin:infobox type="success"><fmt:message key="ssl.certificates.added_updated"/></admin:infobox>
        </c:if>

        <c:if test="${connectionType != null}">
            <p>
                <fmt:message key="ssl.certificates.truststore.info"/>
            </p>

            <p>
                <fmt:message key="ssl.certificates.truststore.link-to-import">
                    <fmt:param value="<a href='import-truststore-certificate.jsp?connectionType=${connectionType}'>"/>
                    <fmt:param value="</a>"/>
                </fmt:message>
            </p>

            <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                    <tr>
                        <th>
                            <fmt:message key="ssl.signing-request.organization"/> <small>(<fmt:message key="ssl.certificates.alias"/>)</small>
                        </th>
                        <th width="20%">
                            <fmt:message key="ssl.certificates.valid-between"/>
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
                    <c:choose>
                        <c:when test="${empty certificates}">
                            <tr valign="top">
                                <td colspan="5"><em>(<fmt:message key="global.none"/>)</em></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <c:forEach var="certificateEntry" items="${certificates}">
                                <c:set var="certificate" value="${certificateEntry.value}"/>
                                <c:set var="alias" value="${certificateEntry.key}"/>

                                <c:set var="organization" value=""/>
                                <c:set var="commonname" value=""/>
                                <c:forEach var="subjectPart" items="${admin:split(certificate.subjectX500Principal.name, '(?<!\\\\\\\\),')}">
                                    <c:set var="keyValue" value="${fn:split(subjectPart, '=')}"/>
                                    <c:set var="key" value="${fn:toUpperCase(keyValue[0])}"/>
                                    <c:set var="value" value="${admin:replaceAll(keyValue[1], '\\\\\\\\(.)', '$1')}"/>
                                    <c:choose>
                                        <c:when test="${key eq 'O'}">
                                            <c:set var="organization" value="${organization} ${value}"/>
                                        </c:when>
                                        <c:when test="${key eq 'CN'}">
                                            <c:set var="commonname" value="${value}"/>
                                        </c:when>
                                    </c:choose>
                                </c:forEach>

                                <tr valign="top">
                                    <td>
                                        <c:url var="url" value="security-certificate-details.jsp">
                                            <c:param name="connectionType" value="${connectionType}"/>
                                            <c:param name="alias" value="${alias}"/>
                                            <c:param name="isTrustStore" value="true"/>
                                        </c:url>
                                        <a href="<c:out value="${url}"/>" title="<fmt:message key='session.row.cliked'/>">
                                            <c:choose>
                                                <c:when test="${empty fn:trim(organization)}">
                                                    <c:out value="${commonname}"/>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:out value="${organization}"/>
                                                </c:otherwise>
                                            </c:choose>
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
                                    <td width="2%">
                                        <c:out value="${certificate.publicKey.algorithm}"/>
                                    </td>
                                    <td width="1" align="center">
                                        <c:url var="url" value="security-truststore.jsp">
                                            <c:param name="connectionType" value="${connectionType}"/>
                                            <c:param name="alias" value="${alias}"/>
                                            <c:param name="delete" value="true"/>
                                            <c:param name="csrf" value="${csrf}"/>
                                        </c:url>
                                        <a href="<c:out value="${url}"/>"
                                           title="<fmt:message key="global.click_delete"/>"
                                           onclick="return confirm('<fmt:message key="ssl.certificates.confirm_delete"/>');"
                                                ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
                                    </td>
                                </tr>
                            </c:forEach>
                        </c:otherwise>
                    </c:choose>
                </tbody>
            </table>
        </c:if>
    </body>
</html>
