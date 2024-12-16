<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.jivesoftware.openfire.muc.MUCRoomRetiree" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="admin" uri="admin" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />

<%
    // Initialize paging
    final int DEFAULT_RANGE = 100;
    final int[] RANGE_PRESETS = {25, 50, 75, 100, 500, 1000};

    webManager.init(request, response, session, application, out);

    // Get parameters
    String mucname = ParamUtils.getParameter(request, "mucname");
    int start = ParamUtils.getIntParameter(request, "start", 0);
    int range = ParamUtils.getIntParameter(request, "range", webManager.getRowsPerPage("retirees-summary", DEFAULT_RANGE));
    boolean delete = ParamUtils.getBooleanParameter(request, "delete");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    // Validate range is one of the allowed presets, reset to default if invalid
    boolean validRange = false;
    for (int preset : RANGE_PRESETS) {
        if (preset == range) {
            validRange = true;
            break;
        }
    }
    if (!validRange) {
        range = DEFAULT_RANGE;
    }

    // CSRF check
    if (delete) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            delete = false;
        }
    }

    // Handle deletion
    if (delete) {
        String service = ParamUtils.getParameter(request, "service");
        String name = ParamUtils.getParameter(request, "name");

        if (service != null && name != null) {
            webManager.getMultiUserChatManager().deleteRetiree(service, name);
            response.sendRedirect("muc-room-retirees.jsp?mucname=" + URLEncoder.encode(service, "UTF-8") + "&deletesuccess=true");
            return;
        }
    }

    // Set up CSRF protection for subsequent requests
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);

    // Handle range parameter
    if (request.getParameter("range") != null) {
        webManager.setRowsPerPage("retirees-summary", range);
    }

    // Get MUC service
    MultiUserChatService mucService = null;
    if (mucname != null && webManager.getMultiUserChatManager().isServiceRegistered(mucname)) {
        mucService = webManager.getMultiUserChatManager().getMultiUserChatService(mucname);
    } else {
        for (MultiUserChatService muc : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
            if (!muc.isHidden()) {
                mucService = muc;
                break;
            }
        }
    }

    // Redirect if no service exists
    if (mucService == null) {
        response.sendRedirect("muc-service-summary.jsp");
        return;
    }

    // Get retiree information
    int retireeCount = webManager.getMultiUserChatManager().getRetireeCount(mucService.getServiceName());
    int numPages = (int)Math.ceil((double) retireeCount / (double)range);
    int curPage = (start/range) + 1;

    // Get list of services for dropdown
    Collection<MultiUserChatService> mucServices = new ArrayList<>();
    for (MultiUserChatService service : webManager.getMultiUserChatManager().getMultiUserChatServices()) {
        if (!service.isHidden()) {
            mucServices.add(service);
        }
    }

    // Get retirees for current page
    Collection<MUCRoomRetiree> retirees = webManager.getMultiUserChatManager().getRetirees(mucService.getServiceName(), start, range);

    // Set attributes for JSTL
    request.setAttribute("mucService", mucService);
    request.setAttribute("mucServices", mucServices);
    request.setAttribute("retireeCount", retireeCount);
    request.setAttribute("numPages", numPages);
    request.setAttribute("curPage", curPage);
    request.setAttribute("start", start);
    request.setAttribute("range", range);
    request.setAttribute("rangePresets", RANGE_PRESETS);
    request.setAttribute("retirees", retirees);
    request.setAttribute("csrf", csrfParam);
%>

<html>
<head>
    <title><fmt:message key="muc.room.retirees.title"/></title>
    <meta name="pageID" content="muc-room-retirees"/>
</head>
<body>

<p>
    <fmt:message key="muc.room.retirees.info" />
    <a href="muc-service-edit-form.jsp?mucname=${mucService.serviceName}"><c:out value="${mucService.serviceDomain}"/></a>
    <fmt:message key="muc.room.retirees.info2" />
</p>

<c:if test="${param.deletesuccess != null}">
    <admin:infoBox type="success">
        <fmt:message key="muc.room.retirees.deleted" />
    </admin:infoBox>
</c:if>

<p>
    <fmt:message key="muc.room.retirees.total" />:
    <b><fmt:formatNumber value="${retireeCount}"/></b>

    <c:if test="${numPages > 1}">
        --
        <fmt:message key="global.showing" />
        <fmt:formatNumber value="${start + 1}"/>-<fmt:formatNumber value="${Math.min(start + range, retireeCount.intValue())}"/>,
    </c:if>

    <c:if test="${retireeCount > 0}">
        <fmt:message key="muc.room.retirees.sorted" />
    </c:if>

    <c:if test="${mucServices.size() > 1}">
        -- <fmt:message key="muc.room.summary.service" />:
        <select name="mucname" id="mucname" onchange="location.href='muc-room-retirees.jsp?mucname=' + this.options[this.selectedIndex].value;">
            <c:forEach items="${mucServices}" var="service">
                <option value="<c:out value="${service.serviceName}"/>"
                    ${service.serviceName eq mucService.serviceName ? 'selected' : ''}>
                    <c:out value="${service.serviceDomain}"/>
                </option>
            </c:forEach>
        </select>
    </c:if>

    <c:if test="${retireeCount > 0}">
        -- <fmt:message key="muc.room.retirees.per_page" />:
        <select size="1" onchange="location.href='muc-room-retirees.jsp?start=0&range=' + this.options[this.selectedIndex].value;">
            <c:forEach items="${rangePresets}" var="preset">
                <option value="${preset}" ${preset eq range ? 'selected' : ''}>
                        ${preset}
                </option>
            </c:forEach>
        </select>
    </c:if>
</p>

<c:if test="${numPages > 1}">
    <p>
        <fmt:message key="global.pages" />:
        [
        <c:set var="num" value="${15 + curPage}"/>
        <c:set var="s" value="${curPage - 1}"/>
        <c:if test="${s > 5}"><c:set var="s" value="${s - 5}"/></c:if>
        <c:if test="${s < 5}"><c:set var="s" value="0"/></c:if>

        <c:if test="${s > 2}">
            <a href="muc-room-retirees.jsp?start=0&range=${range}">1</a> ...
        </c:if>

        <c:forEach begin="${s}" end="${numPages-1}" var="i" varStatus="status">
            <c:if test="${i < num}">
                <c:set var="isCurrent" value="${(i+1) == curPage}"/>
                <a href="muc-room-retirees.jsp?start=${i*range}&range=${range}"
                   class="${isCurrent ? 'jive-current' : ''}">${i+1}</a>
                ${!status.last ? ' ' : ''}
            </c:if>
        </c:forEach>

        <c:if test="${num < numPages}">
            ... <a href="muc-room-retirees.jsp?start=${(numPages-1)*range}&range=${range}">${numPages}</a>
        </c:if>
        ]
    </p>
</c:if>

<div class="jive-table">
    <table>
        <thead>
        <tr>
            <th>&nbsp;</th>
            <th nowrap><fmt:message key="user.create.name" /></th>
            <th nowrap><fmt:message key="muc.room.retirees.alternate_jid" /></th>
            <th nowrap><fmt:message key="muc.room.retirees.reason" /></th>
            <th nowrap><fmt:message key="muc.room.retirees.retired_at" /></th>
            <th nowrap><fmt:message key="global.delete" /></th>
        </tr>
        </thead>
        <tbody>
        <c:choose>
            <c:when test="${empty retirees}">
                <tr>
                    <td style="text-align: center" colspan="6">
                        <fmt:message key="muc.room.retirees.no_retirees" />
                    </td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach items="${retirees}" var="retiree" varStatus="status">
                    <tr>
                        <td style="width: 1%">${start + status.count}</td>
                        <td style="width: 20%"><c:out value="${retiree.name}"/></td>
                        <td style="width: 20%">
                            <c:if test="${not empty retiree.alternateJID}">
                                <c:out value="${retiree.alternateJID}"/>
                            </c:if>
                            <c:if test="${empty retiree.alternateJID}">
                                &nbsp;
                            </c:if>
                        </td>
                        <td style="width: 30%">
                            <c:if test="${not empty retiree.reason}">
                                <c:out value="${retiree.reason}"/>
                            </c:if>
                            <c:if test="${empty retiree.reason}">
                                &nbsp;
                            </c:if>
                        </td>
                        <td style="width: 20%">
                            <fmt:formatDate value="${retiree.retiredAt}" pattern="yyyy-MM-dd HH:mm:ss"/>
                        </td>
                        <td style="width: 1%; text-align: center; border-right:1px #ccc solid;">
                            <a href="muc-room-retirees.jsp?delete=true&service=${mucService.serviceName}&name=${retiree.name}&csrf=${csrf}"
                               title="<fmt:message key="global.click_delete" />">
                                <img src="images/delete-16x16.gif" alt="<fmt:message key="global.click_delete" />">
                            </a>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
        </tbody>
    </table>
</div>

<c:if test="${numPages > 1}">
    <p>
        <fmt:message key="global.pages" />:
        [
        <c:set var="num" value="${15 + curPage}"/>
        <c:set var="s" value="${curPage - 1}"/>
        <c:if test="${s > 5}"><c:set var="s" value="${s - 5}"/></c:if>
        <c:if test="${s < 5}"><c:set var="s" value="0"/></c:if>

        <c:if test="${s > 2}">
            <a href="muc-room-retirees.jsp?start=0&range=${range}">1</a> ...
        </c:if>

        <c:forEach begin="${s}" end="${numPages-1}" var="i" varStatus="status">
            <c:if test="${i < num}">
                <c:set var="isCurrent" value="${(i+1) == curPage}"/>
                <a href="muc-room-retirees.jsp?start=${i*range}&range=${range}"
                   class="${isCurrent ? 'jive-current' : ''}">${i+1}</a>
                ${!status.last ? ' ' : ''}
            </c:if>
        </c:forEach>

        <c:if test="${num < numPages}">
            ... <a href="muc-room-retirees.jsp?start=${(numPages-1)*range}&range=${range}">${numPages}</a>
        </c:if>
        ]
    </p>
</c:if>

</body>
</html>
