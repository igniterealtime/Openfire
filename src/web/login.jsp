<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>

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
                 org.jivesoftware.messenger.JiveGlobals"
    errorPage="error.jsp"
%>
<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>
<c:set var="admin" value="${admin}" />



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
    
   
%>
<!-- Check if in setup mode -->
<c:if test="${admin.setupMode}">
  <c:redirect url="setup-index.jsp" />
</c:if>


<%	// get parameters
    String username = ParamUtils.getParameter(request,"username");
	  String password = ParamUtils.getParameter(request,"password");

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
            response.sendRedirect("index.jsp");
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
	<title><fmt:message key="title" /> Admin - Login</title>
	<script language="JavaScript" type="text/javascript">
		<!--
		// break out of frames
		if (self.parent.frames.length != 0) {
			self.parent.location=document.location;
		}
		//-->
	</script>
    <link rel="stylesheet" href="style/global.css" type="text/css">
    <style type="text/css">
    .jive-login-form TH {
        background-color : #eee;
        text-align : left;
        border-top : 1px #bbb solid;
        border-bottom : 1px #bbb solid;
    }
    .jive-login-form .jive-login-label {
        font-size : 0.8em;
    }
    .jive-login-form .jive-footer {
        font-size : 0.8em;
        font-weight : bold;
    }
    </style>
</head>

<body>

<form action="login.jsp" name="loginForm" method="post">
<input type="hidden" name="login" value="true">

<br><br><br><br>

<table width="100%" border="0" cellspacing="0" cellpadding="0">
<tr>
    <td width="49%"><br></td>
    <td width="2%">
        <noscript>
        <table border="0" cellspacing="0" cellpadding="0">
        <td>
            <span class="jive-error-text">
            <b>Error:</b> You don't have JavaScript enabled. This tool uses JavaScript
            and much of it will not work correctly without it enabled. Please turn
            JavaScript back on and reload this page.
            </span>
        </td>
        </table>
        <br><br><br><br>
        </noscript>

        <%  if (errors) { %>
            <p class="jive-error-text">
            Login failed: Make sure your username and password are correct.
            </p>
        <%  } %>

        <span class="jive-login-form">

        <table cellpadding="6" cellspacing="0" border="0" style="border : 1px #bbb solid;">
        <tr>
            <th>
                <fmt:message key="title" bundle="${lang}" /> Admin Login
            </th>
        </tr>
        <tr>
            <td>

                <table cellpadding="3" cellspacing="0" border="0">
                <tr>
                     <td class="jive-login-label">
                        Username:
                    </td>
                    <td>
                        <input type="text" name="username" size="15" maxlength="50">
                    </td>
                </tr>
                <tr>
                  <td class="jive-login-label">
                        Password:
                    </td>
                    <td>
                        <input type="password" name="password" size="15" maxlength="50">
                    </td>
                    <td>
                        <input type="submit" value=" Login ">
                    </td>
                </tr>
                <tr class="jive-login-label">
                    <td colspan="3"><img src="images/blank.gif" width="1" height="4" border="0"></td>
                </tr>
                <tr class="jive-footer">
                    <td colspan="3">
                        <fmt:message key="title" bundle="${lang}" /> Admin
                    </td>
                </tr>
                </table>

            </td>
        </tr>
        </table>

        </span>

    </td>
    <td width="49%"><br></td>
</tr>
</table>

</form>

<script language="JavaScript" type="text/javascript">
<!--
	document.loginForm.username.focus();
//-->
</script>

</body>
</html>
