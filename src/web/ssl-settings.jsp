<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.net.SSLConfig,
                 javax.net.ssl.KeyManager,
                 javax.net.ssl.TrustManager,
                 java.security.KeyStore,
                 java.security.cert.CertificateFactory,
                 java.security.cert.Certificate,
                 java.io.ByteArrayInputStream,
                 org.jivesoftware.admin.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="core" prefix="c" %>

<%  try { %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters:
    String type = ParamUtils.getParameter(request, "type");
    String cert = ParamUtils.getParameter(request, "cert");
    String alias = ParamUtils.getParameter(request, "alias");
    boolean install = request.getParameter("install") != null;
    boolean uninstall = ParamUtils.getBooleanParameter(request,"uninstall");

    KeyStore keyStore = SSLConfig.getKeyStore();
    KeyStore trustStore = SSLConfig.getTrustStore();

    Map errors = new HashMap();
    if (install) {
        if (cert == null){
            errors.put("cert","");
        }
        if (alias == null) {
            errors.put("alias","");
        }
        if (errors.size() == 0) {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate certificate = cf.generateCertificate(new ByteArrayInputStream(cert.getBytes()));
                if ("client".equals(type)){
                    trustStore.setCertificateEntry(alias,certificate);
                }
                else {
                    keyStore.setCertificateEntry(alias,certificate);
                }
                SSLConfig.saveStores();
                response.sendRedirect("ssl-settings.jsp?success=true");
                return;
            }
            catch (Exception e) {
                errors.put("general","");
            }
        }
    }
    if (uninstall) {
        if (type != null && alias != null) {
            try {
                if ("client".equals(type)){
                    SSLConfig.getTrustStore().deleteEntry(alias);
                }
                else if ("server".equals(type)) {
                    SSLConfig.getKeyStore().deleteEntry(alias);
                }
                SSLConfig.saveStores();
                response.sendRedirect("ssl-settings.jsp?deletesuccess=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("delete", e);
            }
        }
    }
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "SSL Security Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "ssl-settings.jsp"));
    pageinfo.setPageID("server-ssl");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<%  if (ParamUtils.getBooleanParameter(request,"success")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (ParamUtils.getBooleanParameter(request,"deletesuccess")) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Certificate uninstalled successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.containsKey("delete")) {
        Exception e = (Exception)errors.get("delete");
%>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error uninstalling the certificate.
        <%  if (e != null && e.getMessage() != null) { %>
            Error message: <%= e.getMessage() %>
        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Error installing the certificate, please see the form below.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<p>
SSL/TLS allows secure connections to be made between the server and clients.
This page displays installed certificates. Use the form at the bottom of the page to
install a new certificate.
</p>

<p><b>Installed Certificates</b></p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th width="1%">&nbsp;</th>
        <th>
            Alias (host)
        </th>
        <th>
            Certificate Type
        </th>
        <th width="1%">
            Uninstall
        </th>
    </tr>
</thead>
<tbody>

<%  int i=0;
    for (Enumeration aliases=keyStore.aliases(); aliases.hasMoreElements();) {
        i++;
        String a = (String)aliases.nextElement();
        Certificate c = keyStore.getCertificate(a);
%>
    <tr valign="top">
        <td width="1" rowspan="2"><%= (i) %>.</td>
        <td width="29%">
            <%= a %>
        </td>
        <td width="69%">
            <%= c.getType() %>
        </td>
        <td width="1" align="center">
            <a href="ssl-settings.jsp?alias=<%= a %>&type=server&uninstall=true"
             title="Click to uninstall..."
             onclick="return confirm('Are you sure you want to uninstall this certificate?');"
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
        </td>
    </tr>
    <tr>
        <td colspan="3">
            <span class="jive-description">
            Public Key:
            </span>
<textarea cols="40" rows="3" style="width:100%;font-size:8pt;" wrap="virtual">
<%= c.getPublicKey() %></textarea>
        </td>
    </tr>

<%  } %>

<%  if (i==0) { %>

    <tr>
        <td colspan="4">
            <p>
            No certificates installed. Use the form below to install one.
            </p>
        </td>
    </tr>

<%  } %>

</tbody>
</table>
</div>

<br><br>

<form action="ssl-settings.jsp" method="post">

<fieldset>
    <legend>Install Certificate</legend>
    <div>
    <p>
    New X.509 certificates can be added to the system by pasting in the certificate
    data sent to you by a Certificate Authority (e.g. Verisign) or you can
    generate your own self-signed certificates.
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <%  if (errors.containsKey("alias")) { %>
            <tr><td>&nbsp;</td>
                <td>
                    <span class="jive-error-text">
                    Please enter a valid alias.
                    </span>
                </td>
            </tr>
        <%  } else if (errors.containsKey("cert")) { %>
            <tr><td>&nbsp;</td>
                <td>
                    <span class="jive-error-text">
                    Please enter a valid certificate.
                    </span>
                </td>
            </tr>
        <%  } else if (errors.containsKey("general")) {
                String error = (String)errors.get("general");
        %>
            <tr><td>&nbsp;</td>
                <td>
                    <span class="jive-error-text">
                    Error installing the certificate.
                    <%  if (error != null && !"".equals(error.trim())) { %>
                        Error reported: <%= error %>.
                    <%  } %>
                    </span>
                </td>
            </tr>
        <%  } %>
        <tr>
            <td nowrap>Certificate Type:</td>
            <td>
                <select name="type" size="1">
                    <option value="server">Server Certificate</option>
                    <option value="client">Client Certificate</option>
                </select>
            </td>
        </tr>
        <tr>
            <td nowrap>Alias:</td>
            <td>
                <input name="alias" type="text" size="50" maxlength="255" value="<%= (alias != null ? alias : "") %>">
            </td>
        </tr>
        <tr valign="top">
            <td nowrap>Certificate:</td>
            <td>
                <span class="jive-description">
                Paste in the certificate sent to you by the CA or the self-signed certificate generated via the keytool.<br>
                </span>
                <textarea name="cert" cols="55" rows="7" wrap="virtual" style="font-size:8pt;"></textarea>
            </td>
        </tr>
        <tr>
            <td colspan="2">
                <br>
                <input type="submit" name="install" value="Add Certificate">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

</form>

<%  } catch (Throwable t) { t.printStackTrace(); } %>

<jsp:include page="bottom.jsp" flush="true" />
