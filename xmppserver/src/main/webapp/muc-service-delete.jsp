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
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String mucname = ParamUtils.getParameter(request,"mucname");
    String reason = ParamUtils.getParameter(request,"reason");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Load the room object
    MultiUserChatService muc = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);

    // Handle a room delete:
    if (delete) {
        // Delete the rooms in the service
        if (muc != null) {
            for (MUCRoom room : muc.getChatRooms()) {
                // If the room still exists then destroy it
                room.destroyRoom(null, reason);
            }
            // Log the event
            webManager.logEvent("destroyed MUC service "+mucname, "reason = "+reason);
            // Remove the service itself
            webManager.getMultiUserChatManager().removeMultiUserChatService(mucname);
        }
        // Done, so redirect
        response.sendRedirect("muc-service-summary.jsp?deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.service.delete.title"/></title>
        <meta name="subPageID" content="muc-service-delete"/>
        <meta name="extraParams" content="<%= "mucname="+URLEncoder.encode(mucname, "UTF-8") %>"/>
    </head>
    <body>

<p>
<fmt:message key="muc.service.delete.info" />
<b><a href="muc-service-edit-form.jsp?mucname=<%= URLEncoder.encode(mucname, "UTF-8") %>"><%= StringUtils.escapeHTMLTags(mucname) %></a></b>
<fmt:message key="muc.service.delete.detail" />
</p>

<form action="muc-service-delete.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
<input type="hidden" name="mucname" value="<%= StringUtils.escapeForXML(mucname) %>">

<fieldset>
    <legend><fmt:message key="muc.service.delete.destructon_title" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="muc.service.delete.service_name" />
            </td>
            <td>
                <%= StringUtils.escapeHTMLTags(mucname) %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.service.delete.reason" />
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="delete" value="<fmt:message key="muc.service.delete.destroy_service" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>
