<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.LocaleUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>

<%@ taglib uri="admin" prefix="admin" %>
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

    pageContext.setAttribute( "locales", LocaleUtils.getSupportedLocales() );
    Locale locale = JiveGlobals.getLocale();
    pageContext.setAttribute( "locale", locale.toString() );

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

    <admin:infobox type="info">
        <fmt:message key="locale.translator-invitation" />
    </admin:infobox>

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
        <b><fmt:message key="locale.current" />:</b> ${locales[locale]} /
            <%= LocaleUtils.getTimeZoneName(JiveGlobals.getTimeZone().getID(), locale) %>
        </p>

        <p><b><fmt:message key="language.choose" />:</b></p>

        <c:forEach var="l" items="${locales}">
            <label for="${l.key}">
                <input type="radio" name="localeCode" value="${l.key}" ${locale eq l.key ? 'checked' : ''} id="${l.key}"/>
                <b>${l.value}</b> (${l.key})
            </label><br>
        </c:forEach>

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
