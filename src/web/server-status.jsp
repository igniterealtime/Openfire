<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.Session,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.messenger.container.*,
                 org.jivesoftware.messenger.spi.BasicServer,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.admin.AdminPageBean"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  // Title of this page and breadcrumbs
    String title = "Server Status";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "server-status.jsp"));
    pageinfo.setPageID("server-status");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is the status of your <fmt:message key="short.title" /> server.
</p>



<p>
<a href="server-props.jsp">View Server Properties</a>
</p>

<jsp:include page="bottom.jsp" flush="true" />
