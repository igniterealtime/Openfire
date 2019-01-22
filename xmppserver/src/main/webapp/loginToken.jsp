<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.admin.AdminConsole,
                 org.jivesoftware.admin.LoginLimitManager"
         errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.auth.AuthFactory" %>
<%@ page import="org.jivesoftware.openfire.auth.AuthToken" %>
<%@ page import="org.jivesoftware.openfire.auth.UnauthorizedException" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin" %>
<%@ page import="org.jivesoftware.util.Base64" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.net.URL" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

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

    Map<String, String> errors = new HashMap<String, String>();

    Boolean login = ParamUtils.getBooleanParameter(request, "login");
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
            if (secret != null && nodeID != null) {
                if (StringUtils.hash(AdminConsolePlugin.secret).equals(secret) && ClusterManager
                    .isClusterMember(Base64.decode(nodeID, Base64.URL_SAFE))) {
                    authToken = new AuthToken(token);
                } else {
                    throw new UnauthorizedException("SSO failed. Invalid secret or node ID was provided");
                }
            } else {
                // Check that a username was provided before trying to verify credentials
                if (token != null) {
                    authToken = AuthFactory.checkOneTimeAccessToken(token);
                    session = admin.invalidateSession();
                } else {
                    errors.put("unauthorized", LocaleUtils.getLocalizedString("login.failed.wrongtoken"));
                }
            }
            if (errors.isEmpty()) {
                LoginLimitManager.getInstance().recordSuccessfulAttempt(token, request.getRemoteAddr());
                session.setAttribute("jive.admin.authToken", authToken);
                response.sendRedirect(go(url));
                return;
            }
        } catch (UnauthorizedException ue) {
            Log.debug(ue);
            LoginLimitManager.getInstance().recordFailedAttempt(token, request.getRemoteAddr());
            // Provide a special message if the user provided something containing @
            errors.put("unauthorized", LocaleUtils.getLocalizedString("login.failed.wrongtoken"));
        }
    }

%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
    <title><%= AdminConsole.getAppName() %> <fmt:message key="login.title"/></title>
    <script language="JavaScript" type="text/javascript">
        <!--
        // break out of frames
        if (self.parent.frames.length != 0) {
            self.parent.location = document.location;
        }

        //-->
    </script>
    <link rel="stylesheet" href="style/global.css" type="text/css">
    <link rel="stylesheet" href="style/login.css" type="text/css">
</head>

<body>

<form action="loginToken.jsp" name="loginForm" method="post">

    <% if (url != null) {
        try { %>

    <input type="hidden" name="url" value="<%= StringUtils.escapeForXML(url) %>">

    <% } catch (Exception e) {
        Log.error(e);
    }
    } %>

    <input type="hidden" name="login" value="true">
    <input type="hidden" name="csrf" value="${csrf}">

    <div align="center">
        <!-- BEGIN login box -->
        <div id="jive-loginBox">

            <div align="center" id="jive-loginTable">

            <span id="jive-login-header"
                  style="background: transparent url(images/login_logo.gif) no-repeat left; padding: 29px 0 10px 205px;">
            <fmt:message key="admin.console"/>
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
                                                        <td><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""
                                                                 vspace="2"></td>
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
                                            <table cellpadding="0" cellspacing="0" border="0">
                                                <% for (String error : errors.values()) { %>
                                                <tr valign="top">
                                                    <td><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""
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
                                        <td align="center"><input type="submit" value="&nbsp; <fmt:message key="login.login" /> &nbsp;">
                                        </td>
                                    </tr>
                                    <tr valign="top">
                                        <td class="jive-login-label"><label for="u01"><fmt:message key="login.token"/></label></td>
                                        <td>&nbsp;</td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td align="right">
                                <div align="right" id="jive-loginVersion">
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

<script language="JavaScript" type="text/javascript">
    <!--
    document.loginForm.token.focus();
    //-->
</script>

</body>
</html>
