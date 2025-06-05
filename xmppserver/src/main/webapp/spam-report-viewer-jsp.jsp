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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="admin" uri="admin" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<jsp:useBean scope="request" id="spamReportManager" type="org.jivesoftware.openfire.spamreporting.SpamReportManager"/>
<jsp:useBean scope="request" id="search" type="org.jivesoftware.admin.servlet.SpamReportViewerServlet.Search"/>
<jsp:useBean scope="request" id="listPager" type="org.jivesoftware.util.ListPager"/>

<html>
<head>
    <title><fmt:message key="spam.report.viewer.title"/></title>
    <meta name="pageID" content="spam-report-viewer"/>
    <style>
        /* Override the even/odd coloring of jive-table, as on this page, each 'row' consists of two TR elements (one this is hidden). */
        .jive-table tr:nth-child(4n) td, .jive-table tr:nth-child(4n-1) td {
            background: #FFFFFF;
        }
        .jive-table tr:nth-child(4n-2) td, .jive-table tr:nth-child(4n-3) td {
            background: #FBFBFB;
        }
    </style>
</head>
<body>
    <p>
        <fmt:message key="spam.report.viewer.total"/>: <c:out value="${listPager.totalItemCount}"/>
        <c:if test="${listPager.filtered}">
            <fmt:message key="spam.report.viewer.filtered"/>: <c:out value="${listPager.filteredItemCount}"/>
        </c:if>

        <c:if test="${listPager.totalPages > 1}">
            <fmt:message key="global.showing"/> <c:out value="${listPager.firstItemNumberOnPage}"/>-<c:out
            value="${listPager.lastItemNumberOnPage}"/>
        </c:if>
        -- <fmt:message key="spam.report.viewer.per-page"/>: ${listPager.pageSizeSelection}

    </p>

    <p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
    <div class="jive-table">
        <table>
            <thead>
            <tr>
                <th nowrap><fmt:message key="spam.report.viewer.id" /></th>
                <th nowrap><fmt:message key="spam.report.viewer.reporter" /></th>
                <th nowrap><fmt:message key="spam.report.viewer.reported" /></th>
                <th nowrap><fmt:message key="spam.report.viewer.type" /></th>
                <th nowrap><fmt:message key="spam.report.viewer.timestamp" /></th>
                <th nowrap></th>
            </tr>
            <tr>
                <td></td>
                <td>
                    <input type="search"
                           id="searchReporter"
                           size="20"
                           value="<c:out value="${search.reporter}"/>"/>
                    <img src="images/search-16x16.png"
                         width="16" height="16"
                         class="clickable"
                         alt="Search" title="Search"
                         style="vertical-align: middle; cursor: pointer;"
                         onclick="submitForm();"
                    >
                </td>
                <td>
                    <input type="search"
                           id="searchReported"
                           size="20"
                           value="<c:out value="${search.reported}"/>"/>
                    <img src="images/search-16x16.png"
                         width="16" height="16"
                         class="clickable"
                         alt="Search" title="Search"
                         style="vertical-align: middle; cursor: pointer;"
                         onclick="submitForm();"
                    >
                </td>
                <td>
                </td>
                <td>
                    <table>
                        <tr>
                            <td nowrap>
                                <fmt:message key="spam.report.viewer.timestamp-from"/>:
                            </td>
                            <td nowrap>
                                <input type="search"
                                       id="searchFrom"
                                       placeholder="YYYY/MM/DD"
                                       size="10"
                                       value="<c:out value="${search.from}"/>"/>
                                <img src="images/search-16x16.png"
                                     width="16" height="16"
                                     class="clickable"
                                     alt="Search" title="Search"
                                     style="vertical-align: middle; cursor: pointer;"
                                     onclick="submitForm();"
                                >
                            </td>
                        </tr>
                        <tr>
                            <td nowrap>
                                <fmt:message key="spam.report.viewer.timestamp-to"/>:
                            </td>
                            <td nowrap>
                                <input type="search"
                                       id="searchTo"
                                       placeholder="YYYY/MM/DD"
                                       size="10"
                                       value="<c:out value="${search.to}"/>"/>
                                <img src="images/search-16x16.png"
                                     width="16" height="16"
                                     class="clickable"
                                     alt="Search" title="Search"
                                     style="vertical-align: middle; cursor: pointer;"
                                     onclick="submitForm();"
                                >
                            </td>
                        </tr>
                    </table>
                </td>
                <td></td>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="spamReport" items="${listPager.itemsOnCurrentPage}">
                <%--@elvariable id="spamReport" type="org.jivesoftware.openfire.spamreporting.SpamReport"--%>

                <tr>
                    <td><c:out value="${spamReport.id}"/></td>
                    <td><c:out value="${spamReport.reportingAddress}"/></td>
                    <td><c:out value="${spamReport.reportedAddress}"/></td>
                    <td><c:out value="${spamReport.reason}"/></td>
                    <td><c:out value="${admin:formatDateTimeInstant(spamReport.timestamp)}"/></td>
                    <td><a href="spam-report-details.jsp?reportId=${admin:escapeHTMLTags(spamReport.id)}"><img src="images/search-16x16.png" alt="<fmt:message key="spam.report.viewer.show_details"/>"/>&nbsp;<fmt:message key="spam.report.viewer.show_details"/></a></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
    <p><fmt:message key="global.pages"/>: [ ${listPager.pageLinks} ]</p>
    ${listPager.jumpToPageForm}
    <script>
        ${listPager.pageFunctions}
    </script>
</body>
