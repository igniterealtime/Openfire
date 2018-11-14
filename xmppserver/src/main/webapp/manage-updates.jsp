<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.

--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.update.UpdateManager,
                 java.util.HashMap,
                 java.util.Map"
         errorPage="error.jsp"
        %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%  webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="manage-updates.title"/></title>
<meta name="pageID" content="manage-updates"/>
</head>
<body>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean serviceEnabled = ParamUtils.getBooleanParameter(request, "serviceEnabled");
    boolean notificationsEnabled = ParamUtils.getBooleanParameter(request, "notificationsEnabled");
    boolean proxyEnabled = ParamUtils.getBooleanParameter(request,"proxyEnabled");
    String proxyHost = ParamUtils.getParameter(request,"proxyHost");
    int proxyPort = ParamUtils.getIntParameter(request,"proxyPort", -1);
    boolean updateSucess = false;

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

    // Update the session kick policy if requested
    Map<String, String> errors = new HashMap<String, String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (update) {

        // Validate params
        if (proxyEnabled) {
            if (proxyHost == null || proxyHost.trim().length() == 0) {
                errors.put("proxyHost","");
            }
            if (proxyPort <= 0) {
                errors.put("proxyPort","");
            }
        }
        else {
            proxyHost = null;
            proxyPort = -1;
        }
        // If no errors, continue:
        if (errors.isEmpty()) {
            updateManager.setServiceEnabled(serviceEnabled);
            updateManager.setNotificationEnabled(notificationsEnabled);
            updateManager.setProxyHost(proxyHost);
            updateManager.setProxyPort(proxyPort);
            // Log the event
            webManager.logEvent("edited managed updates settings", "enabeld = "+serviceEnabled+"\nnotificationsenabled = "+notificationsEnabled+"\nproxyhost = "+proxyHost+"\nproxypost = "+proxyPort);
            updateSucess = true;
        }
    }

    // Set page vars
    if (errors.size() == 0) {
        serviceEnabled = updateManager.isServiceEnabled();
        notificationsEnabled = updateManager.isNotificationEnabled();
        proxyEnabled = updateManager.isUsingProxy();
        proxyHost = updateManager.getProxyHost();
        proxyPort = updateManager.getProxyPort();
    }
    else {
    }
%>

<p>
<fmt:message key="manage-updates.info"/>
</p>

<%  if (!errors.isEmpty()) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""/></td>
            <td class="jive-icon-label">

                <% if (errors.get("proxyHost") != null) { %>
                <fmt:message key="manage-updates.proxy.valid.host" />
                <% } else if (errors.get("proxyPort") != null) { %>
                <fmt:message key="manage-updates.proxy.valid.port" />
                <% } %>
            </td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  }
else if (updateSucess) { %>

    <div class="success">
        <fmt:message key="manage-updates.config.updated"/>
    </div><br>

<%  } %>



<!-- BEGIN manage updates settings -->
<form action="manage-updates.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <!--<div class="jive-contentBoxHeader">

    </div>-->
    <div class="jive-contentBox" style="-moz-border-radius: 3px;">

        <h4><fmt:message key="manage-updates.enabled.legend"/></h4>
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="serviceEnabled" value="true" id="rb02"
                    <%= (serviceEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                    <b><fmt:message key="manage-updates.label_enable"/></b> - <fmt:message key="manage-updates.label_enable_info"/>
                    </label>
                </td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="serviceEnabled" value="false" id="rb01"
                    <%= (!serviceEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb01">
                    <b><fmt:message key="manage-updates.label_disable"/></b> - <fmt:message key="manage-updates.label_disable_info"/>
                    </label>
                </td>
            </tr>
        </tbody>
        </table>

        <br/>
        <br/>

        <h4><fmt:message key="manage-updates.notif.enabled.legend"/></h4>
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="notificationsEnabled" value="true" id="rb04"
                    <%= (notificationsEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb04">
                    <b><fmt:message key="manage-updates.notif.label_enable"/></b> - <fmt:message key="manage-updates.notif.label_enable_info"/>
                    </label>
                </td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="notificationsEnabled" value="false" id="rb03"
                    <%= (!notificationsEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb03">
                    <b><fmt:message key="manage-updates.notif.label_disable"/></b> - <fmt:message key="manage-updates.notif.label_disable_info"/>
                    </label>
                </td>
            </tr>
        </tbody>
        </table>

        <br/>
        <br/>

        <h4><fmt:message key="manage-updates.proxy.enabled.legend"/></h4>
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="proxyEnabled" value="false" id="rb05"
                    <%= (!proxyEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb05">
                    <b><fmt:message key="manage-updates.proxy.label_disable"/></b> - <fmt:message key="manage-updates.proxy.label_disable_info"/>
                    </label>
                </td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="radio" name="proxyEnabled" value="true" id="rb06"
                    <%= (proxyEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb06">
                    <b><fmt:message key="manage-updates.proxy.label_enable"/></b> - <fmt:message key="manage-updates.proxy.label_enable_info"/>
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    &nbsp;
                </td>
                <td width="99%">
                    <table cellpadding="3" cellspacing="0" border="0">
                        <tr valign="top">
                            <td width="1%" nowrap align="right" class="c1">
                                <fmt:message key="manage-updates.proxy.host" />
                            </td>
                            <td width="99%">
                                <input type="text" size="15" maxlength="70" name="proxyHost"
                                 value="<%= ((proxyHost != null) ? StringUtils.escapeForXML(proxyHost) : "") %>">
                            </td>
                        </tr>
                        <tr valign="top">
                            <td width="1%" align="right" nowrap class="c1">
                                <fmt:message key="manage-updates.proxy.port" />
                            </td>
                            <td width="99%">
                                <input type="text" size="10" maxlength="50" name="proxyPort"
                                 value="<%= ((proxyPort > 0) ? proxyPort : "") %>">
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </tbody>
        </table>
    </div>
<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END manage updates settings -->


</body>
</html>
