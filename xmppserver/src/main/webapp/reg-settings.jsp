<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

<%@ page
    errorPage="error.jsp"
%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.handler.IQRegisterHandler" %>
<%@ page import="org.jivesoftware.openfire.net.SASLAuthentication" %>
<%@ page import="org.jivesoftware.openfire.sasl.AnonymousSaslServer" %>
<%@ page import="org.jivesoftware.openfire.session.LocalClientSession" %>
<%@ page import="org.jivesoftware.openfire.user.UserManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="org.jivesoftware.util.IpUtils" %>
<%@ page import="java.util.*" %>

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
    boolean futureUsersEnabled = ParamUtils.getBooleanParameter(request, "futureUsersEnabled");
    String deleteBlockedIP = ParamUtils.getParameter(request, "deleteBlockedIP");
    String deleteAllowedIP = ParamUtils.getParameter(request, "deleteAllowedIP");
    String deleteAllowedAnonymIP = ParamUtils.getParameter(request, "deleteAllowedAnonymIP");
    String blockValue = ParamUtils.getParameter(request, "blockValue");
    String allowValue = ParamUtils.getParameter(request, "allowValue");
    String allowAnonymValue = ParamUtils.getParameter(request, "allowAnonymValue");

    final Map<String, Object> errors = new HashMap<>();

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

    if (save || blockValue != null || deleteBlockedIP != null || allowValue != null || deleteAllowedIP != null || allowAnonymValue != null || deleteAllowedAnonymIP != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "csrf");
            save = false;
            deleteBlockedIP = null;
            blockValue = null;
            deleteAllowedIP = null;
            allowValue = null;
            deleteAllowedAnonymIP = null;
            allowAnonymValue = null;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (deleteBlockedIP != null && errors.isEmpty())
    {
        final Set<String> blocklist = LocalClientSession.getBlacklistedIPs();
        if (blocklist.remove(deleteBlockedIP) ) {
            LocalClientSession.setBlacklistedIPs(blocklist);
            webManager.logEvent("edited registration settings", "Removed value from list of blocked IP/IP-ranges: " + deleteBlockedIP);
        }
    }
    if (blockValue != null && errors.isEmpty()) {
        if (!IpUtils.isValidIpAddressOrRange(blockValue)) {
            errors.put("blockValue", "invalid-syntax");
        } else {
            final Set<String> blocklist = LocalClientSession.getBlacklistedIPs();
            if (blocklist.add(blockValue)) {
                LocalClientSession.setBlacklistedIPs(blocklist);
                webManager.logEvent("edited registration settings", "Added value to list of blocked IP/IP-ranges: " + blockValue);
                blockValue = null;
            }
        }
    }
    if (deleteAllowedIP != null && errors.isEmpty())
    {
        final Set<String> allowlist = LocalClientSession.getWhitelistedIPs();
        if (allowlist.remove(deleteAllowedIP) ) {
            LocalClientSession.setWhitelistedIPs(allowlist);
            webManager.logEvent("edited registration settings", "Removed value from list of allowed IP/IP-ranges: " + deleteAllowedIP);
        }
    }
    if (allowValue != null && errors.isEmpty()) {
        if (!IpUtils.isValidIpAddressOrRange(allowValue)) {
            errors.put("allowValue", "invalid-syntax");
        } else {
            final Set<String> allowlist = LocalClientSession.getWhitelistedIPs();
            if (allowlist.add(allowValue)) {
                LocalClientSession.setWhitelistedIPs(allowlist);
                webManager.logEvent("edited registration settings", "Added value to list of allowed IP/IP-ranges: " + blockValue);
                allowValue = null;
            }
        }
    }
    if (deleteAllowedAnonymIP != null && errors.isEmpty())
    {
        final Set<String> allowlist = LocalClientSession.getWhitelistedAnonymousIPs();
        if (allowlist.remove(deleteAllowedAnonymIP) ) {
            LocalClientSession.setWhitelistedAnonymousIPs(allowlist);
            webManager.logEvent("edited registration settings", "Removed value from list of allowed anonymous IP/IP-ranges: " + deleteAllowedIP);
        }
    }
    if (allowAnonymValue != null && errors.isEmpty()) {
        if (!IpUtils.isValidIpAddressOrRange(allowAnonymValue)) {
            errors.put("allowAnonymValue", "invalid-syntax");
        } else {
            final Set<String> allowlist = LocalClientSession.getWhitelistedAnonymousIPs();
            if (allowlist.add(allowAnonymValue)) {
                LocalClientSession.setWhitelistedAnonymousIPs(allowlist);
                webManager.logEvent("edited registration settings", "Added value to list of allowed anonymous IP/IP-ranges: " + blockValue);
                allowAnonymValue = null;
            }
        }
    }

    if (save && errors.isEmpty()) {
        regHandler.setInbandRegEnabled(inbandEnabled);
        regHandler.setCanChangePassword(canChangePassword);
        AnonymousSaslServer.ENABLED.setValue(anonLogin);
        UserManager.ALLOW_FUTURE_USERS.setValue( futureUsersEnabled );
        SASLAuthentication.setEnabledMechanisms( mechsEnabled );

        // Log the event
        webManager.logEvent("edited registration settings", "inband enabled = "+inbandEnabled+"\ncan change password = "+canChangePassword+"\nanon login = "+anonLogin+"\nFuture users enabled = "+futureUsersEnabled+"\nSASL mechanisms enabled = "+ mechsEnabled);
    }

    // Reset the value of page vars:
    inbandEnabled = regHandler.isInbandRegEnabled();
    canChangePassword = regHandler.canChangePassword();
    anonLogin = AnonymousSaslServer.ENABLED.getValue();
    futureUsersEnabled = UserManager.ALLOW_FUTURE_USERS.getValue();

    pageContext.setAttribute( "errors",             errors );
    pageContext.setAttribute( "readOnly",           UserManager.getUserProvider().isReadOnly() );
    pageContext.setAttribute( "inbandEnabled",      inbandEnabled );
    pageContext.setAttribute( "canChangePassword",  canChangePassword );
    pageContext.setAttribute( "anonLogin",          anonLogin );
    pageContext.setAttribute( "blockedIPs",         LocalClientSession.getBlacklistedIPs().stream().sorted().collect(Collectors.toList()));
    pageContext.setAttribute( "allowedIPs",         LocalClientSession.getWhitelistedIPs().stream().sorted().collect(Collectors.toList()));
    pageContext.setAttribute( "allowedAnonymIPs",   LocalClientSession.getWhitelistedAnonymousIPs().stream().sorted().collect(Collectors.toList()));
    pageContext.setAttribute( "futureUsersEnabled", futureUsersEnabled );
    pageContext.setAttribute( "saslEnabledMechanisms",     SASLAuthentication.getEnabledMechanisms() );
    pageContext.setAttribute( "saslImplementedMechanisms", SASLAuthentication.getImplementedMechanisms() );
    pageContext.setAttribute( "saslSupportedMechanisms",   SASLAuthentication.getSupportedMechanisms() );
    pageContext.setAttribute( "blockValue", blockValue );
    pageContext.setAttribute( "allowValue", allowValue );
    pageContext.setAttribute( "allowAnonymValue", allowAnonymValue );
    pageContext.setAttribute( "saveSuccess", save && errors.isEmpty());

    final SortedSet<String> union = new TreeSet<>();
    union.addAll( SASLAuthentication.getEnabledMechanisms() );
    union.addAll( SASLAuthentication.getImplementedMechanisms() );
    pageContext.setAttribute( "saslConsideredOrImplementedMechanisms", union );
%>

<p>
<fmt:message key="reg.settings.info" />
</p>

<form action="reg-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <c:choose>
        <c:when test="${not empty errors}">
            <c:forEach var="err" items="${errors}">
                <admin:infobox type="error">
                    <c:choose>
                        <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                        <c:when test="${err.key eq 'blockValue'}"><fmt:message key="reg.settings.block-value.invalid" /></c:when>
                        <c:when test="${err.key eq 'allowValue'}"><fmt:message key="reg.settings.allow-value.invalid" /></c:when>
                        <c:when test="${err.key eq 'allowAnonymValue'}"><fmt:message key="reg.settings.allow-value.invalid" /></c:when>
                        <c:otherwise>
                            <c:if test="${not empty err.value}">
                                <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                            </c:if>
                            (<c:out value="${err.key}"/>)
                        </c:otherwise>
                    </c:choose>
                </admin:infobox>
            </c:forEach>
        </c:when>
        <c:when test="${saveSuccess}">
            <admin:infoBox type="success">
                <fmt:message key="reg.settings.update" />
            </admin:infoBox>
        </c:when>
    </c:choose>

<!-- BEGIN registration settings -->

    <fmt:message key="reg.settings.inband_account" var="inband_account_boxtitle"/>
    <admin:contentBox title="${inband_account_boxtitle}">
        <p><fmt:message key="reg.settings.inband_account_info" /></p>
        <c:if test="${readOnly}">
            <admin:infoBox type="info"><fmt:message key="reg.settings.inband_account_readonly" /></admin:infoBox>
        </c:if>
        <table>
            <tr>
                <td style="width: 1%"><input type="radio" name="inbandEnabled" value="true" id="rb01" ${inbandEnabled ? 'checked' : ''} ${readOnly ? 'disabled' : ''}></td>
                <td><label for="rb01"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.auto_create_user" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%"><input type="radio" name="inbandEnabled" value="false" id="rb02" ${inbandEnabled ?  '' : 'checked'} ${readOnly ? 'disabled' : ''}></td>
                <td><label for="rb02"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.not_auto_create" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.change_password" var="change_password_boxtitle"/>
    <admin:contentBox title="${change_password_boxtitle}">
        <p><fmt:message key="reg.settings.change_password_info" /></p>
        <c:if test="${readOnly}">
            <admin:infoBox type="info"><fmt:message key="reg.settings.change_password_readonly" /></admin:infoBox>
        </c:if>
        <table>
            <tr>
                <td style="width: 1%"><input type="radio" name="canChangePassword" value="true" id="rb03" ${canChangePassword ? 'checked' : ''} ${readOnly ? 'disabled' : ''}></td>
                <td><label for="rb03"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.can_change" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%"><input type="radio" name="canChangePassword" value="false" id="rb04" ${canChangePassword ? '' : 'checked'} ${readOnly ? 'disabled' : ''}></td>
                <td><label for="rb04"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.cannot_change" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.anonymous_login" var="anonymous_login_boxtitle"/>
    <admin:contentBox title="${anonymous_login_boxtitle}">
        <p><fmt:message key="reg.settings.anonymous_login_info" /></p>
        <table>
            <tr>
                <td style="width: 1%"><input type="radio" name="anonLogin" value="true" id="rb05" ${anonLogin ? 'checked' : ''}></td>
                <td><label for="rb05"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.anyone_login" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%"><input type="radio" name="anonLogin" value="false" id="rb06" ${anonLogin ? '' : 'checked'}></td>
                <td><label for="rb06"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.only_registered_login" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.allowed_ips" var="allowed_ips_boxtitle"/>
    <admin:contentBox title="${allowed_ips_boxtitle}">
        <p><fmt:message key="reg.settings.allowed_ips_blocked_info" /></p>
        <table class="jive-table">
            <tr>
                <th style="width: 1%; white-space: nowrap">&nbsp;</th>
                <th style="width: 50%; white-space: nowrap"><fmt:message key="reg.settings.ips_blocked" /></th>
                <th style="width: 1%; white-space: nowrap"><fmt:message key="global.delete" /></th>
            </tr>
            <c:choose>
                <c:when test="${empty blockedIPs}">
                    <tr>
                        <td style="text-align: center" colspan="3"><fmt:message key="global.list.empty" /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach var="blockedIP" varStatus="status" items="${blockedIPs}">
                        <tr>
                            <td></td>
                            <td><c:out value="${blockedIP}"/></td>
                            <td style="border-right:1px #ccc solid; text-align: center">
                                <c:url var="deleteurl" value="reg-settings.jsp">
                                    <c:param name="deleteBlockedIP" value="${admin:escapeHTMLTags(blockedIP)}"/>
                                    <c:param name="csrf" value="${csrf}"/>
                                </c:url>
                                <a href="#" onclick="if (confirm('<fmt:message key="reg.settings.confirm_delete_ip"><fmt:param><c:out value="${blockedIP}"/></fmt:param></fmt:message>')) { location.replace('${deleteurl}'); } "
                                   title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" alt=""></a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </table>

        <br/>

        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <b><label for="blockValue"><fmt:message key="reg.settings.block-value" /></label></b>
                </td>
                <td>
                    <input type="text" size="40" name="blockValue" id="blockValue" value="${fn:escapeXml(blockValue)}" ${not empty errors.blockValue ? 'autofocus style=\'background-color: #ffdddd;\'' :''}/>
                    <input type="submit" name="addBlockedIP" value="<fmt:message key="global.add" />">
                </td>
            </tr>
        </table>

        <br>

        <p><fmt:message key="reg.settings.allowed_ips_info" /></p>
        <table class="jive-table">
            <tr>
                <th style="width: 1%; white-space: nowrap">&nbsp;</th>
                <th style="width: 50%; white-space: nowrap"><fmt:message key="reg.settings.ips_all" /></th>
                <th style="width: 1%; white-space: nowrap"><fmt:message key="global.delete" /></th>
            </tr>
            <c:choose>
                <c:when test="${empty allowedIPs}">
                    <tr>
                        <td style="text-align: center" colspan="3"><fmt:message key="global.list.empty" /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach var="allowedIP" varStatus="status" items="${allowedIPs}">
                        <tr>
                            <td></td>
                            <td><c:out value="${allowedIP}"/></td>
                            <td style="border-right:1px #ccc solid; text-align: center">
                                <c:url var="deleteurl" value="reg-settings.jsp">
                                    <c:param name="deleteAllowedIP" value="${admin:escapeHTMLTags(allowedIP)}"/>
                                    <c:param name="csrf" value="${csrf}"/>
                                </c:url>
                                <a href="#" onclick="if (confirm('<fmt:message key="reg.settings.confirm_delete_ip"><fmt:param><c:out value="${allowedIP}"/></fmt:param></fmt:message>')) { location.replace('${deleteurl}'); } "
                                   title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" alt=""></a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </table>

        <br/>

        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <b><label for="allowValue"><fmt:message key="reg.settings.allow-value" /></label></b>
                </td>
                <td>
                    <input type="text" size="40" name="allowValue" id="allowValue" value="${fn:escapeXml(allowValue)}" ${not empty errors.allowValue ? 'autofocus style=\'background-color: #ffdddd;\'' :''}/>
                    <input type="submit" name="addAllowedIP" value="<fmt:message key="global.add" />">
                </td>
            </tr>
        </table>

        <br/>

        <table class="jive-table">
            <tr>
                <th style="width: 1%; white-space: nowrap">&nbsp;</th>
                <th style="width: 50%; white-space: nowrap"><fmt:message key="reg.settings.ips_anonymous" /></th>
                <th style="width: 1%; white-space: nowrap"><fmt:message key="global.delete" /></th>
            </tr>
            <c:choose>
                <c:when test="${empty allowedAnonymIPs}">
                    <tr>
                        <td style="text-align: center" colspan="3"><fmt:message key="global.list.empty" /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach var="allowedAnonymIP" varStatus="status" items="${allowedAnonymIPs}">
                        <tr>
                            <td></td>
                            <td><c:out value="${allowedAnonymIP}"/></td>
                            <td style="border-right:1px #ccc solid; text-align: center">
                                <c:url var="deleteurl" value="reg-settings.jsp">
                                    <c:param name="deleteAllowedAnonymIP" value="${admin:escapeHTMLTags(allowedAnonymIP)}"/>
                                    <c:param name="csrf" value="${csrf}"/>
                                </c:url>
                                <a href="#" onclick="if (confirm('<fmt:message key="reg.settings.confirm_delete_ip"><fmt:param><c:out value="${allowedAnonymIP}"/></fmt:param></fmt:message>')) { location.replace('${deleteurl}'); } "
                                   title="<fmt:message key="global.click_delete" />"><img src="images/delete-16x16.gif" alt=""></a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </table>

        <br/>

        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <b><label for="allowAnonymValue"><fmt:message key="reg.settings.allow-value" /></label></b>
                </td>
                <td>
                    <input type="text" size="40" name="allowAnonymValue" id="allowAnonymValue" value="${fn:escapeXml(allowAnonymValue)}" ${not empty errors.allowAnonymValue ? 'autofocus style=\'background-color: #ffdddd;\'' :''}/>
                    <input type="submit" name="addAllowedAnonymIP" value="<fmt:message key="global.add" />">
                </td>
            </tr>
        </table>

    </admin:contentBox>

    <fmt:message key="reg.settings.future_users" var="future_users_boxtitle"/>
    <admin:contentBox title="${future_users_boxtitle}">
        <p><fmt:message key="reg.settings.future_users_info" /></p>
        <table>
            <tr>
                <td style="width: 1%"><input type="radio" name="futureUsersEnabled" value="true" id="rb07" ${futureUsersEnabled ? 'checked' : ''}></td>
                <td><label for="rb07"><b><fmt:message key="reg.settings.enable" /></b> - <fmt:message key="reg.settings.future_users_enabled" /></label></td>
            </tr>
            <tr>
                <td style="width: 1%"><input type="radio" name="futureUsersEnabled" value="false" id="rb08" ${futureUsersEnabled ? '' : 'checked'}></td>
                <td><label for="rb08"><b><fmt:message key="reg.settings.disable" /></b> - <fmt:message key="reg.settings.future_users_disabled" /></label></td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="reg.settings.sasl_mechanisms" var="sasl_mechanism_boxtitle"/>
    <admin:contentBox title="${sasl_mechanism_boxtitle}">
        <p><fmt:message key="reg.settings.sasl_mechanisms_info" /></p>
        <table class="jive-table">
            <tr>
                <th style="width: 1%; text-align: center"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_enabled" /></th>
                <th style="width: 20%; text-align: left"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_name" /></th>
                <th style="text-align: left"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_description" /></th>
                <th style="width: 5%; text-align: left"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_implementation" /></th>
                <th style="width: 5%; text-align: left"><fmt:message key="reg.settings.sasl_mechanisms_columntitle_supported" /></th>
            </tr>
            <c:forEach items="${saslConsideredOrImplementedMechanisms}" var="mechanism">
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
                <tr>
                    <td style="text-align: center"><input type="checkbox" name="${idForForm}" id="${idForForm}" ${enabled ? 'checked' : ''}/></td>
                    <td style="text-align: left"><label for="${idForForm}"><c:out value="${mechanism}"/></label></td>
                    <td style="text-align: left"><c:out value="${description}"/></td>
                    <td style="text-align: center"><c:if test="${implemented}"><img src="images/check-16x16.gif" alt=""/></c:if></td>
                    <td style="text-align: center"><c:if test="${supported}"><img src="images/check-16x16.gif" alt=""/></c:if></td>
                </tr>
            </c:forEach>
        </table>
    </admin:contentBox>

    <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
<!-- END registration settings -->

</form>

</body>

</html>
