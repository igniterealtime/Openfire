<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapManager" %>
<%@ page import="org.jivesoftware.openfire.auth.AuthFactory" %>
<%@ page import="org.jivesoftware.openfire.ldap.LdapAuthProvider" %>
<%@ page import="javax.naming.ldap.LdapName" %>
<%--
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Get parameters
    if (request.getParameter("ldapedit") != null) {
        // Redirect to first step.
        response.sendRedirect("ldap-server.jsp");
        return;
    }

    final boolean isLdap = "org.jivesoftware.openfire.ldap.LdapAuthProvider".equals(JiveGlobals.getProperty("provider.auth.className"))
                        || AuthFactory.isProviderHybridInstanceOf(LdapAuthProvider.class);
    request.setAttribute( "isLdap", isLdap );
    if ( isLdap )
    {
        StringBuilder sb = new StringBuilder();
        for ( String host : LdapManager.getInstance().getHosts() )
        {
            sb.append( host ).append( ", " );
        }
        String hosts = sb.toString();
        if ( hosts.trim().length() > 0 )
        {
            hosts = hosts.substring( 0, hosts.length() - 2 );
        }
        int port = LdapManager.getInstance().getPort();
        LdapName baseDN = LdapManager.getInstance().getBaseDN();
        String adminDN = LdapManager.getInstance().getAdminDN();

        request.setAttribute( "hosts", hosts );
        request.setAttribute( "port", port );
        request.setAttribute( "baseDN", baseDN );
        request.setAttribute( "adminDN", adminDN );
    }
%>

<html>
    <head>
        <title><fmt:message key="profile-settings.title"/></title>
        <meta name="pageID" content="profile-settings"/>
    </head>
    <body>
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
                        <input id="rb1" type="radio" ${isLdap ? 'disabled' : 'readonly'} ${isLdap ? '' : 'checked'}
                    </td>
                    <td width="99%">
                        <label for="rb1"><b><fmt:message key="setup.profile.default" /></b> - <fmt:message key="setup.profile.default_description" /></label>
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <input id="rb2" type="radio" ${isLdap ? 'disabled' : 'readonly'} ${isLdap ? 'checked' : ''}
                    </td>
                    <td width="99%">
                        <label for="rb2"><b><fmt:message key="setup.profile.ldap" /></b> - <fmt:message key="setup.profile.ldap_description" /></label>
                    </td>
                </tr>
                <c:if test="${isLdap}">
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
                                        <c:out value="${hosts}"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="c1">
                                        <fmt:message key="setup.ldap.server.port" />:
                                    </td>
                                    <td class="c2">
                                        <c:out value="${port}"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="c1">
                                         <fmt:message key="setup.ldap.server.basedn" />:
                                    </td>
                                    <td class="c2">
                                        <c:out value="${baseDN}"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="c1">
                                        <fmt:message key="setup.ldap.server.admindn" />:
                                    </td>
                                    <td class="c2">
                                        <c:out value="${adminDN}"/>
                                    </td>
                                </tr>
                            </tbody>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2" align="center">
                            <input type="submit" name="ldapedit" value="<fmt:message key="server.properties.edit" />">
                        </td>
                    </tr>
                </c:if>
            </tbody>
            </table>
        </div>
    </form>

</body>
</html>

