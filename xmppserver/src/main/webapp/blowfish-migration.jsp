<%--
  ~ Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%  webManager.init(request, response, session, application, out ); %>

<%
    // Retrieve messages from session (set by POST handler)
    String errorMessage = (String) session.getAttribute("errorMessage");
    String errorParam = (String) session.getAttribute("errorParam");
    String successMessage = (String) session.getAttribute("successMessage");
    Integer successParam = (Integer) session.getAttribute("successParam");

    // Clear session attributes after retrieving to prevent message reappearance
    if (errorMessage != null) {
        session.removeAttribute("errorMessage");
        session.removeAttribute("errorParam");
    }
    if (successMessage != null) {
        session.removeAttribute("successMessage");
        session.removeAttribute("successParam");
    }

    // Set as request attributes for JSTL tags to access
    if (errorMessage != null) {
        request.setAttribute("errorMessage", errorMessage);
        if (errorParam != null) {
            request.setAttribute("errorParam", errorParam);
        }
    }
    if (successMessage != null) {
        request.setAttribute("successMessage", successMessage);
        if (successParam != null) {
            request.setAttribute("successParam", successParam);
        }
    }
%>

<html>
<head>
    <title><fmt:message key="security.blowfish.migration.title"/></title>
    <meta name="pageID" content="security-blowfish-migration"/>
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
        .migration-checklist {
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            padding: 15px;
            margin: 15px 0;
        }
        .migration-checklist input[type="checkbox"] {
            margin-right: 8px;
        }
        .migration-checklist label {
            display: block;
            margin: 10px 0;
            font-weight: bold;
        }
    </style>
</head>
<body>

<p>
<fmt:message key="security.blowfish.migration.info"/>
</p>

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
        <c:choose>
            <c:when test="${not empty successParam}">
                <fmt:message key="${successMessage}">
                    <fmt:param value="${successParam}"/>
                </fmt:message>
            </c:when>
            <c:otherwise>
                <fmt:message key="${successMessage}"/>
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:if>

<c:choose>
    <c:when test="${not isBlowfish}">
        <admin:infobox type="info">
            <fmt:message key="security.blowfish.migration.not-blowfish">
                <fmt:param value="${encryptionAlgorithm}"/>
            </fmt:message>
        </admin:infobox>
    </c:when>

    <c:when test="${alreadyMigrated}">
        <c:if test="${empty successMessage}">
            <admin:infobox type="success">
                <fmt:message key="security.blowfish.migration.already-migrated"/>
            </admin:infobox>
        </c:if>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                    <tr>
                        <th colspan="2"><fmt:message key="security.blowfish.migration.status.title"/></th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="c1"><fmt:message key="security.blowfish.migration.status.current-kdf"/></td>
                        <td class="c2"><strong><c:out value="${currentKdf}"/></strong></td>
                    </tr>
                    <tr>
                        <td colspan="2" class="c1">
                            <fmt:message key="security.blowfish.migration.status.using-pbkdf2"/>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </c:when>

    <c:when test="${needsMigration}">
        <div class="warning-box">
            <h3><fmt:message key="security.blowfish.migration.warning.title"/></h3>
            <p><fmt:message key="security.blowfish.migration.warning.irreversible"/></p>
            <ul>
                <li><fmt:message key="security.blowfish.migration.warning.backup"/></li>
                <li><fmt:message key="security.blowfish.migration.warning.cluster"/></li>
                <li><fmt:message key="security.blowfish.migration.warning.downtime"/></li>
            </ul>
        </div>

        <div class="jive-table">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <thead>
                    <tr>
                        <th colspan="2"><fmt:message key="security.blowfish.migration.status.title"/></th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="c1"><fmt:message key="security.blowfish.migration.status.current-kdf"/></td>
                        <td class="c2"><strong><c:out value="${currentKdf}"/></strong></td>
                    </tr>
                    <tr>
                        <td class="c1"><fmt:message key="security.blowfish.migration.status.encrypted-count"/></td>
                        <td class="c2"><strong><c:out value="${encryptedPropertyCount}"/></strong></td>
                    </tr>
                </tbody>
            </table>
        </div>

        <form action="security-blowfish-migration.jsp" method="post" onsubmit="return confirm('<fmt:message key="security.blowfish.migration.confirm"/>');">
            <input type="hidden" name="csrf" value="${csrf}">
            <input type="hidden" name="action" value="migrate">

            <div class="migration-checklist">
                <h3><fmt:message key="security.blowfish.migration.checklist.title"/></h3>
                <label>
                    <input type="checkbox" name="dbBackup" value="true" required>
                    <fmt:message key="security.blowfish.migration.checklist.db-backup"/>
                </label>
                <label>
                    <input type="checkbox" name="securityBackup" value="true" required>
                    <fmt:message key="security.blowfish.migration.checklist.security-backup"/>
                </label>
                <label>
                    <input type="checkbox" name="clusterOffline" value="true" required>
                    <fmt:message key="security.blowfish.migration.checklist.cluster-offline"/>
                </label>
            </div>

            <input type="submit" value="<fmt:message key="security.blowfish.migration.button.migrate"/>">
        </form>
    </c:when>
</c:choose>

</body>
</html>
