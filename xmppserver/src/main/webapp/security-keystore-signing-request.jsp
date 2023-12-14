<%--
  -
  - Copyright (C) 2006-2008 Jive Software, 2018-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@page import="java.util.Enumeration"%>
<%@page import="org.jivesoftware.openfire.XMPPServer"%>
<%@page import="java.security.PublicKey"%>
<%@page import="java.security.KeyPair"%>
<%@page import="java.security.cert.X509Certificate"%>
<%@page import="java.security.PrivateKey"%>
<%@page import="org.jivesoftware.openfire.keystore.IdentityStore"%>
<%@page import="java.security.KeyStore"%>
<%@page import="org.bouncycastle.asn1.x500.style.BCStyle"%>
<%@page import="org.bouncycastle.asn1.x500.X500NameBuilder"%>
<%@page import="org.jivesoftware.util.CertificateManager"%>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="java.time.Duration" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% 
    String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

     // Get parameters:
    boolean save              = ParamUtils.getParameter(request, "save") != null;
    final String name               = domain;
    final String organizationalUnit = ParamUtils.getParameter(request, "ou");
    final String organization       = ParamUtils.getParameter(request, "o");
    final String city               = ParamUtils.getParameter(request, "city");
    final String state              = ParamUtils.getParameter(request, "state");
    final String countryCode        = ParamUtils.getParameter(request, "country");
    final String connectionTypeText = ParamUtils.getParameter( request, "connectionType" );

    final Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    ConnectionType connectionType = null;
    IdentityStore identityStore = null;
    try
    {
        connectionType = ConnectionType.valueOf( connectionTypeText );
        identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( connectionType );
        if ( identityStore == null )
        {
            errors.put( "identityStore", "Unable to get an instance." );
        }
    }
    catch (RuntimeException ex)
    {
        errors.put( "connectionType", ex.getMessage() );
    }

    pageContext.setAttribute( "connectionType", connectionType );

   if (save) {

       // Verify that fields were completed
       if (organizationalUnit == null) {
           errors.put("organizationalUnit", "");
       }
       if (organization == null) {
           errors.put("organization", "");
       }
       if (city == null) {
           errors.put("city", "");
       }
       if (state == null) {
           errors.put("state", "");
       }
       if (countryCode == null) {
           errors.put("countryCode", "");
       }
       if (errors.size() == 0) {
           try {
               // When updating certificates through the admin console, do not cause changes to restart the website, as
               // that is very likely to log out the administrator that is performing the changes. As the keystore change
               // event is async, this line disables restarting the plugin for a few minutes.
               ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).pauseAutoRestartEnabled(Duration.ofMinutes(5));

               X500NameBuilder builder = new X500NameBuilder();
               builder.addRDN(BCStyle.CN, name);
               builder.addRDN(BCStyle.OU, organizationalUnit);
               builder.addRDN(BCStyle.O, organization);
               builder.addRDN(BCStyle.L, city);
               builder.addRDN(BCStyle.ST, state);
               builder.addRDN(BCStyle.C, countryCode);

               // Update certs with new issuerDN information
               KeyStore keyStore = identityStore.getStore();
               for (Enumeration<String> certAliases = keyStore.aliases(); certAliases.hasMoreElements();) {

                   String alias = certAliases.nextElement();
                   X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

                   // Update only Self-signed certs
                   if (CertificateManager.isSelfSignedCertificate(certificate)) {
                       
                       PrivateKey privKey = (PrivateKey) keyStore.getKey(alias, identityStore.getConfiguration().getPassword());
                       PublicKey pubKey = certificate.getPublicKey();

                       String signAlgoritm = "SHA256WITH" + pubKey.getAlgorithm();
                       int days = 60;

                       // Regenerate self-sign certs whose subjectDN matches the issuerDN and set the new issuerDN
                       final Set<String> sanDnsNames = CertificateManager.determineSubjectAlternateNameDnsNameValues();
                       X509Certificate newCertificate = CertificateManager.createX509V3Certificate(new KeyPair(pubKey, privKey), days, builder, builder, domain, signAlgoritm, sanDnsNames);
                       keyStore.setKeyEntry(alias, privKey, identityStore.getConfiguration().getPassword(), new X509Certificate[] { newCertificate });
                   }
               }
               // Save keystore
               identityStore.persist();
               // Log the event
               webManager.logEvent("generated SSL signing request", null);
               response.sendRedirect("security-keystore.jsp?connectionType="+connectionType);
               return;
           }
           catch (Exception e) {
               e.printStackTrace();
               errors.put("general", "");
           }
       }
   }
%>

<html>
<head>
    <title>
        <fmt:message key="ssl.signing-request.title"/>
    </title>
    <meta name="pageID" content="security-keystore-${connectionType}"/>
</head>
<body>

<% pageContext.setAttribute("errors", errors); %>
<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
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
<form action="security-keystore-signing-request.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="save" value="true">
    <input type="hidden" name="connectionType" value="${connectionType}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="ssl.signing-request.issuer_information"/>
    </div>
    <div class="jive-contentBox">
        <p>
            <fmt:message key="ssl.signing-request.issuer_information_info"/>
        </p>
        <table>
            <tbody>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="namef">
                            <fmt:message key="ssl.signing-request.name"/>
                            :</label>
                    </td>
                    <td>
                        <input type="text" name="name" size="50" maxlength="75"
                               value="<%= ((name!=null) ? StringUtils.escapeForXML(name) : "") %>" id="namef" disabled="disabled">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="ouf"><fmt:message key="ssl.signing-request.organizational_unit"/>:</label></td>
                    <td>
                        <input type="text" name="ou" size="50" maxlength="75" value="<%= ((organizationalUnit!=null) ? StringUtils.escapeForXML(organizationalUnit) : "") %>" id="ouf">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="of"><fmt:message key="ssl.signing-request.organization"/>:</label></td>
                    <td>
                        <input type="text" name="o" size="50" maxlength="75" value="<%= ((organization!=null) ? StringUtils.escapeForXML(organization) : "") %>" id="of">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="cityf"><fmt:message key="ssl.signing-request.city"/>:</label></td>
                    <td>
                        <input type="text" name="city" size="50" maxlength="75" value="<%= ((city!=null) ? StringUtils.escapeForXML(city) : "") %>" id="cityf">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="statef"><fmt:message key="ssl.signing-request.state"/>:</label></td>
                    <td>
                        <input type="text" name="state" size="30" maxlength="75" value="<%= ((state!=null) ? StringUtils.escapeForXML(state) : "") %>" id="statef">
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap">
                        <label for="countryf"><fmt:message key="ssl.signing-request.country"/>:</label></td>
                    <td>
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
