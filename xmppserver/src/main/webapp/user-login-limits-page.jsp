<%--
  -
  - Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
<%--@elvariable id="csrf" type="java.lang.String"--%>
<%--@elvariable id="errors" type="java.util.Map<java.lang.String, java.lang.String>"--%>
<%--@elvariable id="limitsEnabled" type="java.lang.Boolean"--%>
<%--@elvariable id="maxDurationMinutes" type="java.lang.Long"--%>
<%--@elvariable id="addresses" type="org.jivesoftware.admin.servlet.UserLoginLimitsServlet.AddressTable"--%>
<%--@elvariable id="row" type="org.jivesoftware.admin.servlet.UserLoginLimitsServlet.AddressRow"--%>
<%--@elvariable id="entry" type="org.jivesoftware.admin.servlet.UserLoginLimitsServlet.UsernameEntry"--%>
<%--@elvariable id="status" type="javax.servlet.jsp.jstl.core.LoopTagStatus"--%>
<%--@elvariable id="fieldErrors" type="java.lang.Boolean"--%>
<%--@elvariable id="settingsTitle" type="java.lang.String"--%>
<%--@elvariable id="pairTitle" type="java.lang.String"--%>
<%--@elvariable id="addressTitle" type="java.lang.String"--%>
<%--@elvariable id="usernameTitle" type="java.lang.String"--%>
<%--@elvariable id="addressesTitle" type="java.lang.String"--%>
<%-- The view of UserLoginLimitsServlet, which is mapped to user-login-limits.jsp and provides every value shown here. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="admin" uri="admin" %>
<jsp:useBean scope="request" id="settings" type="org.jivesoftware.admin.servlet.UserLoginLimitsServlet.SettingsForm"/>

<html>
<head>
    <title><fmt:message key="user.login.limits.title"/></title>
    <meta name="pageID" content="user-login-limits"/>
    <style>
        .login-limits-relative { color: #666; }
    </style>
</head>
<body>

<c:set var="fieldErrors" value="${not empty settings.errors}"/>
<c:choose>
    <c:when test="${not empty errors['csrf']}">
        <admin:infobox type="error"><fmt:message key="global.csrf.failed"/></admin:infobox>
    </c:when>
    <c:when test="${not empty errors['address']}">
        <admin:infobox type="error"><fmt:message key="user.login.limits.error.address"/></admin:infobox>
    </c:when>
    <c:when test="${fieldErrors}">
        <admin:infobox type="error"><fmt:message key="user.login.limits.error.fields"/></admin:infobox>
    </c:when>
    <c:when test="${param.saved eq 'true'}">
        <admin:infobox type="success"><fmt:message key="user.login.limits.saved"/></admin:infobox>
    </c:when>
    <c:when test="${param.cleared eq 'true'}">
        <admin:infobox type="success"><fmt:message key="user.login.limits.addresses.cleared"/></admin:infobox>
    </c:when>
    <c:when test="${param.gone eq 'true'}">
        <admin:infobox type="info"><fmt:message key="user.login.limits.addresses.gone"/></admin:infobox>
    </c:when>
</c:choose>

<p><fmt:message key="user.login.limits.info"/></p>

<h2 style="font-weight: bold; font-size: 12pt;"><fmt:message key="user.login.limits.settings.heading"/></h2>

<%-- The settings: one form across several boxes, saved by one button below them, as on reg-settings.jsp. --%>
<form action="user-login-limits.jsp" method="post">
    <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
    <input type="hidden" name="action" value="save">

    <fmt:message key="user.login.limits.settings.title" var="settingsTitle"/>
    <admin:contentBox title="${settingsTitle}">
        <table>
            <tr>
                <td style="width: 1%"><input type="radio" name="enabled" value="true" id="enabledOn" ${settings.enabled ? 'checked' : ''}></td>
                <td><label for="enabledOn"><b><fmt:message key="user.login.limits.settings.enabled"/></b> - <fmt:message key="user.login.limits.settings.enabled.info"/></label></td>
            </tr>
            <tr>
                <td style="width: 1%"><input type="radio" name="enabled" value="false" id="enabledOff" ${settings.enabled ? '' : 'checked'}></td>
                <td><label for="enabledOff"><b><fmt:message key="user.login.limits.settings.disabled"/></b> - <fmt:message key="user.login.limits.settings.disabled.info"/></label></td>
            </tr>
            <c:if test="${not empty settings.errors['enabled']}">
                <tr><td></td><td><span class="jive-error-text"><fmt:message key="${settings.errors['enabled']}"/></span></td></tr>
            </c:if>
        </table>
    </admin:contentBox>

    <fmt:message key="user.login.limits.settings.pair" var="pairTitle"/>
    <admin:contentBox title="${pairTitle}">
        <p><fmt:message key="user.login.limits.settings.pair.info"/></p>
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="pairLimit"><fmt:message key="user.login.limits.settings.limit"/></label></b></td>
                <td>
                    <input type="number" min="0" style="width: 8em;${not empty settings.errors['pairLimit'] ? ' background-color: #ffdddd;' : ''}" name="pairLimit" id="pairLimit" value="${fn:escapeXml(settings.pairLimit)}">
                    <c:if test="${not empty settings.errors['pairLimit']}"><span class="jive-error-text"><fmt:message key="${settings.errors['pairLimit']}"/></span></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="pairInterval"><fmt:message key="user.login.limits.settings.interval"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['pairInterval'] ? ' background-color: #ffdddd;' : ''}" name="pairInterval" id="pairInterval" value="${fn:escapeXml(settings.pairInterval)}">
                    <c:if test="${not empty settings.errors['pairInterval']}"><span class="jive-error-text"><fmt:message key="${settings.errors['pairInterval']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['pairInterval']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['pairInterval'])}"/></fmt:message></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="pairBlockDuration"><fmt:message key="user.login.limits.settings.block"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['pairBlockDuration'] ? ' background-color: #ffdddd;' : ''}" name="pairBlockDuration" id="pairBlockDuration" value="${fn:escapeXml(settings.pairBlockDuration)}">
                    <c:if test="${not empty settings.errors['pairBlockDuration']}"><span class="jive-error-text"><fmt:message key="${settings.errors['pairBlockDuration']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['pairBlockDuration']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['pairBlockDuration'])}"/></fmt:message></c:if>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="user.login.limits.settings.address" var="addressTitle"/>
    <admin:contentBox title="${addressTitle}">
        <p><fmt:message key="user.login.limits.settings.address.info"/></p>
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="addressLimit"><fmt:message key="user.login.limits.settings.limit"/></label></b></td>
                <td>
                    <input type="number" min="0" style="width: 8em;${not empty settings.errors['addressLimit'] ? ' background-color: #ffdddd;' : ''}" name="addressLimit" id="addressLimit" value="${fn:escapeXml(settings.addressLimit)}">
                    <c:if test="${not empty settings.errors['addressLimit']}"><span class="jive-error-text"><fmt:message key="${settings.errors['addressLimit']}"/></span></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="addressInterval"><fmt:message key="user.login.limits.settings.interval"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['addressInterval'] ? ' background-color: #ffdddd;' : ''}" name="addressInterval" id="addressInterval" value="${fn:escapeXml(settings.addressInterval)}">
                    <c:if test="${not empty settings.errors['addressInterval']}"><span class="jive-error-text"><fmt:message key="${settings.errors['addressInterval']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['addressInterval']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['addressInterval'])}"/></fmt:message></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="addressBlockDuration"><fmt:message key="user.login.limits.settings.block"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['addressBlockDuration'] ? ' background-color: #ffdddd;' : ''}" name="addressBlockDuration" id="addressBlockDuration" value="${fn:escapeXml(settings.addressBlockDuration)}">
                    <c:if test="${not empty settings.errors['addressBlockDuration']}"><span class="jive-error-text"><fmt:message key="${settings.errors['addressBlockDuration']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['addressBlockDuration']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['addressBlockDuration'])}"/></fmt:message></c:if>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <fmt:message key="user.login.limits.settings.username" var="usernameTitle"/>
    <admin:contentBox title="${usernameTitle}">
        <p><fmt:message key="user.login.limits.settings.username.info"/></p>
        <table>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="usernameLimit"><fmt:message key="user.login.limits.settings.limit"/></label></b></td>
                <td>
                    <input type="number" min="0" style="width: 8em;${not empty settings.errors['usernameLimit'] ? ' background-color: #ffdddd;' : ''}" name="usernameLimit" id="usernameLimit" value="${fn:escapeXml(settings.usernameLimit)}">
                    <c:if test="${not empty settings.errors['usernameLimit']}"><span class="jive-error-text"><fmt:message key="${settings.errors['usernameLimit']}"/></span></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="usernameInterval"><fmt:message key="user.login.limits.settings.interval"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['usernameInterval'] ? ' background-color: #ffdddd;' : ''}" name="usernameInterval" id="usernameInterval" value="${fn:escapeXml(settings.usernameInterval)}">
                    <c:if test="${not empty settings.errors['usernameInterval']}"><span class="jive-error-text"><fmt:message key="${settings.errors['usernameInterval']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['usernameInterval']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['usernameInterval'])}"/></fmt:message></c:if>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap"><b><label for="lockoutDuration"><fmt:message key="user.login.limits.settings.lockout"/></label></b></td>
                <td>
                    <input type="number" min="1" max="${maxDurationMinutes}" style="width: 8em;${not empty settings.errors['lockoutDuration'] ? ' background-color: #ffdddd;' : ''}" name="lockoutDuration" id="lockoutDuration" value="${fn:escapeXml(settings.lockoutDuration)}">
                    <c:if test="${not empty settings.errors['lockoutDuration']}"><span class="jive-error-text"><fmt:message key="${settings.errors['lockoutDuration']}"><fmt:param value="${maxDurationMinutes}"/></fmt:message></span></c:if>
                    <c:if test="${not empty settings.exactDurations['lockoutDuration']}"><fmt:message key="user.login.limits.settings.exact"><fmt:param value="${fn:escapeXml(settings.exactDurations['lockoutDuration'])}"/></fmt:message></c:if>
                </td>
            </tr>
        </table>
    </admin:contentBox>

    <input type="submit" value="<fmt:message key="global.save_settings"/>">
</form>


<%-- The IP addresses from which logins are refused: one row per address, each with its own Clear form. --%>
<h2 style="font-weight: bold; font-size: 12pt; margin-top: 28px;"><fmt:message key="user.login.limits.addresses.title"/></h2>
<p><fmt:message key="user.login.limits.addresses.info"/></p>
<c:if test="${not limitsEnabled}">
    <admin:infobox type="info"><fmt:message key="user.login.limits.disabled"/></admin:infobox>
</c:if>

<fmt:message key="user.login.limits.addresses.box" var="addressesTitle"/>
<admin:contentBox title="${addressesTitle}">
    <c:if test="${addresses.total gt 0}">
        <p>
            <c:choose>
                <c:when test="${addresses.hidden gt 0}">
                    <fmt:message key="user.login.limits.addresses.shown_some">
                        <fmt:param value="${fn:length(addresses.rows)}"/>
                        <fmt:param value="${addresses.total}"/>
                        <fmt:param value="${addresses.hidden}"/>
                    </fmt:message>
                </c:when>
                <c:otherwise>
                    <fmt:message key="user.login.limits.addresses.shown_all"><fmt:param value="${addresses.total}"/></fmt:message>
                </c:otherwise>
            </c:choose>
        </p>
    </c:if>
    <table class="jive-table">
        <tr>
            <th nowrap><fmt:message key="user.login.limits.blocks.address"/></th>
            <th nowrap><fmt:message key="user.login.limits.addresses.refused_for"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.ends"/></th>
            <th style="width: 1%; white-space: nowrap">&nbsp;</th>
        </tr>
        <c:choose>
            <c:when test="${empty addresses.rows}">
                <tr>
                    <td style="text-align: center" colspan="4"><fmt:message key="global.list.empty"/></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="row" items="${addresses.rows}">
                    <tr>
                        <td><c:out value="${row.address}"/></td>
                        <td>
                            <%-- An address can be blocked for all usernames and for some usernames at once; the row shows both. --%>
                            <c:if test="${row.allUsernames}">
                                <fmt:message key="user.login.limits.addresses.all"><fmt:param value="${row.addressFailureCount}"/></fmt:message>
                            </c:if>
                            <c:if test="${row.allUsernames and row.usernameTotal gt 0}"><br></c:if>
                            <c:if test="${row.usernameTotal gt 0}">
                                <fmt:message key="user.login.limits.addresses.usernames"><fmt:param value="${row.usernameTotal}"/></fmt:message>
                                <%-- Kept on one line, so that no space comes before each comma. --%>
                                <c:forEach var="entry" items="${row.usernames}" varStatus="status"><c:if test="${not status.first}">, </c:if><c:choose><c:when test="${not empty entry.lockoutUrl}"><a href="${fn:escapeXml(entry.lockoutUrl)}"><c:out value="${entry.username}"/></a></c:when><c:otherwise><c:out value="${entry.username}"/></c:otherwise></c:choose><c:if test="${entry.truncated}"> <fmt:message key="user.login.limits.blocks.truncated"/></c:if></c:forEach>
                                <c:if test="${row.hiddenUsernames gt 0}"><fmt:message key="user.login.limits.addresses.more"><fmt:param value="${row.hiddenUsernames}"/></fmt:message></c:if>
                            </c:if>
                        </td>
                        <td><c:out value="${row.ends.at}"/><c:if test="${not empty row.ends.fromNow}"><br><span class="login-limits-relative"><c:out value="${row.ends.fromNow}"/></span></c:if></td>
                        <td>
                            <form action="user-login-limits.jsp" method="post" style="margin: 0">
                                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                                <input type="hidden" name="action" value="clear">
                                <input type="hidden" name="address" value="${fn:escapeXml(row.formValue)}">
                                <input type="submit" value="<fmt:message key="user.login.limits.blocks.clear"/>">
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>
</admin:contentBox>

</body>
</html>
