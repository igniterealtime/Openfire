<%--
  -
  - Copyright (C) 2019-2022 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
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


    <div class="jive-form-body">
        <div class="row justify-content-center">
            <div class="jive-form-holder">
                <div class="jive-form-content">
                    <div class="jive-form-items">
                        <img src="images/login_logo.gif" alt="">
                        <h3><fmt:message key="admin.console" /></h3>
                        <form action="login.jsp" name="loginForm" method="post">
                            <!-- BEGIN hidden input -->
                            <%  if (url != null) { %> <input type="hidden" name="url" value="<%= StringUtils.escapeForXML(url) %>"> <% } %>
                            <input type="hidden" name="login" value="true">
                            <input type="hidden" name="csrf" value="${csrf}">
                            <!-- END hidden input -->

                            <!-- BEGIN login box -->
                            <div class="jive-body-input-box">
                                <div class="jive-input-box">
                                    <input class="form-control" type="text" name="token"  maxlength="80" id="u01" placeholder="<fmt:message key="login.token"/>" >
                                    <span style="position: absolute"><i class="fa fa-key"></i></span>
                                </div>
                            </div>
                            <div class="row">
                                <div class="col-lg-12 col-md-12 col-sm-12 ">
                                    <div class="jive-form-button">
                                        <button id="submit" type="submit" class="jive-ibtn jive-btn-gradient"><fmt:message key="login.login" /></button>
                                    </div>
                                </div>
                            </div>
                            <!-- END login box -->

                            <!-- BEGIN error box -->
                            <noscript>
                                <table class="table table-sm table-responsive table-borderless">
                                    <tbody>
                                    <tr>
                                        <td class="jive-error-text" ><i class="fa fa-close fa-lg"></i></td>
                                        <td class=" jive-error-text" ><p><fmt:message key="login.error" /></p></td>
                                    </tr>
                                    </tbody>
                                </table>
                            </noscript>
                            <%  if (errors.size() > 0) { %>
                            <% for (String error:errors.values()) { %>
                            <table class="table table-sm table-responsive table-borderless">
                                <tbody>
                                <tr>
                                    <td class="jive-error-text" ><i class="fa fa-close fa-lg"></i></td>
                                    <td class="jive-error-text"><p><%= error%></p></td>
                                </tr>
                                </tbody>
                            </table>
                            <% } %>
                            <%  } %>
                            <!-- END error box -->
                        </form>
                        <div class="text" id="jive-loginVersion"> <%= AdminConsole.getAppName() %>, <fmt:message key="login.version" />: <%= AdminConsole.getVersionString() %></div>
                    </div>
                </div>
            </div>
        </div>
    </div>

</form>

<script>
    <!--
    document.loginForm.token.focus();
    //-->
</script>

</body>
</html>
