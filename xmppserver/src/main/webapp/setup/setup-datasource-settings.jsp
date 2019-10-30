<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.EmbeddedConnectionProvider,
                 org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="java.io.File"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
<%@ page import="org.jivesoftware.util.*" %>
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

<%
    final String STANDARD = "standard";
    final String JNDI = "jndi";
    final String EMBEDDED = "embedded";

    boolean embeddedMode = false;
    try {
        ClassUtils.forName("org.jivesoftware.openfire.starter.ServerStarter");
        embeddedMode = true;
    }
    catch (Exception ignored) {}

    // Get parameters
    String mode = ParamUtils.getParameter(request,"mode");
    boolean next = ParamUtils.getBooleanParameter(request,"next");

    Map<String,String> errors = new HashMap<>();

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (next || mode != null) {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) ) {
            next = false;
            mode = null;
            errors.put( "general", "CSRF Failure!" );
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // handle a mode redirect
    if (next) {
        if (STANDARD.equals(mode)) {
            response.sendRedirect("setup-datasource-standard.jsp");
            return;
        }
        else if (JNDI.equals(mode)) {
            if (!embeddedMode) {
                response.sendRedirect("setup-datasource-jndi.jsp");
                return;
            }
        }
        else if (EMBEDDED.equals(mode)) {
            // Set the classname of the provider in the config file:
            JiveGlobals.setXMLProperty("connectionProvider.className",
                    "org.jivesoftware.database.EmbeddedConnectionProvider");
            ConnectionProvider conProvider = new EmbeddedConnectionProvider();
            DbConnectionManager.setConnectionProvider(conProvider);
            if (DbConnectionManager.testConnection(errors)) {
                // Redirect
                response.sendRedirect("setup-profile-settings.jsp");
                return;
            }
        }
    }

    // Defaults
    if (mode == null) {
        // If the "embedded-database" directory exists, select to the embedded db as the default.
        if (new File(JiveGlobals.getHomeDirectory(), "embedded-db").exists()) {
            mode = EMBEDDED;
        }
        // Otherwise default to standard.
        else {
            mode = STANDARD;
        }
    }
    pageContext.setAttribute("localizedShortTitle", LocaleUtils.getLocalizedString("short.title") );
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("mode", mode);
    pageContext.setAttribute("embeddedMode", embeddedMode);
%>

<html>
<head>
    <title><fmt:message key="setup.datasource.settings.title" /></title>
    <meta name="currentStep" content="2"/>
</head>
<body>

    <h1>
    <fmt:message key="setup.datasource.settings.title" />
    </h1>

    <p>
    <fmt:message key="setup.datasource.settings.info">
        <fmt:param value="${localizedShortTitle}"/>
    </fmt:message>
    </p>

    <c:if test="${not empty errors}">
        <p class="jive-error-text">
            <c:out value="${errors['general']}"/>
        </p>
    </c:if>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">

        <form action="setup-datasource-settings.jsp">
<input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="next" value="true">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="standard" id="rb02" ${mode eq 'standard' ? 'checked' : ''}>
    </td>
    <td>
        <label for="rb02"><b><fmt:message key="setup.datasource.settings.connect" /></b></label>
        <br><fmt:message key="setup.datasource.settings.connect_info" />
    </td>
</tr>

<c:if test="${not embeddedMode}">
    <tr>
        <td align="center" valign="top">
            <input type="radio" name="mode" value="jndi" id="rb03" ${mode eq 'jndi' ? 'checked' : ''}>
        </td>
        <td>
            <label for="rb03"><b><fmt:message key="setup.datasource.settings.jndi" /></b></label>
            <br><fmt:message key="setup.datasource.settings.jndi_info" />
        </td>
    </tr>
</c:if>

<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="embedded" id="rb01" ${mode eq 'embedded' ? 'checked' : ''}>
    </td>
    <td>
        <label for="rb01"><b><fmt:message key="setup.datasource.settings.embedded" /></b></label>
        <br><fmt:message key="setup.datasource.settings.embedded_info" />
    </td>
</tr>
</table>

<br><br>


        <div align="right">
            <input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
        </div>
    </form>

    </div>
    <!-- END jive-contentBox -->


</body>
</html>
