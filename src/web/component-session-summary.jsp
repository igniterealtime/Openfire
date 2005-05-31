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
                 java.util.*,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 org.jivesoftware.admin.*,
                 java.text.DateFormat,
                 org.xmpp.packet.JID"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    int start = ParamUtils.getIntParameter(request,"start",0);
    int range = ParamUtils.getIntParameter(request,"range",15);
    boolean close = ParamUtils.getBooleanParameter(request,"close");
    String jid = ParamUtils.getParameter(request,"jid");

    // Get the user manager
    SessionManager sessionManager = admin.getSessionManager();

    // Get the session count
    int sessionCount = sessionManager.getSessionCount();

    // Close a connection if requested
    if (close) {
        JID address = new JID(jid);
        try {
            Session sess = sessionManager.getSession(address);
            sess.getConnection().close();
            // wait one second
            Thread.sleep(1000L);
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }
        // redirect back to this page
        response.sendRedirect("component-session-summary.jsp?close=success");
        return;
    }

    // paginator vars
    int numPages = (int)Math.ceil((double)sessionCount/(double)range);
    int curPage = (start/range) + 1;
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("session.summary.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "component-session-summary.jsp"));
    pageinfo.setPageID("component-session-summary");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

To be implemented soon!

<jsp:include page="bottom.jsp" flush="true" />
