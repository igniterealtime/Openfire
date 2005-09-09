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

<%@ page import="org.jivesoftware.messenger.auth.AuthToken,
                 org.jivesoftware.util.ClassUtils,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.messenger.user.*"
%>

<%	// Security check
	AuthToken authToken = (AuthToken)session.getAttribute("jive.admin.authToken");
	if (authToken == null) {
		response.sendRedirect("login.jsp");
		return;
	}
    else {
        // check for an anonymous user token  
        if (authToken.isAnonymous()) {
		    response.sendRedirect("login.jsp");
		    return;
        }
    }
    
    // Handle an admin logout requst:
    if (request.getParameter("logout") != null) {
      session.removeAttribute("jive.admin.authToken");
      response.sendRedirect("index.jsp");
      return;
    }

    // Check to see if we're in "setup" mode:
    if (XMPPServer.getInstance().isSetupMode()) {
      response.sendRedirect("setup-index.jsp");
      return;
    }

    // Should only be set to true if logged in user is an admin.
    // Since anyone that logged in is an admin, it's redundant to recheck.
    boolean isSystemAdmin = true;

    // Otherwise, get the xmpp server
    XMPPServer xmppServer = XMPPServer.getInstance();

    // The user object of the logged-in user
    UserManager userManager = xmppServer.getUserManager();
    User pageUser = null;  
    try {
        pageUser = userManager.getUser(authToken.getUsername());
    }
    catch (UserNotFoundException ignored) {}

    // embedded mode?
    boolean embeddedMode = false;
    try {
        ClassUtils.forName("org.jivesoftware.messenger.starter.ServerStarter");
        embeddedMode = true;
    }
    catch (Exception ignored) {}
%>