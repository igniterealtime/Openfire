<%@ page import="org.jivesoftware.util.CertificateManager,
                org.jivesoftware.util.JiveGlobals,
                org.jivesoftware.util.ParamUtils,
                org.jivesoftware.wildfire.XMPPServer,
                org.jivesoftware.wildfire.net.SSLConfig,
                org.jivesoftware.wildfire.net.TLSStreamHandler,
                java.io.ByteArrayInputStream,
                java.security.KeyStore,
                java.security.cert.X509Certificate,
                java.util.Date"
         errorPage="error.jsp"%>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

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
    boolean generate = ParamUtils.getBooleanParameter(request, "generate");
    boolean delete = ParamUtils.getBooleanParameter(request, "delete");
    boolean importReply = ParamUtils.getBooleanParameter(request, "importReply");
    String type = ParamUtils.getParameter(request, "type");
    String alias = ParamUtils.getParameter(request, "alias");

    KeyStore keyStore = SSLConfig.getKeyStore();

    Map<String, Object> errors = new HashMap<String, Object>();
    if (generate) {
        String domain = XMPPServer.getInstance().getServerInfo().getName();
        try {
            if (!CertificateManager.isDSACertificate(keyStore, domain)) {
                CertificateManager
                        .createDSACert(keyStore, domain + "_dsa", "cn=" + domain, "cn=" + domain, "*." + domain);
            }
            if (!CertificateManager.isRSACertificate(keyStore, domain)) {
                CertificateManager
                        .createRSACert(keyStore, domain + "_rsa", "cn=" + domain, "cn=" + domain, "*." + domain);
            }
            // Save new certificates into the key store
            SSLConfig.saveStores();
        }
        catch (Exception e) {
            e.printStackTrace();
            errors.put("generate", e);
        }
    }
    if (delete) {
        if (type != null && alias != null) {
            try {
                SSLConfig.getKeyStore().deleteEntry(alias);
                SSLConfig.saveStores();
                response.sendRedirect("ssl-certificates.jsp?deletesuccess=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("delete", e);
            }
        }
    }
    if (importReply) {
        String reply = ParamUtils.getParameter(request, "reply");
        if (alias != null && reply != null && reply.trim().length() > 0) {
            try {
                CertificateManager.installReply(alias, new ByteArrayInputStream(reply.getBytes()), true, true);
                SSLConfig.saveStores();
                response.sendRedirect("ssl-certificates.jsp?importsuccess=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("importReply", e);
            }
        }
    }
%>

<html>
  <head>
      <title><fmt:message key="ssl.certificates.title"/></title>
      <meta name="pageID" content="ssl-certificates"/>
  </head>
  <body>

  <%  if (keyStore.size() < 2 ) { %>
      <div class="warning">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr>
          <td class="jive-icon-label">
              <fmt:message key="ssl.certificates.no_installed">
                  <fmt:param value="<%= "<a href='ssl-certificates.jsp?generate=true'>" %>" />
                  <fmt:param value="<%= "</a>" %>" />
              </fmt:message>
          </td></tr>
      </tbody>
      </table>
      </div><br>
  <%  } else if (ParamUtils.getBooleanParameter(request,"addupdatesuccess")) { %>

      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.added_updated" />
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (ParamUtils.getBooleanParameter(request,"deletesuccess")) { %>

      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.deleted" />
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (errors.containsKey("delete")) {
          Exception e = (Exception)errors.get("delete");
  %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.error" />
          <%  if (e != null && e.getMessage() != null) { %>
              <fmt:message key="ssl.certificates.error_messenge" />: <%= e.getMessage() %>
          <%  } %>
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (ParamUtils.getBooleanParameter(request,"importsuccess")) { %>

      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.imported" />
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (errors.containsKey("importReply")) {  %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.error_importing-reply" />
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (errors.containsKey("generate")) {
          Exception e = (Exception)errors.get("generate");
  %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.certificates.error" />
          <%  if (e != null && e.getMessage() != null) { %>
              <fmt:message key="ssl.certificates.error_messenge" />: <%= e.getMessage() %>
          <%  } %>
          </td></tr>
      </tbody>
      </table>
      </div><br>

  <%  } else if (errors.size() > 0) { %>

      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
          <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
          <td class="jive-icon-label">
          <fmt:message key="ssl.settings.error_certificate" />
          </td></tr>
      </tbody>
      </table>
      </div><br>
  <% } %>

  <!-- BEGIN 'Installed Certificates' -->
  <p>
  <fmt:message key="ssl.certificates.info" />
  </p>

  <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
  <thead>
      <tr>
          <th width="1%">&nbsp;</th>
          <th>
              <fmt:message key="ssl.certificates.alias" />
          </th>
          <th width="20%">
              <fmt:message key="ssl.certificates.expiration" />
          </th>
          <th colspan="2">
              <fmt:message key="ssl.certificates.status" />
          </th>
          <th>
              <fmt:message key="ssl.certificates.algorithm" />
          </th>
          <th width="1%">
              <fmt:message key="global.delete" />
          </th>
      </tr>
  </thead>
  <tbody>

  <% int i = 0;
      boolean offerSigningRequest = false;
      for (Enumeration aliases = keyStore.aliases(); aliases.hasMoreElements();) {
          i++;
          String a = (String) aliases.nextElement();
          X509Certificate c = (X509Certificate) keyStore.getCertificate(a);
          StringBuffer identities = new StringBuffer();
          for (String identity : TLSStreamHandler.getPeerIdentities(c)) {
              identities.append(identity).append(", ");
          }
          if (identities.length() > 0) {
              identities.setLength(identities.length() - 2);
          }
          // Self-signed certs are certs generated by Wildfire whose IssueDN equals SubjectDN
          boolean isSelfSigned = c.getSubjectDN().equals(c.getIssuerDN());
          // Signing Request pending = not self signed certs whose chain has only 1 cert (the same cert)
          boolean isSigningPending = !isSelfSigned && keyStore.getCertificateChain(a).length == 1;

          offerSigningRequest = offerSigningRequest || isSelfSigned || isSigningPending;
  %>
      <tr valign="top">
          <td id="rs<%=i%>" width="1" rowspan="1"><%= (i) %>.</td>
          <td>
              <%= identities.toString() %> (<%= a %>)
          </td>
          <td>
              <% boolean expired = c.getNotAfter().before(new Date());
                  if (expired) { %>
                  <font color="red">
              <% } %>
              <%= JiveGlobals.formatDateTime(c.getNotAfter()) %>
              <% if (expired) { %>
                  </font>
              <% } %>
          </td>
          <% if (isSelfSigned) { %>
          <td width="1%"><img src="images/certificate_warning-16x16.png" width="16" height="16" border="0" title="<fmt:message key="ssl.certificates.self-signed.info" />"></td>
          <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.self-signed" />
          </td>
          <% } else if (isSigningPending) { %>
          <td width="1%"><img src="images/certificate_warning-16x16.png" width="16" height="16" border="0" title="<fmt:message key="ssl.certificates.signing-pending.info" />"></td>
          <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.signing-pending" />
          </td>
          <% } else { %>
          <td width="1%"><img src="images/certificate_ok-16x16.png" width="16" height="16" border="0" title="<fmt:message key="ssl.certificates.ca-signed.info" />"></td>
          <td width="1%" nowrap>
            <fmt:message key="ssl.certificates.ca-signed" />
          </td>
          <% } %>
          <td width="2%">
              <%= c.getSigAlgName() %>
          </td>
          <td width="1" align="center">
              <a href="ssl-certificates.jsp?alias=<%= a %>&type=server&delete=true"
               title="<fmt:message key="global.click_delete" />"
               onclick="return confirm('<fmt:message key="ssl.certificates.confirm_delete" />');"
               ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt=""></a>
          </td>
      </tr>
      <% if (isSigningPending) { %>
      <form action="ssl-certificates.jsp" method="post">
      <input name="importReply" type="hidden" value="true">
      <input name="alias" type="hidden" value="<%= a%>">
      <tr id="pk<%=i%>">
          <td colspan="6">
              <span class="jive-description">
              <fmt:message key="ssl.certificates.ca-reply" />
              </span>
              <textarea name="reply" cols="40" rows="3" style="width:100%;font-size:8pt;" wrap="virtual"></textarea>
          </td>
          <td valign="bottom">
              <input type="submit" name="install" value="<fmt:message key="global.save" />">   
          </td>
      </tr>
      </form>
      <% } %>

  <%  } %>

  </tbody>
  </table>
  <!-- END 'Installed Certificates' -->
  <% if (offerSigningRequest) { %>
  <br>
  <div class="jive-contentBoxHeader">
      <fmt:message key="ssl.signing-request.title"/>
  </div>
  <div class="jive-contentBox">
      <p>
          <fmt:message key="ssl.signing-request.introduction">
              <fmt:param value="<%= "<a href='ssl-signing-request.jsp'>" %>" />
              <fmt:param value="<%= "</a>" %>" />
          </fmt:message>
      </p>
  </div>
  <% } %>
  </body>
</html>