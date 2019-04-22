<%@ page contentType="text/html; charset=UTF-8" %>
<%--
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

<%@ page import="java.util.ArrayList,
                 java.util.Enumeration,
                 java.util.HashSet,
                 java.util.Iterator,
                 java.util.List,
                 java.util.Set"
    errorPage="error.jsp"
%>
<%@ page import="java.util.SortedSet" %>
<%@ page import="java.util.StringTokenizer" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.handler.IQRegisterHandler" %>
<%@ page import="org.jivesoftware.openfire.net.SASLAuthentication" %>
<%@ page import="org.jivesoftware.openfire.sasl.AnonymousSaslServer" %>
<%@ page import="org.jivesoftware.openfire.session.LocalClientSession" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

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

    final Enumeration<String> parameterNames = request.getParameterNames();
    final String mechEnabledPrefix = "mech-enabled-";
    final List<String> mechsEnabled = new ArrayList<>();
    while ( parameterNames.hasMoreElements() )
    {
        final String parameterName = parameterNames.nextElement();
        if (parameterName.startsWith( mechEnabledPrefix ))
        {
            mechsEnabled.add( parameterName.substring( mechEnabledPrefix.length() ) );
        }
    }

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
        AnonymousSaslServer.ENABLED.setValue(anonLogin);

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
        SASLAuthentication.setEnabledMechanisms( mechsEnabled );

        // Log the event
        webManager.logEvent("edited registration settings", "inband enabled = "+inbandEnabled+"\ncan change password = "+canChangePassword+"\nanon login = "+anonLogin+"\nallowed ips = "+allowedIPs+"\nblocked ips = "+blockedIPs+"\nSASL mechanisms enabled = "+ mechsEnabled);
    }

    // Reset the value of page vars:
    inbandEnabled = regHandler.isInbandRegEnabled();
    canChangePassword = regHandler.canChangePassword();
    anonLogin = AnonymousSaslServer.ENABLED.getValue();
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

    pageContext.setAttribute( "readOnly",           UserManager.getUserProvider().isReadOnly() );
    pageContext.setAttribute( "inbandEnabled",      inbandEnabled );
    pageContext.setAttribute( "canChangePassword",  canChangePassword );
    pageContext.setAttribute( "anonLogin",          anonLogin );
    pageContext.setAttribute( "blockedIPs",         blockedIPs);
    pageContext.setAttribute( "allowedIPs",         allowedIPs );
    pageContext.setAttribute( "allowedAnonymIPs",   allowedAnonymIPs );
    pageContext.setAttribute( "saslEnabledMechanisms",     SASLAuthentication.getEnabledMechanisms() );
    pageContext.setAttribute( "saslImplementedMechanisms", SASLAuthentication.getImplementedMechanisms() );
    pageContext.setAttribute( "saslSupportedMechanisms",   SASLAuthentication.getSupportedMechanisms() );

    final SortedSet<String> union = new TreeSet<>();
    union.addAll( SASLAuthentication.getEnabledMechanisms() );
    union.addAll( SASLAuthentication.getImplementedMechanisms() );
    pageContext.setAttribute( "saslConsideredOrImplementedMechanisms", union );
%>

<p>
<fmt:message key="reg.settings.info" />
</p>

<form action="reg-settings.jsp">
    <input type="hidden" name="csrf" value="${csrf}">

<% if (save) { %>

    <admin:infoBox type="success">
        <fmt:message key="reg.settings.update" />
    </admin:infoBox>

<% } %>

<!-- BEGIN registration settings -->

    <fmt:message key="reg.settings.inband_account" var="inband_account_boxtitle"/>
    <admin:contentBox title="${inband_account_boxtitle}">
        <p><fmt:message key="reg.settings.inband_account_info" /></p>
        <c:if test="${readOnly}">
            <admin:infoBox type="info"><fmt:message key="reg.settings.inband_account_readonly" /></admin:infoBox>
        </c:if>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td width="1%"><input type="radio" name="inbandEnabled" value="true" id="rb01" ${inbandEnabled ? 'checked' : ''} ${readOnly ? 'disabled' : ''}></td>
                <td width="99%"><label for="rb01"><b><fmt:message key="reg.settings.enable" /></b> -<fmt:message key="reg.settings.auto_create_user" /></label></td>
            </tr>
            <tr>
                <td width="1%"><input type="radio" name="inbandEnabled" value="false" id="rb02" ${inbandEnabled ?  '' : 'checked'} ${readOnly ? 'disabled' : ''}></td>
                <td width="99%"><label for="rb02"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.not_auto_create" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.change_password" var="change_password_boxtitle"/>
    <admin:contentBox title="${change_password_boxtitle}">
        <p><fmt:message key="reg.settings.change_password_info" /></p>
        <c:if test="${readOnly}">
            <admin:infoBox type="info"><fmt:message key="reg.settings.change_password_readonly" /></admin:infoBox>
        </c:if>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td width="1%"><input type="radio" name="canChangePassword" value="true" id="rb03" ${canChangePassword ? 'checked' : ''} ${readOnly ? 'disabled' : ''}></td>
                <td width="99%"><label for="rb03"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.can_change" /></label></td>
            </tr>
            <tr>
                <td width="1%"><input type="radio" name="canChangePassword" value="false" id="rb04" ${canChangePassword ? '' : 'checked'} ${readOnly ? 'disabled' : ''}></td>
                <td width="99%"><label for="rb04"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.cannot_change" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.anonymous_login" var="anonymous_login_boxtitle"/>
    <admin:contentBox title="${anonymous_login_boxtitle}">
        <p><fmt:message key="reg.settings.anonymous_login_info" /></p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td width="1%"><input type="radio" name="anonLogin" value="true" id="rb05" ${anonLogin ? 'checked' : ''}></td>
                <td width="99%"><label for="rb05"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.anyone_login" /></label></td>
            </tr>
            <tr>
                <td width="1%"><input type="radio" name="anonLogin" value="false" id="rb06" ${anonLogin ? '' : 'checked'}></td>
                <td width="99%"><label for="rb06"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.only_registered_login" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.allowed_ips" var="allowed_ips_boxtitle"/>
    <admin:contentBox title="${allowed_ips_boxtitle}">
        <p><fmt:message key="reg.settings.allowed_ips_blocked_info" /></p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td valign='top'><b><fmt:message key="reg.settings.ips_blocked" /></b></td>
                <td><textarea name="blockedIPs" cols="40" rows="3" wrap="virtual"><c:if test="${not empty blockedIPs}"><c:out value="${blockedIPs}"/></c:if></textarea></td>
            </tr>
        </table>

        <p><fmt:message key="reg.settings.allowed_ips_info" /></p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td valign='top'><b><fmt:message key="reg.settings.ips_all" /></b></td>
                <td><textarea name="allowedIPs" cols="40" rows="3" wrap="virtual"><c:if test="${not empty allowedIPs}"><c:out value="${allowedIPs}"/></c:if></textarea></td>
            </tr>
            <tr>
                <td valign='top'><b><fmt:message key="reg.settings.ips_anonymous" /></b></td>
                <td><textarea name="allowedAnonymIPs" cols="40" rows="3" wrap="virtual"><c:if test="${not empty allowedAnonymIPs}"><c:out value="${allowedAnonymIPs}"/></c:if></textarea></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.sasl_mechanisms" var="sasl_mechanism_boxtitle"/>
    <admin:contentBox title="${sasl_mechanism_boxtitle}">
        <p><fmt:message key="reg.settings.sasl_mechanisms_info" /></p>
        <table class="jive-table" cellpadding="3" cellspacing="0" border="0">
            <tr>
                <th align="center" width="1%"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_enabled" /></th>
                <th align="left" width="20%"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_name" /></th>
                <th align="left"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_description" /></th>
                <th align="center" width="5%" style="text-align: center"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_implementation" /></th>
                <th align="center" width="5%" style="text-align: center"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_supported" /></th>
            </tr>
            <c:forEach items="${saslConsideredOrImplementedMechanisms}" var="mechanism" varStatus="status">
                <c:set var="idForForm">mech-enabled-<c:out value="${mechanism}"/></c:set>
                <c:set var="description"><fmt:message key="reg.settings.description.${mechanism}" /></c:set>
                <c:choose>
                    <c:when test="${fn:startsWith(description,'???')}">
                        <c:set var="description"><fmt:message key="reg.settings.description.none" /></c:set>
                    </c:when>
                </c:choose>
                <c:set var="enabled" value="${saslEnabledMechanisms.contains(mechanism)}"/>
                <c:set var="implemented" value="${saslImplementedMechanisms.contains(mechanism)}"/>
                <c:set var="supported" value="${saslSupportedMechanisms.contains(mechanism)}"/>
                <tr class="${ ( (status.index + 1) % 2 ) eq 0 ? 'jive-even' : 'jive-odd'}">
                    <td align="center"><input type="checkbox" name="${idForForm}" id="${idForForm}" ${enabled ? 'checked' : ''}/></td>
                    <td align="left"><label for="${idForForm}"><c:out value="${mechanism}"/></label></td>
                    <td align="left"><c:out value="${description}"/></td>
                    <td align="center"><c:if test="${implemented}"><img src="images/check-16x16.gif" width="16" height="16" border="0" alt=""/></c:if></td>
                    <td align="center"><c:if test="${supported}"><img src="images/check-16x16.gif" width="16" height="16" border="0" alt=""/></c:if></td>
                </tr>
            </c:forEach>
        </table>
    </admin:contentBox>

    <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
<!-- END registration settings -->

</form>

</body>

</html>
