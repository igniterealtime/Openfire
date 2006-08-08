<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 java.util.Date,
                 org.jivesoftware.wildfire.user.User,
                 org.jivesoftware.wildfire.user.UserManager,
                 org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>
<%@ page import="org.jivesoftware.wildfire.auth.AuthFactory"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
	// Redirect if we've already run setup:
	if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    // Get parameters

    boolean next = request.getParameter("continue") != null;
    if (next) {
        // Redirect
        response.sendRedirect("setup-admin-settings.jsp");
        return;
    }
%>
<html>
<head>
    <title>Profile Settings - Directory Server</title>
    <meta name="currentStep" content="3"/>
</head>
<body>

	<h1>
    Profile Settings - Directory Server
	</h1>




</body>
</html>
