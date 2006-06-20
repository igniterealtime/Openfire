<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.wildfire.auth.AuthToken,
                 org.jivesoftware.wildfire.auth.AuthFactory,
                 org.jivesoftware.wildfire.auth.UnauthorizedException,
                 org.jivesoftware.admin.AdminConsole"
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.util.*"%>
<%@ page import="org.jivesoftware.wildfire.XMPPServer"%>
<%@ page import="org.xmpp.packet.JID"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%! // List of allowed usernames:
    static Map authorizedUsernames = new HashMap();
    static {
        for (JID jid : XMPPServer.getInstance().getAdmins()) {
            // Only allow local users to log into the admin console
            if (XMPPServer.getInstance().isLocal(jid)) {
                authorizedUsernames.put(jid.getNode(), jid.getNode());
            }
        }
    }
    static final String go(String url) {
        if (url == null) {
            return "index.jsp";
        }
        else {
            return url;
        }
    }
%>

<%-- Check if in setup mode --%>
<c:if test="${admin.setupMode}">
  <c:redirect url="setup/index.jsp" />
</c:if>

<%	// get parameters
    String username = ParamUtils.getParameter(request,"username");
    // Escape HTML tags in username to prevent cross-site scripting attacks. This
    // is necessary because we display the username in the page below.
    username = org.jivesoftware.util.StringUtils.escapeHTMLTags(username);

    String password = ParamUtils.getParameter(request,"password");
    String url = ParamUtils.getParameter(request,"url");

    // The user auth token:
    AuthToken authToken = null;

    // Check the request/response for a login token
    
    boolean errors = false;

	if (ParamUtils.getBooleanParameter(request,"login")) {
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
            authToken = AuthFactory.authenticate(username, password);
            session.setAttribute("jive.admin.authToken", authToken);
            response.sendRedirect(go(url));
            return;
		}
		catch (UnauthorizedException ue) {
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
    <style type="text/css">
    #jive-loginBox {
        display: block;
        position: relative;
        width: 476px;
        height: 200px;
        overflow: hidden;
        background: url('images/login_boxbg.gif') no-repeat;
        text-align: left;
        top: 138px;
    }
    #jive-loginBoxPadding {
        padding: 15px;
    }
    #jive-loginLogo {
        display: block;
        position: relative;
        width: 180px;
        height: 53px;
        margin: 12px 0px 0px 7px;
        background: url('images/login_logo.gif') no-repeat;
        float: left;
        overflow: hidden;
    }
    #jive-loginHeader {
        display: block;
        position: relative;
        width: 215px;
        height: 40px;
        margin: 25px 0px 0px 0px;
        font-size: 20px;
        padding-top: 9px;
        color: #255480;
        float: left;
        overflow: hidden;
    }
    #jive-loginTable {
        display: block;
        position: relative;
        clear: both;
        margin: 10px 0px 0px 0px;
        padding: 0px 0px 0px 90px;
    }
    #jive-loginVersion {
        display: block;
        position: relative;
        clear: both;
        width: 440px;
        text-align: right;
        bottom: 0px;
        color: #666666;
        font-weight: normal;
        font-size: 11px;
        margin-bottom: -5px;
        padding-top: 20px;
    }

    .jive-login-label {
        font-size : 12px;
    }


    BODY {
        background-image : url(images/login_background.jpg);
        background-repeat : repeat-x;
        background-color : #fff;
        padding : 0px;
        margin : 0px;
    }
    </style>
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
    <div id="jive-loginBoxPadding">
        <div id="jive-loginLogo"></div>
        <div id="jive-loginHeader"><fmt:message key="admin.console" /></div>



        <div id="jive-loginTable">
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
        </div>

        <br>

        <div id="jive-loginVersion">
            <%= AdminConsole.getAppName() %>, <fmt:message key="login.version" />: <%= AdminConsole.getVersionString() %>
        </div>

    </div>
    </div>
    <!-- END login box -->
</div>

<!-- old login log image
 <img src="<%= AdminConsole.getLoginLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %>">
 -->


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
