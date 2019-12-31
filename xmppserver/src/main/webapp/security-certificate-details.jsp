<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp"%>

<%@ page import="org.jivesoftware.openfire.keystore.CertificateStore"%>
<%@ page import="org.jivesoftware.openfire.keystore.CertificateStoreManager"%>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.util.StringUtils"%>
<%@ page import="java.security.AlgorithmParameters" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.CertificateManager" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%!
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
%>
<%  webManager.init(request, response, session, application, out );

    final String alias            = ParamUtils.getParameter( request, "alias" );
    final String storePurposeText = ParamUtils.getParameter( request, "connectionType" );
    final boolean isTrustStore    = ParamUtils.getBooleanParameter( request, "isTrustStore" );

    final Map<String, String> errors = new HashMap<String, String>();

    ConnectionType connectionType;
    try
    {
        connectionType = ConnectionType.valueOf( storePurposeText );
    } catch (RuntimeException ex) {
        errors.put( "connectionType", ex.getMessage() );
        connectionType = null;
    }

    pageContext.setAttribute( "connectionType", connectionType );

    if (alias == null) {
        errors.put("alias", "The alias has not been specified.");
    }
    else
    {
        try
        {
            final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();
            final CertificateStore store;
            if (isTrustStore) {
                store = certificateStoreManager.getTrustStore( connectionType );
            } else {
                store = certificateStoreManager.getIdentityStore( connectionType );
            }

            // Get the certificate
            final X509Certificate certificate = (X509Certificate) store.getStore().getCertificate( alias );

            if ( certificate == null ) {
                errors.put( "alias", "alias" );
            } else {
                pageContext.setAttribute( "certificate", certificate );
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            errors.put( "type", e.getMessage() );
        }
    }

    // Handle a "go back" click:
    if ( request.getParameter( "back" ) != null ) {
        if ( isTrustStore ) {
            response.sendRedirect( "security-truststore.jsp?connectionType=" + connectionType );
        } else {
            response.sendRedirect( "security-keystore.jsp?connectionType=" + connectionType );
        }
        return;
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "alias", StringUtils.escapeHTMLTags(alias) );
%>

<html>
<head>
    <title><fmt:message key="ssl.certificate.details.title"/></title>
    <meta name="pageID" content="security-certificate-store-management"/>
    <c:choose>
        <c:when test="${isTrustStore}">
            <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-trust-store"/>
        </c:when>
        <c:otherwise>
            <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-identity-store"/>
        </c:otherwise>
    </c:choose>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'type'}">
                <fmt:message key="ssl.certificate.details.type-error"/>
            </c:when>

            <c:when test="${err.key eq 'alias'}">
                <fmt:message key="ssl.certificate.details.alias-error"/>
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

<c:if test="${empty errors}">
    <p>
        <fmt:message key="ssl.certificate.details.intro">
            <fmt:param value="${alias}"/>
            <fmt:param>
                <c:choose>
                    <c:when test="${param.type eq 'c2s'}"><fmt:message key="ssl.certificates.truststore.c2s-title"/></c:when>
                    <c:when test="${param.type eq 's2s'}"><fmt:message key="ssl.certificates.truststore.s2s-title"/></c:when>
                    <c:when test="${param.type eq 'server'}"><fmt:message key="ssl.certificates.keystore.title"/></c:when>
                </c:choose>
            </fmt:param>
        </fmt:message>
    </p>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="ssl.certificate.details.title"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td class="c1"><fmt:message key="ssl.certificates.version"/></td>
                    <td><c:out value="${certificate.version}"/></td>
                </tr>
                <tr>
                    <td class="c1"><fmt:message key="ssl.certificates.serialnumber"/></td>
                    <td><c:out value="${certificate.serialNumber}"/></td>
                </tr>
            </tbody>
        </table>
    </div>

    <br/>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th colspan="2">
                    <fmt:message key="ssl.certificates.subject"/>
                </th>
            </tr>
            </thead>
            <tbody>
                <c:forEach var="namePart" items="${admin:split(certificate.subjectX500Principal.name, '(?<!\\\\\\\\),')}">
                    <c:set var="keyValue" value="${fn:split(namePart, '=')}"/>
                    <tr>
                        <td class="c1">
                            <c:choose>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'C'}"><fmt:message key="ssl.certificates.c"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'ST'}"><fmt:message key="ssl.certificates.st"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'L'}"><fmt:message key="ssl.certificates.l"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'O'}"><fmt:message key="ssl.certificates.o"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'OU'}"><fmt:message key="ssl.certificates.ou"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'CN'}"><fmt:message key="ssl.certificates.cn"/></c:when>
                                <c:otherwise><c:out value="${keyValue[0]}"/></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:out value="${admin:replaceAll(keyValue[1], '\\\\\\\\(.)', '$1')}"/>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <c:forEach var="alternativeName" items="${certificate.subjectAlternativeNames}">

        <br/>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="ssl.certificates.subject"/> <fmt:message key="ssl.certificates.alternative-name"/>
                        <c:choose>
                            <c:when test="${alternativeName[0] eq 0}"><fmt:message key="ssl.certificates.alternative-name.other"/></c:when>
                            <c:when test="${alternativeName[0] eq 1}"><fmt:message key="ssl.certificates.alternative-name.rfc822"/></c:when>
                            <c:when test="${alternativeName[0] eq 2}"><fmt:message key="ssl.certificates.alternative-name.dns"/></c:when>
                            <c:when test="${alternativeName[0] eq 3}"><fmt:message key="ssl.certificates.alternative-name.x400"/></c:when>
                            <c:when test="${alternativeName[0] eq 4}"><fmt:message key="ssl.certificates.alternative-name.directory"/></c:when>
                            <c:when test="${alternativeName[0] eq 5}"><fmt:message key="ssl.certificates.alternative-name.edi-party"/></c:when>
                            <c:when test="${alternativeName[0] eq 6}"><fmt:message key="ssl.certificates.alternative-name.url"/></c:when>
                            <c:when test="${alternativeName[0] eq 7}"><fmt:message key="ssl.certificates.alternative-name.ip-addres"/></c:when>
                            <c:when test="${alternativeName[0] eq 8}"><fmt:message key="ssl.certificates.alternative-name.registered-id"/></c:when>
                        </c:choose>
                    </th>
                </tr>
                </thead>
                <tbody>
                <c:choose>
                    <c:when test="${alternativeName[0] eq 4}">
                        <c:forEach var="namePart" items="${admin:split(alternativeName[1], '(?<!\\\\\\\\),')}">
                            <c:set var="keyValue" value="${fn:split(namePart, '=')}"/>
                            <tr>
                                <td class="c1">
                                    <c:choose>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'C'}"><fmt:message key="ssl.certificates.c"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'ST'}"><fmt:message key="ssl.certificates.st"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'L'}"><fmt:message key="ssl.certificates.l"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'O'}"><fmt:message key="ssl.certificates.o"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'OU'}"><fmt:message key="ssl.certificates.ou"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'CN'}"><fmt:message key="ssl.certificates.cn"/></c:when>
                                        <c:otherwise><c:out value="${keyValue[0]}"/></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:out value="${admin:replaceAll(keyValue[1], '\\\\\\\\(.)', '$1')}"/>
                                </td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:when test="${alternativeName[0] eq 1 or alternativeName[0] eq 2 or alternativeName[0] eq 6 or alternativeName[0] eq 7 or alternativeName[0] eq 8}">
                        <tr><td><c:out value="${alternativeName[1]}"/></td></tr>
                    </c:when>
                    <c:otherwise>
                        <tr><td><admin:ASN1DER value="${alternativeName[1]}"/> </td></tr>
                    </c:otherwise>
                </c:choose>
                </tbody>
            </table>
        </div>

    </c:forEach>

    <br/>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th colspan="2">
                    <fmt:message key="ssl.certificates.validity"/>
                </th>
            </tr>
            </thead>
            <tbody>
                <tr>
                    <td class="c1"><fmt:message key="ssl.certificates.not-valid-before"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${certificate.notBefore gt now}">
                                <span style="color: red;">
                                    <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notBefore}"/>
                                </span>
                            </c:when>
                            <c:otherwise>
                                <span>
                                    <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notBefore}"/>
                                </span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
                <tr>
                    <td class="c1"><fmt:message key="ssl.certificates.not-valid-after"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${certificate.notAfter lt now}">
                                <span style="color: red;">
                                    <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notAfter}"/>
                                </span>
                                </c:when>
                                <c:otherwise>
                                <span>
                                    <fmt:formatDate type="DATE" dateStyle="MEDIUM" value="${certificate.notAfter}"/>
                                </span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <br/>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th colspan="2">
                    <fmt:message key="ssl.certificates.issuer"/>
                </th>
            </tr>
            </thead>
            <tbody>
                <c:forEach var="namePart" items="${admin:split(certificate.issuerX500Principal.name, '(?<!\\\\\\\\),')}">
                    <c:set var="keyValue" value="${fn:split(namePart, '=')}"/>
                    <tr>
                        <td class="c1">
                            <c:choose>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'C'}"><fmt:message key="ssl.certificates.c"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'ST'}"><fmt:message key="ssl.certificates.st"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'L'}"><fmt:message key="ssl.certificates.l"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'O'}"><fmt:message key="ssl.certificates.o"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'OU'}"><fmt:message key="ssl.certificates.ou"/></c:when>
                                <c:when test="${fn:toUpperCase(keyValue[0]) eq 'CN'}"><fmt:message key="ssl.certificates.cn"/></c:when>
                                <c:otherwise><c:out value="${keyValue[0]}"/></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:out value="${admin:replaceAll(keyValue[1], '\\\\\\\\(.)', '$1')}"/>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <c:forEach var="alternativeName" items="${certificate.issuerAlternativeNames}">

        <br/>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="ssl.certificates.issuer"/> <fmt:message key="ssl.certificates.alternative-name"/>
                        <c:choose>
                            <c:when test="${alternativeName[0] eq 0}">(Other Name)</c:when>
                            <c:when test="${alternativeName[0] eq 1}">(RFC-822 Name)</c:when>
                            <c:when test="${alternativeName[0] eq 2}">(DNS Name)</c:when>
                            <c:when test="${alternativeName[0] eq 3}">(X400 Address)</c:when>
                            <c:when test="${alternativeName[0] eq 4}">(Directory Name)</c:when>
                            <c:when test="${alternativeName[0] eq 5}">(EDI Party Name)</c:when>
                            <c:when test="${alternativeName[0] eq 6}">(Uniform Resource Identifier)</c:when>
                            <c:when test="${alternativeName[0] eq 7}">(IP Address)</c:when>
                            <c:when test="${alternativeName[0] eq 8}">(Registered ID)</c:when>
                        </c:choose>
                    </th>
                </tr>
                </thead>
                <tbody>
                <c:choose>
                    <c:when test="${alternativeName[0] eq 4}">
                        <c:forEach var="namePart" items="${admin:split(alternativeName[1], '(?<!\\\\\\\\),')}">
                            <c:set var="keyValue" value="${fn:split(namePart, '=')}"/>
                            <tr>
                                <td class="c1">
                                    <c:choose>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'C'}"><fmt:message key="ssl.certificates.c"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'ST'}"><fmt:message key="ssl.certificates.st"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'L'}"><fmt:message key="ssl.certificates.l"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'O'}"><fmt:message key="ssl.certificates.o"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'OU'}"><fmt:message key="ssl.certificates.ou"/></c:when>
                                        <c:when test="${fn:toUpperCase(keyValue[0]) eq 'CN'}"><fmt:message key="ssl.certificates.cn"/></c:when>
                                        <c:otherwise><c:out value="${keyValue[0]}"/></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:out value="${admin:replaceAll(keyValue[1], '\\\\\\\\(.)', '$1')}"/>
                                </td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:when test="${alternativeName[0] eq 1 or alternativeName[0] eq 2 or alternativeName[0] eq 6 or alternativeName[0] eq 7 or alternativeName[0] eq 8}">
                        <tr><td><c:out value="${alternativeName[1]}"/></td></tr>
                    </c:when>
                    <c:otherwise>
                        <tr><td><admin:ASN1DER value="${alternativeName[1]}"/> </td></tr>
                    </c:otherwise>
                </c:choose>
                </tbody>
            </table>
        </div>

    </c:forEach>

    <br/>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
                <tr>
                    <th colspan="2">
                        <fmt:message key="ssl.certificates.signature"/>
                    </th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td class="c1">
                        <fmt:message key="ssl.certificates.signature-algorithm"/>
                    </td>
                    <td><c:out value="${certificate.sigAlgName}"/></td>
                </tr>
                <c:if test="${not empty certificate.sigAlgParams}">
                    <%

                        final X509Certificate certificate = (X509Certificate) pageContext.getAttribute("certificate");
                        final AlgorithmParameters sigParams = AlgorithmParameters.getInstance(certificate.getSigAlgName());
                        sigParams.init( certificate.getSigAlgParams() );
                    %>
                    <tr>
                        <td class="c1"><fmt:message key="ssl.certificates.signature-algorithm-parameters"/></td>
                        <td><%= sigParams.toString() %></td>
                    </tr>
                </c:if>
                <tr valign="top">
                    <%
                        final X509Certificate certificate = (X509Certificate) pageContext.getAttribute("certificate");
                        final String hex = bytesToHex(certificate.getSignature());
                        final StringBuilder sb = new StringBuilder();
                        for (int i=0; i<hex.length(); i++) {
                            if (i != 0 && i != hex.length() - 1) {
                                if (i % 2 == 0) {
                                    sb.append(':');
                                }
                                if (i % 40 == 0) {
                                    sb.append("<br/>");
                                }
                            }
                            sb.append(hex.charAt(i));
                        }
                    %>
                    <td class="c1"><fmt:message key="ssl.certificates.signature"/></td>
                    <td style="font-family: monospace;"><%=sb.toString()%></td>
                </tr>
            </tbody>
        </table>
    </div>

    <br/>

    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th>
                    PEM representation
                </th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <%
                    final String pemRepresentation = CertificateManager.toPemRepresentation( (X509Certificate) pageContext.getAttribute( "certificate" ) );
                %>
                <td class="c1" align="center">
                    <textarea readonly cols="72" rows="<%= pemRepresentation.split( "\n" ).length + 5 %>"><%= pemRepresentation %></textarea>
                </td>
            </tr>
            </tbody>
        </table>
    </div>

    <br/>

    <form action="security-certificate-details.jsp">
        <input type="hidden" name="connectionType" value="${connectionType}"/>
        <input type="hidden" name="isTrustStore" value="<c:out value='${param.isTrustStore}'/>"/>
        <div style="text-align: center;">
            <input type="submit" name="back" value="<fmt:message key="session.details.back_button"/>">
        </div>
    </form>
</c:if>

</body>
</html>
