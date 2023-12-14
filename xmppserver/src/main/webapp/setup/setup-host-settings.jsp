<%--
  -
  - Copyright (C) 2004-2007 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="java.net.InetAddress" %>
<%@ page import="java.net.UnknownHostException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.sasl.AnonymousSaslServer" %>
<%@ page import="org.jivesoftware.openfire.session.ConnectionSettings" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.openfire.XMPPServerInfo" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<% // Get parameters
    String domain = ParamUtils.getParameter(request, "domain");
    String fqdn = ParamUtils.getParameter(request, "fqdn");
    int embeddedPort = ParamUtils.getIntParameter(request, "embeddedPort", Integer.MIN_VALUE);
    int securePort = ParamUtils.getIntParameter(request, "securePort", Integer.MIN_VALUE);
    boolean restrictAdminLocalhost = ParamUtils.getBooleanParameter(request, "restrictAdminLocalhost");
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled", true);
    boolean anonymousAuthentication = JiveGlobals.getXMLProperty(AnonymousSaslServer.ENABLED.getKey(), false);
    String encryptionAlgorithm = ParamUtils.getParameter(request, "encryptionAlgorithm");
    String encryptionKey = ParamUtils.getParameter(request, "encryptionKey");

    boolean doContinue = request.getParameter("continue") != null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    // Handle a continue request:
    Map<String,String> errors = new HashMap<>();

    if (doContinue) {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) ) {
            doContinue = false;
            errors.put( "csrf", "CSRF Failure!" );
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (doContinue) {
        // Validate parameters
        if (domain == null || domain.isEmpty()) {
            errors.put("domain", "domain");
        } else {
            try {
                domain = JID.domainprep(domain);
            } catch (IllegalArgumentException e) {
                errors.put("domain", "domain");
            }
        }
        if (fqdn == null || fqdn.isEmpty()) {
            errors.put("fqdn", "fqdn");
        } else {
            try {
                fqdn = JID.domainprep(fqdn);
            } catch (IllegalArgumentException e) {
                errors.put("fqdn", "fqdn");
            }
        }
        if (XMPPServer.getInstance().isStandAlone()) {
            if (embeddedPort == Integer.MIN_VALUE) {
                errors.put("embeddedPort", "embeddedPort");
            }
            // Force any negative value to -1.
            else if (embeddedPort < 0) {
                embeddedPort = -1;
            }

            if (securePort == Integer.MIN_VALUE) {
                errors.put("securePort", "securePort");
            }
            // Force any negative value to -1.
            else if (securePort < 0) {
                securePort = -1;
            }
            
            if (encryptionKey != null) {
            // ensure the same key value was provided twice
                String repeat = ParamUtils.getParameter(request, "encryptionKey1");
                if (!encryptionKey.equals(repeat)) {
                    errors.put("encryptionKey", "encryptionKey");
                }
            }
        } else {
            embeddedPort = -1;
            securePort = -1;
        }
        // Continue if there were no errors
        if (errors.size() == 0) {
            Map<String, String> xmppSettings = new HashMap<String, String>();

            xmppSettings.put(XMPPServerInfo.XMPP_DOMAIN.getKey(), domain);
            xmppSettings.put(ConnectionSettings.Client.ENABLE_OLD_SSLPORT_PROPERTY.getKey(), "" + sslEnabled);
            xmppSettings.put(AnonymousSaslServer.ENABLED.getKey(), "" + anonymousAuthentication);
            session.setAttribute("xmppSettings", xmppSettings);

            Map<String, String> xmlSettings = new HashMap<String, String>();
            xmlSettings.put("adminConsole.port", Integer.toString(embeddedPort));
            xmlSettings.put("adminConsole.securePort", Integer.toString(securePort));
            xmlSettings.put("fqdn", fqdn);
            if (restrictAdminLocalhost){
                xmlSettings.put("adminConsole.interface", "127.0.0.1");
            }

            session.setAttribute("xmlSettings", xmlSettings);

            session.setAttribute("encryptedSettings", new HashSet<String>());

            JiveGlobals.setupPropertyEncryptionAlgorithm(encryptionAlgorithm);
            JiveGlobals.setupPropertyEncryptionKey(encryptionKey);

            // Successful, so redirect
            response.sendRedirect("setup-datasource-settings.jsp");
            return;
        }
    }

    // Load the current values:
    if (!doContinue) {
        domain = JiveGlobals.getXMLProperty(XMPPServerInfo.XMPP_DOMAIN.getKey());
        fqdn = JiveGlobals.getXMLProperty("fqdn");
        embeddedPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);

        // If the fqdn (server name) is still blank, guess:
        if (fqdn == null || fqdn.isEmpty())
        {
            try
            {
                fqdn = InetAddress.getLocalHost().getCanonicalHostName();
            }
            catch (UnknownHostException ex)
            {
                System.err.println( "Unable to determine the fully qualified domain name (canonical hostname) of this server." );
                ex.printStackTrace();
                fqdn = "localhost";
            }
        }

        // If the domain is still blank, use the host name.
        if (domain == null) {
            domain = fqdn;
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "domain", domain );
    pageContext.setAttribute( "fqdn", fqdn );
    if ( embeddedPort != Integer.MIN_VALUE ) {
        pageContext.setAttribute( "embeddedPort", embeddedPort );
    }
    if ( securePort != Integer.MIN_VALUE ) {
        pageContext.setAttribute( "securePort", securePort );
    }
    pageContext.setAttribute( "xmppServer", XMPPServer.getInstance() );
%>

<html>
<head>
    <title><fmt:message key="setup.host.settings.title" /></title>
    <meta name="currentStep" content="1"/>
</head>
<body>

    <c:if test="${not empty errors['csrf']}">
        <div class="error">
            <fmt:message key="global.csrf.failed"/>
        </div>
    </c:if>

    <h1>
    <fmt:message key="setup.host.settings.title" />
    </h1>

    <p>
    <fmt:message key="setup.host.settings.info" />
    </p>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">

        <form action="setup-host-settings.jsp" name="f" method="post">
            <input type="hidden" name="csrf" value="${csrf}">

<table>
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <label for="domain"><fmt:message key="setup.host.settings.domain" /></label>
    </td>
    <td>
        <input type="text" size="30" maxlength="150" name="domain" id="domain" value="${not empty domain ? fn:escapeXml(domain) : ''}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.domain.help" /></span></div>
        <c:if test="${not empty errors['domain']}">
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_domain" />
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <label for="fqdn"><fmt:message key="setup.host.settings.fqdn" /></label>
    </td>
    <td>
        <input type="text" size="30" maxlength="150" name="fqdn" id="fqdn" value="${not empty fqdn ? fn:escapeXml(fqdn) : ''}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.fqdn.help" /></span></div>
        <c:if test="${not empty errors['fqdn']}">
        <span class="jive-error-text">
        <fmt:message key="setup.host.settings.invalid_fqdn" />
        </span>
        </c:if>
    </td>
</tr>

<c:if test="${xmppServer.standAlone}">
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <label for="embeddedPort"><fmt:message key="setup.host.settings.port" /></label>
    </td>
    <td>
        <input type="number" min="1" max="65535" size="6" maxlength="6" name="embeddedPort" id="embeddedPort" value="${not empty embeddedPort ? embeddedPort : 9090}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.port_number" /></span></div>
        <c:if test="${not empty errors['embeddedPort']}">
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <label for="securePort"><fmt:message key="setup.host.settings.secure_port" /></label>
    </td>
    <td>
        <input type="number" min="1" max="65535" size="6" maxlength="6" name="securePort" id="securePort" value="${not empty securePort ? securePort : 9091}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.secure_port_number" /></span></div>
        <c:if test="${not empty errors['securePort']}">
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>
        </c:if>
    </td>
</tr>
    <tr>
        <td/>
        <td>
            <input type="checkbox" name="restrictAdminLocalhost" checked>
            <label>  <fmt:message key="setup.host.settings.restrict_localhost" /></label>
            <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span>
                <span class="tooltiptext"><fmt:message key="setup.host.settings.restrict_localhost_info" /></span>
            </div><br /><br />
        </td>
    </tr>
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <fmt:message key="setup.host.settings.encryption_algorithm" />
    </td>
    <td>
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.encryption_algorithm_info" /></span></div><br /><br />
        <input type="radio" name="encryptionAlgorithm" value="Blowfish" id="Blowfish" checked><label for="Blowfish"><fmt:message key="setup.host.settings.encryption_blowfish" /></label><br /><br />
        <input type="radio" name="encryptionAlgorithm" value="AES" id="AES"><label for="AES"><fmt:message key="setup.host.settings.encryption_aes" /></label><br /><br />
    </td>
</tr>
<tr>
    <td style="width: 1%; white-space: nowrap" align="right">
        <label for="encryptionKey"><fmt:message key="setup.host.settings.encryption_key" /></label>
    </td>
    <td>
        <input type="password" size="50" name="encryptionKey" id="encryptionKey"/><br /><br />
        <input type="password" size="50" name="encryptionKey1" id="encryptionKey1" />
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.host.settings.encryption_key_info"/></span></div>
        <c:if test="${not empty errors['encryptionKey']}">
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.encryption_key_invalid" />
            </span>
        </c:if>
    </td>
</tr>
</c:if>
</table>

<br><br>


        <div align="right">
            <input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save">
        </div>
    </form>

    </div>
    <!-- END jive-contentBox -->


<script>
// give focus to domain field
document.f.domain.focus();
</script>


</body>
</html>
