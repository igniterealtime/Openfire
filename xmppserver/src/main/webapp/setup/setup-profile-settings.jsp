<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.admin.AdminManager" %>
<%@ page import="org.jivesoftware.openfire.group.GroupManager" %>
<%@ page import="org.jivesoftware.openfire.lockout.LockOutManager" %>
<%@ page import="org.jivesoftware.openfire.security.SecurityAuditManager" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.openfire.auth.AuthFactory" %>
<%@ page import="org.jivesoftware.openfire.vcard.VCardManager" %>

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
                xmppSettings.put(AuthFactory.AUTH_PROVIDER.getKey(), JiveGlobals.getXMLProperty(AuthFactory.AUTH_PROVIDER.getKey(),
                    AuthFactory.AUTH_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(UserManager.USER_PROVIDER.getKey(), JiveGlobals.getXMLProperty(UserManager.USER_PROVIDER.getKey(),
                    UserManager.USER_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(GroupManager.GROUP_PROVIDER.getKey(), JiveGlobals.getXMLProperty(GroupManager.GROUP_PROVIDER.getKey(),
                    GroupManager.GROUP_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(VCardManager.VCARD_PROVIDER.getKey(), JiveGlobals.getXMLProperty(VCardManager.VCARD_PROVIDER.getKey(),
                    VCardManager.VCARD_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(LockOutManager.LOCKOUT_PROVIDER.getKey(), JiveGlobals.getXMLProperty(LockOutManager.LOCKOUT_PROVIDER.getKey(),
                    LockOutManager.LOCKOUT_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(SecurityAuditManager.AUDIT_PROVIDER.getKey(), JiveGlobals.getXMLProperty(SecurityAuditManager.AUDIT_PROVIDER.getKey(),
                    SecurityAuditManager.AUDIT_PROVIDER.getDefaultValue().getName()));
                xmppSettings.put(AdminManager.ADMIN_PROVIDER.getKey(), JiveGlobals.getXMLProperty(AdminManager.ADMIN_PROVIDER.getKey(),
                    AdminManager.ADMIN_PROVIDER.getDefaultValue().getName()));
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

    pageContext.setAttribute( "sessionFailure", sessionFailure );
    pageContext.setAttribute( "isLDAP", isLDAP );
    pageContext.setAttribute( "scramOnly", scramOnly );
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

    <c:if test="${sessionFailure}">
        <span class="jive-error-text">
            <fmt:message key="setup.invalid_session" />
        </span>
    </c:if>

    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">
    <form action="setup-profile-settings.jsp" name="profileform" method="post">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="default" id="rb01" ${not isLDAP ? 'checked' : ''}>
    </td>
    <td>
        <label for="rb01"><b><fmt:message key="setup.profile.default" /></b></label><br>
        <fmt:message key="setup.profile.default_description" />
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="checkbox" name="scramOnly" value="scramOnly" id="rb01-0" ${scramOnly ? 'checked' : ''}>
    </td>
    <td>
        <label for="rb01-0"><b><fmt:message key="setup.profile.default.scramOnly" /></b></label><br>
        <fmt:message key="setup.profile.default.scramOnly_description" />
    </td>
</tr>
<tr>
    <td align="center" valign="top">
        <input type="radio" name="mode" value="ldap" id="rb02" ${isLDAP ? 'checked' : ''}>
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
