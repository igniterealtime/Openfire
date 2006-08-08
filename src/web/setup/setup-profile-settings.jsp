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

    boolean next = request.getParameter("continue") != null;
    if (next) {
        // Figure out where to send the user.
        String mode = request.getParameter("mode");

        if ("default".equals(mode)) {
            response.sendRedirect("setup-admin-settings.jsp");
            return;
        }
        else if ("ldap".equals(mode)) {
            response.sendRedirect("setup-ldap-server.jsp");
            return;
        }
    }
%>
<html>
<head>
    <title>Profile Settings</title>
    <meta name="currentStep" content="3"/>
</head>
<body>

	<h1>
    Profile Settings
	</h1>

	<p>
	Choose the user and group system to use with Wildfire.
	</p>

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox">
	<form action="setup-profile-settings.jsp" name="profileform" method="post">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="default" id="rb01" checked>
    </td>
    <td>
        <label for="rb01"><b>Default</b></label><br>
	    Store users and groups in the Wildfire database. This is the best option for simple
        deployments.
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="ldap" id="rb02">
    </td>
    <td>
        <label for="rb02"><b>Directory Server (LDAP)</b></label><br>
	    Integrate with a directory server such as Active Directory or OpenLDAP using the
        LDAP protocol. Users and groups are stored in the directory and treated as read-only.
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="other" id="rb03" disabled>
    </td>
    <td>
        <label for="rb03"><b>Other</b></label><br>
	    Users and groups are stored in a different external system.
    </td>
</tr>
</table>

<br>

		<div align="right">
			<input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
		</div>

	</form>
	</div>
	<!-- END jive-contentBox -->



</body>
</html>
