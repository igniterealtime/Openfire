<!--
  - Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  - http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->
<%@ page errorPage="error.jsp" %>
<%@ page import="org.igniterealtime.openfire.plugins.externalservicediscovery.Service" %>
<%@ page import="org.igniterealtime.openfire.plugins.externalservicediscovery.ServiceManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.io.UnsupportedEncodingException" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.database.SequenceManager" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%!
    public String getDecodedParameter( HttpServletRequest request, String name ) throws UnsupportedEncodingException
    {
        String value = request.getParameter( name );
        if ( value != null )
        {
            return URLDecoder.decode( value.trim(), "UTF-8" );
        }
        else
        {
            return null;
        }
    }
%>
<%
    String deleteService = getDecodedParameter( request, "deleteService" );
    String addService = getDecodedParameter( request, "addService" );
    String name = getDecodedParameter( request, "name" );
    String host = getDecodedParameter( request, "host" );
    String port = getDecodedParameter( request, "port" );
    String transport = getDecodedParameter( request, "transport" );
    String type = getDecodedParameter( request, "type" );
    String credentials = getDecodedParameter( request, "credentials" );
    String username = getDecodedParameter( request, "username" );
    String password = getDecodedParameter( request, "password" );
    String secret = getDecodedParameter( request, "secret" );

    Map<String, String> errors = new HashMap<>();

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter( request, "csrf");

    if (deleteService != null || addService != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            deleteService = null;
            addService = null;
            errors.put( "csrf", "Invalid CSRF value. Reload the page and try again." );
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if ( errors.isEmpty() )
    {
        if ( addService != null )
        {
            if ( host == null || host.isEmpty() )
            {
                errors.put( "host", "empty" );
            }

            if ( type == null || type.isEmpty() )
            {
                errors.put( "type", "empty" );
            }

            Integer portValue = null;

            if ( port != null && !port.isEmpty() )
            {
                try
                {
                    portValue = Integer.parseInt( port );
                }
                catch ( NumberFormatException e )
                {
                    errors.put( "port", "not a number" );
                }
            }

            if ( "userpass".equals( credentials ) )
            {
                if ( username == null || username.isEmpty() )
                {
                    errors.put( "username", "empty" );
                }

                if ( password == null || password.isEmpty() )
                {
                    errors.put( "password", "empty" );
                }
            }

            if ( "sharedsecret".equals( credentials ) )
            {
                if ( secret == null || secret.isEmpty() )
                {
                    errors.put( "sharedsecret", "empty" );
                }
            }

            if ( errors.isEmpty() )
            {
                final Service service;
                if ( "userpass".equals( credentials ) )
                {
                    service = new Service( name, host, portValue, transport, type, username, password );
                }
                else if ( "sharedsecret".equals( credentials ) )
                {
                    service = new Service( name, host, portValue, transport, type, secret );
                }
                else
                {
                    service = new Service( name, host, portValue, transport, type );
                }

                ServiceManager.getInstance().addService( service );
                response.sendRedirect( "external-service-discovery-config.jsp?success=addService" );
                return;
            }
        }

        if ( deleteService != null )
        {
            for ( final Service service : ServiceManager.getInstance().getAllServices() )
            {
                if ( Long.toString( service.getDatabaseId() ).equals( deleteService ) )
                {
                    ServiceManager.getInstance().removeService( service );
                    response.sendRedirect("external-service-discovery-config.jsp?success=deleteService");
                    return;
                }
            }
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "services", ServiceManager.getInstance().getAllServices() );
%>
<html>
<head>
    <title><fmt:message key="external-service-discovery-config.title"/></title>
    <meta name="pageID" content="external-service-discovery-config"/>

    <script>
        function check( id )
        {
            document.getElementById('nocredentials').checked = ( id === 'nocredentials' );
            document.getElementById('userpass').checked = ( id === 'userpass' );
            document.getElementById('sharedsecret').checked = ( id === 'sharedsecret' );
        }
    </script>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'csrf'}">
                <fmt:message key="global.csrf.failed" />
            </c:when>

            <c:when test="${err.key eq 'host'}">
                <fmt:message key="external-service-discovery-config.host-required"/>
            </c:when>

            <c:when test="${err.key eq 'type'}">
                <fmt:message key="external-service-discovery-config.type-required"/>
            </c:when>

            <c:when test="${err.key eq 'port'}">
                <fmt:message key="external-service-discovery-config.port-number"/>
            </c:when>

            <c:when test="${err.key eq 'username'}">
                <fmt:message key="external-service-discovery-config.credentials.username-required"/>
            </c:when>

            <c:when test="${err.key eq 'password'}">
                <fmt:message key="external-service-discovery-config.credentials.password-required"/>
            </c:when>

            <c:when test="${err.key eq 'sharedsecret'}">
                <fmt:message key="external-service-discovery-config.credentials.sharedsecret-required"/>
            </c:when>

            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<!-- Display success report, but only if there were no errors. -->
<c:if test="${not empty param['success'] and empty errors}">
    <admin:infoBox type="success">
        <c:choose>
            <c:when test="${param['success'] eq 'deleteService'}">
                <fmt:message key="external-service-discovery-config.success-delete"/>
            </c:when>
            <c:when test="${param['success'] eq 'addService'}">
                <fmt:message key="external-service-discovery-config.success-add"/>
            </c:when>
        </c:choose>
    </admin:infoBox>
</c:if>

<p>
    <fmt:message key="external-service-discovery-config.descr"/>
</p>

<div class="jive-table">
    <table cellpadding="0" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th>&nbsp;</th>
            <th nowrap><fmt:message key="external-service-discovery-config.host" /></th>
            <th nowrap><fmt:message key="external-service-discovery-config.port" /></th>
            <th nowrap><fmt:message key="external-service-discovery-config.name" /></th>
            <th nowrap><fmt:message key="external-service-discovery-config.transport" /></th>
            <th nowrap><fmt:message key="external-service-discovery-config.type" /></th>
            <th nowrap><fmt:message key="external-service-discovery-config.credentials" /></th>
            <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
            <c:choose>
                <c:when test="${empty services}">
                    <tr>
                        <td align="center" colspan="7"><fmt:message key="external-service-discovery-config.table.empty" /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach var="service" items="${services}">
                        <tr>
                            <td>&nbsp;</td>
                            <td><c:out value="${service.host}"/></td>
                            <td><c:out value="${service.port}"/></td>
                            <td><c:out value="${service.name}"/></td>
                            <td><c:out value="${service.transport}"/></td>
                            <td><c:out value="${service.type}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty service.rawUsername}">
                                        <fmt:message key="external-service-discovery-config.credentials.using-userpass" /> <c:out value="service.rawUsername"/>
                                    </c:when>
                                    <c:when test="${not empty service.sharedSecret}">
                                        <fmt:message key="external-service-discovery-config.credentials.using-sharedsecret" />
                                    </c:when>
                                    <c:otherwise>
                                        <fmt:message key="external-service-discovery-config.credentials.using-nocredentials" />
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td width="1%" align="center">
                                <a href="external-service-discovery-config.jsp?deleteService=${service.databaseId}&csrf=${csrf}" title="<fmt:message key="global.click_delete" />"
                                ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.click_delete" />"></a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </tbody>
    </table>
</div>

<br/>
<p><fmt:message key="external-service-discovery-config.add-service.descr"/></p>

<form action="external-service-discovery-config.jsp">

    <fmt:message key="external-service-discovery-config.add-service.title" var="add_title"/>
    <admin:contentBox title="${add_title}">

        <input type="hidden" name="csrf" value="${csrf}">

        <table cellpadding="0" cellspacing="0" border="0" >
            <tr>
                <td nowrap><fmt:message key="external-service-discovery-config.host" />*:</td>
                <td><input type="text" id="host" name="host" size="50" maxlength="255"></td>
            </tr>
            <tr>
                <td nowrap><fmt:message key="external-service-discovery-config.port" />:</td>
                <td><input type="text" id="port" name="port" size="7" maxlength="5"></td>
            </tr>
            <tr>
                <td nowrap><fmt:message key="external-service-discovery-config.name" />:</td>
                <td><input type="text" id="name" name="name" size="75" maxlength="255"></td>
            </tr>
            <tr>
                <td nowrap><fmt:message key="external-service-discovery-config.transport" />:</td>
                <td>
                    <input type="radio" id="tcp" name="transport" value="tcp"><label for="tcp"><fmt:message key="external-service-discovery-config.tcp"/></label>
                </td>
            </tr>
            <tr>
                <td nowrap>&nbsp;</td>
                <td>
                    <input type="radio" id="udp" name="transport" value="tcp"><label for="udp"><fmt:message key="external-service-discovery-config.udp"/></label>
                </td>
            </tr>
            <tr>
                <td nowrap><fmt:message key="external-service-discovery-config.type" />*:</td>
                <td>
                    <input type="radio" id="stun" name="type" value="stun"><label for="stun"><fmt:message key="external-service-discovery-config.stun"/></label>
                </td>
            </tr>
            <tr>
                <td nowrap>&nbsp;</td>
                <td>
                    <input type="radio" id="turn" name="type" value="turn"><label for="turn"><fmt:message key="external-service-discovery-config.turn"/></label>
                </td>
            </tr>
            <tr>
                <td nowrap>&nbsp;</td>
                <td>
                    <input type="radio" id="turns" name="type" value="turns"><label for="turns"><fmt:message key="external-service-discovery-config.turns"/></label>
                </td>
            </tr>
            <tr>
                <td><fmt:message key="external-service-discovery-config.credentials"/>*:</td>
                <td>
                    <input type="radio" id="nocredentials" name="credentials" value="nocredentials" checked><label for="nocredentials"><fmt:message key="external-service-discovery-config.credentials.nocredentials"/></label>
                </td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <input type="radio" id="userpass" name="credentials" value="userpass"><label for="userpass"><fmt:message key="external-service-discovery-config.credentials.userpass"/></label>
                    <label for="username"><fmt:message key="external-service-discovery-config.credentials.username"/></label>: <input type="text" id="username" name="username" size="25" maxlength="255" onclick="check('userpass')">
                    <label for="password"><fmt:message key="external-service-discovery-config.credentials.password"/></label>: <input type="password" id="password" name="password" size="25" maxlength="1024" onclick="check('userpass')">
                </td>
            </tr>
            <tr>
                <td>&nbsp;</td>
                <td>
                    <input type="radio" id="sharedsecret" name="credentials" value="sharedsecret"><label for="sharedsecret"><fmt:message key="external-service-discovery-config.credentials.sharedsecret"/></label>
                    <input type="text" id="secret" name="secret" size="40" maxlength="1024" onclick="check('sharedsecret')">
                </td>
            </tr>
        </table>

    </admin:contentBox>

    <input type="submit" name="addService" value="<fmt:message key="external-service-discovery-config.add-service.button-caption" />">
</form>

</body>
</html>
