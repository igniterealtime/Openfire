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
        // Update the sidebar status
        session.setAttribute("jive.setup.sidebar.4","done");
        session.setAttribute("jive.setup.sidebar.5","in_progress");
        // Redirect
        response.sendRedirect("setup-admin-settings.jsp");
        return;
    }
%>
<html>
<head>
<title>Profile Settings</title>
</head>
<body>

	<h1>
    Profile Settings
	</h1>

	<p>
	Choose whether or not Wildfire integrates with an existing directory server for user profiles.
	</p>

	<!-- BEGIN jive-contentBox -->
	<div class="jive-contentBox">
	<form action="setup-profile-settings.jsp" name="profileform" method="post">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="" id="rb01" checked>
    </td>
    <td>
        <label for="rb01"><b>None (default)</b></label><br>
	    No directory server available.
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="" id="rb02" disabled>
    </td>
    <td>
        <label for="rb02"><b>Integrate with a directory server</b></label><br>
	    Use an existing directory server (such as Active Directory, OpenLDAP, etc) for profile integration.
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="" id="rb03" disabled>
    </td>
    <td>
        <label for="rb03"><b>Other</b></label><br>
	    If you have a custom profile integration system, selecting this will create the config file
	    <tt>\wildfire\foo.conf</tt>, though you will need to edit it manually after you complete the setup process.
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
