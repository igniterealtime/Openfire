<%@ page errorPage="error.jsp"%>

<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="org.jivesoftware.openfire.net.SSLConfig"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.security.KeyStore" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="javax.xml.bind.DatatypeConverter" %>
<%@ page import="java.security.AlgorithmParameters" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    final String type  = ParamUtils.getParameter(request, "type");
    final String alias = ParamUtils.getParameter(request, "alias");

    final Map<String, String> errors = new HashMap<String, String>();

    KeyStore store = null;

    if (type == null)
    {
        errors.put("type", "The store type has not been specified.");
    }
    else if (alias == null) {
        errors.put("alias", "The alias has not been specified.");
    }
    else
    {
        try
        {
            switch (type)
            {
                case "s2s":
                    store = SSLConfig.gets2sTrustStore();
                    break;

                case "c2s":
                    store = SSLConfig.getc2sTrustStore();
                    break;

                case "server":
                    store = SSLConfig.getKeyStore();
                    break;

                default:
                    throw new Exception("Unknown store type: " + type);
            }

            // Get the certificate
            final X509Certificate certificate = (X509Certificate) store.getCertificate(alias);

            if (certificate == null) {
                errors.put("alias", "alias");
            } else {
                pageContext.setAttribute("certificate", certificate);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            errors.put("type", e.getMessage());
        }
    }

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        if ("server".equals(type)) {
            response.sendRedirect("security-keystore.jsp");
        } else {
            response.sendRedirect("security-truststore.jsp?type=" + type);
        }
        return;
    }

    pageContext.setAttribute("errors", errors);
%>

<html>
<head>
    <title><fmt:message key="ssl.certificate.details.title"/></title>
    <c:choose>
        <c:when test="${param.type eq 'server'}">
            <meta name="pageID" content="security-keystore"/>
        </c:when>
        <c:otherwise>
            <meta name="pageID" content="security-truststore-${param.type}"/>
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
            <fmt:param value="${param.alias}"/>
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
                        final String hex = DatatypeConverter.printHexBinary( certificate.getSignature());
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

    <form action="security-certificate-details.jsp">
        <input type="hidden" name="type" value="${param.type}"/>
        <div style="text-align: center;">
            <input type="submit" name="back" value="<fmt:message key="session.details.back_button"/>">
        </div>
    </form>
</c:if>

</body>
</html>
