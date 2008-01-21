<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.openfire.auth.AuthToken,
                 org.jivesoftware.openfire.auth.AuthFactory,
                 org.jivesoftware.openfire.auth.UnauthorizedException,
                 org.jivesoftware.admin.AdminConsole"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.*"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%! // List of allowed usernames:
    static Map<String, String> authorizedUsernames = new HashMap<String, String>();
    static {
        for (JID jid : XMPPServer.getInstance().getAdmins()) {
            // Only allow local users to log into the admin console
            if (XMPPServer.getInstance().isLocal(jid)) {
                authorizedUsernames.put(jid.getNode(), jid.getNode());
            }
        }
    }
    static String go(String url) {
        if (url == null) {
            return "index.jsp";
        }
        else {
            return url;
        }
    }
%>

<%-- Check if in setup mode --%>
<% 
    if (admin.isSetupMode()) {
        response.sendRedirect("setup/index.jsp");
        return;
    }
%>

<% // get parameters
    String username = ParamUtils.getParameter(request, "username");
    if (username != null) {
        username = JID.escapeNode(username);
    }
    // Escape HTML tags in username to prevent cross-site scripting attacks. This
    // is necessary because we display the username in the page below.
    username = org.jivesoftware.util.StringUtils.escapeHTMLTags(username);

    String password = ParamUtils.getParameter(request, "password");
    String url = ParamUtils.getParameter(request, "url");

    // SSO between cluster nodes
    String secret = ParamUtils.getParameter(request, "secret");
    String nodeID = ParamUtils.getParameter(request, "nodeID");

    // The user auth token:
    AuthToken authToken;

    // Check the request/response for a login token

    boolean errors = false;

    if (ParamUtils.getBooleanParameter(request, "login")) {
        try {
            if (authorizedUsernames != null && !authorizedUsernames.isEmpty()) {
                if (!authorizedUsernames.containsKey(username)) {
                    throw new UnauthorizedException("User '" + username + "' no allowed to login.");
                }
            }
            else {
                if (!"admin".equals(username)) {
                    throw new UnauthorizedException("Only user 'admin' may login.");
                }
            }
            if (secret != null && nodeID != null) {
                if (StringUtils.hash(AdminConsolePlugin.secret).equals(secret) && ClusterManager.isClusterMember(Base64.decode(nodeID, Base64.URL_SAFE))) {
                    authToken = new AuthToken(username);
                }
                else {
                    throw new UnauthorizedException("SSO failed. Invalid secret or node ID was provided");
                }
            }
            else {
                authToken = AuthFactory.authenticate(username, password);
            }
            session.setAttribute("jive.admin.authToken", authToken);
            response.sendRedirect(go(url));
            return;
        }
        catch (UnauthorizedException ue) {
            Log.debug(ue);
            errors = true;
        }
    }
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
	<title><%= AdminConsole.getAppName() %> <fmt:message key="login.title" /></title>
	<script language="JavaScript" type="text/javascript">
		<!--
		// break out of frames
		if (self.parent.frames.length != 0) {
			self.parent.location=document.location;
		}
        function updateFields(el) {
            if (el.checked) {
                document.loginForm.username.disabled = true;
                document.loginForm.password.disabled = true;
            }
            else {
                document.loginForm.username.disabled = false;
                document.loginForm.password.disabled = false;
                document.loginForm.username.focus();
            }
        }
		//-->
	</script>
    <link rel="stylesheet" href="style/global.css" type="text/css">
    <link rel="stylesheet" href="style/login.css" type="text/css">
</head>

<body>

<form action="login.jsp" name="loginForm" method="post">

<%  if (url != null) { try { %>

    <input type="hidden" name="url" value="<%= url %>">

<%  } catch (Exception e) { Log.error(e); } } %>

<input type="hidden" name="login" value="true">

<div align="center">
    <!-- BEGIN login box -->
    <div id="jive-loginBox">
        
        <div align="center" id="jive-loginTable">

            <span id="jive-login-header" style="background: transparent url(images/login_logo.gif) no-repeat left; padding: 29px 0px 10px 205px;">
            <fmt:message key="admin.console" />
            </span>

            <div style="text-align: center; width: 380px;">
            <table cellpadding="0" cellspacing="0" border="0" align="center">
                <tr>
                    <td align="right" class="loginFormTable">

                        <table cellpadding="2" cellspacing="0" border="0">
                        <noscript>
                            <tr>
                                <td colspan="3">
                                    <table cellpadding="0" cellspacing="0" border="0">
                                    <tr valign="top">
                                        <td><img src="images/error-16x16.gif" width="16" height="16" border="0" alt="" vspace="2"></td>
                                        <td><div class="jive-error-text" style="padding-left:5px; color:#cc0000;"><fmt:message key="login.error" /></div></td>
                                    </tr>
                                    </table>
                                </td>
                            </tr>
                        </noscript>
                        <%  if (errors) { %>
                            <tr>
                                <td colspan="3">
                                    <table cellpadding="0" cellspacing="0" border="0">
                                    <tr valign="top">
                                        <td><img src="images/error-16x16.gif" width="16" height="16" border="0" alt="" vspace="2"></td>
                                        <td><div class="jive-error-text" style="padding-left:5px; color:#cc0000;"><fmt:message key="login.failed" /></div></td>
                                    </tr>
                                    </table>
                                </td>
                            </tr>
                        <%  } %>
                        <tr>
                            <td><input type="text" name="username" size="15" maxlength="50" id="u01" value="<%= (username != null ? username : "") %>"></td>
                            <td><input type="password" name="password" size="15" maxlength="50" id="p01"></td>
                            <td align="center"><input type="submit" value="&nbsp; <fmt:message key="login.login" /> &nbsp;"></td>
                        </tr>
                        <tr valign="top">
                            <td class="jive-login-label"><label for="u01"><fmt:message key="login.username" /></label></td>
                            <td class="jive-login-label"><label for="p01"><fmt:message key="login.password" /></label></td>
                            <td>&nbsp;</td>
                        </tr>
                        </table>
                            <script language="JavaScript" type="text/javascript">
                                <!--
                                document.loginForm.username.focus();
                                //-->
                            </script>
                    </td>
                </tr>
                <tr>
                    <td align="right">
                        <div align="right" id="jive-loginVersion">
                        <%= AdminConsole.getAppName() %>, <fmt:message key="login.version" />: <%= AdminConsole.getVersionString() %>
                        </div>
                    </td>
                </tr>
            </table>
            </div>
        </div>

    </div>
    <!-- END login box -->
</div>

</form>

<script language="JavaScript" type="text/javascript">
<!--
    if (document.loginForm.username.value == '')  {
	    document.loginForm.username.focus();
    } else {
        document.loginForm.password.focus();
    }
//-->
</script>

</body>
</html>
