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
        Current Locale: <%= locale.getDisplayName() %> (<%= locale %>)
        </p>

        <%  boolean usingPreset = false;
            Locale[] locales = Locale.getAvailableLocales();
            for (int i=0; i<locales.length; i++) {
                usingPreset = locales[i].equals(locale);
                if (usingPreset) { break; }
            }
        %>

        <label for="sel01">Choose new locale:</label>

        <select size="1" name="localeCode" style="font-family:courier new; font-size:9pt;"
         id="sel01" onchange="this.form.localeChoice[0].checked=true;">
            <%  Arrays.sort(locales, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        Locale loc1 = (Locale)o1;
                        Locale loc2 = (Locale)o2;
                        return loc1.getDisplayName().toLowerCase()
                                .compareTo(loc2.getDisplayName().toLowerCase());
                    }
                });
            %>

            <%  for (int i=0; i<locales.length; i++) {
                    boolean selected = locales[i].equals(locale);
            %>
                <option value="<%= locales[i] %>" <%= (selected ? "selected" : "") %>
                 ><%= locales[i].getDisplayName() %> - <%= locales[i] %></option>

            <%  } %>
        </select>

    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="Save Settings">

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