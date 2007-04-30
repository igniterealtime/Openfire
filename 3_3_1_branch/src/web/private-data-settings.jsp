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
                 org.jivesoftware.openfire.*,
                 org.jivesoftware.admin.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="private.data.settings.title"/></title>
<meta name="pageID" content="server-data-settings"/>
<meta name="helpPage" content="set_private_data_storage_policy.html"/>
</head>
<body>

<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean privateEnabled = ParamUtils.getBooleanParameter(request,"privateEnabled");

    // Get an audit manager:
    PrivateStorage privateStorage = webManager.getPrivateStore();

    Map errors = new HashMap();
    if (update) {
      privateStorage.setEnabled(privateEnabled);
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="private.data.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%
    
    }

    // Set page vars
    if (errors.size() == 0) {
        privateEnabled = privateStorage.isEnabled();
    }
%>

<p>
<fmt:message key="private.data.settings.info" />
</p>

<!-- BEGIN 'Set Private Data Policy' -->
<form action="private-data-settings.jsp">
	<div class="jive-contentBoxHeader">
		<fmt:message key="private.data.settings.policy" />
	</div>
	<div class="jive-contentBox">
		<table cellpadding="3" cellspacing="0" border="0">
		<tbody>
			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="privateEnabled" value="true" id="rb01"
					 <%= (privateEnabled ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb01">
					<b><fmt:message key="private.data.settings.enable_storage" /></b> -
					<fmt:message key="private.data.settings.enable_storage_info" />
					</label>
				</td>
			</tr>
			<tr valign="top">
				<td width="1%" nowrap>
					<input type="radio" name="privateEnabled" value="false" id="rb02"
					 <%= (!privateEnabled ? "checked" : "") %>>
				</td>
				<td width="99%">
					<label for="rb02">
					<b><fmt:message key="private.data.settings.disable_storage" /></b> -
					<fmt:message key="private.data.settings.disable_storage_info" />
					</label>
				</td>
			</tr>
		</tbody>
		</table>
	</div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END 'Set Private Data Policy' -->

</body>
</html>