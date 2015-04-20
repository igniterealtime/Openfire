<%@ page errorPage="error.jsp"%>

<%@ page import="org.jivesoftware.openfire.net.SSLConfig"%>
<%@ page import="org.jivesoftware.util.CertificateManager"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="java.security.KeyStore"%>
<%@ page import="java.security.cert.X509Certificate"%>
<%@ page import="java.util.Enumeration"%>
<%@ page import="java.util.HashMap"%>

<%@ page import="java.util.Map"%>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    boolean delete = ParamUtils.getBooleanParameter(request, "delete");
    String type = ParamUtils.getParameter(request, "type");
    String alias = ParamUtils.getParameter(request, "alias");
    Map<String, Exception> errors = new HashMap<String, Exception>();

    KeyStore store = null;

    if (type == null) {
        errors.put("type", new Exception("The store type has not been specified."));
    } else {
        try {
            switch (type) {
                case "s2s":
                    store = SSLConfig.gets2sTrustStore();
                    break;
                case "c2s":
                    store = SSLConfig.getc2sTrustStore();
                    break;
                default:
                    throw new Exception("Unknown store type: " + type);
            }
        } catch (Exception e) {
            errors.put("type", e);
        }
    }

    if (delete) {
        if (store != null && alias != null) {
            try {
                CertificateManager.deleteCertificate(store, alias);
                SSLConfig.saveStores();

                // Log the event
                webManager.logEvent("deleted SSL cert from "+type+"-truststore with alias "+alias, null);
                response.sendRedirect("security-truststore.jsp?type="+type+"&deletesuccess=true");
                return;
            }
            catch (Exception e) {
                errors.put("delete", e);
            }
        }
    }
%>

<html>
<head>
  <title><fmt:message key="ssl.certificates.truststore.${param.type}-title"/></title>
  <meta name="pageID" content="security-truststore-${param.type}"/>
</head>
<body>

<% pageContext.setAttribute("errors", errors); %>
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

  <% if (type != null && store != null) { %>
  <p>
      <fmt:message key="ssl.certificates.truststore.${param.type}-intro"/>
  </p>
  <p>
      <fmt:message key="ssl.certificates.general-usage"/>
  </p>
  <p>
      <fmt:message key="ssl.certificates.truststore.${param.type}-info">
          <fmt:param value="<a href='ssl-settings.jsp'>"/>
          <fmt:param value="</a>"/>
      </fmt:message>
  </p>
  <p>
      <fmt:message key="ssl.certificates.truststore.link-to-import">
          <fmt:param value="<a href='import-truststore-certificate.jsp?type=${param.type}'>"/>
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
      <%  if (store != null && store.aliases().hasMoreElements()) {
              for (Enumeration aliases = store.aliases(); aliases.hasMoreElements();) {
                  String a = (String) aliases.nextElement();
                  X509Certificate certificate = (X509Certificate) store.getCertificate(a);

                  pageContext.setAttribute("alias", a);
                  pageContext.setAttribute("certificate", certificate);
      %>
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
              <a href="security-certificate-details.jsp?type=${param.type}&alias=${alias}" title="<fmt:message key='session.row.cliked'/>">
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
              <a href="security-truststore.jsp?alias=${alias}&type=${param.type}&delete=true"
                 title="<fmt:message key="global.click_delete"/>"
                 onclick="return confirm('<fmt:message key="ssl.certificates.confirm_delete"/>');"
                      ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
          </td>
      </tr>
      <%
              }
          } else {
      %>
      <tr valign="top">
          <td colspan="5"><em>(<fmt:message key="global.none"/>)</em></td>
      </tr>
      <% } %>
      </tbody>
  </table>

  <% } %>

  </body>
</html>
