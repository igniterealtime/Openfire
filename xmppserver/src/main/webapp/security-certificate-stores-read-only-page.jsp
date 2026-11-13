<%--
  ~ Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp"%>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
<head>
    <title><fmt:message key="ssl.certificates.read-only-stores.title"/></title>
    <meta name="pageID" content="security-certificate-stores-read-only"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed"/></c:when>
            <c:otherwise><fmt:message key="admin.error"/>: <c:out value="${err.value}"/> (<c:out value="${err.key}"/>)</c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<!-- Display success report, but only if there were no errors. -->
<c:if test="${param.saved and empty errors}">
    <admin:infobox type="success">
        <fmt:message key="ssl.certificates.read-only-stores.saved"/>
    </admin:infobox>
</c:if>

<p>
    <fmt:message key="ssl.certificates.read-only-stores.info"/>
</p>

<form action="security-certificate-stores-read-only.jsp" method="post">
    <input type="hidden" name="csrf" value="<c:out value="${csrf}"/>">

    <fmt:message key="ssl.certificates.read-only-stores.settings" var="settingsTitle"/>
    <admin:contentBox title="${settingsTitle}">
        <table>
            <tbody>
            <tr>
                <td>
                    <input type="checkbox" id="${checkboxParameter}" name="${checkboxParameter}" value="true" ${readOnly ? 'checked' : ''}/>
                </td>
                <td>
                    <label for="${checkboxParameter}"><fmt:message key="ssl.certificates.read-only-stores.label"/></label>
                </td>
            </tr>
            </tbody>
        </table>

        <br/>

        <input type="submit" value="<fmt:message key="global.save_settings" />">
    </admin:contentBox>
</form>

</body>
</html>
