<%--
  -
  - Copyright (C) 2005-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
<%--@elvariable id="username" type="java.lang.String"--%>
<%--@elvariable id="displayName" type="java.lang.String"--%>
<%--@elvariable id="usernameUrlEncoded" type="java.lang.String"--%>
<%--@elvariable id="ownAccount" type="java.lang.Boolean"--%>
<%--@elvariable id="readOnly" type="java.lang.Boolean"--%>
<%--@elvariable id="delayedStartSupported" type="java.lang.Boolean"--%>
<%--@elvariable id="timeoutSupported" type="java.lang.Boolean"--%>
<%--@elvariable id="locked" type="java.lang.Boolean"--%>
<%--@elvariable id="lockStart" type="org.jivesoftware.admin.servlet.UserLockoutServlet.Time"--%>
<%--@elvariable id="lockEnd" type="org.jivesoftware.admin.servlet.UserLockoutServlet.Time"--%>
<%--@elvariable id="lockInForce" type="java.lang.Boolean"--%>
<%--@elvariable id="lockSetByLoginLimits" type="java.lang.Boolean"--%>
<%--@elvariable id="administrator" type="java.lang.Boolean"--%>
<%--@elvariable id="answer" type="org.jivesoftware.admin.servlet.UserLockoutServlet.Answer"--%>
<%--@elvariable id="loginLimits" type="org.jivesoftware.admin.servlet.UserLockoutServlet.LoginLimitsSection"--%>
<%--@elvariable id="row" type="org.jivesoftware.admin.servlet.UserLockoutServlet.BlockRow"--%>
<%--@elvariable id="userLink" type="java.lang.String"--%>
<%--@elvariable id="boldName" type="java.lang.String"--%>
<%--@elvariable id="answerUntil" type="java.lang.String"--%>
<%--@elvariable id="answerLockStart" type="java.lang.String"--%>
<%--@elvariable id="firstUntil" type="java.lang.String"--%>
<%--@elvariable id="secondUntil" type="java.lang.String"--%>
<%--@elvariable id="lockStartText" type="java.lang.String"--%>
<%--@elvariable id="lockEndText" type="java.lang.String"--%>
<%--@elvariable id="rowEnds" type="java.lang.String"--%>
<%--@elvariable id="lockTitle" type="java.lang.String"--%>
<%--@elvariable id="limitsTitle" type="java.lang.String"--%>
<%-- The view of UserLockoutServlet, which is mapped to user-lockout.jsp and provides every value shown here. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="admin" uri="admin" %>

<html>
<head>
    <title><fmt:message key="user.lockout.title"/></title>
    <meta name="subPageID" content="user-lockout"/>
    <meta name="extraParams" content="username=${fn:escapeXml(usernameUrlEncoded)}"/>
    <style>
        .lockout-relative, .lockout-hint { color: #666; }
        .lockout-hint { margin-top: 6px; }
        /* More specific than global.css .jive-contentBox form, which sets margin 0. */
        .jive-contentBox form.lockout-clear { margin-top: 12px; }
        .lockout-form td { padding: 2px 4px 2px 0; }
        /* The minutes field is shown only when its dropdown asks for minutes (and always where :has is not supported). */
        .lockout-when:has(option[value="-2"]:not(:checked)) .lockout-minutes { display: none; }
    </style>
</head>
<body>

<c:choose>
    <c:when test="${not empty errors['csrf']}">
        <admin:infobox type="error"><fmt:message key="global.csrf.failed"/></admin:infobox>
    </c:when>
    <c:when test="${param.lockfailed eq 'true'}">
        <admin:infobox type="error"><fmt:message key="user.lockout.limits.lock_failed"/></admin:infobox>
    </c:when>
    <c:when test="${param.unlockfailed eq 'true'}">
        <admin:infobox type="error"><fmt:message key="user.lockout.unlock_failed"/></admin:infobox>
    </c:when>
    <c:when test="${param.cleared eq 'true'}">
        <admin:infobox type="success">
            <fmt:message key="user.lockout.limits.cleared"/>
            <c:if test="${param.unlocked eq 'true'}"><fmt:message key="user.lockout.limits.cleared.unlocked"/></c:if>
            <c:if test="${loginLimits.stillRefused}"><fmt:message key="user.lockout.limits.cleared.still_refused"/></c:if>
        </admin:infobox>
    </c:when>
    <c:when test="${param.gone eq 'true'}">
        <admin:infobox type="info"><fmt:message key="user.lockout.limits.nothing_to_clear"/></admin:infobox>
    </c:when>
</c:choose>

<c:url var="userLink" value="user-properties.jsp"><c:param name="username" value="${username}"/></c:url>
<c:set var="boldName" value="<b><a href=\"${fn:escapeXml(userLink)}\">${fn:escapeXml(displayName)}</a></b>"/>
<c:set var="answerUntil">${fn:escapeXml(answer.until.at)}<c:if test="${not empty answer.until.fromNow}"> (${fn:escapeXml(answer.until.fromNow)})</c:if></c:set>
<c:set var="answerLockStart">${fn:escapeXml(answer.lockStart.at)}<c:if test="${not empty answer.lockStart.fromNow}"> (${fn:escapeXml(answer.lockStart.fromNow)})</c:if></c:set>

<%-- One sentence that answers whether the user can log in, and if not, why and until when. --%>
<admin:infobox type="${answer.refused ? 'warning' : 'info'}">
    <c:choose>
        <c:when test="${answer.kind eq 'LOCKED_BY_LOGIN_LIMITS'}">
            <fmt:message key="user.lockout.answer.locked_by_login_limits">
                <fmt:param value="${boldName}"/><fmt:param value="${answerUntil}"/><fmt:param value="${answer.failures}"/>
            </fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'LOCKED_UNTIL'}">
            <fmt:message key="user.lockout.answer.locked_until"><fmt:param value="${boldName}"/><fmt:param value="${answerUntil}"/></fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'LOCKED'}">
            <fmt:message key="user.lockout.answer.locked"><fmt:param value="${boldName}"/></fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'REFUSED_FROM_ANY_ADDRESS'}">
            <fmt:message key="user.lockout.answer.refused_from_any_address">
                <fmt:param value="${boldName}"/><fmt:param value="${answerUntil}"/><fmt:param value="${answer.failures}"/>
            </fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'REFUSED_FROM_ADDRESSES' and answer.addressCount eq 1}">
            <fmt:message key="user.lockout.answer.refused_from_one_address">
                <fmt:param value="${boldName}"/><fmt:param value="${fn:escapeXml(answer.addresses[0].address)}"/><fmt:param value="${answerUntil}"/>
            </fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'REFUSED_FROM_ADDRESSES' and answer.addressCount eq 2 and fn:length(answer.addresses) eq 2}">
            <%-- Each address with its own end, as the blocks can end at different times. --%>
            <c:set var="firstUntil">${fn:escapeXml(answer.addresses[0].until.at)}<c:if test="${not empty answer.addresses[0].until.fromNow}"> (${fn:escapeXml(answer.addresses[0].until.fromNow)})</c:if></c:set>
            <c:set var="secondUntil">${fn:escapeXml(answer.addresses[1].until.at)}<c:if test="${not empty answer.addresses[1].until.fromNow}"> (${fn:escapeXml(answer.addresses[1].until.fromNow)})</c:if></c:set>
            <fmt:message key="user.lockout.answer.refused_from_two_addresses">
                <fmt:param value="${boldName}"/><fmt:param value="${fn:escapeXml(answer.addresses[0].address)}"/><fmt:param value="${firstUntil}"/>
                <fmt:param value="${fn:escapeXml(answer.addresses[1].address)}"/><fmt:param value="${secondUntil}"/>
            </fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'REFUSED_FROM_ADDRESSES'}">
            <fmt:message key="user.lockout.answer.refused_from_addresses">
                <fmt:param value="${boldName}"/><fmt:param value="${answer.addressCount}"/><fmt:param value="${answerUntil}"/>
            </fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'CAN_LOG_IN_UNTIL_LOCK'}">
            <fmt:message key="user.lockout.answer.can_log_in_until_lock"><fmt:param value="${boldName}"/><fmt:param value="${answerUntil}"/></fmt:message>
        </c:when>
        <c:when test="${answer.kind eq 'CAN_LOG_IN_COUNTED'}">
            <fmt:message key="user.lockout.answer.can_log_in_counted"><fmt:param value="${boldName}"/></fmt:message>
        </c:when>
        <c:otherwise>
            <fmt:message key="user.lockout.answer.can_log_in"><fmt:param value="${boldName}"/></fmt:message>
        </c:otherwise>
    </c:choose>
    <c:if test="${answer.kind eq 'REFUSED_FROM_ANY_ADDRESS' or answer.kind eq 'REFUSED_FROM_ADDRESSES'}">
        <c:choose>
            <c:when test="${answer.accountLocked}">
                <fmt:message key="user.lockout.answer.lock_starts"><fmt:param value="${answerLockStart}"/></fmt:message>
            </c:when>
            <c:otherwise><fmt:message key="user.lockout.answer.not_locked"/></c:otherwise>
        </c:choose>
    </c:if>
</admin:infobox>

<%-- The account's lock, with the form that locks or unlocks the account. --%>
<fmt:message key="user.lockout.lock.title" var="lockTitle"/>
<admin:contentBox title="${lockTitle}">
    <c:choose>
        <c:when test="${locked}">
            <c:set var="lockStartText">${fn:escapeXml(lockStart.at)}<c:if test="${not empty lockStart.fromNow}"> <span class="lockout-relative">(${fn:escapeXml(lockStart.fromNow)})</span></c:if></c:set>
            <c:set var="lockEndText">${fn:escapeXml(lockEnd.at)}<c:if test="${not empty lockEnd.fromNow}"> <span class="lockout-relative">(${fn:escapeXml(lockEnd.fromNow)})</span></c:if></c:set>
            <p>
                <c:choose>
                    <c:when test="${lockSetByLoginLimits and not empty lockStart}">
                        <fmt:message key="user.lockout.lock.by_login_limits"><fmt:param value="${lockStartText}"/><fmt:param value="${lockEndText}"/></fmt:message>
                    </c:when>
                    <c:when test="${lockSetByLoginLimits}">
                        <fmt:message key="user.lockout.lock.by_login_limits.no_start"><fmt:param value="${lockEndText}"/></fmt:message>
                    </c:when>
                    <c:when test="${not empty lockStart and not empty lockEnd}">
                        <fmt:message key="user.lockout.lock.by_hand.start_end"><fmt:param value="${lockStartText}"/><fmt:param value="${lockEndText}"/></fmt:message>
                    </c:when>
                    <c:when test="${not empty lockStart}">
                        <fmt:message key="user.lockout.lock.by_hand.start"><fmt:param value="${lockStartText}"/></fmt:message>
                    </c:when>
                    <c:when test="${not empty lockEnd}">
                        <fmt:message key="user.lockout.lock.by_hand.end"><fmt:param value="${lockEndText}"/></fmt:message>
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="user.lockout.lock.by_hand"/>
                    </c:otherwise>
                </c:choose>
            </p>
            <c:if test="${readOnly}">
                <admin:infobox type="error"><fmt:message key="user.read_only"/></admin:infobox>
            </c:if>
            <form action="user-lockout.jsp" method="post">
                <input type="hidden" name="username" value="${fn:escapeXml(username)}">
                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                <input type="submit" name="unlock" value="<fmt:message key="user.lockout.unlock"/>" ${readOnly ? 'disabled' : ''}>
            </form>
            <c:if test="${lockSetByLoginLimits}">
                <p class="jive-description lockout-hint"><fmt:message key="user.lockout.unlock.hint"><fmt:param value="${fn:escapeXml(displayName)}"/></fmt:message></p>
            </c:if>
        </c:when>
        <c:otherwise>
            <p><fmt:message key="user.lockout.not_locked"/></p>
            <p><b><fmt:message key="user.lockout.lock.form"/></b></p>
            <c:if test="${readOnly}">
                <admin:infobox type="error"><fmt:message key="user.read_only"/></admin:infobox>
            </c:if>
            <form action="user-lockout.jsp" method="post">
                <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                <input type="hidden" name="username" value="${fn:escapeXml(username)}">
                <fieldset style="border: 0; margin: 0; padding: 0" ${readOnly ? 'disabled' : ''}>
                    <table class="lockout-form">
                        <c:if test="${delayedStartSupported}">
                            <tr>
                                <td><label for="startdelay"><fmt:message key="user.lockout.lock.starts"/></label></td>
                                <td class="lockout-when">
                                    <select name="startdelay" id="startdelay">
                                        <option value="-1" selected><fmt:message key="user.lockout.time.immediate"/></option>
                                        <option value="60"><fmt:message key="user.lockout.time.in"/> <fmt:message key="user.lockout.time.1hour"/></option>
                                        <option value="1440"><fmt:message key="user.lockout.time.in"/> <fmt:message key="user.lockout.time.1day"/></option>
                                        <option value="10080"><fmt:message key="user.lockout.time.in"/> <fmt:message key="user.lockout.time.1week"/></option>
                                        <option value="-2"><fmt:message key="user.lockout.time.in_minutes"/></option>
                                    </select>
                                    <span class="lockout-minutes">
                                        <input type="text" size="5" maxlength="10" id="startdelay_custom" name="startdelay_custom">
                                        <label for="startdelay_custom"><fmt:message key="user.lockout.time.minutes"/></label>
                                    </span>
                                </td>
                            </tr>
                        </c:if>
                        <c:if test="${timeoutSupported}">
                            <tr>
                                <td><label for="duration"><fmt:message key="user.lockout.lock.lasts"/></label></td>
                                <td class="lockout-when">
                                    <select name="duration" id="duration">
                                        <option value="-1" selected><fmt:message key="user.lockout.time.forever"/></option>
                                        <option value="60"><fmt:message key="user.lockout.time.for"/> <fmt:message key="user.lockout.time.1hour"/></option>
                                        <option value="1440"><fmt:message key="user.lockout.time.for"/> <fmt:message key="user.lockout.time.1day"/></option>
                                        <option value="10080"><fmt:message key="user.lockout.time.for"/> <fmt:message key="user.lockout.time.1week"/></option>
                                        <option value="-2"><fmt:message key="user.lockout.time.for_minutes"/></option>
                                    </select>
                                    <span class="lockout-minutes">
                                        <input type="text" size="5" maxlength="10" id="duration_custom" name="duration_custom">
                                        <label for="duration_custom"><fmt:message key="user.lockout.time.minutes"/></label>
                                    </span>
                                </td>
                            </tr>
                        </c:if>
                    </table>
                    <input type="submit" name="lock" value="<fmt:message key="user.lockout.lock"/>">
                </fieldset>
            </form>
            <c:if test="${ownAccount}">
                <p class="jive-description jive-warning-text"><fmt:message key="user.lockout.lock.own_account"/></p>
            </c:if>
        </c:otherwise>
    </c:choose>
</admin:contentBox>

<br>

<%-- The login limits' blocks for the user, the IP address blocks that refused the user, and the form that clears the user's login limits. --%>
<fmt:message key="user.lockout.limits.title" var="limitsTitle"/>
<admin:contentBox title="${limitsTitle}">
    <c:choose>
        <c:when test="${loginLimits.shown}">
            <c:if test="${loginLimits.blocked}">
                <table class="jive-table">
                    <tr>
                        <th nowrap><fmt:message key="user.lockout.limits.from"/></th>
                        <th nowrap><fmt:message key="user.login.limits.blocks.failures"/></th>
                        <th nowrap><fmt:message key="user.login.limits.blocks.ends"/></th>
                    </tr>
                    <c:if test="${not empty loginLimits.fromAnyAddress}">
                        <c:set var="row" value="${loginLimits.fromAnyAddress}"/>
                        <tr>
                            <td><fmt:message key="user.lockout.limits.any_address"/></td>
                            <td><c:out value="${row.failureCount}"/></td>
                            <td><c:out value="${row.ends.at}"/><c:if test="${not empty row.ends.fromNow}"><br><span class="lockout-relative"><c:out value="${row.ends.fromNow}"/></span></c:if></td>
                        </tr>
                    </c:if>
                    <c:forEach var="row" items="${loginLimits.fromAddresses}">
                        <tr>
                            <td><c:out value="${row.address}"/></td>
                            <td><c:out value="${row.failureCount}"/></td>
                            <td><c:out value="${row.ends.at}"/><c:if test="${not empty row.ends.fromNow}"><br><span class="lockout-relative"><c:out value="${row.ends.fromNow}"/></span></c:if></td>
                        </tr>
                    </c:forEach>
                </table>
                <c:if test="${loginLimits.hiddenFromAddresses gt 0}">
                    <p><fmt:message key="user.lockout.limits.hidden"><fmt:param value="${loginLimits.hiddenFromAddresses}"/></fmt:message></p>
                </c:if>
            </c:if>

            <c:forEach var="row" items="${loginLimits.refusingAddresses}">
                <c:set var="rowEnds">${fn:escapeXml(row.ends.at)}<c:if test="${not empty row.ends.fromNow}"> (${fn:escapeXml(row.ends.fromNow)})</c:if></c:set>
                <p>
                    <fmt:message key="user.lockout.limits.address_block">
                        <fmt:param value="${fn:escapeXml(displayName)}"/>
                        <fmt:param value="${fn:escapeXml(row.address)}"/>
                        <fmt:param value="${rowEnds}"/>
                        <fmt:param value="<a href=\"user-login-limits.jsp\">"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </p>
            </c:forEach>
            <c:if test="${loginLimits.hiddenRefusingAddresses gt 0}">
                <p>
                    <fmt:message key="user.lockout.limits.address_block.hidden">
                        <fmt:param value="${loginLimits.hiddenRefusingAddresses}"/>
                        <fmt:param value="${fn:escapeXml(displayName)}"/>
                        <fmt:param value="<a href=\"user-login-limits.jsp\">"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </p>
            </c:if>

            <%-- Only when nothing refuses the user: an IP address block that refused the user is described above. --%>
            <c:if test="${not loginLimits.blocked and not loginLimits.stillRefused and loginLimits.counted}">
                <p><fmt:message key="user.lockout.limits.counted"><fmt:param value="${fn:escapeXml(displayName)}"/></fmt:message></p>
            </c:if>

            <c:if test="${loginLimits.clearable}">
                <form class="lockout-clear" action="user-lockout.jsp" method="post">
                    <input type="hidden" name="username" value="${fn:escapeXml(username)}">
                    <input type="hidden" name="csrf" value="${fn:escapeXml(csrf)}">
                    <input type="hidden" name="action" value="clear">
                    <input type="submit" value="<fmt:message key="user.lockout.limits.clear"/>">
                </form>
                <p class="jive-description lockout-hint">
                    <c:choose>
                        <c:when test="${loginLimits.blocked}">
                            <fmt:message key="user.lockout.limits.hint.blocked"><fmt:param value="${fn:escapeXml(displayName)}"/></fmt:message>
                        </c:when>
                        <c:otherwise>
                            <fmt:message key="user.lockout.limits.hint.counted"><fmt:param value="${fn:escapeXml(displayName)}"/></fmt:message>
                        </c:otherwise>
                    </c:choose>
                    <c:if test="${not empty loginLimits.fromAnyAddress and lockSetByLoginLimits}"><fmt:message key="user.lockout.limits.hint.unlocks"/></c:if>
                    <fmt:message key="user.lockout.limits.hint.lock_kept"/>
                    <c:if test="${administrator}"><fmt:message key="user.lockout.limits.note.administrator"/></c:if>
                </p>
            </c:if>
        </c:when>
        <c:otherwise>
            <p><fmt:message key="user.lockout.limits.none"><fmt:param value="${fn:escapeXml(displayName)}"/></fmt:message></p>
        </c:otherwise>
    </c:choose>
</admin:contentBox>

</body>
</html>
