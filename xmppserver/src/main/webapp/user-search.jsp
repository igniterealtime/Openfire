<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.user.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.net.URLEncoder"
%><%@ page import="org.xmpp.packet.JID"%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%   webManager.init(request, response, session, application, out ); %>
<%  
    // Get parameters
    boolean search = ParamUtils.getBooleanParameter(request,"search");
    String username = ParamUtils.getParameter(request,"username");
    username = JID.escapeNode(username);

    // Handle a cancel
    if (request.getParameter("cancel") != null) {
        response.sendRedirect("user-summary.jsp");
        return;
    }

    // Handle a search execute:
    Map<String,String> errors = new HashMap<>();
    if (search) {
        User user = null;
        try {
            user = webManager.getUserManager().getUser(username);
        }
        catch (Exception e2) {
            errors.put("username","username");
        }
        if (user != null) {
            // found the user, so redirect to the user properties page:
            response.sendRedirect("user-properties.jsp?username=" +
                    URLEncoder.encode(user.getUsername(), "UTF-8"));
            return;
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="user.search.title"/></title>
        <meta name="pageID" content="user-search"/>
        <meta name="helpPage" content="search_for_a_user.html"/>
    </head>
    <body>

<%    if (errors.size() > 0) { %>
<p class="jive-error-text"><fmt:message key="user.search.not_found" /></p>
<%    } %>
<form name="f" action="user-search.jsp" autocomplete="off">
  <input type="hidden" name="search" value="true"/>
  <fieldset>
    <legend><fmt:message key="user.search.search_user" /></legend>
    <table style="width: 600px;">
      <tr class="c1">
        <td style="width: 1%; white-space: nowrap"><label for="username"><fmt:message key="user.create.username" />:</label></td>
        <td class="c2">
          <input type="text" id="username" name="username" value="<%= ((username!=null) ? StringUtils.escapeForXML(username) : "") %>" size="30" maxlength="75"/>
        </td>
      </tr>
     <tr><td colspan="2" nowrap><input type="submit" name="search" value="<fmt:message key="user.search.search" />"/><input type="submit" name="cancel" value="<fmt:message key="global.cancel" />"/></td>
     </tr>
    </table>
  </fieldset>
</form>
<script>
document.f.username.focus();
</script>

    </body>
</html>
