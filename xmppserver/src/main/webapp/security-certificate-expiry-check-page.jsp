<%--@elvariable id="errors" type="java.util.Map<java.lang.String, java.lang.Object>"--%>
<%--@elvariable id="csrf" type="java.lang.String"--%>
<%--@elvariable id="isEnabled" type="java.lang.Boolean"--%>
<%--@elvariable id="isNotifyAdmins" type="java.lang.Boolean"--%>
<%--@elvariable id="frequencyHours" type="java.lang.Long"--%>
<%--@elvariable id="warningPeriodHours" type="java.lang.Long"--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%  webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="ssl.certificate.expirycheck.title"/></title>
<meta name="pageID" content="security-certificate-expiry-check"/>
</head>
<body>
<p>
<fmt:message key="ssl.certificate.expirycheck.info"/>
</p>

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:when test="${err.key eq 'frequency'}"><fmt:message key="ssl.certificate.expirycheck.error.frequency" /></c:when>
                    <c:when test="${err.key eq 'warningPeriod'}"><fmt:message key="ssl.certificate.expirycheck.error.warning-period" /></c:when>
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
    <c:when test="${param.success}">
        <admin:infobox type="success">
            <fmt:message key="ssl.certificate.expirycheck.save-success"/>
        </admin:infobox>
    </c:when>
</c:choose>

<form action="security-certificate-expiry-check.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}"/>

    <c:set var="configHeader"><fmt:message key="ssl.certificate.expirycheck.settings-header" /></c:set>
    <admin:contentBox title="${configHeader}">

        <h4><fmt:message key="ssl.certificate.expirycheck.enabled.legend"/></h4>
        <table>
        <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="isEnabled" value="true" id="rb02" ${isEnabled ? 'checked' : ''}>
                </td>
                <td>
                    <label for="rb02">
                    <b><fmt:message key="ssl.certificate.expirycheck.label_enable"/></b> - <fmt:message key="ssl.certificate.expirycheck.label_enable_info"/>
                    </label>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="isEnabled" value="false" id="rb01" ${isEnabled ? '' : 'checked'}>
                </td>
                <td>
                    <label for="rb01">
                    <b><fmt:message key="ssl.certificate.expirycheck.label_disable"/></b> - <fmt:message key="ssl.certificate.expirycheck.label_disable_info"/>
                    </label>
                </td>
            </tr>
        </tbody>
        </table>

        <br/>

        <h4><fmt:message key="ssl.certificate.expirycheck.notification.legend"/></h4>
        <table>
        <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="isNotifyAdmins" value="true" id="rb04" ${isNotifyAdmins ? 'checked' : ''}>
                </td>
                <td>
                    <label for="rb04">
                    <b><fmt:message key="ssl.certificate.expirycheck.notification.label_enable"/></b> - <fmt:message key="ssl.certificate.expirycheck.notification.label_enable_info"/>
                    </label>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="isNotifyAdmins" value="false" id="rb03" ${isNotifyAdmins ? '' : 'checked'}>
                </td>
                <td>
                    <label for="rb03">
                        <b><fmt:message key="ssl.certificate.expirycheck.notification.label_disable"/></b> - <fmt:message key="ssl.certificate.expirycheck.notification.label_disable_info"/>
                    </label>
                </td>
            </tr>
        </tbody>
        </table>

        <br/>

        <h4><fmt:message key="ssl.certificate.expirycheck.frequency.legend"/></h4>
        <table>
            <tbody>
            <tr>
                <td colspan="2">
                    <fmt:message key="ssl.certificate.expirycheck.frequency.info" />
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap; text-align: right" class="c1">
                    <label for="frequencyHours"><b><fmt:message key="ssl.certificate.expirycheck.frequency-label" /></b></label>
                </td>
                <td>
                    <input type="number" min="1" id="frequencyHours" name="frequencyHours" value="${admin:escapeHTMLTags(frequencyHours)}"> <fmt:message key="global.hours" />
                </td>
            </tr>

        </table>

        <br/>

        <h4><fmt:message key="ssl.certificate.expirycheck.warning-period.legend"/></h4>
        <table>
            <tbody>
            <tr>
                <td colspan="2">
                    <fmt:message key="ssl.certificate.expirycheck.warning-period.info" />
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap; text-align: right" class="c1">
                    <label for="warningPeriodHours"><b><fmt:message key="ssl.certificate.expirycheck.warning-period-label" /></b></label>
                </td>
                <td>
                    <input type="number" min="1" id="warningPeriodHours" name="warningPeriodHours" value="${admin:escapeHTMLTags(warningPeriodHours)}"> <fmt:message key="global.hours" />
                </td>
            </tr>

        </table>

    </admin:contentBox>

<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

</body>
</html>
