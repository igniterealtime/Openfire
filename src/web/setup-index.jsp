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
Setup Checklist
</p>

<p>


Welcome to <fmt:message key="title" />  Setup. This tool will lead you through the initial setup or
upgrade process. Before continuing, verify that your environment meets all the requirements
below.
</p>

<%  if (errors.size() > 0) { %>

    <%-- print out errors here --%>

<%  } %>

<table cellpadding="3" cellspacing="2" border="0" width="100%">
<tr>
    <th width="98%">&nbsp;</th>
    <th width="1%" nowrap class="jive-setup-checklist-box">Success</th>
    <th width="1%" nowrap class="jive-setup-checklist-box">Error</th>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        Java VM Support
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        At least JDK 1.5
        <br>
        <span class="jive-info">
        Found: JVM <%= System.getProperty("java.version") %> - <%= System.getProperty("java.vendor") %>
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" /> Classes
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        messenger.jar
        <br>
        <span class="jive-info">
        <fmt:message key="title" /> classes.
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" /> Configuration Files
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        conf Directory
        <br>
        <span class="jive-info">
        <%  boolean messengerHomeOK = true;
            if (messengerHomeErrors.size() == 0) {
        %>

            Valid conf directory.

        <%  } else {
                messengerHomeOK = false;
        %>

            <%  if (messengerHomeErrors.get("exists") != null) { %>

                Unable to locate valid conf directory. Please refer to the installation
                documentation for the correct way to set the conf directory.

            <%  } else if (messengerHomeErrors.get("read") != null) { %>

                Setup was able to find your conf directory but does not have read
                permission on it. Please alter the directory permissions.

            <%  } else if (messengerHomeErrors.get("write") != null) { %>

                Setup was able to find your conf directory but does not have write permission
                on it. Please alter the directory permissions.

           

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