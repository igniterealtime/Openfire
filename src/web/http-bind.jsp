<%--
  -	$Revision:  $
  -	$Date:  $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%!
    HttpBindManager serverManager = HttpBindManager.getInstance();

    Map<String, String> handleUpdate(HttpServletRequest request) {
        Map<String, String> errorMap = new HashMap<String, String>();
        boolean isEnabled = ParamUtils.getBooleanParameter(request, "httpBindEnabled");
        if (isEnabled) {
            int requestedPort = ParamUtils.getIntParameter(request, "port", -1);
            int requestedSecurePort = ParamUtils.getIntParameter(request, "securePort", -1);
            try {
                serverManager.setHttpBindPorts(requestedPort, requestedSecurePort);
            }
            catch (Exception e) {
                Log.error("An error has occured configuring the HTTP binding ports", e);
                errorMap.put("port", e.getMessage());
            }
        }
        if (errorMap.size() <= 0) {
            serverManager.setHttpBindEnabled(isEnabled);
        }
        return errorMap;
    }
%>

<%
    Map<String, String> errorMap = new HashMap<String, String>();
    if (request.getParameter("update") != null) {
        errorMap = handleUpdate(request);
    }

    boolean isHttpBindEnabled = serverManager.isHttpBindEnabled();
    int port = serverManager.getHttpBindUnsecurePort();
    int securePort = serverManager.getHttpBindSecurePort();
%>
<html>
<head>
    <title>
        <fmt:message key="httpbind.settings.title"/>
    </title>
    <meta name="pageID" content="http-bind"/>
    <script type="text/javascript">
        var enabled = <%=isHttpBindEnabled%>;
        var setEnabled = function() {
            $("port").disabled = !enabled
            $("securePort").disabled = !enabled;
        }
        window.onload = setTimeout("setEnabled()", 500);
    </script>
</head>
<body>
<p>
    <fmt:message key="httpbind.settings.info"/>
</p>
<% if (errorMap.size() > 0) {
    for (String key : errorMap.keySet()) { %>
<div class="error" style="width: 400px">
    <% if (key.equals("port")) { %>
    <fmt:message key="httpbind.settings.error.port"/>
    <% }
    else { %>
    <fmt:message key="httpbind.settings.error.general"/>
    <% } %>
</div>
<% }
} %>

<form action="http-bind.jsp" method="post">
    
    <div class="jive-contentBox" style="-moz-border-radius: 3px;">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="true" id="rb02"
                                onclick="enabled = true; setEnabled();"
                        <%= (isHttpBindEnabled ? "checked" : "") %>>
                    </td>
                    <td width="99%" colspan="2">
                        <label for="rb02">
                            <b>
                                <fmt:message key="httpbind.settings.label_enable"/>
                            </b> -
                            <fmt:message key="httpbind.settings.label_enable_info"/>
                        </label>

                        <table border="0">
                             <tr>
                                <td>
                                    <label for="port">
                                    <fmt:message key="httpbind.settings.vanilla_port"/>
                                    </label>
                                </td><td>
                                    <input id="port" type="text" size="5" maxlength="10" name="port"
                                           value="<%=port%>" />
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <label for="securePort">
                                    <fmt:message key="httpbind.settings.secure_port"/>
                                    </label>
                                </td><td>
                                    <input id="securePort" type="text" size="5" maxlength="10" name="securePort"
                                           value="<%=securePort%>" />
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="false" id="rb01"
                               onclick="enabled = false; setEnabled();"
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
            </tbody>
        </table>
    </div>
    <input type="submit" id="settingsUpdate" name="update"
               value="<fmt:message key="global.save_settings" />">
</form>
</body>
</html>