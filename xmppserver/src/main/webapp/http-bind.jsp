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
<%@ page import="org.jivesoftware.openfire.http.FlashCrossDomainServlet" %>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.Log" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionManagerImpl" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%!
    HttpBindManager serverManager = HttpBindManager.getInstance();

    Map<String, String> handleUpdate( HttpServletRequest request )
    {
        final Map<String, String> errorMap = new HashMap<>();
        final boolean isEnabled = ParamUtils.getBooleanParameter( request, "httpBindEnabled", serverManager.isHttpBindEnabled() );

        if ( isEnabled )
        {
            final int requestedPort = ParamUtils.getIntParameter( request, "port", serverManager.getHttpBindUnsecurePort() );
            final int requestedSecurePort = ParamUtils.getIntParameter( request, "securePort", serverManager.getHttpBindSecurePort() );
            final boolean isCORSEnabled = ParamUtils.getBooleanParameter( request, "CORSEnabled", serverManager.isCORSEnabled() );
            final boolean isXFFEnabled = ParamUtils.getBooleanParameter( request, "XFFEnabled", serverManager.isXFFEnabled() );
            final String CORSDomains = ParamUtils.getParameter( request, "CORSDomains", true );

            final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
            final ConnectionConfiguration configuration = manager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();
            final String mutualAuthenticationText = ParamUtils.getParameter( request, "mutualauthentication", true );
            final Connection.ClientAuth mutualAuthentication;
            if ( mutualAuthenticationText == null || mutualAuthenticationText.isEmpty() ) {
                mutualAuthentication = configuration.getClientAuth();
            } else {
                mutualAuthentication = Connection.ClientAuth.valueOf( mutualAuthenticationText );
            }

            try
            {
                serverManager.setHttpBindPorts( requestedPort, requestedSecurePort );
                serverManager.setCORSEnabled( isCORSEnabled );
                serverManager.setCORSAllowOrigin( CORSDomains );
                serverManager.setXFFEnabled( isXFFEnabled );
                serverManager.setXFFHeader( ParamUtils.getParameter( request, "XFFHeader" ) );
                serverManager.setXFFServerHeader( ParamUtils.getParameter( request, "XFFServerHeader" ) );
                serverManager.setXFFHostHeader( ParamUtils.getParameter( request, "XFFHostHeader" ) );
                serverManager.setXFFHostName( ParamUtils.getParameter( request, "XFFHostName" ) );
                manager.getListener( ConnectionType.BOSH_C2S, true ).setClientAuth( mutualAuthentication );
            }
            catch ( Exception e )
            {
                Log.error( "An error has occured configuring the HTTP binding ports", e );
                errorMap.put( "port", e.getMessage() );
            }
            boolean isScriptSyntaxEnabled = ParamUtils.getBooleanParameter( request, "scriptSyntaxEnabled", serverManager.isScriptSyntaxEnabled() );
            serverManager.setScriptSyntaxEnabled( isScriptSyntaxEnabled );
        }
        if ( errorMap.isEmpty() )
        {
            serverManager.setHttpBindEnabled( isEnabled );
        }
        return errorMap;
    }
%>
<%
    final Map<String, String> errorMap = new HashMap<>();
    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf" );
    String csrfParam = ParamUtils.getParameter( request, "csrf" );
    if ( request.getParameter( "update" ) != null )
    {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) )
        {
            errorMap.put( "csrf", "CSRF Failure!" );
        }
        else
        {
            errorMap.putAll( handleUpdate( request ) );
            // Log the event
            webManager.logEvent( "updated HTTP bind settings", null );
        }
    }

    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie( request, response, "csrf", csrfParam, -1 );
    final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
    final ConnectionConfiguration configuration = manager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();

    pageContext.setAttribute( "csrf", csrfParam );
    pageContext.setAttribute( "errors", errorMap );
    pageContext.setAttribute( "serverManager", serverManager );
    pageContext.setAttribute( "crossDomainContent", FlashCrossDomainServlet.getCrossDomainContent() );
    pageContext.setAttribute( "configuration", configuration );
%>

<html>
<head>
    <title>
        <fmt:message key="httpbind.settings.title"/>
    </title>
    <meta name="pageID" content="http-bind"/>
    <script type="text/javascript">
        var enabled = ${serverManager.httpBindEnabled ? 'true' : 'false'};
        var setEnabled = function() {
            $("port").disabled = !enabled;
            $("securePort").disabled = !enabled;
            $("rb03").disabled = !enabled;
            $("rb04").disabled = !enabled;
            $("rb05").disabled = !enabled;
            $("rb06").disabled = !enabled;
            $("rb07").disabled = !enabled;
            $("rb08").disabled = !enabled;
            $("CORSDomains").disabled = !enabled;
            $("XFFHeader").disabled = !enabled;
            $("XFFServerHeader").disabled = !enabled;
            $("XFFHostHeader").disabled = !enabled;
            $("XFFHostName").disabled = !enabled;
            $("crossdomain").disabled = !enabled;
        };
        window.onload = setTimeout("setEnabled()", 500);
    </script>
</head>
<body>
<p>
    <fmt:message key="httpbind.settings.info"/>
</p>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'port'}"><fmt:message key="httpbind.settings.error.port"/></c:when>
            <c:when test="${err.key eq 'missingMotdMessage'}"><fmt:message key="motd.message.missing"/></c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="httpbind.settings.error.general"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<form action="http-bind.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="httpbind.settings.title" var="general_settings_boxtitle"/>
    <admin:contentBox title="${general_settings_boxtitle}">
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="true" id="rb02" onclick="enabled = true; setEnabled();" ${serverManager.httpBindEnabled ? "checked" : ""}>
                    </td>
                    <td width="99%" colspan="2">
                        <label for="rb02"><b><fmt:message key="httpbind.settings.label_enable"/></b> - <fmt:message key="httpbind.settings.label_enable_info"/></label>

                        <table border="0">
                             <tr>
                                <td><label for="port"><fmt:message key="httpbind.settings.vanilla_port"/></label></td>
                                <td><input id="port" type="text" size="5" maxlength="10" name="port" value="${serverManager.httpBindUnsecurePort}" /></td>
                                <td>( <c:out value="${serverManager.httpBindUnsecureAddress}"/> )</td>
                            </tr>
                            <tr>
                                <td><label for="securePort"><fmt:message key="httpbind.settings.secure_port"/></label></td>
                                <td><input id="securePort" type="text" size="5" maxlength="10" name="securePort" value="${serverManager.httpBindSecurePort}" /></td>
                                <td>( <c:out value="${serverManager.httpBindSecureAddress}"/> )</td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr valign="top">
                    <td width="1%" nowrap>
                        <input type="radio" name="httpBindEnabled" value="false" id="rb01" onclick="enabled = false; setEnabled();" ${serverManager.httpBindEnabled ? "" : "checked"} %>
                    </td>
                    <td width="99%" colspan="2">
                        <label for="rb01"><b><fmt:message key="httpbind.settings.label_disable"/></b> - <fmt:message key="httpbind.settings.label_disable_info"/></label>
                    </td>
                </tr>
            </tbody>
        </table>
    </admin:contentBox>

    <fmt:message key="httpbind.settings.clientauth.boxtitle" var="clientauthboxtitle"/>
    <admin:contentBox title="${clientauthboxtitle}">
        <p><fmt:message key="httpbind.settings.clientauth.info"/></p>
        <table cellpadding="3" cellspacing="0" border="0" class="tlsconfig">
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="disabled" id="mutualauthentication-disabled" ${configuration.clientAuth.name() eq 'disabled' ? 'checked' : ''}/>
                    <label for="mutualauthentication-disabled"><fmt:message key="httpbind.settings.clientauth.label_disabled"/></label>
                </td>
            </tr>
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="wanted" id="mutualauthentication-wanted" ${configuration.clientAuth.name() eq 'wanted' ? 'checked' : ''}/>
                    <label for="mutualauthentication-wanted"><fmt:message key="httpbind.settings.clientauth.label_wanted"/></label>
                </td>
            </tr>
            <tr valign="middle">
                <td>
                    <input type="radio" name="mutualauthentication" value="needed" id="mutualauthentication-needed" ${configuration.clientAuth.name() eq 'needed' ? 'checked' : ''}/>
                    <label for="mutualauthentication-needed"><fmt:message key="httpbind.settings.clientauth.label_needed"/></label>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="httpbind.settings.script.group" var="script_boxtitle"/>
    <admin:contentBox title="${script_boxtitle}">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="middle">
                <td width="1%" nowrap><input type="radio" name="scriptSyntaxEnabled" value="true" id="rb03" ${serverManager.scriptSyntaxEnabled ? "checked" : ""}></td>
                <td width="99%"><label for="rb03"><b><fmt:message key="httpbind.settings.script.label_enable" /></b> - <fmt:message key="httpbind.settings.script.label_enable_info" /></label></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><input type="radio" name="scriptSyntaxEnabled" value="false" id="rb04" ${serverManager.scriptSyntaxEnabled ? "" : "checked"}></td>
                <td width="99%"><label for="rb04"><b><fmt:message key="httpbind.settings.script.label_disable" /></b> - <fmt:message key="httpbind.settings.script.label_disable_info" /></label></td>
            </tr>
        </tbody>
        </table>
    </admin:contentBox>

    <!-- CORS -->
    <fmt:message key="httpbind.settings.cors.group" var="cors_boxtitle"/>
    <admin:contentBox title="${cors_boxtitle}">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="CORSEnabled" value="true" id="rb05" ${serverManager.CORSEnabled ? "checked" : ""}>
                </td>
                <td width="99%">
                    <label for="rb05"><b><fmt:message key="httpbind.settings.cors.label_enable"/></b> - <fmt:message key="httpbind.settings.cors.label_enable_info"/></label>
                    <table border="0">
                        <tr><td><label for="CORSDomains"><fmt:message key="httpbind.settings.cors.domain_list"/></label></td></tr>
                        <tr><td><input id="CORSDomains" type="text" size="80" name="CORSDomains" value="${fn:escapeXml(serverManager.CORSAllowOrigin)}"></td></tr>
                    </table>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="CORSEnabled" value="false" id="rb06" ${serverManager.CORSEnabled ? "" : "checked"}>
                </td>
                <td width="99%">
                    <label for="rb06"><b><fmt:message key="httpbind.settings.cors.label_disable"/></b> - <fmt:message key="httpbind.settings.cors.label_disable_info"/></label>
                </td>
            </tr>
        </tbody>
        </table>
    </admin:contentBox>
    <!-- CORS -->
    
    <!-- XFF -->
    <fmt:message key="httpbind.settings.xff.group" var="xff_boxtitle"/>
    <admin:contentBox title="${xff_boxtitle}">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="XFFEnabled" value="true" id="rb07" ${serverManager.XFFEnabled ? "checked" : ""}>
                </td>
                <td width="99%">
                    <label for="rb07"><b><fmt:message key="httpbind.settings.xff.label_enable"/></b> - <fmt:message key="httpbind.settings.xff.label_enable_info"/></label>
                    <table border="0">
                        <tr>
                            <td><label for="XFFHeader"><fmt:message key="httpbind.settings.xff.forwarded_for"/></label></td>
                            <td><input id="XFFHeader" type="text" size="40" name="XFFHeader" value="${fn:escapeXml(serverManager.XFFHeader == null ? "" : serverManager.XFFHeader)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFServerHeader"><fmt:message key="httpbind.settings.xff.forwarded_server"/></label></td>
                            <td><input id="XFFServerHeader" type="text" size="40" name="XFFServerHeader" value="${fn:escapeXml(serverManager.XFFServerHeader == null ? "" : serverManager.XFFServerHeader)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostHeader"><fmt:message key="httpbind.settings.xff.forwarded_host"/></label></td>
                            <td><input id="XFFHostHeader" type="text" size="40" name="XFFHostHeader" value="${fn:escapeXml(serverManager.XFFHostHeader == null ? "" : serverManager.XFFHostHeader)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostName"><fmt:message key="httpbind.settings.xff.host_name"/></label></td>
                            <td><input id="XFFHostName" type="text" size="40" name="XFFHostName" value="${fn:escapeXml(serverManager.XFFHostName == null ? "" : serverManager.XFFHostName)}"></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="XFFEnabled" value="false" id="rb08" ${serverManager.XFFEnabled ? "" : "checked"}>
                </td>
                <td width="99%">
                    <label for="rb08"><b><fmt:message key="httpbind.settings.xff.label_disable"/></b> - <fmt:message key="httpbind.settings.xff.label_disable_info"/></label>
                </td>
            </tr>
        </tbody>
        </table>
    </admin:contentBox>
    <!-- XFF -->

    <fmt:message key="httpbind.settings.crossdomain.group" var="crossdomain_boxtitle"/>
    <admin:contentBox title="${crossdomain_boxtitle}">
        <p><fmt:message key="httpbind.settings.crossdomain.info.general" /></p>
        <p><fmt:message key="httpbind.settings.crossdomain.info.override"><fmt:param value="<tt>&lt;openfireHome&gt;/conf/crossdomain.xml</tt>" /></fmt:message></p>
        <p><fmt:message key="httpbind.settings.crossdomain.info.policy" /></p>
        <textarea id="crossdomain" cols="120" rows="10" wrap="virtual" readonly="readonly"><c:out value="${crossDomainContent}"/></textarea>
    </admin:contentBox>
    
    <input type="submit" id="settingsUpdate" name="update" value="<fmt:message key="global.save_settings" />">
</form>
</body>
</html>
