<%@ taglib uri="core" prefix="c"%>
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

<%@ include file="setup-global.jsp" %>

<%  // Get parameters
    String domain = ParamUtils.getParameter(request,"domain");
    String chatDomain = ParamUtils.getParameter(request,"chatDomain");
    int port = ParamUtils.getIntParameter(request,"port",-1);
    int embeddedPort = ParamUtils.getIntParameter(request,"embeddedPort",-1);
    int sslPort = ParamUtils.getIntParameter(request,"sslPort",-1);
    String storeType = ParamUtils.getParameter(request,"storeType",true);
    String keystore = ParamUtils.getParameter(request,"keystore",true);
    String keypass = ParamUtils.getParameter(request,"keypass",true);
    String truststore = ParamUtils.getParameter(request,"truststore",true);
    String trustpass = ParamUtils.getParameter(request,"trustpass",true);
    boolean sslEnabled = ParamUtils.getBooleanParameter(request,"sslEnabled");

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request:
    Map errors = new HashMap();
    if (doContinue) {
        // Validate parameters
        if (domain == null) {
            errors.put("domain","domain");
        }
        if (chatDomain == null) {
            errors.put("chatDomain","chatDomain");
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
            JiveGlobals.setProperty("xmpp.domain",domain);
            JiveGlobals.setProperty("xmpp.chat.domain",chatDomain);
            JiveGlobals.setProperty("xmpp.socket.plain.port",Integer.toString(port));
            JiveGlobals.setProperty("embedded-web.port",Integer.toString(embeddedPort));
            JiveGlobals.setProperty("xmpp.socket.ssl.active",""+sslEnabled);
            JiveGlobals.setProperty("xmpp.socket.ssl.port",Integer.toString(sslPort));
            JiveGlobals.setProperty("xmpp.auth.anonymous", "true" );
            // JiveGlobals.setProperty("xmpp.socket.ssl.storeType",storeType);
            // JiveGlobals.setProperty("xmpp.socket.ssl.keystore",keystore);
            // JiveGlobals.setProperty("xmpp.socket.ssl.keypass",keypass);
            // JiveGlobals.setProperty("xmpp.socket.ssl.truststore",truststore);
            // JiveGlobals.setProperty("xmpp.socket.ssl.trustpass",trustpass);

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
        chatDomain = JiveGlobals.getProperty("xmpp.chat.domain");
        // storeType = JiveGlobals.getProperty("xmpp.socket.ssl.storeType");
        // keystore = JiveGlobals.getProperty("xmpp.socket.ssl.keystore");
        // keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass");
        // truststore = JiveGlobals.getProperty("xmpp.socket.ssl.truststore");
        // trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass");
        try {
            port = Integer.parseInt(JiveGlobals.getProperty("xmpp.socket.plain.port"));
        } catch (Exception ignored) {}
        try {
            embeddedPort = Integer.parseInt(JiveGlobals.getProperty("embedded-web.port"));
        } catch (Exception ignored) {}
        try {
            sslPort = Integer.parseInt(JiveGlobals.getProperty("xmpp.socket.ssl.port"));
        } catch (Exception ignored) {}
        sslEnabled = "true".equals(JiveGlobals.getProperty("xmpp.socket.ssl.active"));

        // If the domain and chat domain are still blank, guess at their values:
        if (domain == null) {
            domain = InetAddress.getLocalHost().getHostName();
            if (domain != null && chatDomain == null) {
                chatDomain = "chat." + domain;
            }
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Server Settings
</p>

<p>
Below are host and port settings for this server. Note, Setup has suggested values for the
domain and chat domain fields based on the network settings for this machine.
</p>

<script language="JavaScript" type="text/javascript">
function fillChatDomain(el) {
    if (el.chatDomain.value == '' && el.domain.value != '') {
        el.chatDomain.value = 'chat.' + el.domain.value;
    }
}
</script>

<style type="text/css">
LABEL { font-weight : normal; }
</style>

<form action="setup-host-settings.jsp" name="f" method="post">

<script langauge="JavaScript" type="text/javascript">
function toggle(form,disabled) {
    form.sslPort.disabled = disabled;
    // form.storeType.disabled = disabled;
    // form.keystore.disabled = disabled;
    // form.keypass.disabled = disabled;
    // form.truststore.disabled = disabled;
    // form.trustpass.disabled = disabled;
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
         onblur="fillChatDomain(this.form);"
         value="<%= ((domain != null) ? domain : "") %>">
        <span class="jive-description">
        <br>
        Hostname or IP address of the IM server.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Chat Domain:
        <%  if (errors.get("chatDomain") != null) { %>

            <span class="jive-error-text"><br>
            Invalid chat domain.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="chatDomain"
         value="<%= ((chatDomain != null) ? chatDomain : "") %>">
        <span class="jive-description">
        <br>
        Hostname or IP address of the chat server.
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
         value="<%= ((port != -1) ? ""+port : "") %>">
        <span class="jive-description">
        <br>
        Port number this server listens to. Default XMPP port is 5222.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Embedded Web Server Port:
        <%  if (errors.get("embeddedPort") != null) { %>

            <span class="jive-error-text"><br>
            Invalid port number.
            </span>

        <%  } %>
    </td>
    <td width="99%">
        <input type="text" size="6" maxlength="6" name="embeddedPort"
         value="<%= ((embeddedPort != -1) ? ""+embeddedPort : "") %>">
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
<!--
<tr valign="top">
    <td width="1%" nowrap>
        Server Certificate Store Type:
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="storeType"
         value="<%= ((storeType != null) ? storeType : "") %>">
        <span class="jive-description">
        <br>
        A code for the type of key store. Default is 'JKS' for Java Key Store (Sun's implmentation).
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Server Certificate Store:
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="keystore"
         value="<%= ((keystore != null) ? keystore : "") %>">
        <span class="jive-description">
        <br>
        Filename (relative to jiveHome directory) of the certificate used for this server.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Server Store Password:
    </td>
    <td width="99%">
        <input type="password" size="15" maxlength="50" name="keypass"
         value="<%= ((keypass != null) ? keypass : "") %>">
        <span class="jive-description">
        <br>
        Password for the server's certificate.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Client Certificate Store:
    </td>
    <td width="99%">
        <input type="text" size="30" maxlength="150" name="truststore"
         value="<%= ((truststore != null) ? truststore : "") %>">
        <span class="jive-description">
        <br>
        Filename (relative to jiveHome directory) of the store used for client certificates.
        </span>
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        Client Store Password:
    </td>
    <td width="99%">
        <input type="password" size="15" maxlength="50" name="trustpass"
         value="<%= ((trustpass != null) ? trustpass : "") %>">
        <span class="jive-description">
        <br>
        Password for the client certificate store.
        </span>
    </td>
</tr>
-->
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