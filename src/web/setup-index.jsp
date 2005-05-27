<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals,
                 java.util.*" %>

<%! // Global vars, methods, etc

    static final String JIVE_HOME = "jive_home";
    static final String JIVE_LICENSE = "jive_license_file";
    static final String JIVE_LICENSE_TEXT = "jive_license_text";
    static final String JIVE_DEPENDENCY = "jive_dependency";
    static final String JIVE_CONFIG_FILE = "jive_config_file";
%>

<%@ include file="setup-global.jspf" %>

<%@ include file="setup-env-check.jspf" %>

<%  // Get parameters
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
                // update the sidebar status
                session.setAttribute("jive.setup.sidebar.1","done");
                session.setAttribute("jive.setup.sidebar.2","in_progress");
                // redirect
                response.sendRedirect("setup-host-settings.jsp");
                return;
            }
        }
    }

    Locale locale = JiveGlobals.getLocale();
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="setup.index.title" />
</p>

<p>


<fmt:message key="setup.index.info">
    <fmt:param value="<%= LocaleUtils.getLocalizedString("title") %>" />
</fmt:message>
</p>

<form action="setup-index.jsp" name="sform">

<b><fmt:message key="setup.index.choose_lang" /></b>

<%  boolean usingPreset = false;
    Locale[] locales = Locale.getAvailableLocales();
    for (int i=0; i<locales.length; i++) {
        usingPreset = locales[i].equals(locale);
        if (usingPreset) { break; }
    }
%>

<ul>
<table cellpadding="4" cellspacing="0" border="0">
<tbody>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="cs_CZ" <%= ("cs_CZ".equals(locale.toString()) ? "checked" : "") %>
             id="loc01" />
        </td>
        <td colspan="2">
            <label for="loc01">Czech</label> (cs_CZ)
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="de" <%= ("de".equals(locale.toString()) ? "checked" : "") %>
             id="loc02" />
        </td>
        <td colspan="2">
            <label for="loc02">Deutsch</label> (de)
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="en" <%= ("en".equals(locale.toString()) ? "checked" : "") %>
             id="loc03" />
        </td>
        <td colspan="2">
            <label for="loc03">English</label> (en)
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="fr" <%= ("fr".equals(locale.toString()) ? "checked" : "") %>
             id="loc04" />
        </td>
        <td colspan="2">
            <label for="loc04">Fran&ccedil;ais</label> (fr)
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="nl" <%= ("nl".equals(locale.toString()) ? "checked" : "") %>
             id="loc05" />
        </td>
        <td colspan="2">
            <label for="loc05">Nederlands</label> (nl)
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="localeCode" value="zh_CN" <%= ("zh_CN".equals(locale.toString()) ? "checked" : "") %>
             id="loc06" />
        </td>
        <td>
            <a href="#" onclick="document.sform.localeCode[1].checked=true; return false;"><img src="images/language_zh_CN.gif" border="0" /></a>
        </td>
        <td>
            <label for="loc06">Simplified Chinese</label> (zh_CN)
        </td>
    </tr>
</tbody>
</table>
</ul>

<br/>
<hr size="0">

<div align="right">
<input type="submit" name="save" value=" <fmt:message key="global.continue" /> ">
</div>
</form>

<%@ include file="setup-footer.jsp" %>