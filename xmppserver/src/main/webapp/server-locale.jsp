<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TimeZone" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%
    final Logger Log = LoggerFactory.getLogger("server-db.jsp");
    // Get parameters //
    String localeCode = ParamUtils.getParameter(request,"localeCode");
    String timeZoneID = ParamUtils.getParameter(request,"timeZoneID");
    boolean save = request.getParameter("save") != null;

    Map<String,String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);
    if (save) {
        // Set the timezeone
        try {
            TimeZone tz = TimeZone.getTimeZone(timeZoneID);
            JiveGlobals.setTimeZone(tz);
            // Log the event
            webManager.logEvent("updated time zone to "+tz.getID(), tz.toString());
        }
        catch (Exception e) {
            errors.put("timezone", "Unable to change timezone: " + e.getMessage());
            Log.error("Unexpected exception changing timezone", e);
        }
        if (localeCode != null) {
            Locale newLocale = LocaleUtils.localeCodeToLocale(localeCode.trim());
            if (newLocale == null) {
                errors.put("localeCode","");
            }
            else {
                JiveGlobals.setLocale(newLocale);
                // Log the event
                webManager.logEvent("updated locale to "+newLocale.getDisplayName(), null);
                response.sendRedirect("server-locale.jsp?success=true");
                return;
            }
        }
    }

    Locale locale = JiveGlobals.getLocale();

    // Get the time zone list.
    String[][] timeZones = LocaleUtils.getTimeZoneList();

    // Get the current time zone.
    TimeZone timeZone = JiveGlobals.getTimeZone();
    pageContext.setAttribute( "errors", errors );
%>

<html>
    <head>
        <title><fmt:message key="locale.title" /></title>
        <meta name="pageID" content="server-locale"/>
        <meta name="helpPage" content="edit_server_properties.html"/>
    </head>
    <body>

    <c:forEach var="err" items="${errors}">
        <div class="error"><c:out value="${err.value}"/></div>
    </c:forEach>

    <p>
<fmt:message key="locale.title.info" />
</p>


<!-- BEGIN locale settings -->
<form action="server-locale.jsp" method="post" name="sform">
    <input type="hidden" name="csrf" value="${csrf}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="locale.system.set" />
    </div>
    <div class="jive-contentBox">
        <p>
        <b><fmt:message key="locale.current" />:</b> <%= locale.getDisplayName(locale) %> /
            <%= LocaleUtils.getTimeZoneName(JiveGlobals.getTimeZone().getID(), locale) %>
        </p>

        <%  boolean usingPreset;
            Locale[] locales = Locale.getAvailableLocales();
            for (Locale locale1 : locales) {
                usingPreset = locale1.equals(locale);
                if (usingPreset) {
                    break;
                }
            }
        %>

        <p><b><fmt:message key="language.choose" />:</b></p>

        <table cellspacing="0" cellpadding="3" border="0">
        <tbody>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="cs_CZ" <%= ("cs_CZ".equals(locale.toString()) ? "checked" : "") %>
                     id="loc01" />
                </td>
                <td colspan="2">
                    <label for="loc01">Czech (cs_CZ)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="de" <%= ("de".equals(locale.toString()) ? "checked" : "") %>
                     id="loc02" />
                </td>
                <td colspan="2">
                    <label for="loc02">Deutsch (de)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="en" <%= ("en".equals(locale.toString()) ? "checked" : "") %>
                     id="loc03" />
                </td>
                <td colspan="2">
                    <label for="loc03">English (en)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="es" <%= ("es".equals(locale.toString()) ? "checked" : "") %>
                     id="loc04" />
                </td>
                <td colspan="2">
                    <label for="loc04">Espa&ntilde;ol (es)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="fr" <%= ("fr".equals(locale.toString()) ? "checked" : "") %>
                     id="loc05" />
                </td>
                <td colspan="2">
                    <label for="loc05">Fran&ccedil;ais (fr)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="ja_JP" <%= ("ja_JP".equals(locale.toString()) ? "checked" : "") %>
                           id="locja" />
                </td>
                <td colspan="2">
                    <label for="locja">日本語 (ja_JP)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="nl" <%= ("nl".equals(locale.toString()) ? "checked" : "") %>
                     id="loc06" />
                </td>
                <td colspan="2">
                    <label for="loc06">Nederlands (nl)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="pl_PL" <%= ("pl_PL".equals(locale.toString()) ? "checked" : "") %>
                     id="loc07" />
                </td>
                <td colspan="2">
                    <label for="loc07">Polski (pl_PL)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="pt_PT" <%= ("pt_PT".equals(locale.toString()) ? "checked" : "") %>
                     id="loc08" />
                </td>
                <td colspan="2">
                    <label for="loc08">Portugu&ecirc;s Portugal (pt_PT)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="pt_BR" <%= ("pt_BR".equals(locale.toString()) ? "checked" : "") %>
                     id="loc09" />
                </td>
                <td colspan="2">
                    <label for="loc09">Portugu&ecirc;s Brasileiro (pt_BR)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="ru_RU" <%= ("ru_RU".equals(locale.toString()) ? "checked" : "") %>
                     id="loc10" />
                </td>
                <td colspan="2">
                    <label for="loc10">&#x420;&#x443;&#x441;&#x441;&#x43A;&#x438;&#x439; (ru_RU)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="sk" <%= ("sk".equals(locale.toString()) ? "checked" : "") %>
                     id="loc11" />
                </td>
                <td colspan="2">
                    <label for="loc11">Sloven&#269;ina (sk)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="zh_CN" <%= ("zh_CN".equals(locale.toString()) ? "checked" : "") %>
                     id="loc12" />
                </td>
                <td>
                    <a href="#" onclick="document.sform.localeCode[1].checked=true; return false;"><img src="images/language_zh_CN.gif" border="0" alt="" /></a>
                </td>
                <td>
                    <label for="loc12">Simplified Chinese (zh_CN)</label>
                </td>
            </tr>
        </tbody>
        </table>

        <br>

        <p><b><label for="timeZoneID"><fmt:message key="timezone.choose" />:</label></b></p>

        <select size="1" name="timeZoneID" id="timeZoneID">
        <% for (String[] timeZone1 : timeZones) {
            String selected = "";
            if (timeZone.getID().equals(timeZone1[0].trim())) {
                selected = " selected";
            }
        %>
            <option value="<%= timeZone1[0] %>"<%= selected %>><%= timeZone1[1] %>
                <%  } %>
        </select>
    </div>
<input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
</form>
<!-- END locale settings -->


</body>
</html>
