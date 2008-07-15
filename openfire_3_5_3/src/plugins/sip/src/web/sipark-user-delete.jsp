<%--
  -	$Revision: 5374 $
  -	$Date: 2006-09-14 19:04:51 -0300 (qui, 14 set 2006) $
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.openfire.sip.sipaccount.SipAccount,
                 org.jivesoftware.openfire.sip.sipaccount.SipAccountDAO,
                 org.jivesoftware.util.ParamUtils,
                 org.xmpp.packet.JID,
                 java.net.URLEncoder"
        %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

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
            <fmt:param value="<%= "<a href='./../../user-properties.jsp?username=" + URLEncoder.encode(account.getUsername(), "UTF-8") + "'>" + JID.unescapeNode(account.getUsername()) + "</a>"%>" />
    </fmt:message></b>
</p>

<form action="sipark-user-delete.jsp">
    <input type="hidden" name="username" value="<%= username %>">
    <input type="submit" name="delete" value="<fmt:message key="sipark.user.delete.delete" />">
    <input type="submit" name="cancel" value="<fmt:message key="sipark.user.delete.cancel" />">
</form>

</body>
</html>
