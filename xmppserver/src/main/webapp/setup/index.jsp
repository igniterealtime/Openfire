<%--
  -
  - Copyright (C) 2004-2010 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="java.lang.reflect.Method" %>
<%@ page import="java.util.*"%>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.nio.file.Files" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    boolean jreVersionCompatible = false;
    boolean servlet22Installed = false;
    boolean jsp11Installed = false;
    boolean jiveJarsInstalled = false;
    boolean openfireHomeExists = false;
    Path openfireHome = null;

    // Check for min JRE requirement
    int MIN_JAVA_VERSION = 11;
    try {
        String version = System.getProperty("java.version");
        jreVersionCompatible = Integer.parseInt(version.split("[-.]+")[0]) >= MIN_JAVA_VERSION;
    }
    catch (Exception ignored) {}
    // Check for Servlet 2.3:
    try {
        Class c = ClassUtils.forName("javax.servlet.http.HttpSession");
        Method m = c.getMethod("getAttribute",new Class[]{String.class});
        servlet22Installed = true;
    }
    catch (ClassNotFoundException cnfe) {}
    // Check for JSP 1.1:
    try {
        ClassUtils.forName("javax.servlet.jsp.tagext.Tag");
        jsp11Installed = true;
    }
    catch (ClassNotFoundException cnfe) {}
    // Check that the Openfire jar are installed:
    try {
        ClassUtils.forName("org.jivesoftware.openfire.XMPPServer");
        jiveJarsInstalled = true;
    }
    catch (ClassNotFoundException cnfe) {}

    // Try to determine what the jiveHome directory is:
    try {
        Class jiveGlobalsClass = ClassUtils.forName("org.jivesoftware.util.JiveGlobals");
        Method getOpenfireHomeMethod = jiveGlobalsClass.getMethod("getHomePath", (Class[])null);
        openfireHome = (Path)getOpenfireHomeMethod.invoke(jiveGlobalsClass, (Object[])null);
        if (openfireHome != null) {
            openfireHomeExists = Files.exists(openfireHome);
        }
    }
    catch (Exception e) {
        e.printStackTrace();
    }

    pageContext.setAttribute( "jreVersionCompatible", jreVersionCompatible );
    pageContext.setAttribute( "servlet22Installed", servlet22Installed );
    pageContext.setAttribute( "jsp11Installed", jsp11Installed );
    pageContext.setAttribute( "jiveJarsInstalled", jiveJarsInstalled );
    pageContext.setAttribute( "configLocation", JiveGlobals.getConfigLocation() );
    pageContext.setAttribute( "configLocationExistsAndAccessible", Files.isWritable(JiveGlobals.getConfigLocation()) );
    pageContext.setAttribute( "securityConfigLocation", JiveGlobals.getSecurityConfigLocation() );
    pageContext.setAttribute( "securityConfigLocationExistsAndAccessible", Files.isWritable(JiveGlobals.getSecurityConfigLocation()) );
    pageContext.setAttribute( "openfireHomeExists", openfireHomeExists );
    pageContext.setAttribute( "openfireHome", openfireHome );
    pageContext.setAttribute( "localizedTitle", LocaleUtils.getLocalizedString("title") );

    // Get parameters
    String localeCode = ParamUtils.getParameter(request,"localeCode");
    boolean save = request.getParameter("save") != null;

    Map<String, String> errors = new HashMap<>();

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) ) {
            save = false;
            errors.put( "general", "CSRF Failure!" );
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (save) {
        Locale newLocale;
        if (localeCode != null) {
            newLocale = LocaleUtils.localeCodeToLocale(localeCode.trim());
            if (newLocale == null) {
                errors.put("localeCode","");
            }
            else {
                JiveGlobals.setLocale(newLocale);
                // redirect
                response.sendRedirect("setup-host-settings.jsp");
                return;
            }
        }
    }

    pageContext.setAttribute( "locales", LocaleUtils.getSupportedLocales() );
    Locale locale = JiveGlobals.getLocale();
    pageContext.setAttribute( "locale", locale.toString() );
    pageContext.setAttribute( "localizedTitle", LocaleUtils.getLocalizedString("title") );
    pageContext.setAttribute( "errors", errors );
%>

<html>
<head>
<title><fmt:message key="setup.index.title" /></title>
<meta name="currentStep" content="0"/>
</head>
<body>

    <h1>
        <fmt:message key="setup.index.title" />
    </h1>

    <c:if test="${not empty errors}">
        <div class="error">
            <c:forEach var="err" items="${errors}">
                <c:out value="${err.value}"/><br/>
            </c:forEach>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${not jreVersionCompatible or not servlet22Installed or not jsp11Installed or not jiveJarsInstalled or not openfireHomeExists or not configLocationExistsAndAccessible or not securityConfigLocationExistsAndAccessible}">
            <div class="error">
                <fmt:message key="setup.env.check.error"/> <fmt:message key="title"/> <fmt:message key="setup.title"/>.
            </div>

            <div class="jive-contentBox">

                <p>
                    <fmt:message key="setup.env.check.error_info">
                        <fmt:param value="${localizedTitle}"/>
                    </fmt:message>
                </p>

                <table cellpadding="3" cellspacing="2">
                    <c:choose>
                        <c:when test="${jreVersionCompatible}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13"></td>
                                <td><fmt:message key="setup.env.check.jdk"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.jdk"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${servlet22Installed}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13"></td>
                                <td><fmt:message key="setup.env.check.servlet"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.servlet"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${jsp11Installed}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13"></td>
                                <td><fmt:message key="setup.env.check.jsp"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.jsp"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${jiveJarsInstalled}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13"></td>
                                <td><fmt:message key="title"/> <fmt:message key="setup.env.check.class"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="title"/> <fmt:message key="setup.env.check.class"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${openfireHomeExists}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13"></td>
                                <td><fmt:message key="setup.env.check.jive"/> (<tt><c:out value="${openfireHome}"/></tt>)</td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.not_home"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:if test="${openfireHomeExists}">
                        <c:choose>
                            <c:when test="${configLocationExistsAndAccessible}">
                                <tr>
                                    <td><img src="../images/check.gif" width="13" height="13"></td>
                                    <td><fmt:message key="setup.env.check.config_found">
                                            <fmt:param><c:out value="${configLocation}"/></fmt:param>
                                        </fmt:message>
                                    </td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <tr>
                                    <td><img src="../images/x.gif" width="13" height="13"></td>
                                    <td><fmt:message key="setup.env.check.config_not_loaded">
                                            <fmt:param><c:out value="${configLocation}"/></fmt:param>
                                        </fmt:message>
                                    </td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                        <c:choose>
                            <c:when test="${securityConfigLocationExistsAndAccessible}">
                                <tr>
                                    <td><img src="../images/check.gif" width="13" height="13"></td>
                                    <td><fmt:message key="setup.env.check.config_found">
                                        <fmt:param><c:out value="${securityConfigLocation}"/></fmt:param>
                                    </fmt:message>
                                    </td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <tr>
                                    <td><img src="../images/x.gif" width="13" height="13"></td>
                                    <td><fmt:message key="setup.env.check.config_not_loaded">
                                        <fmt:param><c:out value="${securityConfigLocation}"/></fmt:param>
                                    </fmt:message>
                                    </td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                    </c:if>
                </table>
            </div>

            <p>
                <fmt:message key="setup.env.check.doc"/>
            </p>

        </c:when>
        <c:otherwise>

            <p>
                <fmt:message key="setup.index.info">
                    <fmt:param value="${localizedTitle}" />
                </fmt:message>
            </p>

            <!-- BEGIN jive-contentBox -->
            <div class="jive-contentBox">

                <h2><fmt:message key="setup.index.choose_lang" /></h2>

                <form action="index.jsp" name="sform">
                    <input type="hidden" name="csrf" value="${csrf}">
                    <div id="jive-setup-language">
                        <p>
                        <c:forEach var="l" items="${locales}">
                            <label for="${l.key}">
                                <input type="radio" name="localeCode" value="${l.key}" ${locale eq l.key ? 'checked' : ''} id="${l.key}"/>
                                <b>${l.value}</b> (${l.key})
                            </label><br>
                        </c:forEach>
                        </p>
                    </div>

                    <div align="right">
                        <input type="Submit" name="save" value="<fmt:message key="global.continue" />" id="jive-setup-save">
                    </div>
                </form>

            </div>

            <p class="info">
                <fmt:message key="setup.index.translator-invitation"/>
            </p>
            <!-- END jive-contentBox -->

        </c:otherwise>
    </c:choose>


</body>
</html>
