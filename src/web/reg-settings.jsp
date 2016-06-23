<%--
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.handler.IQRegisterHandler,
                 org.jivesoftware.openfire.session.LocalClientSession,
                 org.jivesoftware.util.CookieUtils,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="reg.settings.title"/></title>
<meta name="pageID" content="server-reg-and-login"/>
<meta name="helpPage" content="manage_registration_and_login_settings.html"/>
</head>
<body>

<% // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean inbandEnabled = ParamUtils.getBooleanParameter(request, "inbandEnabled");
    boolean canChangePassword = ParamUtils.getBooleanParameter(request, "canChangePassword");
    boolean anonLogin = ParamUtils.getBooleanParameter(request, "anonLogin");
    String allowedIPs = request.getParameter("allowedIPs");
    String allowedAnonymIPs = request.getParameter("allowedAnonymIPs");
    String blockedIPs = request.getParameter("blockedIPs");
    // Get an IQRegisterHandler:
    IQRegisterHandler regHandler = XMPPServer.getInstance().getIQRegisterHandler();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (save) {
        regHandler.setInbandRegEnabled(inbandEnabled);
        regHandler.setCanChangePassword(canChangePassword);
        JiveGlobals.setProperty("xmpp.auth.anonymous", Boolean.toString(anonLogin));

        // Build a Map with the allowed IP addresses
        Pattern pattern = Pattern.compile("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)" +
                "(?:(?:\\*|25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){2}" +
                "(?:\\*|25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");
        Set<String> allowedSet = new HashSet<String>();
        StringTokenizer tokens = new StringTokenizer(allowedIPs, ", ");
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken().trim();
            if (pattern.matcher(address).matches()) {
                allowedSet.add( address );
            }
        }
        

        Set<String> allowedAnonymousSet = new HashSet<String>();
        StringTokenizer tokens1 = new StringTokenizer(allowedAnonymIPs, ", ");
        while (tokens1.hasMoreTokens()) {
            String address = tokens1.nextToken().trim();
            if (pattern.matcher(address).matches()) {
                allowedAnonymousSet.add( address );
            }
        }

        Set<String> blockedSet = new HashSet<String>();
        StringTokenizer tokens2 = new StringTokenizer(blockedIPs, ", ");
        while (tokens2.hasMoreTokens()) {
            String address = tokens2.nextToken().trim();
            if (pattern.matcher(address).matches()) {
                blockedSet.add( address );
            }
        }
        LocalClientSession.setWhitelistedIPs( allowedSet );
        LocalClientSession.setWhitelistedAnonymousIPs( allowedAnonymousSet );
        LocalClientSession.setBlacklistedIPs( blockedSet );

        // Log the event
        webManager.logEvent("edited registration settings", "inband enabled = "+inbandEnabled+"\ncan change password = "+canChangePassword+"\nanon login = "+anonLogin+"\nallowed ips = "+allowedIPs+"\nblocked ips = "+blockedIPs);
    }

    // Reset the value of page vars:
    inbandEnabled = regHandler.isInbandRegEnabled();
    canChangePassword = regHandler.canChangePassword();
    anonLogin = JiveGlobals.getBooleanProperty( "xmpp.auth.anonymous" );
    // Encode the allowed IP addresses
    StringBuilder buf = new StringBuilder();
    Iterator<String> iter = org.jivesoftware.openfire.session.LocalClientSession.getWhitelistedIPs().iterator();
    if (iter.hasNext()) {
        buf.append(iter.next());
    }
    while (iter.hasNext()) {
        buf.append(", ").append(iter.next());
    }
    allowedIPs = buf.toString();

    StringBuilder buf1 = new StringBuilder();
    Iterator<String> iter1 = org.jivesoftware.openfire.session.LocalClientSession.getWhitelistedAnonymousIPs().iterator();
    if (iter1.hasNext()) {
        buf1.append(iter1.next());
    }
    while (iter1.hasNext()) {
        buf1.append(", ").append(iter1.next());
    }
    allowedAnonymIPs = buf1.toString();

    StringBuilder buf2 = new StringBuilder();
    Iterator<String> iter2 = org.jivesoftware.openfire.session.LocalClientSession.getBlacklistedIPs().iterator();
    if (iter2.hasNext()) {
        buf2.append(iter2.next());
    }
    while (iter2.hasNext()) {
        buf2.append(", ").append(iter2.next());
    }
    blockedIPs = buf2.toString();

%>

<p>
<fmt:message key="reg.settings.info" />
</p>

<form action="reg-settings.jsp">
    <input type="hidden" name="csrf" value="${csrf}">

<% if (save) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="reg.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<% } %>

<!-- BEGIN registration settings -->
	<!--<div class="jive-contentBoxHeader">

	</div>-->
	<div class="jive-contentBox" style="-moz-border-radius: 3px;">

	<h4><fmt:message key="reg.settings.inband_account" /></h4>
	<p>
    <fmt:message key="reg.settings.inband_account_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="true" id="rb01"
                 <%= ((inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="reg.settings.enable" /></b> -
                <fmt:message key="reg.settings.auto_create_user" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="false" id="rb02"
                 <%= ((!inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.not_auto_create" /></label>
            </td>
        </tr>
    </tbody>
    </table>

	<br>
	<br>

	<h4><fmt:message key="reg.settings.change_password" /></h4>
	<p>
    <fmt:message key="reg.settings.change_password_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="canChangePassword" value="true" id="rb03"
             <%= ((canChangePassword) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.can_change" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="canChangePassword" value="false" id="rb04"
             <%= ((!canChangePassword) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb04"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.cannot_change" /></label>
            </td>
        </tr>
    </tbody>
    </table>

	<br>
	<br>

	<h4><fmt:message key="reg.settings.anonymous_login" /></h4>
	<p>
    <fmt:message key="reg.settings.anonymous_login_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="true" id="rb05"
             <%= ((anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb05"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.anyone_login" /></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="false" id="rb06"
             <%= ((!anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb06"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.only_registered_login" /></label>
            </td>
        </tr>
    </tbody>
    </table>

	<br>
	<br>

	<h4><fmt:message key="reg.settings.allowed_ips" /></h4>
    <p>
        <fmt:message key="reg.settings.allowed_ips_blocked_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
        <tbody>
        <tr>
            <td valign='top'><b><fmt:message key="reg.settings.ips_blocked" /></b></td>
            <td>
                <textarea name="blockedIPs" cols="40" rows="3" wrap="virtual"><%= ((blockedIPs != null) ? blockedIPs : "") %></textarea>
            </td>
        </tr>
        </tbody>
    </table>

	<p>
    <fmt:message key="reg.settings.allowed_ips_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td valign='top'><b><fmt:message key="reg.settings.ips_all" /></b></td>
            <td>
                <textarea name="allowedIPs" cols="40" rows="3" wrap="virtual"><%= ((allowedIPs != null) ? allowedIPs : "") %></textarea>
            </td>
        </tr>
        <tr>
            <td valign='top'><b><fmt:message key="reg.settings.ips_anonymous" /></b></td>
            <td>
                <textarea name="allowedAnonymIPs" cols="40" rows="3" wrap="virtual"><%= ((allowedAnonymIPs != null) ? allowedAnonymIPs : "") %></textarea>
            </td>
        </tr>
    </tbody>
    </table>
	
	</div>
    <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
<!-- END registration settings -->

</form>


</body>

</html>
