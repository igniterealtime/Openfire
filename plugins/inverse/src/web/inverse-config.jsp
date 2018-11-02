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
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.util.List" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoom" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="org.igniterealtime.openfire.plugin.inverse.Language" %>
<%@ page import="org.igniterealtime.openfire.plugin.inverse.InversePlugin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    String update = request.getParameter("update");
    String success = request.getParameter("success");
    String error = null;

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter( request, "csrf");

    if (update != null ) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = null;
            error = "csrf";
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    HttpBindManager httpBindManager = HttpBindManager.getInstance();

    if ( error == null && update != null )
    {
        if ( "general".equals( update ) )
        {
            if ( ParamUtils.getParameter( request, "default_domain" ) != null )
            {
                JiveGlobals.setProperty( "inverse.config.default_domain", URLEncoder.encode( ParamUtils.getParameter( request, "default_domain" ), "UTF-8" ) );
            }
            else
            {
                JiveGlobals.deleteProperty( "inverse.config.default_domain" );
            }

            JiveGlobals.setProperty( "inverse.config.locked_domain", Boolean.toString( ParamUtils.getBooleanParameter( request, "locked_domain" ) ) );

            if ( ParamUtils.getParameter( request, "view_mode" ) != null )
            {
                JiveGlobals.setProperty( "inverse.config.view_mode", URLEncoder.encode( ParamUtils.getParameter( request, "view_mode" ) ) );
            }
            else
            {
                JiveGlobals.deleteProperty( "inverse.config.view_mode" );
            }
            response.sendRedirect("inverse-config.jsp?success=update");
            return;
        }

        if ( "language".equals( update ) )
        {
            if ( ParamUtils.getParameter( request, "language" ) != null )
            {
                JiveGlobals.setProperty( "inverse.config.language", URLEncoder.encode( ParamUtils.getParameter( request, "language" ) , "UTF-8" ) );
            }

            response.sendRedirect("inverse-config.jsp?success=update");
            return;
        }

        if ( "debug".equals( update ) )
        {
            if ( ParamUtils.getParameter( request, "debugEnabled" ) != null )
            {
                JiveGlobals.setProperty( "inverse.config.debug", Boolean.toString( ParamUtils.getBooleanParameter( request, "debugEnabled" ) ) );
            }

            response.sendRedirect("inverse-config.jsp?success=update");
            return;
        }

        // Should not happen. Indicates that a form wasn't processed above.
        response.sendRedirect("inverse-config.jsp?noupdate");
        return;
    }

    // Read all updated values from the properties.
    final boolean debugEnabled = JiveGlobals.getBooleanProperty( "inverse.config.debug", false );
    final String defaultDomain = JiveGlobals.getProperty( "inverse.config.default_domain", XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
    final boolean lockedDomain = JiveGlobals.getBooleanProperty( "inverse.config.locked_domain", false );
    final String viewMode = JiveGlobals.getProperty( "inverse.config.view_mode", "overlayed" );
%>
<html>
<head>
    <title><fmt:message key="config.page.title"/></title>
    <meta name="pageID" content="inverse-config"/>
</head>
<body>

<% if ( !httpBindManager.isHttpBindEnabled() ) { %>

<div class="jive-warning">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/warning-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <fmt:message key="warning.httpbinding.disabled">
                    <fmt:param value="<a href=\"../../http-bind.jsp\">"/>
                    <fmt:param value="</a>"/>
                </fmt:message>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>

<% if (error != null) { %>

<div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <% if ( "csrf".equalsIgnoreCase( error )  ) { %>
                <fmt:message key="global.csrf.failed" />
                <% } else { %>
                <fmt:message key="admin.error" />: <c:out value="error"></c:out>
                <% } %>
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>


<%  if (success != null) { %>

<div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
        <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
            <td class="jive-icon-label">
                <fmt:message key="properties.save.success" />
            </td></tr>
        </tbody>
    </table>
</div><br>

<%  } %>

<p>
    <fmt:message key="config.page.description">
        <fmt:param value=""/>
    </fmt:message>
    <% if ( httpBindManager.isHttpBindActive() ) {
        final String unsecuredAddress = "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + httpBindManager.getHttpBindUnsecurePort() + "/inverse/";
    %>
        <fmt:message key="config.page.link.unsecure">
            <fmt:param value="<%=unsecuredAddress%>"/>
        </fmt:message>
    <% } %>
    <% if ( httpBindManager.isHttpsBindActive() ) {
        final String securedAddress = "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + httpBindManager.getHttpBindSecurePort() + "/inverse/";
    %>
        <fmt:message key="config.page.link.secure">
            <fmt:param value="<%=securedAddress%>"/>
        </fmt:message>
    <% } %>
</p>

<br>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.general.header" /></div>
<div class="jive-contentBox">

    <p><fmt:message key="config.page.general.description" /></p>

    <form action="inverse-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}"/>
        <input type="hidden" name="update" value="general"/>

        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <tr valign="top">
                <td colspan="2" style="padding-bottom: .5em;">
                    <fmt:message key="config.page.default_domain.description"/>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap style="padding-top: 1em;">
                    <b><label for="default_domain"><fmt:message key="config.page.default_domain.label" /></label></b>
                </td>
                <td width="99%" style="padding-top: 1em;">
                    <input type="text" name="default_domain" id="default_domain" size="30" value="<%=defaultDomain%>">
                </td>
            </tr>
            <tr valign="top">
                <td colspan="2" style="padding-top: 1em;">
                    <input type="checkbox" name="locked_domain" id="locked_domain" <%= lockedDomain ? "checked" : "" %>/>&nbsp;
                    <label for="locked_domain">
                        <b><fmt:message key="config.page.locked_domain.label"/></b> - <fmt:message key="config.page.locked_domain.description"/>
                    </label>
                </td>
            </tr>
            <%--<tr valign="top">--%>
                <%--<td colspan="2" style="padding-top: 1em;">--%>
                    <%--<b><fmt:message key="config.page.view_mode.label"/></b> - <fmt:message key="config.page.view_mode.description"/>--%>
                <%--</td>--%>
            <%--</tr>--%>
            <%--<tr valign="top">--%>
                <%--<td colspan="2">--%>
                    <%--<input type="radio" name="view_mode" id="overlayed" value="overlayed" <%= "overlayed".equalsIgnoreCase( viewMode ) ? "checked" : "" %>/>&nbsp;--%>
                    <%--<label for="overlayed">--%>
                        <%--<fmt:message key="config.page.view_mode.overlayed.label"/>--%>
                    <%--</label>--%>
                <%--</td>--%>
            <%--</tr>--%>
            <%--<tr valign="top">--%>
                <%--<td colspan="2">--%>
                    <%--<input type="radio" name="view_mode" id="fullscreen" value="fullscreen" <%= "fullscreen".equalsIgnoreCase( viewMode ) ? "checked" : "" %>/>&nbsp;--%>
                    <%--<label for="fullscreen">--%>
                        <%--<fmt:message key="config.page.view_mode.fullscreen.label"/>--%>
                    <%--</label>--%>
                <%--</td>--%>
            <%--</tr>--%>
            <%--<tr valign="top">--%>
                <%--<td colspan="2">--%>
                    <%--<input type="radio" name="view_mode" id="mobile" value="mobile" <%= "mobile".equalsIgnoreCase( viewMode ) ? "checked" : "" %>/>&nbsp;--%>
                    <%--<label for="mobile">--%>
                        <%--<fmt:message key="config.page.view_mode.mobile.label"/>--%>
                    <%--</label>--%>
                <%--</td>--%>
            <%--</tr>--%>
            </tbody>
        </table>

        <br>

        <input type="submit" value="<fmt:message key="global.save_settings" />">

    </form>

</div>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.language.header" /></div>
<div class="jive-contentBox">
    <p><fmt:message key="config.page.language.description" /></p>

    <form action="inverse-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="update" value="language"/>

        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <%
                final Language currentLanguage = InversePlugin.getLanguage();
                for ( final Language language : Language.values() )
                {
            %>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="language" value="<%=language.getCode()%>" id="<%=language.getCode()%>" <%= (currentLanguage == language ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="<%=language.getCode()%>">
                        <%= language %>
                    </label>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>

        <br>

        <input type="submit" value="<fmt:message key="global.save_settings" />">

    </form>

</div>

<div class="jive-contentBoxHeader"><fmt:message key="config.page.debug.header" /></div>
<div class="jive-contentBox">

    <p><fmt:message key="config.page.debug.description" /></p>

    <form action="inverse-config.jsp">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="update" value="debug"/>

        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="debugEnabled" value="true" id="rb01" <%= (debugEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb01">
                        <b><fmt:message key="config.page.debug.enabled" /></b> - <fmt:message key="config.page.debug.enabled_info" />
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="radio" name="debugEnabled" value="false" id="rb02" <%= (!debugEnabled ? "checked" : "") %>>
                </td>
                <td width="99%">
                    <label for="rb02">
                        <b><fmt:message key="config.page.debug.disabled" /></b> - <fmt:message key="config.page.debug.disabled_info" />
                    </label>
                </td>
            </tr>
            </tbody>
        </table>

        <br>

        <input type="submit" value="<fmt:message key="global.save_settings" />">

    </form>

</div>

</body>
</html>
