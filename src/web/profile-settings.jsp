<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Get parameters
    boolean edit = request.getParameter("edit") != null;
    if (edit) {
        // Redirect to first step.
        response.sendRedirect("ldap-server.jsp");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="profile-settings.title"/></title>
        <meta name="pageID" content="profile-settings"/>
    </head>
    <body>
    <%
        boolean isLDAP = "org.jivesoftware.openfire.ldap.LdapAuthProvider".equals(
                JiveGlobals.getXMLProperty("provider.auth.className"));
        StringBuilder sb = new StringBuilder();
        for (String host : LdapManager.getInstance().getHosts()) {
            sb.append(host).append(", ");
        }
        String hosts = sb.toString();
        if (hosts.trim().length() > 0) {
            hosts = hosts.substring(0, hosts.length()-2);
        }
        int port = LdapManager.getInstance().getPort();
        String baseDN = LdapManager.getInstance().getBaseDN();
        String adminDN = LdapManager.getInstance().getAdminDN();

    %>
    <p>
    <fmt:message key="profile-settings.info"/>
    </p>

    <form action="profile-settings.jsp" method="post">
        <!--<div class="jive-contentBoxHeader">

        </div>-->
        <div class="jive-contentBox" style="-moz-border-radius: 3px;">
            <table cellpadding="3" cellspacing="3" border="0">
            <tbody>
                <tr>
                    <td width="1%" nowrap>
                        <input type="radio" <%= isLDAP ? "disabled" : "readonly"%>
                        <%= (isLDAP ? "" : "checked") %>>
                    </td>
                    <td width="99%">
                        <b><fmt:message key="setup.profile.default" /></b> - <fmt:message key="setup.profile.default_description" />
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <input type="radio" <%= isLDAP ? "readonly" : "disabled"%>
                        <%= (isLDAP ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <b><fmt:message key="setup.profile.ldap" /></b> - <fmt:message key="setup.profile.ldap_description" />
                    </td>
                </tr>
                <% if (isLDAP) { %>
                <tr>
                    <td width="1%" nowrap>
                        &nbsp;
                    </td>
                    <td width="99%">
                        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="98%" align="right">
                        <thead>
                            <tr>
                                <th colspan="2"><fmt:message key="profile-settings.ldap_mapping_info" /></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td class="c1">
                                    <fmt:message key="setup.ldap.server.host" />:
                                </td>
                                <td class="c2">
                                    <%= hosts %>
                                </td>
                            </tr>
                            <tr>
                                <td class="c1">
                                    <fmt:message key="setup.ldap.server.port" />:
                                </td>
                                <td class="c2">
                                    <%= port %>
                                </td>
                            </tr>
                            <tr>
                                <td class="c1">
                                     <fmt:message key="setup.ldap.server.basedn" />:
                                </td>
                                <td class="c2">
                                    <%= baseDN %>
                                </td>
                            </tr>
                            <tr>
                                <td class="c1">
                                    <fmt:message key="setup.ldap.server.admindn" />:
                                </td>
                                <td class="c2">
                                    <%= adminDN %>
                                </td>
                            </tr>
                        </tbody>
                        </table>
                    </td>
                    <tr>
                        <td colspan="2" align="center">
                            <input type="submit" name="edit" value="<fmt:message key="server.properties.edit" />">
                        </td>
                    </tr>
                </tr>
                <% } %>
            </tbody>
            </table>
        </div>
    </form>

</body>
</html>

