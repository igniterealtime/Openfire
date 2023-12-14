<%--
  -
  - Copyright (C) 2004-2007 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
 <%--
--%>
<%@ page import="org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.util.LocaleUtils"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  String path = request.getContextPath();

    // Title of this page
    String title = AdminConsole.getAppName() + " " +LocaleUtils.getLocalizedString("error.serverdown.title");
    pageinfo.setTitle(title);
    pageContext.setAttribute("title", title);
%>
<!DOCTYPE html>

<html>
<head>
 <title><%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.admin_console" /><%= (pageinfo.getTitle() != null ? (": "+pageinfo.getTitle()) : "") %></title>
 <meta http-equiv="content-type" content="text/html; charset=UTF-8">
 <link rel="stylesheet" type="text/css" href="<%= path %>/style/global.css">
</head>

<body>

<div id="jive-header">
<table>
<tbody>
    <tr>
     <% if (AdminConsole.getLogoImage() != null) { %>
     <td>
         <img src="<%= path %>/<%= AdminConsole.getLogoImage() %>" alt="<%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.admin_console" />">
     </td>
     <% }%>
     <td style="text-align: right">
         <table>
         <tr>
             <td>&nbsp;</td>
             <td class="info">
                 <nobr><%= AdminConsole.getAppName() %> <%= AdminConsole.getVersionString() %></nobr>
             </td>
         </tr>
         </table>
     </td>
    </tr>
</tbody>
</table>
</div>

<div id="jive-main">
<table>
<tbody>
    <tr>
        <td style="width: 1%">
            <div id="jive-sidebar">
                <img src="<%= path %>/images/blank.gif" width="5" height="1" alt="">
            </div>
        </td>
        <td id="jive-content">

        <div id="jive-title">
            <c:out value="${title}"/>
        </div>

        <p>
        <%= AdminConsole.getAppName() %> <fmt:message key="error.serverdown.is_down" />
        </p>

        <ol>
            <li>
                <fmt:message key="error.serverdown.start" />
            </li>
            <li>
                <a href="index.jsp"><fmt:message key="error.serverdown.login" /></a>.
            </li>
        </ol>

        </td>
    </tr>
</tbody>
</table>
</div>

</body>
</html>
