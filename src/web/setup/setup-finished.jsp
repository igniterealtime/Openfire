<%--
  -	$Revision: 1644 $
  -	$Date: 2005-07-19 09:05:10 -0700 (Tue, 19 Jul 2005) $
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 java.util.Map,
                 org.jivesoftware.wildfire.XMPPServer"
%>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>

<%
	// Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    // First, update with XMPPSettings
    Map<String,String> xmppSettings = (Map<String,String>)session.getAttribute("xmppSettings");
    for (String name : xmppSettings.keySet()) {
        String value = xmppSettings.get(name);
        JiveGlobals.setProperty(name, value);
    }
    Map<String,String> xmlSettings = (Map<String,String>)session.getAttribute("xmlSettings");
    for (String name : xmlSettings.keySet()) {
        String value = xmlSettings.get(name);
        JiveGlobals.setXMLProperty(name, value);
    }
    // Notify that the XMPP server that setup is finished.
    XMPPServer.getInstance().finishSetup();
%>

<html>
    <head>
        <title><fmt:message key="setup.finished.title" /></title>
        <meta name="currentStep" content="5"/>
    </head>
<body>

	<h1>
	<fmt:message key="setup.finished.title" />
	</h1>

	<p>
	<fmt:message key="setup.finished.info">
	    <fmt:param value="<%= LocaleUtils.getLocalizedString("title") %>" />
	</fmt:message>
	</p>

<%
    // Figure out the URL that the user can use to login to the admin console.
    String url;
    if (XMPPServer.getInstance().isStandAlone()) {
        String server = request.getServerName();
        int plainPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        int securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        // Use secure login if we're currently secure (and the secure port isn't disabled)
        // or if the user disabled the plain port.
        if ((request.isSecure() && securePort > 0) || plainPort < 0) {
            url = "https://" + server + ":" + securePort + "/login.jsp?username=admin";
        }
        else {
            url = "http://" + server + ":" + plainPort + "/login.jsp?username=admin";
        }
    }
    else {
        url = request.getRequestURL().toString();
        url = url.replace("setup/setup-finished.jsp", "login.jsp?username=admin");
    }
%>

<br><br>
	<div class="jive_setup_launchAdmin">
		<a href="<%= url %>"><fmt:message key="setup.finished.login" /></a>
	</div>


</body>
</html>