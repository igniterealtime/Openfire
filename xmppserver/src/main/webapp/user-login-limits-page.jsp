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
</head>
<body>

<c:set var="fieldErrors" value="${not empty settings.errors}"/>
<c:choose>
    <c:when test="${not empty errors['csrf']}">
        <admin:infobox type="error"><fmt:message key="global.csrf.failed"/></admin:infobox>
    </c:when>
    <c:when test="${not empty errors['block']}">
        <admin:infobox type="error"><fmt:message key="user.login.limits.error.block"/></admin:infobox>
    </c:when>
    <c:when test="${fieldErrors}">
        <admin:infobox type="error"><fmt:message key="user.login.limits.error.fields"/></admin:infobox>
    </c:when>
    <c:when test="${param.saved eq 'true'}">
        <admin:infobox type="success"><fmt:message key="user.login.limits.saved"/></admin:infobox>
    </c:when>
    <c:when test="${param.cleared eq 'true'}">
        <admin:infobox type="success"><fmt:message key="user.login.limits.cleared"/></admin:infobox>
    </c:when>
    <c:when test="${param.gone eq 'true'}">
        <admin:infobox type="info"><fmt:message key="user.login.limits.gone"/></admin:infobox>
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

<%-- The blocks in force: one box per kind of block, each Clear button its own form. --%>
<h2 style="font-weight: bold; font-size: 12pt; margin-top: 28px;"><fmt:message key="user.login.limits.blocks.title"/></h2>
<p><fmt:message key="user.login.limits.blocks.info"/></p>
<c:if test="${not limitsEnabled}">
    <admin:infobox type="info"><fmt:message key="user.login.limits.disabled"/></admin:infobox>
</c:if>

<fmt:message key="user.login.limits.blocks.section.pair" var="pairBlocksTitle"><fmt:param value="${pairBlocks.total}"/></fmt:message>
<admin:contentBox title="${pairBlocksTitle}">
    <p><fmt:message key="user.login.limits.blocks.note"/>
        <c:if test="${pairBlocks.truncated}"><fmt:message key="user.login.limits.blocks.shown"><fmt:param value="${fn:length(pairBlocks.rows)}"/></fmt:message></c:if></p>
    <table class="jive-table">
        <tr>
            <th nowrap><fmt:message key="user.login.limits.blocks.username"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.address"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.failures"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.ends"/></th>
            <th style="width: 1%; white-space: nowrap">&nbsp;</th>
        </tr>
        <c:choose>
            <c:when test="${empty pairBlocks.rows}">
                <tr>
                    <td style="text-align: center" colspan="5"><fmt:message key="global.list.empty"/></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="row" items="${pairBlocks.rows}">
                    <tr>
                        <td><c:out value="${row.username}"/><c:if test="${row.usernameTruncated}"> <fmt:message key="user.login.limits.blocks.truncated"/></c:if></td>
                        <td><c:out value="${row.address}"/></td>
                        <td><c:out value="${row.failureCount}"/></td>
                        <td><c:out value="${row.endsAt}"/></td>
                        <td>
                            <form action="user-login-limits.jsp" method="post" style="margin: 0">
                                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                                <input type="hidden" name="action" value="clear">
                                <input type="hidden" name="block" value="${fn:escapeXml(row.token)}">
                                <input type="submit" value="<fmt:message key="user.login.limits.blocks.clear"/>">
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>
</admin:contentBox>

<fmt:message key="user.login.limits.blocks.section.address" var="addressBlocksTitle"><fmt:param value="${addressBlocks.total}"/></fmt:message>
<admin:contentBox title="${addressBlocksTitle}">
    <p><fmt:message key="user.login.limits.blocks.note"/>
        <c:if test="${addressBlocks.truncated}"><fmt:message key="user.login.limits.blocks.shown"><fmt:param value="${fn:length(addressBlocks.rows)}"/></fmt:message></c:if></p>
    <table class="jive-table">
        <tr>
            <th nowrap><fmt:message key="user.login.limits.blocks.address"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.failures"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.ends"/></th>
            <th style="width: 1%; white-space: nowrap">&nbsp;</th>
        </tr>
        <c:choose>
            <c:when test="${empty addressBlocks.rows}">
                <tr>
                    <td style="text-align: center" colspan="4"><fmt:message key="global.list.empty"/></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="row" items="${addressBlocks.rows}">
                    <tr>
                        <td><c:out value="${row.address}"/></td>
                        <td><c:out value="${row.failureCount}"/></td>
                        <td><c:out value="${row.endsAt}"/></td>
                        <td>
                            <form action="user-login-limits.jsp" method="post" style="margin: 0">
                                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                                <input type="hidden" name="action" value="clear">
                                <input type="hidden" name="block" value="${fn:escapeXml(row.token)}">
                                <input type="submit" value="<fmt:message key="user.login.limits.blocks.clear"/>">
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>
</admin:contentBox>

<fmt:message key="user.login.limits.blocks.section.username" var="usernameBlocksTitle"><fmt:param value="${usernameBlocks.total}"/></fmt:message>
<admin:contentBox title="${usernameBlocksTitle}">
    <p><fmt:message key="user.login.limits.blocks.note.username"/>
        <c:if test="${usernameBlocks.truncated}"><fmt:message key="user.login.limits.blocks.shown"><fmt:param value="${fn:length(usernameBlocks.rows)}"/></fmt:message></c:if></p>
    <table class="jive-table">
        <tr>
            <th nowrap><fmt:message key="user.login.limits.blocks.username"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.failures"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.ends"/></th>
            <th nowrap><fmt:message key="user.login.limits.blocks.account"/></th>
            <th style="width: 1%; white-space: nowrap">&nbsp;</th>
        </tr>
        <c:choose>
            <c:when test="${empty usernameBlocks.rows}">
                <tr>
                    <td style="text-align: center" colspan="5"><fmt:message key="global.list.empty"/></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="row" items="${usernameBlocks.rows}">
                    <tr>
                        <td><c:out value="${row.username}"/><c:if test="${row.usernameTruncated}"> <fmt:message key="user.login.limits.blocks.truncated"/></c:if></td>
                        <td><c:out value="${row.failureCount}"/></td>
                        <td><c:out value="${row.endsAt}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${row.accountLocked}">
                                    <c:url var="lockoutUrl" value="user-lockout.jsp"><c:param name="username" value="${row.lockoutUsername}"/></c:url>
                                    <fmt:message key="user.login.limits.blocks.locked">
                                        <fmt:param value="<a href='${fn:escapeXml(lockoutUrl)}'>"/>
                                        <fmt:param value="</a>"/>
                                    </fmt:message>
                                </c:when>
                                <c:otherwise>-</c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <form action="user-login-limits.jsp" method="post" style="margin: 0">
                                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                                <input type="hidden" name="action" value="clear">
                                <input type="hidden" name="block" value="${fn:escapeXml(row.token)}">
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
