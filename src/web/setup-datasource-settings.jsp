<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.database.EmbeddedConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 java.util.*" %>

<%! // Global vars

    static final String STANDARD = "standard";
    static final String JNDI = "jndi";
    static final String EMBEDDED = "embedded";
%>

<%@ include file="setup-global.jspf" %>

<%  // Get parameters
    String mode = ParamUtils.getParameter(request,"mode");
    boolean next = ParamUtils.getBooleanParameter(request,"next");

    // handle a mode redirect
    Map errors = new HashMap();
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
            if (testConnection(errors)) {
                // Update the sidebar status
                session.setAttribute("jive.setup.sidebar.3","done");
                session.setAttribute("jive.setup.sidebar.4","in_progress");
                // Redirect
                response.sendRedirect("setup-admin-settings.jsp");
                return;
            }
        }
    }

    // Defaults
    if (mode == null) {
        // If the "embedded-database" directory exists, select to the embedded db as the default.
        if (new File(JiveGlobals.getMessengerHome(), "embedded-db").exists()) {
            mode = EMBEDDED;
        }
        // Otherwise default to standard.
        else {
            mode = STANDARD;
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="setup.datasource.settings.title" />
</p>

<p>
<fmt:message key="setup.datasource.settings.info" /> <fmt:message key="short.title" /> 
<fmt:message key="setup.datasource.settings.info1" />
</p>

<%  if (errors.size() > 0) { %>

    <p class="jive-error-text">
    <%= errors.get("general") %>
    </p>

<%  } %>

<form action="setup-datasource-settings.jsp">
<input type="hidden" name="next" value="true">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="<%= STANDARD %>" id="rb02"
         <%= ((STANDARD.equals(mode)) ? "checked" : "") %>>
    </td>
    <td>
        <label for="rb02"><b><fmt:message key="setup.datasource.settings.connect" /></b></label> -
        <fmt:message key="setup.datasource.settings.connect_info" />
    </td>
</tr>

<%  if (!embeddedMode) { %>

    <tr>
        <td align="center" valign="top">
            <input type="radio" name="mode" value="<%= JNDI %>" id="rb03"
             <%= ((JNDI.equals(mode)) ? "checked" : "") %>>
        </td>
        <td>
            <label for="rb03"><b><fmt:message key="setup.datasource.settings.jndi" /></b></label> -
            <fmt:message key="setup.datasource.settings.jndi_info" />
        </td>
    </tr>

<%  } %>

<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="<%= EMBEDDED %>" id="rb01"
         <%= ((EMBEDDED.equals(mode)) ? "checked" : "") %>>
    </td>
    <td>
        <label for="rb01"><b><fmt:message key="setup.datasource.settings.embedded" /></b></label> -
        <fmt:message key="setup.datasource.settings.embedded_info" />
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right"><input type="submit" value=" Continue "></div>

</form>

<%@ include file="setup-footer.jsp" %>