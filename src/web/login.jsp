<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.auth.AuthToken,
                 org.jivesoftware.messenger.auth.AuthFactory,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.JiveGlobals,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.admin.AdminConsole"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%! // List of allowed usernames:
    static Map allowedUsernames = null;
    static String allowedUsernameProp = JiveGlobals.getProperty("admin.login.allowedUsernames");
    static {
        if (allowedUsernameProp != null) {
            StringTokenizer tokenizer = new StringTokenizer(allowedUsernameProp, ",");
            while (tokenizer.hasMoreTokens()) {
                if (allowedUsernames == null) {
                    allowedUsernames = new HashMap();
                }
                String tok = tokenizer.nextToken().trim();
                allowedUsernames.put(tok, tok);
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
  <c:redirect url="setup-index.jsp" />
</c:if>

<%	// get parameters
    String username = ParamUtils.getParameter(request,"username");
    String password = ParamUtils.getParameter(request,"password");
    String url = ParamUtils.getParameter(request,"url");

    // The user auth token:
    AuthToken authToken = null;

    // Check the request/response for a login token
    
    boolean errors = false;

	if (ParamUtils.getBooleanParameter(request,"login")) {
		try {
            if (allowedUsernames != null) {
                if (!allowedUsernames.containsKey(username)) {
                    throw new UnauthorizedException("User '" + username + "' no allowed to login.");
                }
            }
            else {
                if (!"admin".equals(username)) {
                    throw new UnauthorizedException("Only user 'admin' may login.");
                }
            }
            authToken = AuthFactory.getAuthToken(username, password);
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
	<title><%= AdminConsole.getAppName() %> Admin Console</title>
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
    .jive-login-form {
        position : relative;
        top : 148px;
        text-align : center;
        width : 100%;
    }
    .jive-login-form .jive-login-label {
        font-size : 0.8em;
    }
    .jive-login-form .jive-footer {
        font-size : 0.8em;
        font-weight : bold;
    }
    #jive-login-text-image {
        padding : 0px;
        margin : 0px;
        padding-top : 18px;
        padding-bottom : 10px;
    }
    #jive-logo-image {
        padding : 0px;
        margin : 0px;
        padding-right : 10px;
    }
    BODY {
        background-image : url(images/login-back.gif);
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

<div class="jive-login-form">

    <table cellpadding="0" cellspacing="0" border="0" width="100%">
    <tr>
        <td width="48%">&nbsp;</td>
        <td width="1%" nowrap>

            <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="top">
                <td rowspan="99"
                    ><div id="jive-logo-image"
                    ><img src="<%= AdminConsole.getLoginLogoImage() %>" border="0" alt="<%= AdminConsole.getAppName() %>"
                    ></div></td>
                <td colspan="3"
                    ><div id="jive-login-text-image"
                    ><img src="images/login-text.gif" width="237" height="28" border="0" alt="Admin Console Login"
                    ></div></td>
            </tr>

            <noscript>
                <tr>
                    <td colspan="3">
                        <table cellpadding="0" cellspacing="0" border="0">
                        <tr valign="top">
                            <td><img src="images/error-16x16.gif" width="16" height="16" border="0" alt="" vspace="2"></td>
                            <td>
                                <div class="jive-error-text" style="padding-left:5px;">
                                Error: You don't have JavaScript enabled. This tool uses JavaScript
                                and much of it will not work correctly without it enabled. Please turn
                                JavaScript back on and reload this page.
                                </div>
                            </td>
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
                            <td>
                                <div class="jive-error-text" style="padding-left:5px;">
                                Login failed: make sure your username and password are correct
                                and that you're an admin or moderator.
                                </div>
                            </td>
                        </tr>
                        </table>
                    </td>
                </tr>

            <%  } %>

            <tr>
                <td>
                    <input type="text" name="username" size="15" maxlength="50" id="u01">
                </td>
                <td>
                    <input type="password" name="password" size="15" maxlength="50" id="p01">
                </td>
                <td align="center">
                    <input type="submit" value="&nbsp; Login &nbsp;">
                </td>
            </tr>
            <tr valign="top">
                <td class="jive-login-label">
                    <label for="u01">
                    username
                    </label>
                </td>
                <td class="jive-login-label">
                    <label for="p01">
                    password
                    </label>
                </td>
                <td>
                    &nbsp;
                </td>
            </tr>
            <tr class="jive-login-label">
                <td colspan="3"><img src="images/blank.gif" width="1" height="4" border="0"></td>
            </tr>
            <tr class="jive-footer">
                <td colspan="3" nowrap>
                    <span style="font-size:0.8em;">
                    <%= AdminConsole.getAppName() %>, Version: <%= AdminConsole.getVersionString() %>
                    </span>
                </td>
            </tr>
            </table>

        </td>
        <td width="48%">&nbsp;</td>
    </tr>
    </table>

</div>

</form>

<script language="JavaScript" type="text/javascript">
<!--
	document.loginForm.username.focus();
//-->
</script>

</body>
</html>
