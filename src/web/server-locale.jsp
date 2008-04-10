<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2005 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.LocaleUtils,
                 org.jivesoftware.util.Log,
                 org.jivesoftware.util.ParamUtils"
%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.Locale"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.TimeZone"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    String localeCode = ParamUtils.getParameter(request,"localeCode");
    String timeZoneID = ParamUtils.getParameter(request,"timeZoneID");
    boolean save = request.getParameter("save") != null;

    // TODO: We're not displaying this error ever.
    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        // Set the timezeone
        try {
            TimeZone tz = TimeZone.getTimeZone(timeZoneID);
            JiveGlobals.setTimeZone(tz);
            // Log the event
            webManager.logEvent("updated time zone to "+tz.getID(), tz.toString());
        }
        catch (Exception e) {
            Log.error(e);
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
%>

<html>
    <head>
        <title><fmt:message key="locale.title" /></title>
        <meta name="pageID" content="server-locale"/>
        <meta name="helpPage" content="edit_server_properties.html"/>
    </head>
    <body>

<p>
<fmt:message key="locale.title.info" />
</p>


<!-- BEGIN locale settings -->
<form action="server-locale.jsp" method="post" name="sform">
	<div class="jive-contentBoxHeader">
		<fmt:message key="locale.system.set" />
	</div>
	<div class="jive-contentBox">
		<p>
        <b><fmt:message key="locale.current" />:</b> <%= locale.getDisplayName(locale) %> /
            <%= LocaleUtils.getTimeZoneName(JiveGlobals.getTimeZone().getID(), locale) %>
        </p>

        <%  boolean usingPreset = false;
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
                    <input type="radio" name="localeCode" value="pt_BR" <%= ("pt_BR".equals(locale.toString()) ? "checked" : "") %>
                     id="loc08" />
                </td>
                <td colspan="2">
                    <label for="loc08">Portugu&ecirc;s Brasileiro (pt_BR)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="zh_CN" <%= ("zh_CN".equals(locale.toString()) ? "checked" : "") %>
                     id="loc09" />
                </td>
                <td>
                    <a href="#" onclick="document.sform.localeCode[1].checked=true; return false;"><img src="images/language_zh_CN.gif" border="0" alt="" /></a>
                </td>
                <td>
                    <label for="loc09">Simplified Chinese (zh_CN)</label>
                </td>
            </tr>
        </tbody>
        </table>

        <br>

        <p><b><fmt:message key="timezone.choose" />:</b></p>

        <select size="1" name="timeZoneID">
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