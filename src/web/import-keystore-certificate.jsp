<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.keystore.IdentityStore" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"  %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters:
    final boolean save            = ParamUtils.getParameter( request, "save" ) != null;
    final String privateKey       = ParamUtils.getParameter(request, "private-key");
    final String passPhrase       = ParamUtils.getParameter(request, "passPhrase");
    final String certificate      = ParamUtils.getParameter(request, "certificate");
    final String storePurposeText = ParamUtils.getParameter(request, "connectionType");

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

    if (save) {
        if (privateKey == null || "".equals(privateKey)) {
            errors.put("privateKey", "privateKey");
        }
        if (certificate == null || "".equals(certificate)) {
            errors.put("certificate", "certificate");
        }
        if (errors.isEmpty()) {
            try {
                final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( connectionType );

                // Create an alias for the signed certificate
                String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                int index = 1;
                String alias = domain + "_" + index;
                while ( identityStore.getStore().containsAlias( alias )) {
                    index = index + 1;
                    alias = domain + "_" + index;
                }

                // Import certificate
                identityStore.installCertificate( alias, privateKey, passPhrase, certificate );

                // Log the event
                webManager.logEvent("imported SSL certificate in identity store "+ storePurposeText, "alias = "+alias);

                response.sendRedirect("security-keystore.jsp?connectionType="+storePurposeText);
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
      <title><fmt:message key="ssl.import.certificate.keystore.${connectionType}.title"/></title>
      <meta name="pageID" content="security-certificate-store-management"/>
      <meta name="subPageID" content="sidebar-certificate-store-${fn:toLowerCase(connectionType)}-identity-store"/>
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
      <input type="hidden" name="connectionType" value="${connectionType}"/>
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
