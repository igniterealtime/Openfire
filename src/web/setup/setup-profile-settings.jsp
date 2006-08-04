<%--
  -	$RCSfile$
  -	$Revision: 1410 $
  -	$Date: 2005-05-26 23:00:40 -0700 (Thu, 26 May 2005) $
--%>

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


%>
<html>
    <head>
        <title>Profile Settings</title>
    </head>
<body>

<p class="jive-setup-page-header">
    Profile Settings
</p>

<p>
Blah blah.
</p>


<form action="setup-profile-settings.jsp" name="profileform" method="post">

</form>

</body>
</html>
