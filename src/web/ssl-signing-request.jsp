<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% // Get parameters:
    final boolean save              = ParamUtils.getParameter(request, "save") != null;
    final String name               = ParamUtils.getParameter(request, "name");
    final String organizationalUnit = ParamUtils.getParameter(request, "ou");
    final String organization       = ParamUtils.getParameter(request, "o");
    final String city               = ParamUtils.getParameter(request, "city");
    final String state              = ParamUtils.getParameter(request, "state");
    final String countryCode        = ParamUtils.getParameter(request, "country");
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

//    if (save) {
//
//        // Verify that fields were completed
//        if (name == null) {
//            errors.put("name", "");
//        }
//        if (organizationalUnit == null) {
//            errors.put("organizationalUnit", "");
//        }
//        if (organization == null) {
//            errors.put("organization", "");
//        }
//        if (city == null) {
//            errors.put("city", "");
//        }
//        if (state == null) {
//            errors.put("state", "");
//        }
//        if (countryCode == null) {
//            errors.put("countryCode", "");
//        }
//        if (errors.size() == 0) {
//            try {
//                final IdentityStore identityStoreConfig = (IdentityStore) SSLConfig.getInstance().getStoreConfig( connectionType );
//
//                identityStoreConfig.ensureSelfSignedDomainCertificates( name, organizationalUnit, organization, city, state, countryCode, "rsa", "dsa" );
//                // Regenerate self-sign certs whose subjectDN matches the issuerDN and set the new issuerDN
//                String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
//                StringBuilder issuerDN = new StringBuilder();
//                issuerDN.append("CN=").append(name);
//                issuerDN.append(", OU=").append(organizationalUnit);
//                issuerDN.append(", O=").append(organization);
//                issuerDN.append(", L=").append(city);
//                issuerDN.append(", ST=").append(state);
//                issuerDN.append(", C=").append(countryCode);
//                StringBuilder subjectDN = new StringBuilder();
//                subjectDN.append("CN=").append(domain);
//                subjectDN.append(", OU=").append(organizationalUnit);
//                subjectDN.append(", O=").append(organization);
//                subjectDN.append(", L=").append(city);
//                subjectDN.append(", ST=").append(state);
//                subjectDN.append(", C=").append(countryCode);
//                // Update certs with new issuerDN information
//                for (Enumeration<String> certAliases = keyStore.aliases(); certAliases.hasMoreElements();) {
//                    String alias = certAliases.nextElement();
//                    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
//                    // Update only Self-signed certs
//                    if (CertificateManager.isSelfSignedCertificate(keyStore, alias)) {
//                        if (CertificateManager.isDSACertificate(certificate)) {
//                            CertificateManager.createDSACert(keyStore, sslConfig.getKeyStorePassword(), alias,
//                                    issuerDN.toString(), subjectDN.toString(), "*." + domain);
//                        } else {
//                            CertificateManager.createRSACert(keyStore, sslConfig.getKeyStorePassword(), alias,
//                                    issuerDN.toString(), subjectDN.toString(), "*." + domain);
//                        }
//                    }
//                }
//                // Save keystore
//                sslConfig.saveStores();
//                // Log the event
//                webManager.logEvent("generated SSL signing request", null);
//                response.sendRedirect("security-keystore.jsp?connectivityType="+connectivityType);
//                return;
//            }
//            catch (Exception e) {
//                e.printStackTrace();
//                errors.put("general", "");
//            }
//        }
//    }
%>

<html>
<head>
    <title>
        <fmt:message key="ssl.signing-request.title"/>
    </title>
    <meta name="pageID" content="security-keystore-${connectivityType}"/>
</head>
<body>

<% pageContext.setAttribute("errors", errors); %>
<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'name'}">
                <fmt:message key="ssl.signing-request.enter_name" />
            </c:when>
            <c:when test="${err.key eq 'organizationalUnit'}">
                <fmt:message key="ssl.signing-request.enter_ou" />
            </c:when>
            <c:when test="${err.key eq 'organization'}">
                <fmt:message key="ssl.signing-request.enter_o" />
            </c:when>
            <c:when test="${err.key eq 'city'}">
                <fmt:message key="ssl.signing-request.enter_city" />
            </c:when>
            <c:when test="${err.key eq 'state'}">
                <fmt:message key="ssl.signing-request.enter_state" />
            </c:when>
            <c:when test="${err.key eq 'countryCode'}">
                <fmt:message key="ssl.signing-request.enter_country" />
            </c:when>
            <c:otherwise>
                <c:out value="${err.key}"/>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<!-- BEGIN 'Issuer information form' -->
<form action="ssl-signing-request.jsp" method="post">
    <input type="hidden" name="save" value="true">
    <input type="hidden" name="connectivityType" value="${connectivityType}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="ssl.signing-request.issuer_information"/>
    </div>
    <div class="jive-contentBox">
        <p>
            <fmt:message key="ssl.signing-request.issuer_information_info"/>
        </p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td width="1%" nowrap>
                        <label for="namef">
                            <fmt:message key="ssl.signing-request.name"/>
                            :</label>
                    </td>
                    <td width="99%">
                        <input type="text" name="name" size="50" maxlength="75"
                               value="<%= ((name!=null) ? StringUtils.escapeForXML(name) : "") %>" id="namef">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="ouf"><fmt:message key="ssl.signing-request.organizational_unit"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="ou" size="50" maxlength="75" value="<%= ((organizationalUnit!=null) ? StringUtils.escapeForXML(organizationalUnit) : "") %>" id="ouf">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="of"><fmt:message key="ssl.signing-request.organization"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="o" size="50" maxlength="75" value="<%= ((organization!=null) ? StringUtils.escapeForXML(organization) : "") %>" id="of">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="cityf"><fmt:message key="ssl.signing-request.city"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="city" size="50" maxlength="75" value="<%= ((city!=null) ? StringUtils.escapeForXML(city) : "") %>" id="cityf">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="statef"><fmt:message key="ssl.signing-request.state"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="state" size="30" maxlength="75" value="<%= ((state!=null) ? StringUtils.escapeForXML(state) : "") %>" id="statef">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="countryf"><fmt:message key="ssl.signing-request.country"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="country" size="2" maxlength="2" value="<%= ((countryCode!=null) ? StringUtils.escapeForXML(countryCode) : "") %>" id="countryf">
                    </td>
                </tr>
              <tr>
                  <td colspan="2">
                      <br>
                      <input type="submit" name="install" value="<fmt:message key="ssl.signing-request.save" />">
                  </td>
              </tr>
          </tbody>
          </table>
      </div>
  </form>
  <!-- END 'Issuer information form' -->
  </body>
</html>
