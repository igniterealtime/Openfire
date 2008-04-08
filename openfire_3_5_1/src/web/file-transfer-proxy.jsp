<%--
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.filetransfer.proxy.FileTransferProxy" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer"%>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<% webManager.init(request, response, session, application, out); %>

<%
    Map<String, String> errors = new HashMap<String, String>();
    FileTransferProxy transferProxy = XMPPServer.getInstance().getFileTransferProxy();

    boolean isUpdated = request.getParameter("update") != null;
    boolean isProxyEnabled = ParamUtils.getBooleanParameter(request, "proxyEnabled");
    int port = ParamUtils.getIntParameter(request, "port", 0);

    if (isUpdated) {
        if (isProxyEnabled) {
            if (port <= 0) {
                errors.put("port", "");
            }
        }

        if (errors.isEmpty()) {
            if (isProxyEnabled) {
                transferProxy.setProxyPort(port);
            }
            transferProxy.enableFileTransferProxy(isProxyEnabled);
            // Log the event
            webManager.logEvent("edited file transfer proxy settings", "port = "+port+"\nenabled = "+isProxyEnabled);
        }
    }

    if (errors.isEmpty()) {
        isProxyEnabled = transferProxy.isProxyEnabled();
        port = transferProxy.getProxyPort();
    }
    else {
        if (port == 0) {
            port = transferProxy.getProxyPort();
        }
    }
%>

<html>
<head>
<title><fmt:message key="filetransferproxy.settings.title"/></title>
</head>
<meta name="pageID" content="server-transfer-proxy"/>
<body>

<p>
    <fmt:message key="filetransferproxy.settings.info"/>
</p>

<%  if (!errors.isEmpty()) { %>
<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr>
                <td class="jive-icon"><img alt="error" src="images/error-16x16.gif" width="16" height="16"
                                           border="0"/></td>
                <td class="jive-icon-label">
                   <% if (errors.get("port") != null) { %>
                    <fmt:message key="filetransferproxy.settings.valid.port"/>
                    <% }  %>
                </td>
            </tr>
        </tbody>
    </table>
</div>
<br>
<%  }
else if (isUpdated) { %>
<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
            <tr><td class="jive-icon"><img alt="Success" src="images/success-16x16.gif" width="16" height="16"
                                           border="0"></td>
                <td class="jive-icon-label">
                    <fmt:message key="filetransferproxy.settings.confirm.updated"/>

                </td></tr>
        </tbody>
    </table>
</div><br>
<% }
else { %>
<br>
<% } %>


<!-- BEGIN 'Proxy Service' -->
<form action="file-transfer-proxy.jsp" method="post">
	<div class="jive-contentBoxHeader">
		<fmt:message key="filetransferproxy.settings.enabled.legend"/>
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input type="radio" name="proxyEnabled" value="true" id="rb02"
				<%= (isProxyEnabled ? "checked" : "") %> >
			</td>
			<td width="99%">
				<label for="rb02">
					<b><fmt:message key="filetransferproxy.settings.label_enable"/></b>
					- <fmt:message key="filetransferproxy.settings.label_enable_info"/>
				</label>  <input type="text" size="5" maxlength="10" name="port"
								 value="<%= port %>" >
			</td>
		</tr>
		<tr valign="middle">
			<td width="1%" nowrap>
				<input type="radio" name="proxyEnabled" value="false" id="rb01"
				<%= (!isProxyEnabled ? "checked" : "") %> >
			</td>
			<td width="99%">
				<label for="rb01">
					<b><fmt:message key="filetransferproxy.settings.label_disable"/></b>
					- <fmt:message key="filetransferproxy.settings.label_disable_info"/>
				</label>
			</td>
		</tr>
		</tbody>
		</table>
	</div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Proxy Service' -->

</body>
</html>