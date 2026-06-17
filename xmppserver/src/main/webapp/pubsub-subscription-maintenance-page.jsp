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
<%--@elvariable id="totalRows" type="java.lang.Long"--%>
<%--@elvariable id="distinctSubscriptions" type="java.lang.Long"--%>
<%--@elvariable id="removableRows" type="java.lang.Long"--%>
<%--@elvariable id="cleanupRecommended" type="java.lang.Boolean"--%>
<%--@elvariable id="redundancyPercent" type="java.lang.Long"--%>
<%--@elvariable id="analysisAvailable" type="java.lang.Boolean"--%>
<%--@elvariable id="analysisError" type="java.lang.String"--%>
<%--@elvariable id="progressPhase" type="java.lang.String"--%>
<%--@elvariable id="progressPercent" type="java.lang.Integer"--%>
<%--@elvariable id="progressRemoved" type="java.lang.Long"--%>
<%--@elvariable id="excludedServiceCount" type="java.lang.Integer"--%>
<%--@elvariable id="justStarted" type="java.lang.Boolean"--%>
<%--@elvariable id="clusteringAvailable" type="java.lang.Boolean"--%>
<%--@elvariable id="clusteringEnabled" type="java.lang.Boolean"--%>
<%--@elvariable id="clusteringStarted" type="java.lang.Boolean"--%>
<%--@elvariable id="clusterNodeCount" type="java.lang.Integer"--%>
<%--@elvariable id="csrf" type="java.lang.String"--%>
<%--@elvariable id="errorMessage" type="java.lang.String"--%>
<%--@elvariable id="errorParam" type="java.lang.String"--%>
<%--@elvariable id="successMessage" type="java.lang.String"--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out ); %>

<%
    // Retrieve messages from session (set by the POST handler), then clear them (POST-Redirect-GET).
    String errorMessage = (String) session.getAttribute("errorMessage");
    String errorParam = (String) session.getAttribute("errorParam");
    String successMessage = (String) session.getAttribute("successMessage");

    if (errorMessage != null) {
        session.removeAttribute("errorMessage");
        session.removeAttribute("errorParam");
        request.setAttribute("errorMessage", errorMessage);
        if (errorParam != null) {
            request.setAttribute("errorParam", errorParam);
        }
    }
    if (successMessage != null) {
        session.removeAttribute("successMessage");
        request.setAttribute("successMessage", successMessage);
    }
%>

<html>
<head>
    <title><fmt:message key="pubsub.subscription.maintenance.title"/></title>
    <meta name="pageID" content="server-settings"/>
    <style>
        .warning-box {
            background-color: #fff3cd;
            border: 1px solid #ffc107;
            border-radius: 4px;
            padding: 15px;
            margin: 15px 0;
        }
        .warning-box h3 {
            margin-top: 0;
            color: #856404;
        }
        .maintenance-checklist {
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            padding: 15px;
            margin: 15px 0;
        }
        .maintenance-checklist input[type="checkbox"] {
            margin-right: 8px;
            vertical-align: middle;
        }
        .maintenance-checklist label {
            display: block;
            margin: 10px 0;
            font-weight: bold;
        }
        .progress-outer {
            background-color: #e9ecef;
            border-radius: 4px;
            height: 22px;
            width: 100%;
            margin: 10px 0;
            overflow: hidden;
        }
        .progress-inner {
            background-color: #28a745;
            height: 100%;
            width: 0;
            text-align: center;
            color: #fff;
            font-size: 12px;
            line-height: 22px;
            transition: width 0.4s ease;
        }
    </style>
</head>
<body>

<%-- Page-level status messages, shown first for consistency with other admin pages. --%>
<c:if test="${not empty errorMessage}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${not empty errorParam}">
                <fmt:message key="${errorMessage}">
                    <fmt:param value="${errorParam}"/>
                </fmt:message>
            </c:when>
            <c:otherwise>
                <fmt:message key="${errorMessage}"/>
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:if>

<c:if test="${not empty successMessage}">
    <admin:infobox type="success">
        <fmt:message key="${successMessage}"/>
    </admin:infobox>
</c:if>

<c:if test="${not analysisAvailable}">
    <admin:infobox type="error">
        <fmt:message key="pubsub.subscription.maintenance.analysis-failed">
            <fmt:param value="${analysisError}"/>
        </fmt:message>
    </admin:infobox>
</c:if>

<%-- Completed/failed banners, toggled by the progress poller (kept here so all status sits at the top). --%>
<div id="completedSection" style="display: ${progressPhase == 'COMPLETED' ? 'block' : 'none'};">
    <admin:infobox type="success">
        <fmt:message key="pubsub.subscription.maintenance.progress.completed">
            <fmt:param><fmt:formatNumber value="${progressRemoved}"/></fmt:param>
        </fmt:message>
    </admin:infobox>
</div>
<div id="failedSection" style="display: ${progressPhase == 'FAILED' ? 'block' : 'none'};">
    <admin:infobox type="error">
        <fmt:message key="pubsub.subscription.maintenance.progress.failed"/>
    </admin:infobox>
</div>

<c:if test="${analysisAvailable and not cleanupRecommended and progressPhase != 'RUNNING'}">
    <admin:infobox type="success">
        <fmt:message key="pubsub.subscription.maintenance.nothing-to-do"/>
    </admin:infobox>
</c:if>

<p>
    <fmt:message key="pubsub.subscription.maintenance.info"/>
</p>

<c:if test="${analysisAvailable}">

    <%-- Status table: the effect of a cleanup, in row counts. --%>
    <div class="jive-table">
        <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead>
            <tr>
                <th colspan="2"><fmt:message key="pubsub.subscription.maintenance.status.title"/></th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td class="c1"><fmt:message key="pubsub.subscription.maintenance.status.total-rows"/></td>
                <td class="c2"><strong><fmt:formatNumber value="${totalRows}"/></strong></td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="pubsub.subscription.maintenance.status.distinct"/></td>
                <td class="c2"><strong><fmt:formatNumber value="${distinctSubscriptions}"/></strong></td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="pubsub.subscription.maintenance.status.removable"/></td>
                <td class="c2">
                    <strong><fmt:formatNumber value="${removableRows}"/></strong>
                    (<fmt:formatNumber value="${redundancyPercent}"/>%)
                </td>
            </tr>
            <tr>
                <td class="c1"><fmt:message key="pubsub.subscription.maintenance.status.protected-services"/></td>
                <td class="c2"><strong><fmt:formatNumber value="${excludedServiceCount}"/></strong></td>
            </tr>
            </tbody>
        </table>
    </div>

    <%-- Progress bar: shown while a cleanup runs (initially visible if a run is already in progress on page load). --%>
    <div id="progressSection" style="display: ${progressPhase == 'RUNNING' ? 'block' : 'none'};">
        <h3><fmt:message key="pubsub.subscription.maintenance.progress.title"/></h3>
        <div class="progress-outer">
            <div id="progressBar" class="progress-inner" style="width: ${progressPercent}%;">
                <fmt:formatNumber value="${progressPercent}"/>%
            </div>
        </div>
        <p id="progressDetail"></p>
    </div>

    <%-- Cleanup form: shown only when there is something to remove and no run is in progress. --%>
    <c:if test="${cleanupRecommended}">
        <c:choose>
            <c:when test="${clusteringEnabled and not clusteringStarted}">
                <admin:infobox type="error">
                    <fmt:message key="pubsub.subscription.maintenance.error.cluster-enabled-not-started"/>
                </admin:infobox>
            </c:when>
            <c:when test="${clusterNodeCount > 1}">
                <admin:infobox type="error">
                    <fmt:message key="pubsub.subscription.maintenance.error.multi-node-active">
                        <fmt:param><fmt:formatNumber value="${clusterNodeCount}"/></fmt:param>
                    </fmt:message>
                </admin:infobox>
            </c:when>
            <c:otherwise>
                <div id="formSection" style="display: ${progressPhase == 'RUNNING' ? 'none' : 'block'};">
                    <div class="warning-box">
                        <h3><fmt:message key="pubsub.subscription.maintenance.warning.title"/></h3>
                        <p><fmt:message key="pubsub.subscription.maintenance.warning.irreversible"/></p>
                        <ul>
                            <li><fmt:message key="pubsub.subscription.maintenance.warning.backup"/></li>
                            <li><fmt:message key="pubsub.subscription.maintenance.warning.quiescent"/></li>
                            <c:if test="${clusteringEnabled}">
                                <li><fmt:message key="pubsub.subscription.maintenance.warning.cluster"/></li>
                            </c:if>
                        </ul>
                    </div>

                    <form id="cleanupForm" action="pubsub-subscription-maintenance.jsp" method="post"
                          onsubmit="return confirm('<fmt:message key="pubsub.subscription.maintenance.confirm"/>');">
                        <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}">
                        <input type="hidden" name="action" value="cleanup">

                        <div class="maintenance-checklist">
                            <h3><fmt:message key="pubsub.subscription.maintenance.checklist.title"/></h3>
                            <label>
                                <input type="checkbox" name="dbBackup" value="true" required>
                                <fmt:message key="pubsub.subscription.maintenance.checklist.db-backup"/>
                            </label>
                        </div>

                        <input type="submit" value="<fmt:message key="pubsub.subscription.maintenance.button.cleanup"/>">
                    </form>
                </div>
            </c:otherwise>
        </c:choose>
    </c:if>

</c:if>

<script>
    (function () {
        // Localized labels passed from the page so the script needs no hardcoded strings.
        var LBL_DETAIL = "<fmt:message key="pubsub.subscription.maintenance.progress.detail"/>";

        var phase = "${admin:escapeHTMLTags(progressPhase)}";

        function show(id, visible) {
            var el = document.getElementById(id);
            if (el) { el.style.display = visible ? "block" : "none"; }
        }

        function render(data) {
            var bar = document.getElementById("progressBar");
            if (bar) {
                bar.style.width = data.percent + "%";
                bar.textContent = data.percent + "%";
            }
            var detail = document.getElementById("progressDetail");
            if (detail) {
                // LBL_DETAIL is expected to contain two {0}/{1} placeholders (removed / total).
                detail.textContent = LBL_DETAIL
                    .replace("{0}", data.removed)
                    .replace("{1}", data.total);
            }
        }

        function poll() {
            var xhr = new XMLHttpRequest();
            xhr.open("GET", "pubsub-subscription-maintenance.jsp?action=progress", true);
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) { return; }
                if (xhr.status !== 200) {
                    // On a transient error, keep polling; the next tick may succeed.
                    setTimeout(poll, 2000);
                    return;
                }
                var data;
                try { data = JSON.parse(xhr.responseText); } catch (e) { setTimeout(poll, 2000); return; }

                if (data.phase === "RUNNING") {
                    show("progressSection", true);
                    show("formSection", false);
                    render(data);
                    setTimeout(poll, 1500);
                } else if (data.phase === "COMPLETED") {
                    render({ percent: 100, removed: data.removed, total: data.total });
                    show("progressSection", false);
                    show("completedSection", true);
                    // Reload so the status table reflects the now-reduced row counts.
                    setTimeout(function () { window.location.href = "pubsub-subscription-maintenance.jsp"; }, 1500);
                } else if (data.phase === "FAILED") {
                    show("progressSection", false);
                    show("failedSection", true);
                }
            };
            xhr.send();
        }

        // Begin polling if a run is already in progress, or was just started (the background job may still be in its
        // brief pre-RUNNING analysis phase, so do not rely on the phase alone immediately after starting).
        var justStarted = ${justStarted ? 'true' : 'false'};
        if (phase === "RUNNING" || justStarted) {
            show("progressSection", true);
            show("formSection", false);
            poll();
        }
    })();
</script>

</body>
</html>
