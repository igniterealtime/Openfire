<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
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
    int port = ParamUtils.getIntParameter(request,"port",-1);
    int embeddedPort = ParamUtils.getIntParameter(request,"embeddedPort",-1);
    int sslPort = ParamUtils.getIntParameter(request,"sslPort",-1);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request,"sslEnabled",true);

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request:
    Map errors = new HashMap();
    if (doContinue) {
        // Validate parameters
        if (domain == null) {
            errors.put("domain","domain");
        }
        if (port < 0) {
            errors.put("port","port");
        }
        if (embeddedPort < 0) {
            errors.put("embeddedPort","embeddedPort");
        }
        if (sslEnabled) {
            if (sslPort < 0) {
                errors.put("sslPort","sslPort");
            }
        }
        // Continue if there were no errors
        if (errors.size() == 0) {
            Map xmppSettings = new HashMap();

            xmppSettings.put("xmpp.domain",domain);
            xmppSettings.put("xmpp.socket.plain.port",Integer.toString(port));
            xmppSettings.put("embedded-web.port",Integer.toString(embeddedPort));
            xmppSettings.put("xmpp.socket.ssl.active",""+sslEnabled);
            xmppSettings.put("xmpp.socket.ssl.port",Integer.toString(sslPort));
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
        port = JiveGlobals.getIntProperty("xmpp.socket.plain.port", 5222);
        embeddedPort = JiveGlobals.getIntProperty("embedded-web.port", 9090);
        sslPort = JiveGlobals.getIntProperty("xmpp.socket.ssl.port",5223);
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
Below are host and port settings for this server. Note, the suggested value for the
domain is based on the network settings of this machine.
</p>

<style type="text/css">
LABEL { font-weight : normal; }
</style>

<form action="setup-host-settings.jsp" name="f" method="post">

<script langauge="JavaScript" type="text/javascript">
function toggle(form,disabled) {
    form.sslPort.disabled = disabled;
}
</script>

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
        Hostname or IP address of the IM server.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Server Port:
        <%  if (errors.get("port") != null) { %>

            <span class="jive-error-text"><br>
            Invalid port number.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="port"
         value="<%= ((port != -1) ? ""+port : "5222") %>">
        <span class="jive-description">
        <br>
        Port number this server listens to. Default XMPP port is 5222.
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
        Port number for the web-based admin console. Default port is 9090.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        SSL Connections Enabled:
    </td>
    <td width="99%">
        <input type="radio" name="sslEnabled" value="true" id="rb01"
            onclick="toggle(this.form,false);"
            <%= ((sslEnabled) ? " checked" : "") %>>
        <label for="rb01">Yes</label>
        &nbsp;
        <input type="radio" name="sslEnabled" value="false" id="rb02"
            onclick="toggle(this.form,true);"
            <%= ((!sslEnabled) ? " checked" : "") %>>
        <label for="rb02">No</label>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        SSL Port:
        <%  if (sslEnabled && errors.get("sslPort") != null) { %>

            <span class="jive-error-text"><br>
            Invalid port number.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="sslPort"
         value="<%= ((sslPort != -1) ? ""+sslPort : "") %>">
        <span class="jive-description">
        <br>
        Port number this server listens to for SSL connections. Default SSL XMPP port is 5223.
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
// set default disabled state of ssl
if (document.f.sslEnabled[0].checked) {
    toggle(document.f,false);
}
else {
    toggle(document.f,true);
}
</script>

<%@ include file="setup-footer.jsp" %>