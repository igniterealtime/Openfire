<%--
--%>

<%@ page import="org.jivesoftware.openfire.XMPPServer"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="java.util.Map" %>

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
    // Get parameters
    boolean isLDAP = "org.jivesoftware.openfire.ldap.LdapAuthProvider".equals(
            JiveGlobals.getProperty("provider.auth.className"));
    boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
    boolean requestedScramOnly = (request.getParameter("scramOnly") != null);
    boolean next = request.getParameter("continue") != null;
    boolean sessionFailure = false;
    if (next) {
        // Figure out where to send the user.
        String mode = request.getParameter("mode");

        if ("default".equals(mode)) {
            // Set to default providers by deleting any existing values.
            @SuppressWarnings("unchecked")
            Map<String,String> xmppSettings = (Map<String,String>)session.getAttribute("xmppSettings");
            if (xmppSettings == null){
                sessionFailure = true;
            } else {
                xmppSettings.put("provider.auth.className", JiveGlobals.getXMLProperty("provider.auth.className",
                    org.jivesoftware.openfire.auth.DefaultAuthProvider.class.getName()));
                xmppSettings.put("provider.user.className", JiveGlobals.getXMLProperty("provider.user.className",
                    org.jivesoftware.openfire.user.DefaultUserProvider.class.getName()));
                xmppSettings.put("provider.group.className", JiveGlobals.getXMLProperty("provider.group.className",
                    org.jivesoftware.openfire.group.DefaultGroupProvider.class.getName()));
                xmppSettings.put("provider.vcard.className", JiveGlobals.getXMLProperty("provider.vcard.className",
                    org.jivesoftware.openfire.vcard.DefaultVCardProvider.class.getName()));
                xmppSettings.put("provider.lockout.className", JiveGlobals.getXMLProperty("provider.lockout.className",
                    org.jivesoftware.openfire.lockout.DefaultLockOutProvider.class.getName()));
                xmppSettings.put("provider.securityAudit.className", JiveGlobals.getXMLProperty("provider.securityAudit.className",
                    org.jivesoftware.openfire.security.DefaultSecurityAuditProvider.class.getName()));
                xmppSettings.put("provider.admin.className", JiveGlobals.getXMLProperty("provider.admin.className",
                    org.jivesoftware.openfire.admin.DefaultAdminProvider.class.getName()));
                if (requestedScramOnly) {
                    JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");
                }

                // Redirect
                response.sendRedirect("setup-admin-settings.jsp");
                return;
            }
        }
        else if ("ldap".equals(mode)) {
            response.sendRedirect("setup-ldap-server.jsp");
            return;
        }
    }
%>
<html>
<head>
    <title><fmt:message key="setup.profile.title" /></title>
    <meta name="currentStep" content="3"/>
</head>
<body>

    <h1>
    <fmt:message key="setup.profile.title" />
    </h1>

    <p>
    <fmt:message key="setup.profile.description" />
    </p>

<% if (sessionFailure) { %>
            <span class="jive-error-text">
            <fmt:message key="setup.invalid_session" />
            </span>
<% } %>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">
    <form action="setup-profile-settings.jsp" name="profileform" method="post">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="default" id="rb01" <% if (!isLDAP) { %>checked<% } %>>
    </td>
    <td>
        <label for="rb01"><b><fmt:message key="setup.profile.default" /></b></label><br>
        <fmt:message key="setup.profile.default_description" />
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="checkbox" name="scramOnly" value="scramOnly" id="rb01-0" <% if (scramOnly) { %>checked<% } %>>
    </td>
    <td>
        <label for="rb01-0"><b><fmt:message key="setup.profile.default.scramOnly" /></b></label><br>
        <fmt:message key="setup.profile.default.scramOnly_description" />
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="ldap" id="rb02" <% if (isLDAP) { %>checked<% } %>>
    </td>
    <td>
        <label for="rb02"><b><fmt:message key="setup.profile.ldap" /></b></label><br>
        <fmt:message key="setup.profile.ldap_description" />
    </td>
</tr>
</table>

<br>
        <div align="right">
            <input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save" border="0">
        </div>

    </form>
    </div>
    <!-- END jive-contentBox -->

</body>
</html>
