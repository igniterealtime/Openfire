<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.JiveGlobals,
                 java.util.*,
                 org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.messenger.container.ServiceLookupFactory" %>

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
    Map jiveHomeErrors = new HashMap();
    // Get a handle on the jiveHome directory
    File jiveHomeDir = new File(JiveGlobals.getJiveHome());
    // Validate it:
    if (jiveHomeDir == null || !jiveHomeDir.exists()) {
        jiveHomeErrors.put("exists","exists");
    }
    else {
        if (!jiveHomeDir.canRead()) {
            jiveHomeErrors.put("read","read");
        }
        if (!jiveHomeDir.canWrite()) {
            jiveHomeErrors.put("write","write");
        }
    }

    // If this is JDK 1.3, check for dependencies:
    boolean isJDK13 = false;
    boolean isJDK14 = false;
    try {
        loadClass("java.util.TimerTask");
        isJDK13 = true;
    }
    catch (Exception ignored) {}
    try {
        loadClass("java.nio.Buffer");
        isJDK14 = true;
    }
    catch (Exception ignored) {}
    Map depErrors = new HashMap();
    if (isJDK13 && !isJDK14) {
        // Check for jcert.jar, jnet.jar, jsse.jar
        try {
            loadClass("javax.security.cert.Certificate");
        }
        catch (ClassNotFoundException e) {
            depErrors.put("jcert.jar","jcert.jar");
        }
        try {
            loadClass("javax.net.SocketFactory");
        }
        catch (ClassNotFoundException e) {
            depErrors.put("jnet.jar","jnet.jar");
        }
        try {
            loadClass("javax.net.ssl.SSLSocket");
        }
        catch (ClassNotFoundException e) {
            depErrors.put("jsse.jar","jsse.jar");
        }
        try {
            loadClass("javax.sql.DataSource");
        }
        catch (ClassNotFoundException e) {
            depErrors.put("jdbc2_0-stdext.jar","jdbc2_0-stdext.jar");
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Installation Checklist
</p>

<p>


Welcome to <fmt:message key="title" bundle="${lang}" />  Setup. This tool will lead you through the initial configuration process
of the application. Before continuing, verify that your environment meets all the requirements
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
    <td class="jive-setup-category">
        At least Servlet 2.2 API
        <br>
        <span class="jive-info">
        Appserver: <%= application.getServerInfo() %>,
        Supports Servlet 2.2 API and JSP 1.2.
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" bundle="${lang}" /> Classes
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        jive-xmpp.jar
        <br>
        <span class="jive-info">
        <fmt:message key="title" bundle="${lang}" /> classes.
        </span>
    </td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
    <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
</tr>
<tr>
    <td class="jive-setup-category">
        Dependency Libraries
        <br>
        <span class="jive-info">
        <%  boolean depsOK = true;
            if (isJDK14) {
        %>

            Found <fmt:message key="title" bundle="${lang}" /> dependency classes.

        <%  } else { %>

            <%  if (depErrors.size() == 0) { %>

                Found <fmt:message key="title" bundle="${lang}" /> dependency classes.

            <%  } else {
                    depsOK = false;
            %>

                Missing some dependency classes. You are running JDK 1.3 - please make
                sure you have copied all required JDK 1.3 JAR files from the distribution to the
                'lib' directory of this server.

                <ul>

                <%  for (Iterator iter=depErrors.keySet().iterator(); iter.hasNext(); ) { %>

                    <li><%= (depErrors.get(iter.next())) %></li>

                <%  } %>

                </ul>

            <%  } %>

        <%  } %>
        </span>
    </td>
    <%  if (depsOK) { %>

        <td align="center" class="jive-setup-checklist-box"><img src="images/check.gif" width="13" height="13" border="0"></td>
        <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>

    <%  } else { %>

        <td align="center" class="jive-setup-checklist-box"><img src="images/blank.gif" width="13" height="13" border="0"></td>
        <td align="center" class="jive-setup-checklist-box"><img src="images/x.gif" width="13" height="13" border="0"></td>

    <%  } %>
</tr>
<tr>
    <td colspan="3" class="jive-setup-category-header">
        <fmt:message key="title" bundle="${lang}" /> Configuration Files
    </td>
</tr>
<tr>
    <td class="jive-setup-category">
        jiveHome Directory
        <br>
        <span class="jive-info">
        <%  boolean jiveHomeOK = true;
            if (jiveHomeErrors.size() == 0) {
        %>

            Valid jiveHome directory.

        <%  } else {
                jiveHomeOK = false;
        %>

            <%  if (jiveHomeErrors.get("exists") != null) { %>

                Unable to locate valid jiveHome directory. Please refer to the installation
                documentation for the correct way to set the jiveHome directory.

            <%  } else if (jiveHomeErrors.get("read") != null) { %>

                Setup was able to find your jiveHome directory but does not have read
                permission on it. Please alter the directory permissions.

            <%  } else if (jiveHomeErrors.get("write") != null) { %>

                Setup was able to find your jiveHome directory but does not have write permission
                on it. Please alter the directory permissions.

           

            <%  } %>

        <%  } %>
        </span>
    </td>
    <%  if (jiveHomeOK) { %>

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
<%  if (!jiveHomeOK || !depsOK) { %>

    <input type="submit" value=" Continue " disabled onclick="return false;">

<%  } else {  %>

    <input type="submit" name="continue" value=" Continue ">

<%  } %>
</div>
</form>

<%@ include file="setup-footer.jsp" %>