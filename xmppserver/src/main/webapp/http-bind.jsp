<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.Connection" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionConfiguration" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.spi.ConnectionType" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.openfire.ConnectionManager" %>
<%@ page contentType="text/html;charset=UTF-8" %>

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
            final int requestedPort = ParamUtils.getIntParameter( request, "port", HttpBindManager.HTTP_BIND_PORT.getValue() );
            final int requestedSecurePort = ParamUtils.getIntParameter( request, "securePort", HttpBindManager.HTTP_BIND_SECURE_PORT.getValue() );
            final boolean isCORSEnabled = ParamUtils.getBooleanParameter( request, "CORSEnabled", HttpBindManager.HTTP_BIND_CORS_ENABLED.getValue() );
            final boolean isXFFEnabled = ParamUtils.getBooleanParameter( request, "XFFEnabled", HttpBindManager.HTTP_BIND_FORWARDED.getValue() );
            final boolean isCSPEnabled = ParamUtils.getBooleanParameter( request, "CSPEnabled", HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_ENABLED.getValue() );
            final String CORSDomains = ParamUtils.getParameter( request, "CORSDomains", true );
            final String cspValue = ParamUtils.getParameter( request, "CSPValue", true);

            final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();
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
                HttpBindManager.HTTP_BIND_PORT.setValue(requestedPort);
                HttpBindManager.HTTP_BIND_SECURE_PORT.setValue(requestedSecurePort);
                HttpBindManager.HTTP_BIND_CORS_ENABLED.setValue(isCORSEnabled);

                final Set<String> update = new HashSet<>();
                if (CORSDomains == null || CORSDomains.trim().isEmpty())
                    update.add(HttpBindManager.HTTP_BIND_CORS_ALLOW_ORIGIN_ALL);
                else {
                    update.addAll(Arrays.asList(CORSDomains.replaceAll("\\s+", "").split(",")));
                }
                HttpBindManager.HTTP_BIND_ALLOWED_ORIGINS.setValue(update);
                HttpBindManager.HTTP_BIND_FORWARDED.setValue( isXFFEnabled );

                final String xffHeader = ParamUtils.getParameter( request, "XFFHeader" );
                if (xffHeader == null || xffHeader.trim().isEmpty()) {
                    HttpBindManager.HTTP_BIND_FORWARDED_FOR.setValue(null);
                } else {
                    HttpBindManager.HTTP_BIND_FORWARDED_FOR.setValue(xffHeader.trim());
                }

                final String xffServerHeader = ParamUtils.getParameter( request, "XFFServerHeader" );
                if (xffServerHeader == null || xffServerHeader.trim().isEmpty()) {
                    HttpBindManager.HTTP_BIND_FORWARDED_SERVER.setValue(null);
                } else {
                    HttpBindManager.HTTP_BIND_FORWARDED_SERVER.setValue(xffServerHeader.trim());
                }

                final String xffHostHeader = ParamUtils.getParameter( request, "xffHostHeader" );
                if (xffHostHeader == null || xffHostHeader.trim().isEmpty()) {
                    HttpBindManager.HTTP_BIND_FORWARDED_HOST.setValue(null);
                } else {
                    HttpBindManager.HTTP_BIND_FORWARDED_HOST.setValue(xffHostHeader.trim());
                }

                final String name = ParamUtils.getParameter( request, "XFFHostName" );
                if (name == null || name.trim().isEmpty()) {
                    HttpBindManager.HTTP_BIND_FORWARDED_HOST_NAME.setValue(null);
                } else {
                    HttpBindManager.HTTP_BIND_FORWARDED_HOST_NAME.setValue(name.trim());
                }

                HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_ENABLED.setValue( isCSPEnabled );
                if (cspValue == null || cspValue.trim().isEmpty()) {
                    HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_RESPONSEVALUE.setValue(null);
                } else {
                    HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_RESPONSEVALUE.setValue(cspValue.trim());
                }

                manager.getListener( ConnectionType.BOSH_C2S, true ).setClientAuth( mutualAuthentication );
            }
            catch ( Exception e )
            {
                LoggerFactory.getLogger("http-bind.jsp").debug("An error has occurred configuring the HTTP binding ports.", e);
                errorMap.put( "port", e.getMessage() );
            }
            boolean isScriptSyntaxEnabled = ParamUtils.getBooleanParameter( request, "scriptSyntaxEnabled", serverManager.isScriptSyntaxEnabled() );
            serverManager.setScriptSyntaxEnabled( isScriptSyntaxEnabled );
        }
        if ( errorMap.isEmpty() )
        {
            HttpBindManager.HTTP_BIND_ENABLED.setValue(isEnabled);
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
    final ConnectionManager manager = XMPPServer.getInstance().getConnectionManager();
    final ConnectionConfiguration configuration = manager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();

    pageContext.setAttribute( "csrf", csrfParam );
    pageContext.setAttribute( "errors", errorMap );
    pageContext.setAttribute( "serverManager", serverManager );
    pageContext.setAttribute( "configuration", configuration );
%>

<html>
<head>
    <title>
        <fmt:message key="httpbind.settings.title"/>
    </title>
    <meta name="pageID" content="http-bind"/>
    <script>
        let enabled = ${serverManager.httpBindEnabled ? 'true' : 'false'};
        let setEnabled = function() {
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
        };
        window.onload = setTimeout(setEnabled, 500);
    </script>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'port'}"><fmt:message key="httpbind.settings.error.port"/></c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="httpbind.settings.error.general"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<c:if test="${serverManager.httpBindEnabled}">
    <admin:infobox type="info">
        <p><fmt:message key="httpbind.settings.infobox-enabled.title"/></p>
        <p>
            <fmt:message key="httpbind.settings.infobox-enabled.websocket-endpoints">
                <fmt:param><c:out value="${serverManager.websocketUnsecureAddress}"/></fmt:param>
                <fmt:param><c:out value="${serverManager.websocketSecureAddress}"/></fmt:param>
            </fmt:message>
        </p>
        <p>
            <fmt:message key="httpbind.settings.infobox-enabled.bosh-endpoints">
                <fmt:param><c:out value="${serverManager.httpBindUnsecureAddress}"/></fmt:param>
                <fmt:param><c:out value="${serverManager.httpBindSecureAddress}"/></fmt:param>
            </fmt:message>
        </p>
    </admin:infobox>
</c:if>

<p>
    <fmt:message key="httpbind.settings.info"/>
</p>

<form action="http-bind.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="httpbind.settings.title" var="general_settings_boxtitle"/>
    <admin:contentBox title="${general_settings_boxtitle}">
        <table>
            <tbody>
                <tr>
                    <td style="width: 1%; white-space: nowrap; vertical-align: top">
                        <input type="radio" name="httpBindEnabled" value="true" id="rb02" onclick="enabled = true; setEnabled();" ${serverManager.httpBindEnabled ? "checked" : ""}>
                    </td>
                    <td colspan="2">
                        <label for="rb02"><b><fmt:message key="httpbind.settings.label_enable"/></b> - <fmt:message key="httpbind.settings.label_enable_info"/></label>

                        <table>
                             <tr>
                                <td><label for="port"><fmt:message key="httpbind.settings.vanilla_port"/></label></td>
                                <td><input id="port" type="text" size="5" maxlength="10" name="port" value="${HttpBindManager.HTTP_BIND_PORT.value}" /></td>
                            </tr>
                            <tr>
                                <td><label for="securePort"><fmt:message key="httpbind.settings.secure_port"/></label></td>
                                <td><input id="securePort" type="text" size="5" maxlength="10" name="securePort" value="${HttpBindManager.HTTP_BIND_SECURE_PORT.value}" /></td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td style="width: 1%; white-space: nowrap; vertical-align: top">
                        <input type="radio" name="httpBindEnabled" value="false" id="rb01" onclick="enabled = false; setEnabled();" ${serverManager.httpBindEnabled ? "" : "checked"}/>
                    </td>
                    <td colspan="2">
                        <label for="rb01"><b><fmt:message key="httpbind.settings.label_disable"/></b> - <fmt:message key="httpbind.settings.label_disable_info"/></label>
                    </td>
                </tr>
            </tbody>
        </table>
    </admin:contentBox>

    <fmt:message key="httpbind.settings.clientauth.boxtitle" var="clientauthboxtitle"/>
    <admin:contentBox title="${clientauthboxtitle}">
        <p><fmt:message key="httpbind.settings.clientauth.info"/></p>
        <table class="tlsconfig">
            <tr>
                <td>
                    <input type="radio" name="mutualauthentication" value="disabled" id="mutualauthentication-disabled" ${configuration.clientAuth.name() eq 'disabled' ? 'checked' : ''}/>
                    <label for="mutualauthentication-disabled"><fmt:message key="httpbind.settings.clientauth.label_disabled"/></label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="mutualauthentication" value="wanted" id="mutualauthentication-wanted" ${configuration.clientAuth.name() eq 'wanted' ? 'checked' : ''}/>
                    <label for="mutualauthentication-wanted"><fmt:message key="httpbind.settings.clientauth.label_wanted"/></label>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="radio" name="mutualauthentication" value="needed" id="mutualauthentication-needed" ${configuration.clientAuth.name() eq 'needed' ? 'checked' : ''}/>
                    <label for="mutualauthentication-needed"><fmt:message key="httpbind.settings.clientauth.label_needed"/></label>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="httpbind.settings.script.group" var="script_boxtitle"/>
    <admin:contentBox title="${script_boxtitle}">
        <table>
        <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap"><input type="radio" name="scriptSyntaxEnabled" value="true" id="rb03" ${serverManager.scriptSyntaxEnabled ? "checked" : ""}></td>
                <td><label for="rb03"><b><fmt:message key="httpbind.settings.script.label_enable" /></b> - <fmt:message key="httpbind.settings.script.label_enable_info" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><input type="radio" name="scriptSyntaxEnabled" value="false" id="rb04" ${serverManager.scriptSyntaxEnabled ? "" : "checked"}></td>
                <td><label for="rb04"><b><fmt:message key="httpbind.settings.script.label_disable" /></b> - <fmt:message key="httpbind.settings.script.label_disable_info" /></label></td>
            </tr>
        </tbody>
        </table>
    </admin:contentBox>

    <!-- CORS -->
    <fmt:message key="httpbind.settings.cors.group" var="cors_boxtitle"/>
    <admin:contentBox title="${cors_boxtitle}">
        <table>
        <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="CORSEnabled" value="true" id="rb05" ${HttpBindManager.HTTP_BIND_CORS_ENABLED.value ? "checked" : ""}>
                </td>
                <td>
                    <label for="rb05"><b><fmt:message key="httpbind.settings.cors.label_enable"/></b> - <fmt:message key="httpbind.settings.cors.label_enable_info"/></label>
                    <table>
                        <tr><td><label for="CORSDomains"><fmt:message key="httpbind.settings.cors.domain_list"/></label></td></tr>
                        <tr><td><input id="CORSDomains" type="text" size="80" name="CORSDomains" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_ALLOWED_ORIGINS.valueAsSaved)}"></td></tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="CORSEnabled" value="false" id="rb06" ${HttpBindManager.HTTP_BIND_CORS_ENABLED.value ? "" : "checked"}>
                </td>
                <td>
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
        <table>
        <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="XFFEnabled" value="true" id="rb07" ${HttpBindManager.HTTP_BIND_FORWARDED.value ? "checked" : ""}>
                </td>
                <td>
                    <label for="rb07"><b><fmt:message key="httpbind.settings.xff.label_enable"/></b> - <fmt:message key="httpbind.settings.xff.label_enable_info"/></label>
                    <table>
                        <tr>
                            <td><label for="XFFHeader"><fmt:message key="httpbind.settings.xff.forwarded_for"/></label></td>
                            <td><input id="XFFHeader" type="text" size="40" name="XFFHeader" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_FORWARDED_FOR.value == null ? "" : HttpBindManager.HTTP_BIND_FORWARDED_FOR.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFServerHeader"><fmt:message key="httpbind.settings.xff.forwarded_server"/></label></td>
                            <td><input id="XFFServerHeader" type="text" size="40" name="XFFServerHeader" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_FORWARDED_SERVER.value == null ? "" : HttpBindManager.HTTP_BIND_FORWARDED_SERVER.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostHeader"><fmt:message key="httpbind.settings.xff.forwarded_host"/></label></td>
                            <td><input id="XFFHostHeader" type="text" size="40" name="XFFHostHeader" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_FORWARDED_HOST.value == null ? "" : HttpBindManager.HTTP_BIND_FORWARDED_HOST.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostName"><fmt:message key="httpbind.settings.xff.host_name"/></label></td>
                            <td><input id="XFFHostName" type="text" size="40" name="XFFHostName" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_FORWARDED_HOST_NAME.value == null ? "" : HttpBindManager.HTTP_BIND_FORWARDED_HOST_NAME.value)}"></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="XFFEnabled" value="false" id="rb08" ${HttpBindManager.HTTP_BIND_FORWARDED.value ? "" : "checked"}>
                </td>
                <td>
                    <label for="rb08"><b><fmt:message key="httpbind.settings.xff.label_disable"/></b> - <fmt:message key="httpbind.settings.xff.label_disable_info"/></label>
                </td>
            </tr>
        </tbody>
        </table>
    </admin:contentBox>
    <!-- XFF -->

    <!-- Content-Security-Policy -->
    <fmt:message key="httpbind.settings.csp.group" var="csp_boxtitle"/>
    <admin:contentBox title="${csp_boxtitle}">
        <table>
            <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="CSPEnabled" value="true" id="rb09" ${HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_ENABLED.value ? "checked" : ""}>
                </td>
                <td>
                    <label for="rb09"><b><fmt:message key="httpbind.settings.csp.label_enable"/></b> - <fmt:message key="httpbind.settings.csp.label_enable_info"/></label>
                    <table>
                        <tr><td><label for="CSPValue"><fmt:message key="httpbind.settings.csp.value"/></label></td></tr>
                        <tr><td><input id="CSPValue" type="text" size="80" name="CSPValue" value="${fn:escapeXml(HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_RESPONSEVALUE.value == null ? "" : HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_RESPONSEVALUE.value)}"></td></tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="CSPEnabled" value="false" id="rb10" ${HttpBindManager.HTTP_BIND_CONTENT_SECURITY_POLICY_ENABLED.value ? "" : "checked"}>
                </td>
                <td>
                    <label for="rb10"><b><fmt:message key="httpbind.settings.csp.label_disable"/></b> - <fmt:message key="httpbind.settings.csp.label_disable_info"/></label>
                </td>
            </tr>
            </tbody>
        </table>
    </admin:contentBox>
    <!-- Content-Security-Policy -->

    <input type="submit" id="settingsUpdate" name="update" value="<fmt:message key="global.save_settings" />">
</form>
</body>
</html>
