<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.net.SSLConfig,
                 com.sun.net.ssl.KeyManager,
                 com.sun.net.ssl.TrustManager,
                 java.security.KeyStore,
                 java.security.cert.CertificateFactory,
                 java.security.cert.Certificate,
                 java.io.ByteArrayInputStream,
                 org.jivesoftware.admin.*"
%>
<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "SSL Security Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "ssl-settings.jsp"));
    pageinfo.setPageID("server-ssl");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />


<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    KeyStore keyStore = SSLConfig.getKeyStore();
    KeyStore trustStore = SSLConfig.getTrustStore();

    Map errors = new HashMap();
    if (update) {
        // All done, redirect
        String type = request.getParameter("type");
        String cert = request.getParameter("cert");
        String alias = request.getParameter("alias");
        if (cert == null || cert.trim().length() == 0){
            errors.put("cert","");
        }
        if (alias == null || alias.trim().length() == 0){
            errors.put("alias","");
        }
        if (errors.size() == 0){
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate = cf.generateCertificate(
                                        new ByteArrayInputStream(cert.getBytes()));
            if ("client".equals(type)){
                trustStore.setCertificateEntry(alias,certificate);
            }
            else {
                keyStore.setCertificateEntry(alias,certificate);
            }
            SSLConfig.saveStores();
            response.sendRedirect("ssl-settings.jsp?success=true");
        }
        return;
    }

    // Set page vars
    if (errors.size() == 0) {
        //
    }
%>

<%  if (ParamUtils.getBooleanParameter(request,"success")) { %>

    <p class="jive-success-text">
    Settings updated.
    </p>

<%  } %>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">

<tr><td colspan="2" class="text">
SSL/TLS allows secure connections to be made between the server and clients.
This page displays your current SSL/TLS setup.
</td></tr>

<tr>
    <th class="jive-label">Default Cipher Suites</th>
    <th class="jive-label">Supported Cipher Suites</th>
</tr>
<tr valign="top">
    <td>
<%  String[] defaults = SSLConfig.getDefaultCipherSuites();
    for (int i = 0; i < defaults.length; i++){ %>

    <%= defaults[i] %><br>
    <% } %>
    </td><td>
<%  String[] supported = SSLConfig.getDefaultCipherSuites();
    for (int i = 0; i < supported.length; i++){ %>

    <%= supported[i] %><br>
    <% } %>
    </td>
</table>
<br/>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr class="tableHeaderBlue"><td colspan="3" align="center">Server Certificates</td></tr>
<tr><td colspan="3" class="text">
These certificates identify the server to connecting clients.
</td></tr>
<tr>
    <th class="jive-label">Alias (domain)</th>
    <th class="jive-label">Delete</th>
    <th class="jive-label">Certificate summary</th>
</tr>
<%
    Enumeration aliases = keyStore.aliases();
    while (aliases.hasMoreElements()){
        String alias = aliases.nextElement().toString();
%>
<tr valign="top">
    <td class="jive-label" width="1%">
    <%= alias %>
    </td>
    <td class="jive-label" width="1%" align="center">
    <a href="ssl-delete.jsp?alias=<%= alias %>&type=server"
             title="Click to delete..."
             ><img src="images/delete-16x16.gif" width="16" height="16" border="0"></a>
    </td><td width="99%">
    <pre>
    <%= keyStore.getCertificate(alias).toString() %>
    </pre>
    </td>
<tr>
<% } %>
</table>
<br/>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr class="tableHeaderBlue"><td colspan="3" align="center">Client Certificates</td></tr>
<tr><td class="text" colspan="3">
These certificates identify connecting clients to the server and
is often empty.
</td></tr>
<tr>
    <th class="jive-label">Alias (username)</th>
    <th class="jive-label">Delete</th>
    <th class="jive-label">Certificate summary</th>
</tr>
<%
    aliases = trustStore.aliases();
    while (aliases.hasMoreElements()){
        String alias = aliases.nextElement().toString();
%>
<tr valign="top">
    <td class="jive-label" width="1%">
    <%= alias %>
    </td>
    <td class="jive-label" width="1%" align="center">
    <a href="ssl-delete.jsp?alias=<%= alias %>&type=client"
             title="Click to delete..."
             ><img src="images/delete-16x6.gif" width="16" height="16" border="0"></a>
    </td><td width="99%">
    <pre>
    <%= trustStore.getCertificate(alias).toString() %>
    </pre>
    </td>
</tr>
<% } %>
</table>
<br>
<form action="ssl-settings.jsp">
<table class="box" cellpadding="3" cellspacing="1" border="0" width="100%">
<tr class="tableHeaderBlue"><td colspan="3" align="center">Add Certificate</td></tr>
<tr><td class="text" colspan="3">
New X.509 certificates can be added to the system by pasting in the certificate
data sent to you by a Certificate Authority (e.g. Verisign) or you can
generate your own self-signed certificates.

<table cellpadding="3" cellspacing="1" border="0" width="100%">
<tr>
    <td class="jive-label">Certificate Type</td>
    <td><select name="type" size="1">
        <option value="server">Server Certificate</option>
        <option value="client">Client Certificate</option>
        </select>
    </td>
</tr>
<tr>
    <td class="jive-label">Alias</td>
    <td><input name="alias" type="text" size="50"></td>

        <%  if (errors.get("alias") != null) { %>

            <span class="jive-error-text">
            You must specify a non-empty alias.
            </span>

        <%  } %>

</tr>
<tr>
    <td class="jive-label" valign="top">Certificate</td>
    <td>Paste in the certificate sent to you by the CA
    or the self-signed certificate generated via the keytool.
    <textarea name="cert" cols="55" rows="10" wrap="virtual"></textarea></td>
        <%  if (errors.get("cert") != null) { %>

            <span class="jive-error-text">
            You must include the complete certificate text.
            </span>

        <%  } %>
</tr>
</table>

<input type="submit" name="update" value="Add Certificate">
</form>
<br />
<jsp:include page="bottom.jsp" flush="true" />
