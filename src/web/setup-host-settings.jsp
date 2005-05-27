<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 java.util.Map,
                 java.util.HashMap,
                 java.net.InetAddress"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ include file="setup-global.jspf" %>

<%  // Get parameters
    String domain = ParamUtils.getParameter(request,"domain");
    int embeddedPort = ParamUtils.getIntParameter(request, "embeddedPort", -1);
    int securePort = ParamUtils.getIntParameter(request, "securePort", -1);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request, "sslEnabled", true);

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
            xmppSettings.put("xmpp.socket.ssl.active",""+sslEnabled);
            xmppSettings.put("xmpp.auth.anonymous", "true" );
            session.setAttribute("xmppSettings", xmppSettings);

            Map xmlSettings = new HashMap();
            xmlSettings.put("adminConsole.port",Integer.toString(embeddedPort));
            xmlSettings.put("adminConsole.securePort",Integer.toString(securePort));
            session.setAttribute("xmlSettings", xmlSettings);

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
        embeddedPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        sslEnabled = JiveGlobals.getBooleanProperty("xmpp.socket.ssl.active", true);

        // If the domain is still blank, guess at the value:
        if (domain == null) {
            domain = InetAddress.getLocalHost().getHostName().toLowerCase();
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<style type="text/css">
LABEL { font-weight : normal; }
</style>

<p class="jive-setup-page-header">
<fmt:message key="setup.host.settings.title" />
</p>

<p>
<fmt:message key="setup.host.settings.info" />
</p>

<form action="setup-host-settings.jsp" name="f" method="post">

<table cellpadding="3" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="1%" nowrap>
        <fmt:message key="setup.host.settings.domain" />
        <%  if (errors.get("domain") != null) { %>

            <span class="jive-error-text"><br>
            <fmt:message key="setup.host.settings.invalid_domain" />
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="domain"
         value="<%= ((domain != null) ? domain : "") %>">
        <span class="jive-description">
        <br>
        <fmt:message key="setup.host.settings.hostname" />
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        <fmt:message key="setup.host.settings.port" />
        <%  if (errors.get("embeddedPort") != null) { %>

            <span class="jive-error-text"><br>
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="embeddedPort"
         value="<%= ((embeddedPort != -1) ? ""+embeddedPort : "9090") %>">
        <span class="jive-description">
        <br>
        <fmt:message key="setup.host.settings.port_number" />
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        <fmt:message key="setup.host.settings.secure_port" />
        <%  if (errors.get("securePort") != null) { %>

            <span class="jive-error-text"><br>
            <fmt:message key="setup.host.settings.invalid_port" />
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="securePort"
         value="<%= ((securePort != -1) ? ""+securePort : "9091") %>">
        <span class="jive-description">
        <br>
        <fmt:message key="setup.host.settings.secure_port_number" />
        </span>
    </td>
</tr>
<tr valign="middle">
    <td width="1%" nowrap>
        <fmt:message key="setup.host.settings.ssl" />
    </td>
    <td width="99%">
        <input type="radio" name="sslEnabled" value="true" id="rb01"
            <%= ((sslEnabled) ? " checked" : "") %>>
        <label for="rb01"><fmt:message key="setup.host.settings.yes" /></label>
        &nbsp;
        <input type="radio" name="sslEnabled" value="false" id="rb02"
            <%= ((!sslEnabled) ? " checked" : "") %>>
        <label for="rb02"><fmt:message key="setup.host.settings.no" /></label>
        <span class="jive-description">
        <br>
        <fmt:message key="setup.host.settings.secure" />
        </span>
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right">
<input type="submit" name="continue" value=" <fmt:message key="global.continue" /> ">
</div>
</form>

<script language="JavaScript" type="text/javascript">
// give focus to domain field
document.f.domain.focus();
</script>

<%@ include file="setup-footer.jsp" %>