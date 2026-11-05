<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  ~ Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
  ~
  --%>
<%--
  Renders the server-to-server connection test. S2SConnectionTestServlet validates the input, runs the test and
  prepares every text shown here, already localised; this page only renders it. Every value is written with c:out,
  as several hold text that the remote server or the request controls.
--%>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<html>
<head>
    <title><fmt:message key="server2server.settings.testing.title"/></title>
    <meta name="pageID" content="server-connectiontest"/>
    <link rel="stylesheet" type="text/css" href="style/server-connectiontest.css">
    <script src="js/server-connectiontest.js"></script>
</head>
<body>

    <c:if test="${not empty errorMessage}">
        <admin:infobox type="error"><c:out value="${errorMessage}"/></admin:infobox>
    </c:if>

    <p>
        <fmt:message key="server2server.settings.testing.info" />
    </p>

    <fmt:message key="server2server.settings.testing.boxtitle" var="s2sTitle"/>
    <admin:contentBox title="${s2sTitle}">
        <form action="server-connectiontest.jsp" method="post" id="s2s-test-form">
            <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>"/>
            <label for="server2server-testing-domain"><fmt:message key="server2server.settings.testing.domain"/></label>
            <input type="text" name="server2server-testing-domain" id="server2server-testing-domain" value="<c:out value='${s2sDomain}'/>">
            <input type="submit" name="s2s-test" id="s2s-test" value="<fmt:message key="global.test" />">
            <div id="spinner"></div>
        </form>
    </admin:contentBox>

    <c:if test="${tested}">
        <admin:infobox type="${summaryType}"><c:out value="${summary}"/></admin:infobox>

        <p><b><c:out value="${resultsHeading}"/></b></p>
        <div class="jive-table s2s-checklist">
            <table>
                <thead>
                    <tr>
                        <th scope="col">&nbsp;</th>
                        <th scope="col" nowrap><fmt:message key="server2server.settings.testing.column.check"/></th>
                        <th scope="col" nowrap><fmt:message key="server2server.settings.testing.column.result"/></th>
                        <th scope="col"><fmt:message key="server2server.settings.testing.column.detail"/></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="row" items="${rows}">
                        <c:set var="rowClass" value="${row.firstFailure ? 'jive-highlight' : (empty row.icon ? 's2s-unreached' : '')}"/>
                        <tr class="${rowClass}">
                            <td><c:out value="${row.number}"/></td>
                            <td><c:out value="${row.title}"/></td>
                            <td class="s2s-status">
                                <c:if test="${not empty row.icon}"><img src="images/${row.icon}" width="16" height="16" alt=""></c:if><c:out value="${row.statusLabel}"/>
                            </td>
                            <td><c:out value="${row.detail}"/></td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </div>

        <c:if test="${not empty advice}">
            <fmt:message key="server2server.settings.testing.advice.title" var="adviceTitle"/>
            <admin:contentBox title="${adviceTitle}">
                <c:forEach var="item" items="${advice}" varStatus="adviceStatus">
                    <div class="s2s-advice${adviceStatus.first ? '' : ' s2s-advice-next'}">
                        <p class="s2s-advice-heading"><c:out value="${item.heading}"/></p>
                        <c:if test="${not empty item.dnsOverride or not empty item.local}">
                            <p><b><fmt:message key="server2server.settings.testing.advice.local"/>:</b>
                                <c:if test="${not empty item.dnsOverride}"><c:out value="${item.dnsOverride}"/></c:if>
                                <c:if test="${not empty item.local}"><c:out value="${item.local}"/></c:if></p>
                        </c:if>
                        <c:if test="${not empty item.note}">
                            <p><c:out value="${item.note}"/></p>
                        </c:if>
                        <c:if test="${not empty item.links}">
                            <p>
                                <fmt:message key="server2server.settings.testing.advice.pages"/>
                                <c:forEach var="link" items="${item.links}" varStatus="linkStatus">
                                    <%-- A new tab, so that the results stay: they come from a POST, which going back would resubmit. --%>
                                    <a href="<c:out value='${link.url}'/>" target="_blank" rel="noopener"><c:out value="${link.label}"/></a><c:if test="${not linkStatus.last}">,</c:if>
                                </c:forEach>
                            </p>
                        </c:if>
                        <c:if test="${not empty item.remote}">
                            <p><b><fmt:message key="server2server.settings.testing.advice.remote"/>:</b> <c:out value="${item.remote}"/></p>
                            <p class="s2s-advice-note"><fmt:message key="server2server.settings.testing.advice.remote.note"/></p>
                        </c:if>
                    </div>
                </c:forEach>
            </admin:contentBox>
        </c:if>

        <c:if test="${not empty stanzas or not empty certs or not empty logs}">
            <details class="s2s-details">
                <summary class="jive-contentBoxHeader"><fmt:message key="server2server.settings.testing.details"/></summary>
                <div class="jive-contentBox">
                    <table>
                        <tr>
                            <td class="s2s-details-label"><label for="server2server-testing-stanzas"><fmt:message key="server2server.settings.testing.xmpp"/></label></td>
                            <td><textarea id="server2server-testing-stanzas" rows="12" readonly><c:out value="${stanzas}"/></textarea></td>
                        </tr>
                        <tr>
                            <td class="s2s-details-label"><label for="server2server-testing-certs"><fmt:message key="server2server.settings.testing.certificates"/></label></td>
                            <td><textarea id="server2server-testing-certs" rows="12" readonly><c:out value="${certs}"/></textarea></td>
                        </tr>
                        <tr>
                            <td class="s2s-details-label"><label for="server2server-testing-logs"><fmt:message key="server2server.settings.testing.logs"/></label></td>
                            <td><textarea id="server2server-testing-logs" rows="12" readonly><c:out value="${logs}"/></textarea></td>
                        </tr>
                    </table>
                </div>
            </details>
        </c:if>
    </c:if>

</body>
</html>
