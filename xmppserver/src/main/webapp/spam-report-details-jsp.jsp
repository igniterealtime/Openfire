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
<%--@elvariable id="spamReport" type="org.jivesoftware.openfire.spamreporting.SpamReport"--%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="admin" uri="admin" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<html>
<head>
    <title><fmt:message key="spam.report.details.title"/></title>
    <meta name="pageID" content="spam-report-viewer"/>
    <meta name="extraParams" content="reportId=${admin:escapeHTMLTags(param['reportId'])}" />
</head>
<body>
    <c:choose>
        <c:when test="${empty spamReport}">
            <p>
                <fmt:message key="spam.report.details.report-not-found" />
            </p>
        </c:when>
        <c:otherwise>
            <p>
                <fmt:message key="spam.report.details.intro" />
            </p>

            <div class="jive-table">
                <table>
                    <thead>
                        <tr>
                            <th colspan="2">
                                <fmt:message key="spam.report.details.header" />
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.reporter" />
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${webManager.userManager.isRegisteredUser(spamReport.reportingAddress, false)}">
                                        <a href="user-properties.jsp?username=${admin:urlEncode(webManager.userManager.getUser(spamReport.reportingAddress).username)}">
                                            <c:out value="${webManager.userManager.getUser(spamReport.reportingAddress).username}"/>
                                        </a>
                                    </c:when>
                                    <c:otherwise>
                                        <c:out value="${spamReport.reportingAddress}"/>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.reported" />
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${webManager.userManager.isRegisteredUser(spamReport.reportedAddress, false)}">
                                        <a href="user-properties.jsp?username=${admin:urlEncode(webManager.userManager.getUser(spamReport.reportedAddress).username)}">
                                            <c:out value="${webManager.userManager.getUser(spamReport.reportedAddress).username}"/>
                                        </a>
                                    </c:when>
                                    <c:otherwise>
                                        <c:out value="${spamReport.reportedAddress}"/>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.type" />
                            </td>
                            <td>
                                <c:out value="${spamReport.reason}"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.timestamp" />
                            </td>
                            <td>
                                <c:out value="${admin:formatDateTimeInstant(spamReport.timestamp)}"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.originDomain" />
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${spamReport.allowedToReportToOriginDomain}">
                                        <c:set var="img" value="images/check.gif"/>
                                        <c:set var="alt"><fmt:message key="global.yes"/></c:set>
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="img" value="images/x.gif"/>
                                        <c:set var="alt"><fmt:message key="global.no"/></c:set>
                                    </c:otherwise>
                                </c:choose>
                                <img src="${img}" alt="${alt}"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="c1">
                                <fmt:message key="spam.report.viewer.thirdParty" />
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${spamReport.allowedToSendToThirdParties}">
                                        <c:set var="img" value="images/check.gif"/>
                                        <c:set var="alt"><fmt:message key="global.yes"/></c:set>
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="img" value="images/x.gif"/>
                                        <c:set var="alt"><fmt:message key="global.no"/></c:set>
                                    </c:otherwise>
                                </c:choose>
                                <img src="${img}" alt="${alt}"/>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <c:if test="${not empty spamReport.context}">

                <br/><br/>

                <div class="jive-table">
                    <table>
                        <thead>
                        <tr>
                            <th colspan="2">
                                <fmt:message key="spam.report.details.context" />
                            </th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${spamReport.context}" var="entry" varStatus="status">
                            <c:if test="${not empty entry.lang}">
                                <tr>
                                    <td class="c1">
                                        <fmt:message key="spam.report.details.context-lang" />
                                    </td>
                                    <td>
                                        <c:out value="${entry.lang}"/>
                                    </td>
                                </tr>
                            </c:if>
                            <tr>
                                <td colspan="2">
                                    <textarea style="width: 100%; min-height: 2em;" readonly><c:out value="${entry.value}"/></textarea>
                                </td>
                            </tr>

                            <c:if test="${not status.last}">
                                <tr><td colspan="2">&nbsp;</td></tr>
                            </c:if>
                        </c:forEach>

                        </tbody>
                    </table>
                </div>

            </c:if>

            <c:if test="${not empty spamReport.reportedStanzas}">

                <br/><br/>

                <div class="jive-table">
                    <table>
                        <thead>
                        <tr>
                            <th colspan="2">
                                <fmt:message key="spam.report.details.rawdata" />
                            </th>
                        </tr>
                        </thead>
                        <tbody>
                            <c:forEach items="${spamReport.reportedStanzas.entrySet()}" var="entry" varStatus="status">
                                <tr>
                                    <td class="c1">
                                        <fmt:message key="spam.report.details.stanzaid" />
                                    </td>
                                    <td>
                                        <c:out value="${entry.key}"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <c:choose>
                                            <c:when test="${entry.value.isPresent()}">
                                                <textarea style="width: 100%; min-height: 2em;" readonly><c:out value="${entry.value.get()}"/></textarea>
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:message key="spam.report.details.stanza-not-recorded" />
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>

                                <c:if test="${not status.last}">
                                    <tr><td colspan="2">&nbsp;</td></tr>
                                </c:if>
                            </c:forEach>

                        </tbody>
                    </table>
                </div>

            </c:if>

        </c:otherwise>
    </c:choose>

    <br/><br/>

    <form action="spam-report-viewer.jsp" type="get">
        <div style="text-align: center;">
            <input type="submit" name="back" value="<fmt:message key="spam.report.details.back_button" />">
        </div>
    </form>
</body>
