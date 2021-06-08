<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.util.*,
                 java.util.*,
                 org.jivesoftware.openfire.muc.MultiUserChatService"
    errorPage="error.jsp"
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.time.Duration" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean kickEnabled = ParamUtils.getBooleanParameter(request,"kickEnabled");
    boolean pingEnabled = ParamUtils.getBooleanParameter(request,"pingEnabled");
    String idletime = ParamUtils.getParameter(request,"idletime");
    String pingtime = ParamUtils.getParameter(request,"pingtime");
    String maxBatchSize = ParamUtils.getParameter(request,"maxbatchsize");
    String maxBatchInterval = ParamUtils.getParameter(request,"maxbatchinterval");
    String batchGrace = ParamUtils.getParameter(request,"batchgrace");
    boolean idleSettings = request.getParameter("idleSettings") != null;
    boolean logSettings = request.getParameter("logSettings") != null;
    boolean idleSettingSuccess = request.getParameter("idleSettingSuccess") != null;
    boolean logSettingSuccess = request.getParameter("logSettingSuccess") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");

    if (!webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        // The requested service name does not exist so return to the list of the existing rooms
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get muc server
    MultiUserChatService mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    Map<String, String> errors = new HashMap<String, String>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (idleSettings || logSettings) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            idleSettings = false;
            logSettings = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle an update of the idle user task settings
    if (idleSettings) {
        if (!kickEnabled) {
            // Disable kicking users by setting a value of null.
            mucService.setIdleUserKickThreshold(null);
            // Log the event
            webManager.logEvent("disabled muc idle kick timeout for service "+mucname, null);
        } else {
            // do validation
            if (idletime == null) {
                errors.put("idletime","idletime");
            }
            Duration idle = null;
            // Try to obtain an int from the provided strings
            if (errors.size() == 0) {
                try {
                    idle = Duration.ofMinutes(Integer.parseInt(idletime));
                    if (idle.isNegative()) {
                        errors.put("idletime","idletime");
                    }
                }
                catch (NumberFormatException e) {
                    errors.put("idletime","idletime");
                }
            }

            if (errors.isEmpty()) {
                mucService.setIdleUserKickThreshold(idle);
                // Log the event
                webManager.logEvent("edited muc idle kick timeout for service "+mucname, "timeout = "+idle);
            }
        }

        if (!pingEnabled) {
            // Disable pinging users by setting a value of null.
            mucService.setIdleUserPingThreshold(null);
            // Log the event
            webManager.logEvent("disabled muc idle ping timeout for service "+mucname, null);
        } else {
            // do validation
            if (pingtime == null) {
                errors.put("pingtime","pingtime");
            }
            Duration idle = null;
            // Try to obtain an int from the provided strings
            if (errors.size() == 0) {
                try {
                    idle = Duration.ofMinutes(Integer.parseInt(pingtime));
                    if (idle.isNegative()) {
                        errors.put("pingtime","pingtime");
                    }
                }
                catch (NumberFormatException e) {
                    errors.put("pingtime","pingtime");
                }
            }

            if (errors.isEmpty()) {
                mucService.setIdleUserPingThreshold(idle);
                // Log the event
                webManager.logEvent("edited muc idle ping timeout for service "+mucname, "timeout = "+idle);
            }
        }

        if (errors.isEmpty()) {
            response.sendRedirect("muc-tasks.jsp?idleSettingSuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
    }

    // Handle an update of the log conversations task settings
    if (logSettings) {
        // do validation
        if (maxBatchSize == null) {
            errors.put("maxBatchSize","maxBatchSize");
        }
        if (maxBatchInterval == null) {
            errors.put("maxBatchInterval","maxBatchInterval");
        }
        if (batchGrace == null) {
            errors.put("batchGrace","batchGrace");
        }
        int size = mucService.getLogMaxConversationBatchSize();
        Duration batchInterval = mucService.getLogMaxBatchInterval();
        Duration batchGracePeriod = mucService.getLogBatchGracePeriod();
        // Try to obtain an int from the provided strings
        if (errors.size() == 0) {
            try {
                size = Integer.parseInt(maxBatchSize);
            }
            catch (NumberFormatException e) {
                errors.put("maxBatchSize","maxBatchSize");
            }
            try {
                batchInterval = Duration.ofMillis( Long.parseLong(maxBatchInterval) );
            }
            catch (NumberFormatException e) {
                errors.put("maxBatchInterval","maxBatchInterval");
            }
            try {
                batchGracePeriod = Duration.ofMillis( Long.parseLong(batchGrace) );
            }
            catch (NumberFormatException e) {
                errors.put("batchGrace","batchGrace");
            }

            if ( batchGracePeriod.compareTo( batchInterval ) > 0 ) {
                errors.put("batchGrace","largerThanBatchInterval");
            }
        }

        if (errors.size() == 0) {
            mucService.setLogMaxConversationBatchSize( size );
            mucService.setLogMaxBatchInterval( batchInterval );
            mucService.setLogBatchGracePeriod( batchGracePeriod );
            // Log the event
            webManager.logEvent("edited muc conversation log settings for service "+mucname, "maxBatchSize = "+maxBatchSize+"\nmaxBatchInterval = "+maxBatchInterval+"\nbatchGrace = "+batchGrace);
            response.sendRedirect("muc-tasks.jsp?logSettingSuccess=true&mucname="+URLEncoder.encode(mucname, "UTF-8"));
            return;
        }
    }

    pageContext.setAttribute("mucname", mucname);
    pageContext.setAttribute("mucService", mucService);
%>

<html>
<head>
<title><fmt:message key="muc.tasks.title"/></title>
<meta name="subPageID" content="muc-tasks"/>
<meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
<meta name="helpPage" content="edit_idle_user_settings.html"/>
</head>
<body>

<p>
<fmt:message key="muc.tasks.info" />
<fmt:message key="groupchat.service.settings_affect" /> <b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= StringUtils.escapeHTMLTags(mucname) %></a></b>
</p>

<%  if (idleSettingSuccess || logSettingSuccess) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <%  if (idleSettingSuccess) { %>

            <fmt:message key="muc.tasks.update" />

        <%  } else if (logSettingSuccess) { %>

            <fmt:message key="muc.tasks.log" />

        <%  } %>
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<% if (errors.size() != 0) {  %>

    <table class="jive-error-message" cellpadding="3" cellspacing="0" border="0" width="350"> <tr valign="top">
    <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
    <td width="99%" class="jive-error-text">

        <% if (errors.get("idletime") != null || errors.get("pingtime") != null) { %>
            <fmt:message key="muc.tasks.valid_idel_minutes" />
        <% } else if (errors.get("maxBatchSize") != null) { %>
            <fmt:message key="muc.tasks.valid_batchsize" />
        <% } else if (errors.get("maxBatchInterval") != null) { %>
            <fmt:message key="muc.tasks.valid_batchinterval" />
        <% } else if (errors.get("batchGrace") != null) { %>
            <fmt:message key="muc.tasks.valid_batchgrace" />
        <%  } %>

    </td>
    </tr>
    </table><br>

<% } %>


<!-- BEGIN 'Idle User Settings' -->
<form action="muc-tasks.jsp?idleSettings" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<c:out value="${mucname}"/>" />
    <c:set var="idleheader"><fmt:message key="muc.tasks.user_setting" /></c:set>
    <admin:contentBox title="${idleheader}">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="checkbox" name="pingEnabled" value="true" id="cb01" ${empty mucService.idleUserPingThreshold ? '' : 'checked'}>
                </td>
                <td width="99%">
                    <label for="cb01"><fmt:message key="muc.tasks.ping_user" /></label>
                    <input type="number" min="1" name="pingtime" size="5" maxlength="5" onclick="this.form.pingEnabled[1].checked=true;"
                           value="${empty mucService.idleUserPingThreshold ? '8' : mucService.idleUserPingThreshold.toMinutes()}">
                    <fmt:message key="global.minutes" />.
                </td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap>
                    <input type="checkbox" name="kickEnabled" value="true" id="cb02" ${empty mucService.idleUserKickThreshold ? '' : 'checked'}>
                </td>
                <td width="99%">
                    <label for="cb02"><fmt:message key="muc.tasks.kick_user" /></label>
                    <input type="number" min="1" name="idletime" size="5" maxlength="5" onclick="this.form.kickEnabled[1].checked=true;"
                           value="${empty mucService.idleUserKickThreshold ? '30' : mucService.idleUserKickThreshold.toMinutes()}">
                    <fmt:message key="global.minutes" />.
                </td>
            </tr>
        </tbody>
        </table>
        <br/>
        <input type="submit" value="<fmt:message key="global.save_settings" />">
    </admin:contentBox>
</form>
<!-- END 'Idle User Settings' -->

<br>

<!-- BEGIN 'Conversation Logging' -->
<form action="muc-tasks.jsp?logSettings" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>" />
    <div class="jive-contentBoxHeader">
        <fmt:message key="muc.tasks.conversation.logging" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0" >
        <tr valign="middle">
            <td width="1%" nowrap class="c1">
                <fmt:message key="muc.tasks.maxbatchsize" />
            </td>
            <td width="99%">
                <input type="number" name="maxbatchsize" size="15" maxlength="50" min="1"
                       value="<%= mucService.getLogConversationBatchSize() %>">
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap class="c1">
                <fmt:message key="muc.tasks.maxbatchinterval" />
            </td>
            <td width="99%">
                <input type="number" name="maxbatchinterval" size="15" maxlength="50" min="0"
                 value="<%= mucService.getLogMaxBatchInterval().toMillis() %>">
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap class="c1">
                <fmt:message key="muc.tasks.batchgrace" />
            </td>
            <td width="99%">
                <input type="number" name="batchgrace" size="15" maxlength="50" min="0"
                       value="<%= mucService.getLogBatchGracePeriod().toMillis() %>">
            </td>
        </tr>
        </table>
        <br/>
        <input type="submit" value="<fmt:message key="global.save_settings" />">
    </div>
</form>
<!-- END 'Conversation Logging' -->


</body>
</html>
