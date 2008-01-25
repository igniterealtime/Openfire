<%@ page import="org.jivesoftware.util.CertificateManager,
                org.jivesoftware.util.ParamUtils,
                org.jivesoftware.openfire.XMPPServer,
                org.jivesoftware.openfire.net.SSLConfig,
                java.io.ByteArrayInputStream,
                java.util.HashMap,
                java.util.Map"
         errorPage="error.jsp"%>
<%@ page import="java.security.KeyStore" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%--
  Created by IntelliJ IDEA.
  User: gato
  Date: Nov 7, 2006
  Time: 10:03:19 AM
  To change this template use File | Settings | File Templates.
--%>
<% // Get parameters:
    boolean save = ParamUtils.getParameter(request, "save") != null;
    String privateKey = ParamUtils.getParameter(request, "private-key");
    String passPhrase = ParamUtils.getParameter(request, "passPhrase");
    String certificate = ParamUtils.getParameter(request, "certificate");

    Map<String, Object> errors = new HashMap<String, Object>();
    if (save) {
        if (privateKey == null || "".equals(privateKey)) {
            errors.put("privateKey", "privateKey");
        }
        if (certificate == null || "".equals(certificate)) {
            errors.put("certificate", "certificate");
        }
        if (errors.isEmpty()) {
            try {
                // Create an alias for the signed certificate
                String domain = XMPPServer.getInstance().getServerInfo().getName();
                int index = 1;
                String alias = domain + "_" + index;
                while (SSLConfig.getKeyStore().containsAlias(alias)) {
                    index = index + 1;
                    alias = domain + "_" + index;
                }
                KeyStore keystore;
                try {
                    keystore = SSLConfig.getKeyStore();
                }
                catch (Exception e) {
                    keystore = SSLConfig.initializeKeyStore();
                }
                // Import certificate
                CertificateManager.installCert(keystore, SSLConfig.gets2sTrustStore(),
                        SSLConfig.getKeyPassword(), alias, new ByteArrayInputStream(privateKey.getBytes()), passPhrase,
                        new ByteArrayInputStream(certificate.getBytes()), true, true);
                // Save keystore
                SSLConfig.saveStores();
                response.sendRedirect("ssl-certificates.jsp?importsuccess=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("import", e);
            }
        }
    }
%>

<html>
  <head>
      <title><fmt:message key="ssl.import.certificate.title"/></title>
      <meta name="pageID" content="ssl-certificates"/>
  </head>
  <body>

  <%  if (errors.containsKey("privateKey")) { %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.import.certificate.error.private-key" />
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (errors.containsKey("certificate")) { %>

      <div class="jive-error">
  <table cellpadding="0" cellspacing="0" border="0">
  <tbody>
      <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
      <td class="jive-icon-label">
      <fmt:message key="ssl.import.certificate.error.certificate" />
      </td></tr>
  </tbody>
  </table>
  </div><br>

  <%  } else if (errors.containsKey("import")) {
          Exception e = (Exception)errors.get("import");
  %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.import.certificate.error.import" />
          <%  if (e != null && e.getMessage() != null) { %>
              <fmt:message key="ssl.certificates.error_messenge" />: <%= e.getMessage() %>
          <%  } %>
          </td></tr>
      </tbody>
      </table>
      </div><br>
  <% } %>

  <p>
  <fmt:message key="ssl.import.certificate.info">
      <fmt:param value="<%= "<a href='http://java.sun.com/javase/downloads/index.jsp'>" %>" />
      <fmt:param value="<%= "</a>" %>" />
  </fmt:message>
  </p>

  <!-- BEGIN 'Import Private Key and Certificate' -->
  <form action="import-certificate.jsp" method="post" name="f">
      <div class="jive-contentBoxHeader">
          <fmt:message key="ssl.import.certificate.boxtitle" />
      </div>
      <div class="jive-contentBox">
          <table cellpadding="3" cellspacing="0" border="0">
          <tbody>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.pass-phrase" />
                  </td>
                  <td width="99%">
                      <input type="text" size="30" maxlength="100" name="passPhrase">
                  </td>
              </tr>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.private-key" />
                  </td>
                  <td width="99%">
                      <textarea name="private-key" cols="60" rows="5" wrap="virtual"></textarea>
                  </td>
              </tr>
              <tr valign="top">
                  <td width="1%" nowrap class="c1">
                      <fmt:message key="ssl.import.certificate.certificate" />
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
