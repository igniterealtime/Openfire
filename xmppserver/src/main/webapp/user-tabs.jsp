<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%--
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.openfire.PresenceManager,
                 org.jivesoftware.openfire.user.*"
    
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="ad" class="org.jivesoftware.util.WebManager"  />
<% ad.init(request, response, session, application, out ); %>


<c:set var="username" value="${param.username}" />
<c:set var="tabName" value="${pageScope.tab}" />
<jsp:useBean id="tabName" type="java.lang.String" />


<%  // Get params
    String uname = ParamUtils.getParameter(request,"username");

    // Load the user
    User foundUser = ad.getUserManager().getUser(uname);

    // Get a presence manager
    PresenceManager presenceManager = ad.getPresenceManager();
%>

<table class="jive-tabs" style="width: 100%">
<tr>
<c:set var="tabCount" value="1" />

    <td class="jive-<%= (("props".equals(tabName)) ? "selected-" : "") %>tab" style="width: 1%; white-space: nowrap">
        <a href="user-properties.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.properties" /></a>
    </td>
    <td class="jive-tab-spacer" style="width: 1%"><img src="images/blank.gif" width="5" height="1" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("edit".equals(tabName)) ? "selected-" : "") %>tab" style="width: 1%; white-space: nowrap">
        <a href="user-edit-form.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.edit" /></a>
    </td>
    <td class="jive-tab-spacer" style="width: 1%"><img src="images/blank.gif" width="5" height="1" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <%  // Only show the message tab if the user is online
        if (presenceManager.isAvailable(foundUser)) {
    %>

        <td class="jive-<%= (("message".equals(tabName)) ? "selected-" : "") %>tab" style="width: 1%; white-space: nowrap">
            <a href="user-message.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.send" /></a>
        </td>
        <td class="jive-tab-spacer" style="width: 1%"><img src="images/blank.gif" width="5" height="1" alt=""></td>

        <c:set var="tabCount" value="${tabCount + 1}" />


    <%  } %>

    <td class="jive-<%= (("pass".equals(tabName)) ? "selected-" : "") %>tab" style="width: 1%; white-space: nowrap">
        <a href="user-password.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.change_pwd" /></a>
    </td>
    <td class="jive-tab-spacer" style="width: 1%"><img src="images/blank.gif" width="5" height="1" alt=""></td>

<c:set var="tabCount" value="${tabCount + 1}" />

    <td class="jive-<%= (("delete".equals(tabName)) ? "selected-" : "") %>tab" style="width: 1%; white-space: nowrap">
        <a href="user-delete.jsp?username=<c:out value="${username}"/>"><fmt:message key="user.tabs.delete_user" /></a>
    </td>
    <td class="jive-tab-spacer" style="width: 1%"><img src="images/blank.gif" width="5" height="1" alt=""></td>
<c:set var="width" value="${100-(tabCount*2)}" />
    <td class="jive-tab-spring" style="width: <c:out value="${width}" />%; text-align: right" nowrap>
        &nbsp;
    </td>
</tr>
<tr>
    <td class="jive-tab-bar" colspan="99">
        &nbsp;
    </td>
</tr>
</table>
<table style="width: 100%; background-color: #dddddd">
<tr><td style="width: 1%"><img src="images/blank.gif" width="1" height="1" alt=""></td></tr>
</table>
<table style="width: 100%; background-color: #eeeeee">
<tr><td style="width: 1%"><img src="images/blank.gif" width="1" height="1" alt=""></td></tr>
</table>
