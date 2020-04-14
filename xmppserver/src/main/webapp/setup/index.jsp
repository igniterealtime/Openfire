<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="java.io.File,
                 java.lang.reflect.Method" %>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Locale"%>
<%@ page import="java.util.Map"%>
<%@ page import="org.jivesoftware.util.*" %>

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
    File openfireHome = null;

    // Check for JRE 1.8
    try {
        String version = System.getProperty("java.version");
        int pos = version.indexOf('.');
        pos = version.indexOf('.', pos+1);
        jreVersionCompatible = Double.parseDouble ( version.substring ( 0, pos)) >= 1.8;
    }
    catch (Throwable t) {}
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
        Method getOpenfireHomeMethod = jiveGlobalsClass.getMethod("getHomeDirectory", (Class[])null);
        String openfireHomeProp = (String)getOpenfireHomeMethod.invoke(jiveGlobalsClass, (Object[])null);
        if (openfireHomeProp != null) {
            openfireHome = new File(openfireHomeProp);
            if (openfireHome.exists()) {
                openfireHomeExists = true;
            }
        }
    }
    catch (Exception e) {
        e.printStackTrace();
    }

    pageContext.setAttribute( "jreVersionCompatible", jreVersionCompatible );
    pageContext.setAttribute( "servlet22Installed", servlet22Installed );
    pageContext.setAttribute( "jsp11Installed", jsp11Installed );
    pageContext.setAttribute( "jiveJarsInstalled", jiveJarsInstalled );
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

    Locale locale = JiveGlobals.getLocale();
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
        <c:when test="${not jreVersionCompatible or not servlet22Installed or not jsp11Installed or not jiveJarsInstalled or not openfireHomeExists}">
            <div class="error">
                <fmt:message key="setup.env.check.error"/> <fmt:message key="title"/> <fmt:message key="setup.title"/>.
            </div>

            <div class="jive-contentBox">

                <p>
                    <fmt:message key="setup.env.check.error_info">
                        <fmt:param value="${localizedTitle}"/>
                    </fmt:message>
                </p>

                <table cellpadding="3" cellspacing="2" border="0">
                    <c:choose>
                        <c:when test="${jreVersionCompatible}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13" border="0"></td>
                                <td><fmt:message key="setup.env.check.jdk"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13" border="0"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.jdk"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${servlet22Installed}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13" border="0"></td>
                                <td><fmt:message key="setup.env.check.servlet"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13" border="0"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.servlet"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${jsp11Installed}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13" border="0"></td>
                                <td><fmt:message key="setup.env.check.jsp"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13" border="0"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.jsp"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${jiveJarsInstalled}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13" border="0"></td>
                                <td><fmt:message key="title"/> <fmt:message key="setup.env.check.class"/></td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13" border="0"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="title"/> <fmt:message key="setup.env.check.class"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${openfireHomeExists}">
                            <tr>
                                <td><img src="../images/check.gif" width="13" height="13" border="0"></td>
                                <td><fmt:message key="setup.env.check.jive"/> (<tt><c:out value="${openfireHome}"/></tt>)</td>
                            </tr>
                        </c:when>
                        <c:otherwise>
                            <tr>
                                <td><img src="../images/x.gif" width="13" height="13" border="0"></td>
                                <td><span class="jive-setup-error-text"><fmt:message key="setup.env.check.not_home"/></span></td>
                            </tr>
                        </c:otherwise>
                    </c:choose>
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
                    <%  boolean usingPreset = false;
                        Locale[] locales = Locale.getAvailableLocales();
                        for ( final Locale value : locales ) {
                            usingPreset = value.equals( locale );
                            if ( usingPreset ) { break; }
                        }

                        pageContext.setAttribute( "usingPreset", usingPreset );
                        pageContext.setAttribute( "locale", locale.toString() );
                    %>
                    <div id="jive-setup-language">
                        <p>
                            <label for="loc01">
                                <input type="radio" name="localeCode" value="cs_CZ" ${locale eq 'cs_CZ' ? 'checked' : ''} id="loc01" />
                                <b>Czech</b> (cs_CZ)
                            </label><br>

                            <label for="loc02">
                                <input type="radio" name="localeCode" value="de" ${locale eq 'de' ? 'checked' : ''} id="loc02" />
                                <b>Deutsch</b> (de)
                            </label><br>

                            <label for="loc03">
                                <input type="radio" name="localeCode" value="en" ${locale eq 'en' ? 'checked' : ''} id="loc03" />
                                <b>English</b> (en)
                            </label><br>

                            <label for="loc04">
                                <input type="radio" name="localeCode" value="es" ${locale eq 'es' ? 'checked' : ''} id="loc04" />
                                <b>Espa&ntilde;ol</b> (es)
                            </label><br>

                            <label for="loc05">
                                <input type="radio" name="localeCode" value="fr" ${locale eq 'fr' ? 'checked' : ''} id="loc05" />
                                <b>Fran&ccedil;ais</b> (fr)
                            </label><br>

                            <label for="locJa">
                                <input type="radio" name="localeCode" value="ja_JP" ${locale eq 'ja_JP' ? 'checked' : ''} id="locJa" />
                                <b>日本語</b> (ja_JP)
                            </label><br>

                            <label for="loc06">
                                <input type="radio" name="localeCode" value="nl" ${locale eq 'nl' ? 'checked' : ''} id="loc06" />
                                <b>Nederlands</b> (nl)
                            </label><br>

                            <label for="loc07">
                                <input type="radio" name="localeCode" value="pl_PL" ${locale eq 'pl_PL' ? 'checked' : ''} id="loc07" />
                                <b>Polski</b> (pl_PL)
                            </label><br>

                            <label for="loc08">
                                <input type="radio" name="localeCode" value="pt_BR" ${locale eq 'pt_BR' ? 'checked' : ''} id="loc08" />
                                <b>Portugu&ecirc;s Brasileiro</b> (pt_BR)
                            </label><br>

                            <label for="loc09">
                                <input type="radio" name="localeCode" value="ru_RU" ${locale eq 'ru_RU' ? 'checked' : ''} id="loc09" />
                                <b>&#x420;&#x443;&#x441;&#x441;&#x43A;&#x438;&#x439;</b> (ru_RU)
                            </label><br>

                            <label for="loc10">
                                <input type="radio" name="localeCode" value="sk" ${locale eq 'sk' ? 'checked' : ''} id="loc10" />
                                <b>Sloven&#269;ina</b> (sk)
                            </label><br>

                            <label for="loc11">
                                <input type="radio" name="localeCode" value="zh_CN" ${locale eq 'zh_CN' ? 'checked' : ''} id="loc11" />
                                <img src="../images/setup_language_zh_CN.gif" border="0" align="top" />
                                <b>Simplified Chinese</b> (zh_CN)
                            </label><br>
                        </p>
                    </div>

                    <div align="right">
                        <input type="Submit" name="save" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
                    </div>
                </form>

            </div>
            <!-- END jive-contentBox -->

        </c:otherwise>
    </c:choose>


</body>
</html>
