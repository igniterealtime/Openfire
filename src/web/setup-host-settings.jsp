<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
                 java.util.Map,
                 java.util.HashMap,
                 java.net.InetAddress" %>

<%@ include file="setup-global.jspf" %>

<%  // Get parameters
    String domain = ParamUtils.getParameter(request,"domain");
    int embeddedPort = ParamUtils.getIntParameter(request,"embeddedPort",-1);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request,"sslEnabled",true);

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request:
    Map errors = new HashMap();
    if (doContinue) {
        // Validate parameters
        if (domain == null) {
            errors.put("domain","domain");
        }
        if (embeddedPort < 0) {
            errors.put("embeddedPort","embeddedPort");
        }
        // Continue if there were no errors
        if (errors.size() == 0) {
            Map xmppSettings = new HashMap();

            xmppSettings.put("xmpp.domain",domain);
            xmppSettings.put("adminConsole.port",Integer.toString(embeddedPort));
            xmppSettings.put("xmpp.socket.ssl.active",""+sslEnabled);
            xmppSettings.put("xmpp.auth.anonymous", "true" );
            session.setAttribute("xmppSettings", xmppSettings);

            // update the sidebar status
            session.setAttribute("jive.setup.sidebar.2","done");
            session.setAttribute("jive.setup.sidebar.3","in_progress");

            // successful, so redirect
            response.sendRedirect("setup-datasource-settings.jsp");
            return;
        }
    }

    // Load the current values:
    if (!doContinue) {
        domain = JiveGlobals.getProperty("xmpp.domain");
        embeddedPort = JiveGlobals.getIntProperty("adminConsole.port", 9090);
        sslEnabled = JiveGlobals.getBooleanProperty("xmpp.socket.ssl.active", true);

        // If the domain is still blank, guess at the value:
        if (domain == null) {
            domain = InetAddress.getLocalHost().getHostName().toLowerCase();
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Server Settings
</p>

<p>
Below are host settings for this server. Note: the suggested value for the
domain is based on the network settings of this machine.
</p>

<style type="text/css">
LABEL { font-weight : normal; }
</style>

<form action="setup-host-settings.jsp" name="f" method="post">

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="1%" nowrap>
        Domain:
        <%  if (errors.get("domain") != null) { %>

            <span class="jive-error-text"><br>
            Invalid domain.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="domain"
         value="<%= ((domain != null) ? domain : "") %>">
        <span class="jive-description">
        <br>
        Hostname or IP address of this server.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Admin Console Port:
        <%  if (errors.get("embeddedPort") != null) { %>

            <span class="jive-error-text"><br>
            Invalid port number.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="embeddedPort"
         value="<%= ((embeddedPort != -1) ? ""+embeddedPort : "9090") %>">
        <span class="jive-description">
        <br>
        Port number for the web-based admin console (default is 9090).
        </span>
    </td>
</tr>
<tr valign="middle">
    <td width="1%" nowrap>
        SSL Connections Enabled:
    </td>
    <td width="99%">
        <input type="radio" name="sslEnabled" value="true" id="rb01"
            <%= ((sslEnabled) ? " checked" : "") %>>
        <label for="rb01">Yes</label>
        &nbsp;
        <input type="radio" name="sslEnabled" value="false" id="rb02"
            <%= ((!sslEnabled) ? " checked" : "") %>>
        <label for="rb02">No</label>
        <span class="jive-description">
        <br>
        Enables or disables secure XMPP connections.
        </span>
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right">
<input type="submit" name="continue" value=" Continue ">
</div>
</form>

<script language="JavaScript" type="text/javascript">
// give focus to domain field
document.f.domain.focus();
</script>

<%@ include file="setup-footer.jsp" %>