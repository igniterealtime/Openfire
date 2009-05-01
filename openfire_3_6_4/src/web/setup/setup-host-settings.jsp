<%--
  -	$RCSfile$
  -	$Revision: 1638 $
  -	$Date: 2005-07-18 10:16:48 -0700 (Mon, 18 Jul 2005) $
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 java.util.Map,
                 java.util.HashMap,
                 java.net.InetAddress,
                 org.jivesoftware.openfire.XMPPServer"
%>
<%@ page import="java.net.UnknownHostException" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
	// Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<% // Get parameters
    String domain = ParamUtils.getParameter(request, "domain");
    int embeddedPort = ParamUtils.getIntParameter(request, "embeddedPort", Integer.MIN_VALUE);
    int securePort = ParamUtils.getIntParameter(request, "securePort", Integer.MIN_VALUE);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled", true);

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request:
    Map<String, String> errors = new HashMap<String, String>();
    if (doContinue) {
        // Validate parameters
        if (domain == null) {
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
        } else {
            embeddedPort = -1;
            securePort = -1;
        }
        // Continue if there were no errors
        if (errors.size() == 0) {
            Map<String, String> xmppSettings = new HashMap<String, String>();

            xmppSettings.put("xmpp.domain", domain);
            xmppSettings.put("xmpp.socket.ssl.active", "" + sslEnabled);
            xmppSettings.put("xmpp.auth.anonymous", "true");
            session.setAttribute("xmppSettings", xmppSettings);

            Map<String, String> xmlSettings = new HashMap<String, String>();
            xmlSettings.put("adminConsole.port", Integer.toString(embeddedPort));
            xmlSettings.put("adminConsole.securePort", Integer.toString(securePort));
            session.setAttribute("xmlSettings", xmlSettings);

            // Successful, so redirect
            response.sendRedirect("setup-datasource-settings.jsp");
            return;
        }
    }

    // Load the current values:
    if (!doContinue) {
        domain = JiveGlobals.getProperty("xmpp.domain");
        embeddedPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        sslEnabled = JiveGlobals.getBooleanProperty("xmpp.socket.ssl.active", true);

        // If the domain is still blank, guess at the value:
        if (domain == null) {
            try {
                domain = InetAddress.getLocalHost().getHostName().toLowerCase();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                domain = "127.0.0.1";
            }
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
         value="<%= ((domain != null) ? domain : "") %>">
	    <span class="jive-setup-helpicon" onmouseover="domTT_activate(this, event, 'content', '<fmt:message key="setup.host.settings.hostname" />', 'styleClass', 'jiveTooltip', 'trail', true, 'delay', 300, 'lifetime', 8000);"></span>
        <%  if (errors.get("domain") != null) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.host.settings.invalid_domain" />
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