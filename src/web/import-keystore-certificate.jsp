<%@ page import="org.jivesoftware.util.CertificateManager,
                org.jivesoftware.util.ParamUtils,
                org.jivesoftware.util.StringUtils,
                org.jivesoftware.openfire.XMPPServer,
                org.jivesoftware.openfire.net.SSLConfig,
                java.io.ByteArrayInputStream,
                java.util.HashMap,
                java.util.Map"
         errorPage="error.jsp"%>
<%@ page import="java.security.KeyStore" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters:
    boolean save = ParamUtils.getParameter(request, "save") != null;
    String privateKey = ParamUtils.getParameter(request, "private-key");
    String passPhrase = ParamUtils.getParameter(request, "passPhrase");
    String certificate = ParamUtils.getParameter(request, "certificate");

    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        if (privateKey == null || "".equals(privateKey)) {
            errors.put("privateKey", "privateKey");
        }
        if (certificate == null || "".equals(certificate)) {
            errors.put("certificate", "certificate");
        }
        if (errors.isEmpty()) {
            try {
                KeyStore keystore;
                try {
                    keystore = SSLConfig.getKeyStore();
                }
                catch (Exception e) {
                    keystore = SSLConfig.initializeKeyStore();
                }
                // Create an alias for the signed certificate
                String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                int index = 1;
                String alias = domain + "_" + index;
                while (keystore.containsAlias(alias)) {
                    index = index + 1;
                    alias = domain + "_" + index;
                }
                // Import certificate
                CertificateManager.installCert(keystore, SSLConfig.gets2sTrustStore(),
                        SSLConfig.getKeyPassword(), alias, new ByteArrayInputStream(privateKey.getBytes()), passPhrase,
                        new ByteArrayInputStream(certificate.getBytes()), true, true);
                // Save keystore
                SSLConfig.saveStores();
                // Log the event
                webManager.logEvent("imported SSL certificate", "alias = "+alias);
                response.sendRedirect("security-keystore.jsp");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("import", e.getMessage());
            }
        }
    }
%>

<html>
  <head>
      <title><fmt:message key="ssl.import.certificate.keystore.title"/></title>
      <meta name="pageID" content="security-keystore"/>
  </head>
  <body>

  <% pageContext.setAttribute("errors", errors); %>
  <c:forEach var="err" items="${errors}">
      <admin:infobox type="error">
          <c:choose>
              <c:when test="${err.key eq 'privateKey'}">
                  <fmt:message key="ssl.import.certificate.keystore.error.private-key"/>
              </c:when>

              <c:when test="${err.key eq 'certificate'}">
                  <fmt:message key="ssl.import.certificate.keystore.error.certificate"/>
              </c:when>

              <c:when test="${err.key eq 'import'}">
                  <fmt:message key="ssl.import.certificate.keystore.error.import"/>
                  <c:if test="${not empty err.value}">
                      <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                  </c:if>
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
  <fmt:message key="ssl.import.certificate.keystore.info">
      <fmt:param value="<a href='http://java.sun.com/javase/downloads/index.jsp'>" />
      <fmt:param value="</a>" />
  </fmt:message>
  </p>

  <!-- BEGIN 'Import Private Key and Certificate' -->
  <form action="import-keystore-certificate.jsp" method="post" name="f">
      <div class="jive-contentBoxHeader">
          <fmt:message key="ssl.import.certificate.keystore.boxtitle" />
      </div>
      <div class="jive-contentBox">
          <table cellpadding="3" cellspacing="0" border="0">
          <tbody>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.keystore.pass-phrase" />
                  </td>
                  <td width="99%">
                      <input type="text" size="30" maxlength="100" name="passPhrase">
                  </td>
              </tr>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.keystore.private-key" />
                  </td>
                  <td width="99%">
                      <textarea name="private-key" cols="60" rows="5" wrap="virtual"></textarea>
                  </td>
              </tr>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.keystore.certificate" />
                  </td>
                  <td width="99%">
                      <textarea name="certificate" cols="60" rows="5" wrap="virtual"></textarea>
                  </td>
              </tr>
          </tbody>
          </table>
      </div>
      <input type="submit" name="save" value="<fmt:message key="global.save" />">
  </form>
  <!-- END 'Import Private Key and Certificate' -->

  </body>
</html>
