<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.EmbeddedConnectionProvider,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.ClassUtils,
                 org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils"%>
<%@ page import="org.jivesoftware.util.ParamUtils"%>
<%@ page import="java.io.File"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Map"%>
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

    // handle a mode redirect
    Map<String,String> errors = new HashMap<String,String>();
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

<%  if (errors.size() > 0) { %>

    <p class="jive-error-text">
    <%= errors.get("general") %>
    </p>

<%  } %>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">

        <form action="setup-datasource-settings.jsp">

<input type="hidden" name="next" value="true">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="<%= STANDARD %>" id="rb02"
         <%= ((STANDARD.equals(mode)) ? "checked" : "") %>>
    </td>
    <td>
        <label for="rb02"><b><fmt:message key="setup.datasource.settings.connect" /></b></label>
        <br><fmt:message key="setup.datasource.settings.connect_info" />
    </td>
</tr>

<%  if (!embeddedMode) { %>

    <tr>
        <td align="center" valign="top">
            <input type="radio" name="mode" value="<%= JNDI %>" id="rb03"
             <%= ((JNDI.equals(mode)) ? "checked" : "") %>>
        </td>
        <td>
            <label for="rb03"><b><fmt:message key="setup.datasource.settings.jndi" /></b></label>
            <br><fmt:message key="setup.datasource.settings.jndi_info" />
        </td>
    </tr>

<%  } %>

<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="<%= EMBEDDED %>" id="rb01"
         <%= ((EMBEDDED.equals(mode)) ? "checked" : "") %>>
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
