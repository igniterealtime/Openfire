<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.admin.LoginLimitManager"
         errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.auth.AuthFactory" %>
<%@ page import="org.jivesoftware.openfire.auth.AuthToken" %>
<%@ page import="org.jivesoftware.openfire.auth.UnauthorizedException" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"/>
<% admin.init(request, response, session, application, out); %>

<%!
    static String go(String url) {
        if (url == null) {
            return "index.jsp";
        } else {
            if (url.startsWith("/")) {
                return url;
            }
            try {
                URL u = new URL(url);
                StringBuilder s = new StringBuilder();
                if (u.getPath().equals("")) {
                    s.append("/");
                } else {
                    s.append(u.getPath());
                }
                if (u.getQuery() != null) {
                    s.append('?');
                    s.append(u.getQuery());
                }
                if (u.getRef() != null) {
                    s.append('#');
                    s.append(u.getRef());
                }
                return s.toString();
            } catch (Exception e) {
                return "index.jsp";
            }
        }
    }
%>

<%-- Check if in setup mode --%>
<%
    if (!AuthFactory.isOneTimeAccessTokenEnabled()) {
        response.sendRedirect("/login.jsp");
        return;
    }
%>

<% // get parameters
    String token = ParamUtils.getParameter(request, "token");

    String url = ParamUtils.getParameter(request, "url");
    url = org.jivesoftware.util.StringUtils.escapeHTMLTags(url);

    // SSO between cluster nodes
    String secret = ParamUtils.getParameter(request, "secret");
    String nodeID = ParamUtils.getParameter(request, "nodeID");

    // The user auth token:
    AuthToken authToken = null;

    // Check the request/response for a login token

    Map<String, String> errors = new HashMap<>();

    boolean login = ParamUtils.getBooleanParameter(request, "login");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (login) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            login = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (login) {
        try {
            // Check that a token was provided before trying to verify credentials
            if (token != null) {
                authToken = AuthFactory.checkOneTimeAccessToken(token);
                session = admin.invalidateSession();
            } else {
                errors.put("unauthorized", LocaleUtils.getLocalizedString("login.failed.wrongtoken"));
            }
            if (errors.isEmpty()) {
                LoginLimitManager.getInstance().recordSuccessfulAttempt(token, request.getRemoteAddr());
                session.setAttribute("jive.admin.authToken", authToken);
                response.sendRedirect(go(url));
                return;
            }
        } catch (UnauthorizedException ue) {
            LoggerFactory.getLogger("loginToken.jsp").debug("Login failed.", ue);
            LoginLimitManager.getInstance().recordFailedAttempt(token, request.getRemoteAddr());
            // Provide a special message if the user provided something containing @
            errors.put("unauthorized", LocaleUtils.getLocalizedString("login.failed.wrongtoken"));
        }
    }

%>

<!DOCTYPE html>

<html>
<head>
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= AdminConsole.getAppName() %> <fmt:message key="login.title"/></title>
    <script>
        <!--
        // break out of frames
        if (self.parent.frames.length !== 0) {
            self.parent.location = document.location;
        }

        //-->
    </script>
    <link rel="stylesheet" href="style/framework/css/bootstrap.min.css" type="text/css">
    <link rel="stylesheet" href="style/framework/css/font-awesome.min.css" type="text/css">
    <link rel="stylesheet" href="style/global.css" type="text/css">
    <link rel="stylesheet" href="style/login.css" type="text/css">
</head>

<body>

<form action="loginToken.jsp" name="loginForm" method="post">

    <% if (url != null) {
        try { %>

    <input type="hidden" name="url" value="<%= StringUtils.escapeForXML(url) %>">

    <% } catch (Exception e) {
        LoggerFactory.getLogger("loginToken.jsp").error("An exception occurred.", e);
    }
    } %>

    <input type="hidden" name="login" value="true">
    <input type="hidden" name="csrf" value="${csrf}">

    <div style="text-align: center">
        <!-- BEGIN login box -->
        <div id="jive-loginBox">

            <div style="text-align: center" id="jive-loginTable">

            <span id="jive-login-header"
                  style="background: transparent url(images/login_logo.gif) no-repeat left; padding: 29px 0 10px 205px;">
            <fmt:message key="admin.console"/>
            </span>

                <div style="text-align: center; width: 380px;">
                    <table style="text-align: center">
                        <tr>
                            <td style="text-align: right" class="loginFormTable">

                                <table>
                                    <noscript>
                                        <tr>
                                            <td colspan="3">
                                                <table>
                                                    <tr>
                                                        <td><img src="images/error-16x16.gif" alt="" style="margin-top:2px; margin-bottom: 2px;"></td>
                                                        <td>
                                                            <div class="jive-error-text" style="padding-left:5px; color:#cc0000;">
                                                                <fmt:message key="login.error"/></div>
                                                        </td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                    </noscript>
                                    <% if (errors.size() > 0) { %>
                                    <tr>
                                        <td colspan="3">
                                            <table>
                                                <% for (String error : errors.values()) { %>
                                                <tr>
                                                    <td><img src="images/error-16x16.gif" alt=""
                                                             vspace="2"></td>
                                                    <td>
                                                        <div class="jive-error-text" style="padding-left:5px; color:#cc0000;"><%= error%>
                                                        </div>
                                                    </td>
                                                </tr>
                                                <% } %>
                                            </table>
                                        </td>
                                    </tr>
                                    <% } %>
                                    <tr>
                                        <td colspan="2">
                                            <div class="jive-error-text" style="padding-left:5px; color:#cc0000;">
                                                <fmt:message key="login.tokenTitle"/></div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" name="token" size="35" maxlength="80" id="u01"></td>
                                        <td style="text-align: center"><input type="submit" value="&nbsp; <fmt:message key="login.login" /> &nbsp;">
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="jive-login-label"><label for="u01"><fmt:message key="login.token"/></label></td>
                                        <td>&nbsp;</td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td style="text-align: right">
                                <div style="text-align: right" id="jive-loginVersion">
                                    <%= AdminConsole.getAppName() %>, <fmt:message key="login.version"/>: <%= AdminConsole
                                    .getVersionString() %>
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

<script>
    <!--
    document.loginForm.token.focus();
    //-->
</script>

</body>
</html>
