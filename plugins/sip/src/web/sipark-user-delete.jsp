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

<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccount,
                 org.jivesoftware.openfire.sip.sipaccount.SipAccountDAO,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder"
        %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<% // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    String username = ParamUtils.getParameter(request, "username");

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("sipark-user-summary.jsp?username=" + URLEncoder.encode(username, "UTF-8"));
        return;
    }

    SipAccount account = SipAccountDAO.getAccountByUser(username);

    // Handle an account delete:
    if (delete) {
        if (account != null) {
            SipAccountDAO.remove(account);

            // Done, so redirect
            response.sendRedirect("sipark-user-summary.jsp?deletesuccess=true");
            return;
        }
        response.sendRedirect("sipark-user-summary.jsp?deletesuccess=false");
        return;
    }

    pageContext.setAttribute( "usernameUrlEncoded",URLEncoder.encode(account.getUsername(), "UTF-8") );
    pageContext.setAttribute( "usernameUnescaped", JID.unescapeNode(account.getUsername() ) );
%>

<html>
<head>
    <title>
        <fmt:message key="sipark.user.delete.title"/>
    </title>
    <meta name="pageID" content="sipark-user-summary"/>
</head>
<body>

<p>
    <b><fmt:message key="sipark.user.delete.confirm">
            <fmt:param value="${usernameUrlEncoded}"/>
            <fmt:param value="${usernameUnescaped}"/>
    </fmt:message></b>
</p>

<form action="sipark-user-delete.jsp">
    <input type="hidden" name="username" value="<%= username %>">
    <input type="submit" name="delete" value="<fmt:message key="sipark.user.delete.delete" />">
    <input type="submit" name="cancel" value="<fmt:message key="sipark.user.delete.cancel" />">
</form>

</body>
</html>
