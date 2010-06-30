<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="org.jivesoftware.openfire.clearspace.ClearspaceManager" %>
<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    // Get parameters
    if (request.getParameter("ldapedit") != null) {
        // Redirect to first step.
        response.sendRedirect("ldap-server.jsp");
        return;
    }
    else if (request.getParameter("clearspaceedit") != null) {
        // Redirect to clearspace setup.
        response.sendRedirect("clearspace-integration.jsp");
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
                JiveGlobals.getProperty("provider.auth.className"));
        boolean isCLEARSPACE = "org.jivesoftware.openfire.clearspace.ClearspaceAuthProvider".equals(
                JiveGlobals.getProperty("provider.auth.className"));
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
                        <input type="radio" <%= (isLDAP || isCLEARSPACE) ? "disabled" : "readonly"%>
                        <%= ((isLDAP || isCLEARSPACE) ? "" : "checked") %>>
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
                <%
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
                            <input type="submit" name="ldapedit" value="<fmt:message key="server.properties.edit" />">
                        </td>
                    </tr>
                <% } %>
                <tr>
                    <td width="1%" nowrap>
                        <input type="radio" <%= isCLEARSPACE ? "readonly" : "disabled"%>
                        <%= (isCLEARSPACE ? "checked" : "") %>>
                    </td>
                    <td width="99%">
                        <b><fmt:message key="setup.profile.clearspace" /></b> - <fmt:message key="setup.profile.clearspace_description" />
                    </td>
                </tr>
                <% if (isCLEARSPACE) { %>
                <%
                    String uri = ClearspaceManager.getInstance().getConnectionURI();
                %>
                <tr>
                    <td width="1%" nowrap>
                        &nbsp;
                    </td>
                    <td width="99%">
                        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="98%" align="right">
                        <thead>
                            <tr>
                                <th colspan="2"><fmt:message key="profile-settings.clearspace_mapping_info" /></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td class="c1">
                                    <fmt:message key="setup.clearspace.service.uri" />:
                                </td>
                                <td class="c2">
                                    <%= uri %>
                                </td>
                            </tr>
                        </tbody>
                        </table>
                    </td>
                    <tr>
                        <td colspan="2" align="center">
                            <input type="submit" name="clearspaceedit" value="<fmt:message key="server.properties.edit" />">
                        </td>
                    </tr>
                <% } %>
            </tbody>
            </table>
        </div>
    </form>

</body>
</html>

