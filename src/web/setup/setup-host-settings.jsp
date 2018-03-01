<%--
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 java.util.Map,
                 java.util.HashMap,
                 java.net.InetAddress,
                 org.jivesoftware.openfire.XMPPServer"
%>
<%@ page import="java.net.UnknownHostException" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
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
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled", true);
    boolean anonymousAuthentication = JiveGlobals.getXMLProperty("xmpp.auth.anonymous", false);
    String encryptionAlgorithm = ParamUtils.getParameter(request, "encryptionAlgorithm");
    String encryptionKey = ParamUtils.getParameter(request, "encryptionKey");

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request:
    Map<String, String> errors = new HashMap<String, String>();
    if (doContinue) {
        // Validate parameters
        if (domain == null || domain.isEmpty()) {
            errors.put("domain", "domain");
        }
        if (fqdn == null || fqdn.isEmpty()) {
            errors.put("fqdn", "fqdn");
        }
        try {
            fqdn = JID.domainprep(fqdn);
        } catch (IllegalArgumentException e) {
            errors.put("fqdn", "fqdn");
        }
        try {
            domain = JID.domainprep(domain);
        } catch (IllegalArgumentException e) {
            errors.put("domain", "domain");
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

            xmppSettings.put("xmpp.domain", domain);
            xmppSettings.put("xmpp.fqdn", fqdn);
            xmppSettings.put("xmpp.socket.ssl.active", "" + sslEnabled);
            xmppSettings.put("xmpp.auth.anonymous", "" + anonymousAuthentication);
            session.setAttribute("xmppSettings", xmppSettings);

            Map<String, String> xmlSettings = new HashMap<String, String>();
            xmlSettings.put("adminConsole.port", Integer.toString(embeddedPort));
            xmlSettings.put("adminConsole.securePort", Integer.toString(securePort));
            session.setAttribute("xmlSettings", xmlSettings);

            JiveGlobals.setupPropertyEncryptionAlgorithm(encryptionAlgorithm);
            JiveGlobals.setupPropertyEncryptionKey(encryptionKey);

            // Successful, so redirect
            response.sendRedirect("setup-datasource-settings.jsp");
            return;
        }
    }

    // Load the current values:
    if (!doContinue) {
        domain = JiveGlobals.getXMLProperty("xmpp.domain");
        fqdn = JiveGlobals.getXMLProperty("xmpp.fqdn");
        embeddedPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        sslEnabled = JiveGlobals.getXMLProperty("xmpp.socket.ssl.active", true);

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
%>

<html>
<head>
    <title><fmt:message key="setup.host.settings.title" /></title>
    <meta name="currentStep" content="1"/>
</head>
<body>


    <h1>
    <fmt:message key="setup.host.settings.title" />
    </h1>

    <p>
    <fmt:message key="setup.host.settings.info" />
    </p>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">

        <form action="setup-host-settings.jsp" name="f" method="post">

<table cellpadding="3" cellspacing="0" border="0">
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.domain" />
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="domain"
         value="<%= ((domain != null) ? StringUtils.escapeForXML(domain) : "") %>">
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.domain.help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
        <%  if (errors.get("domain") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_domain" />
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.fqdn" />
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="fqdn"
               value="<%= ((fqdn != null) ? StringUtils.escapeForXML(fqdn) : "") %>">
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.fqdn.help" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
        <%  if (errors.get("fqdn") != null) { %>
        <span class="jive-error-text">
        <fmt:message key="setup.host.settings.invalid_fqdn" />
        </span>
        <%  } %>
    </td>
</tr>

<% if (XMPPServer.getInstance().isStandAlone()){ %>
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.port" />
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="embeddedPort"
         value="<%= ((embeddedPort != Integer.MIN_VALUE) ? ""+embeddedPort : "9090") %>">
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.port_number" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
        <%  if (errors.get("embeddedPort") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.secure_port" />
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="securePort"
         value="<%= ((securePort != Integer.MIN_VALUE) ? ""+securePort : "9091") %>">
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.secure_port_number" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
         <%  if (errors.get("securePort") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>
        <%  } %>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.encryption_algorithm" />
    </td>
    <td width="99%">
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.encryption_algorithm_info" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span><br /><br />
        <input type="radio" name="encryptionAlgorithm" value="Blowfish" checked><fmt:message key="setup.host.settings.encryption_blowfish" /><br /><br />
        <input type="radio" name="encryptionAlgorithm" value="AES"><fmt:message key="setup.host.settings.encryption_aes" /><br /><br />
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap align="right">
        <fmt:message key="setup.host.settings.encryption_key" />
    </td>
    <td width="99%">
        <input type="password" size="50" name="encryptionKey" /><br /><br />
        <input type="password" size="50" name="encryptionKey1" />
        <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.encryption_key_info" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
         <%  if (errors.get("encryptionKey") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.encryption_key_invalid" />
            </span>
        <%  } %>
    </td>
</tr>
<% } %>
</table>

<br><br>


        <div align="right">
            <input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
        </div>
    </form>

    </div>
    <!-- END jive-contentBox -->


<script language="JavaScript" type="text/javascript">
// give focus to domain field
document.f.domain.focus();
</script>


</body>
</html>
