<%--
  -
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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>
<head>
    <title><fmt:message key="session.summary.title"/></title>
    <meta name="pageID" content="session-summary"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<p>
    <fmt:message key="session.summary.config.intro" />
</p>

<br>

<form action="session-summary-config.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="session.summary.config.title" var="settingsTitle"/>
    <admin:contentBox title="${settingsTitle}">
        <table>
            <colgroup>
                <col style="width: 1%"/>
                <col style="width: 99%"/>
            </colgroup>
            <tbody>
            <tr>
                <td><input name="showName" id="showName" type="checkbox" value="true" ${showName ? 'checked' : ''}></td>
                <td><label for="showName"><fmt:message key="session.details.name" /></label></td>
            </tr>
            <tr>
                <td><input name="showVersion" id="showVersion" type="checkbox" value="true" ${showVersion ? 'checked' : ''}></td>
                <td><label for="showVersion"><fmt:message key="session.details.version" /></label></td>
            </tr>
            <tr>
                <td><input name="showResource" id="showResource" type="checkbox" value="true" ${showResource ? 'checked' : ''}></td>
                <td><label for="showResource"><fmt:message key="session.details.resource" /></label></td>
            </tr>
            <c:if test="${clusteringEnabled}">
                <tr>
                    <td><input name="showClusterNode" id="showClusterNode" type="checkbox" value="true" ${showClusterNode ? 'checked' : ''}></td>
                    <td><label for="showClusterNode"><fmt:message key="session.details.node" /></label></td>
                </tr>

            </c:if>
            <tr>
                <td><input name="showStatus" id="showStatus" type="checkbox" value="true" ${showStatus ? 'checked' : ''}></td>
                <td><label for="showStatus"><fmt:message key="session.details.status" /></label></td>
            </tr>
            <tr>
                <td><input name="showPresence" id="showPresence" type="checkbox" value="true" ${showPresence ? 'checked' : ''}></td>
                <td><label for="showPresence"><fmt:message key="session.details.presence" /></label></td>
            </tr>
            <tr>
                <td><input name="showRxTx" id="showRxTx" type="checkbox" value="true" ${showRxTx ? 'checked' : ''}></td>
                <td><label for="showRxTx"><fmt:message key="session.details.received" /> <fmt:message key="session.details.transmitted" /></label></td>
            </tr>
            <tr>
                <td><input name="showIp" id="showIp" type="checkbox" value="true" ${showIp ? 'checked' : ''}></td>
                <td><label for="showIp"><fmt:message key="session.details.clientip" /></label></td>
            </tr>
            </tbody>
        </table>
    </admin:contentBox>

    <br/>

    <input type="submit" value="<fmt:message key="global.save_settings" />">
</form>

</body>
</html>
