<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
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
    // Handle a continue:
    if (request.getParameter("continue") != null) {
        // update the sidebar status
        session.setAttribute("jive.setup.sidebar.1","done");
        session.setAttribute("jive.setup.sidebar.2","in_progress");
        // redirect
        response.sendRedirect("setup-host-settings.jsp");
        return;
    }

    Map errors = new HashMap();

    // Error checking
    Map messengerHomeErrors = new HashMap();
    // Get a handle on the messengerHome directory
    File messengerHomeDir = new File(JiveGlobals.getMessengerHome());
    // Validate it:
    if (messengerHomeDir == null || !messengerHomeDir.exists()) {
        messengerHomeErrors.put("exists","exists");
    }
    else {
        if (!messengerHomeDir.canRead()) {
            messengerHomeErrors.put("read","read");
        }
        if (!messengerHomeDir.canWrite()) {
            messengerHomeErrors.put("write","write");
        }
    }
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

<%  if (errors.size() > 0) { %>

    <%-- print out errors here --%>

<%  } %>

<table cellpadding="3" cellspacing="2" border="0" width="100%">
<tr>
    <th width="98%">&nbsp;</th>
    <th width="1%" nowrap class="jive-setup-checklist-box"><fmt:message key="setup.index.success" /> </th>
    <th width="1%" nowrap class="jive-setup-checklist-box"><fmt:message key="setup.index.error" /></th>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="setup.index.vm" />
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        <fmt:message key="setup.index.jdk" />
        <br>
        <span class="jive-info">
        <fmt:message key="setup.index.found" /> <%= System.getProperty("java.version") %> - <%= System.getProperty("java.vendor") %>
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" /> <fmt:message key="setup.index.class" />
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        messenger.jar
        <br>
        <span class="jive-info">
        <fmt:message key="title" /> <fmt:message key="setup.index.class" />.
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" /> <fmt:message key="setup.index.con_file" />
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        <fmt:message key="setup.index.dir" />
        <br>
        <span class="jive-info">
        <%  boolean messengerHomeOK = true;
            if (messengerHomeErrors.size() == 0) {
        %>

            <fmt:message key="setup.index.valid_conf" />

        <%  } else {
                messengerHomeOK = false;
        %>

            <%  if (messengerHomeErrors.get("exists") != null) { %>

                <fmt:message key="setup.index.unable_locate_dir" />

            <%  } else if (messengerHomeErrors.get("read") != null) { %>

                <fmt:message key="setup.index.not_permission" />

            <%  } else if (messengerHomeErrors.get("write") != null) { %>

                <fmt:message key="setup.index.not_write_permission" />

           

            <%  } %>

        <%  } %>
        </span>
    </td>
    <%  if (messengerHomeOK) { %>

        <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
        <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>

    <%  } else { %>

        <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
        <td align="center" class="jive-setup-checklist-box"><img src="images/x.gif" width="13" height="13" border="0"></td>

    <%  } %>
</tr>
</table>

<br><br>

<hr size="0">

<form action="setup-index.jsp">
<div align="right">
<%  if (!messengerHomeOK) { %>

    <input type="submit" value=" Continue " disabled onclick="return false;">

<%  } else {  %>

    <input type="submit" name="continue" value=" Continue ">

<%  } %>
</div>
</form>

<%@ include file="setup-footer.jsp" %>