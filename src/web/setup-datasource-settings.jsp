<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.database.EmbeddedConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager,
                 java.util.HashMap,
                 org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.ConnectionProvider,
                 org.jivesoftware.database.DbConnectionManager" %>

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
            JiveGlobals.setProperty("connectionProvider.className",
                    "org.jivesoftware.database.EmbeddedConnectionProvider");
            ConnectionProvider conProvider = new EmbeddedConnectionProvider();
            DbConnectionManager.setConnectionProvider(conProvider);
            if (testConnection(errors)) {
                // update the sidebar status
                session.setAttribute("jive.setup.sidebar.3","done");
                session.setAttribute("jive.setup.sidebar.4","in_progress");
                // redirect
                response.sendRedirect("setup-admin-settings.jsp");
                return;
            }
        }
    }

    // Defaults
    if (mode == null) {
        mode = STANDARD;
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Datasource Settings
</p>

<p>
Choose how you would like to connect to the <fmt:message key="short.title" bundle="${lang}" /> database.
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
        <label for="rb02"><b>Standard Database Connection</b></label> -
        Use an external database with the built-in connection pool.
    </td>
</tr>

<%  if (!embeddedMode) { %>

    <tr>
        <td align="center" valign="top">
            <input type="radio" name="mode" value="<%= JNDI %>" id="rb03"
             <%= ((JNDI.equals(mode)) ? "checked" : "") %>>
        </td>
        <td>
            <label for="rb03"><b>JNDI Datasource</b></label> -
            Use a datasource defined by your application server via JNDI.
        </td>
    </tr>

<%  } %>

<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="<%= EMBEDDED %>" id="rb01"
         <%= ((EMBEDDED.equals(mode)) ? "checked" : "") %>>
    </td>
    <td>
        <label for="rb01"><b>Embedded Database</b></label> -
        Use an embedded database, powered by HSQLDB. This option requires no external database
        configuration and is an easy way to get running quickly. However, this is not recommended
        for large installations.
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right"><input type="submit" value=" Continue "></div>

</form>

<%@ include file="setup-footer.jsp" %>