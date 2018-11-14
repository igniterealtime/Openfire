<%@ page contentType="text/html; charset=UTF-8" %>
<%--
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.admin.AdminManager" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.xmpp.packet.JID" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    if (session == null
        || session.getAttribute("xmppSettings") == null
        || session.getAttribute("xmlSettings") == null
        || session.getAttribute("encryptedSettings") == null) {
        // Session appears to have timed out, send back to first page.
        response.sendRedirect("index.jsp");
        return;
    }

    // First, update with XML Settings - which may fail due to problems writing to openfire.xml
    String errorMessage = "";
    @SuppressWarnings("unchecked") final Map<String, String> xmlSettings = (Map<String, String>) session.getAttribute("xmlSettings");
    for (final String name : xmlSettings.keySet()) {
        final String value = xmlSettings.get(name);
        final boolean saved = JiveGlobals.setXMLProperty(name, value);
        if (!saved) {
            errorMessage = "Unable to save conf/openfire.xml file - check that it is writable";
            break;
        }
    }

    if (errorMessage.isEmpty()) {
        // Save plain text XMPP Settings to the DB
        @SuppressWarnings("unchecked") final Map<String, String> xmppSettings = (Map<String, String>) session.getAttribute("xmppSettings");
        for (final String name : xmppSettings.keySet()) {
            final String value = xmppSettings.get(name);
            JiveGlobals.setProperty(name, value);
        }

        // And finally writer encrypte XMPP Settings to the DB
        @SuppressWarnings("unchecked") final Set<String> encryptedXmppSettings = (Set<String>) session.getAttribute("encryptedSettings");
        for (final String encryptedSetting : encryptedXmppSettings) {
            JiveGlobals.setPropertyEncrypted(encryptedSetting, true);
        }

        // Now, notify that the XMPP server that setup is finished.
        XMPPServer.getInstance().finishSetup();
    }

    pageContext.setAttribute("localizedTitle", LocaleUtils.getLocalizedString("title"));
    pageContext.setAttribute("errorMessage", errorMessage);
%>

<html>
    <head>
        <title><fmt:message key="setup.finished.title" /></title>
        <meta name="currentStep" content="5"/>
        <script type="text/javascript">

        function showhide(id){
            let obj = document.getElementById(id);
            if (obj.style.display === "none"){
                obj.style.display = "";
            } else {
                obj.style.display = "none";
            }
        }

        function toggleDivs() {
            showhide('loginlink');
            showhide('logintext');
        }
        </script>
    </head>
<body onload="setTimeout(toggleDivs, 1500);">

    <h1>
    <fmt:message key="setup.finished.title" />
    </h1>

    <p>
    <fmt:message key="setup.finished.info">
        <fmt:param value="${localizedTitle}" />
    </fmt:message>
    </p>

<%
    boolean useAdmin = false;
    try {
        List<JID> authorizedJIDS = AdminManager.getInstance().getAdminAccounts();
        useAdmin = authorizedJIDS == null || authorizedJIDS.isEmpty();
    }
    catch (Exception e) {
        // We were not able to load the list of admins right now, so move on.
    }
    String parameters = useAdmin ? "?username=admin" : "";

    // Figure out the URL that the user can use to login to the admin console.
    String url;
    if (XMPPServer.getInstance().isStandAlone()) {
        String server = request.getServerName();
        int plainPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        int securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        // Use secure login if we're currently secure (and the secure port isn't disabled)
        // or if the user disabled the plain port.
        if ((request.isSecure() && securePort > 0) || plainPort < 0) {
            url = "https://" + server + ":" + securePort + "/login.jsp"+parameters;
        }
        else {
            url = "http://" + server + ":" + plainPort + "/login.jsp"+parameters;
        }
    }
    else {
        url = request.getRequestURL().toString();
        url = url.replace("setup/setup-finished.jsp", "login.jsp"+parameters);
    }
%>
    <br><br>
    <c:choose>
        <c:when test="${empty errorMessage}">
            <div id="loginlink" style="display:none;" class="jive_setup_launchAdmin">
                <a href="<%= url %>"><fmt:message key="setup.finished.login" /></a>
            </div>

            <div id="logintext" class="jive_setup_launchAdmin">
                <fmt:message key="setup.finished.wait" /> <img src="../images/working-16x16.gif" alt="<fmt:message key="setup.finished.wait" />" width="16" height="16">
            </div>
        </c:when>
        <c:otherwise>
            <div class="error"><c:out value="${errorMessage}"/></div>
        </c:otherwise>
    </c:choose>

</body>
</html>
