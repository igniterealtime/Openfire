<%@ page import="org.jivesoftware.wildfire.HttpServerManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%--
  -	$Revision:  $
  -	$Date:  $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    Map<String, String> errorMap = new HashMap<String, String>();
    HttpServerManager serverManager = HttpServerManager.getInstance();

    void handleUpdate(HttpServletRequest request) {
        boolean isEnabled = ParamUtils.getBooleanParameter(request, "httpBindEnabled");
        if (isEnabled) {
            boolean httpPortsDistinct = ParamUtils.getBooleanParameter(request, "httpPortsDistinct",
                    false);
            int requestedPort;
            int requestedSecurePort;
            if (httpPortsDistinct) {
                requestedPort = ParamUtils.getIntParameter(request, "port", -1);
                requestedSecurePort = ParamUtils.getIntParameter(request, "securePort", -1);
            }
            else {
                requestedPort = serverManager.getAdminUnsecurePort();
                requestedSecurePort = serverManager.getAdminSecurePort();
            }
            try {
                serverManager.setHttpBindPorts(requestedPort, requestedSecurePort);
            }
            catch (Exception e) {
                errorMap.put("port", e.getMessage());
            }
        }
        serverManager.setHttpBindEnabled(isEnabled);
    }
%>

<%
    if (request.getParameter("update") != null) {
        handleUpdate(request);
    }

    boolean isHttpBindEnabled = serverManager.isHttpBindEnabled();
    boolean isHttpBindServerSperate = serverManager.isSeperateHttpBindServerConfigured();
    int port = serverManager.getHttpBindUnsecurePort();
    int securePort = serverManager.getHttpBindSecurePort();
%>
<html>
<head>
    <title>
        <fmt:message key="httpbind.settings.title"/>
    </title>
    <meta name="pageID" content="http-bind"/>
</head>
<body>
<p>
    <fmt:message key="httpbind.settings.info"/>
</p>

<form action="http-bind.jsp" method="post">
    <div class="jive-contentBoxHeader">
        <fmt:message key="httpbind.settings.enabled.legend"/>
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr valign="middle">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="false" id="rb01"
                        <%= (!isHttpBindEnabled ? "checked" : "") %>>
                    </td>
                    <td width="99%" colspan="2">
                        <label for="rb01">
                            <b>
                                <fmt:message key="httpbind.settings.label_disable"/>
                            </b> -
                            <fmt:message key="httpbind.settings.label_disable_info"/>
                        </label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="true" id="rb02"
                        <%= (isHttpBindEnabled ? "checked" : "") %>>
                    </td>
                    <td width="99%" colspan="2">
                        <label for="rb02">
                            <b>
                                <fmt:message key="httpbind.settings.label_enable"/>
                            </b> -
                            <fmt:message key="httpbind.settings.label_enable_info"/>
                        </label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td width="1%">
                        &nbsp;
                    </td>
                    <td width="1%" nowrap>
                        <input type="radio" name="httpPortsDistinct" value="false" id="rb03"
                        <%= (!isHttpBindServerSperate ? "checked" : "") %>>
                    </td>
                    <td width="98%">
                        <label for="rb03">
                            <b>
                                <fmt:message key="httpbind.settings.label_seperate"/>
                            </b> -
                            <fmt:message key="httpbind.settings.label_seperate_info"/>
                        </label>
                    </td>
                </tr>
                <tr valign="middle">
                    <td width="1%">
                        &nbsp;
                    </td>
                    <td width="1%" nowrap>
                        <input type="radio" name="httpPortsDistinct" value="true" id="rb04"
                        <%= (isHttpBindServerSperate ? "checked" : "") %>>
                    </td>
                    <td width="98%">
                        <label for="rb04">
                            <b>
                                <fmt:message key="httpbind.settings.label_same"/>
                            </b> -
                            <fmt:message key="httpbind.settings.label_same_info"/>
                        </label>
                    </td>
                </tr>
                <tr>
                    <td width="1%">
                        &nbsp;
                    </td>
                    <td colspan="2">
                        <label for="port">
                        <fmt:message key="httpbind.settings.vanilla_port"/>
                        </label>
                        <input id="port" type="text" size="5" maxlength="10" name="port"
                               value="<%=port%>" />
                    </td>
                </tr>
                <tr>
                    <td width="1%">
                        &nbsp;
                    </td>
                    <td colspan="2">
                        <label for="securePort">
                        <fmt:message key="httpbind.settings.secure_port"/>
                        </label>
                        <input id="securePort" type="text" size="5" maxlength="10" name="securePort"
                               value="<%=securePort%>" />
                    </td>
                </tr>
            </tbody>
        </table>
        <input type="submit" id="settingsUpdate" name="update"
               value="<fmt:message key="global.save_settings" />">
    </div>
</form>
</body>
</html>