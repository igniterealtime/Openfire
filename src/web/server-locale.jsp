<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 java.text.*,
                 org.jivesoftware.admin.AdminPageBean,
                 org.jivesoftware.admin.AdminConsole,
                 javax.servlet.jsp.jstl.core.Config"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define page bean for header and sidebar --%>
<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%  // Edit this code when new locales are added:
    final Locale[] builtinLocales = new Locale[] {
        new Locale("en", "US"),
        new Locale("zh", "CN")
    };
%>

<%-- Define Administration Bean --%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out); %>

<%  // Get parameters //
    String localeCode = ParamUtils.getParameter(request,"localeCode");
    boolean save = request.getParameter("save") != null;

    Map errors = new HashMap();
    if (save) {
        Locale newLocale = null;
        if (localeCode != null) {
            newLocale = LocaleUtils.localeCodeToLocale(localeCode.trim());
            if (newLocale == null) {
                errors.put("localeCode","");
            }
            else {
                JiveGlobals.setLocale(newLocale);
                response.sendRedirect("server-locale.jsp?success=true");
                return;
            }
        }
    }

    Locale locale = JiveGlobals.getLocale();
%>

<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("locale.title");
    pageinfo.setTitle(title);
    pageinfo.setPageID("server-locale");
%>

<%@ include file="top.jsp" %>

<jsp:include page="title.jsp" flush="true" />

<p>
<fmt:message key="locale.title.info" />
</p>

<form action="server-locale.jsp" method="post" name="sform">

<fieldset>
    <legend><fmt:message key="locale.system.set" /></legend>
    <div style="padding-top:0.5em;">

        <p>
        <fmt:message key="locale.current" />: <%= locale.getDisplayName() %>
        </p>

        <%  boolean usingPreset = false;
            Locale[] locales = Locale.getAvailableLocales();
            for (int i=0; i<locales.length; i++) {
                usingPreset = locales[i].equals(locale);
                if (usingPreset) { break; }
            }
        %>

        <p><b><fmt:message key="locale.choose" />:</b></p>

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
                    <input type="radio" name="localeCode" value="fr" <%= ("fr".equals(locale.toString()) ? "checked" : "") %>
                     id="loc04" />
                </td>
                <td colspan="2">
                    <label for="loc04">Fran&ccedil;ais (fr)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="nl" <%= ("nl".equals(locale.toString()) ? "checked" : "") %>
                     id="loc05" />
                </td>
                <td colspan="2">
                    <label for="loc05">Nederlands (nl)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="pt_BR" <%= ("pt_BR".equals(locale.toString()) ? "checked" : "") %>
                     id="loc06" />
                </td>
                <td colspan="2">
                    <label for="loc06">Portugu&ecirc;s Brasileiro (pt_BR)</label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="localeCode" value="zh_CN" <%= ("zh_CN".equals(locale.toString()) ? "checked" : "") %>
                     id="loc07" />
                </td>
                <td>
                    <a href="#" onclick="document.sform.localeCode[1].checked=true; return false;"><img src="images/language_zh_CN.gif" border="0" /></a>
                </td>
                <td>
                    <label for="loc07">Simplified Chinese (zh_CN)</label>
                </td>
            </tr>
        </tbody>
        </table>

    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="<fmt:message key="global.save_settings" />">

</form>

<jsp:include page="bottom.jsp" flush="true" />

<%!
    private String spacer(int length) {
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<length; i++) {
            buf.append("&nbsp;");
        }
        return buf.toString();
    }
%>