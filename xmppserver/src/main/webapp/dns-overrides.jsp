<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.net.DNSUtil" %>
<%@ page import="org.jivesoftware.openfire.net.SrvRecord" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>
<%
    final Map<String, String> errors = new LinkedHashMap<>();

    final boolean save = ParamUtils.getParameter(request, "save") != null;
    final String deletePattern = ParamUtils.getParameter(request, "deletePattern", true);
    final String editPattern   = ParamUtils.getParameter(request, "editPattern", true);
    String domainPattern = ParamUtils.getParameter(request, "domainPattern", true);
    String targetHost    = ParamUtils.getParameter(request, "targetHost", true);
    String targetPort    = ParamUtils.getParameter(request, "targetPort", true);
    final boolean success = ParamUtils.getBooleanParameter(request, "success");

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save || deletePattern != null) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", null);
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (deletePattern != null && errors.isEmpty()) {
        final String normalizedPattern = deletePattern.trim().toLowerCase(Locale.ROOT);
        final Map<String, SrvRecord> current = DNSUtil.getDnsOverride();
        if (current != null && current.containsKey(normalizedPattern)) {
            final Map<String, SrvRecord> updated = new HashMap<>(current);
            updated.remove(normalizedPattern);
            // Pass null (not an empty map) so DNSUtil removes the property entirely.
            DNSUtil.setDnsOverride(updated.isEmpty() ? null : updated);
            webManager.logEvent("Updated DNS override configuration.",
                "Removed DNS override for pattern '" + normalizedPattern + "'.");
        }
        response.sendRedirect("dns-overrides.jsp?success=true");
        return;
    }

    if (save && errors.isEmpty()) {
        // Validate that all required fields were submitted.
        if (domainPattern == null || domainPattern.trim().isEmpty()) {
            errors.put("domainPattern.required", null);
        }
        if (targetHost == null || targetHost.trim().isEmpty()) {
            errors.put("targetHost.required", null);
        }
        if (targetPort == null || targetPort.trim().isEmpty()) {
            errors.put("targetPort.required", null);
        }

        // Protect DNSUtil's property encoding format from delimiter collisions in user-provided values.
        final String persistenceDelimiters = "{},:";
        if (!errors.containsKey("domainPattern.required") && domainPattern != null) {
            final String trimmedDomainPattern = domainPattern.trim();
            for (char delimiter : persistenceDelimiters.toCharArray()) {
                if (trimmedDomainPattern.indexOf(delimiter) >= 0) {
                    errors.put("domainPattern.invalid-delimiters", null);
                    break;
                }
            }
        }

        if (!errors.containsKey("targetHost.required") && targetHost != null) {
            final String trimmedTargetHost = targetHost.trim();
            if (trimmedTargetHost.indexOf(':') >= 0) {
                errors.put("targetHost.ipv6-not-supported", null);
            } else {
                for (char delimiter : "{},".toCharArray()) {
                    if (trimmedTargetHost.indexOf(delimiter) >= 0) {
                        errors.put("targetHost.invalid-delimiters", null);
                        break;
                    }
                }
            }
        }

        // Parse and range-check the port number (must be 1–65535).
        Integer parsedPort = null;
        if (!errors.containsKey("targetPort.required") && targetPort != null) {
            try {
                parsedPort = Integer.valueOf(targetPort.trim());
                if (parsedPort < 1 || parsedPort > 65535) {
                    errors.put("targetPort.invalid", targetPort);
                    parsedPort = null;
                }
            } catch (NumberFormatException e) {
                errors.put("targetPort.invalid", targetPort);
            }
        }

        if (errors.isEmpty()) {
            // Normalise the key to lowercase, copy the existing map, add/replace the entry.
            final String normalizedPattern = domainPattern.trim().toLowerCase(Locale.ROOT);
            final Map<String, SrvRecord> current = DNSUtil.getDnsOverride();
            final Map<String, SrvRecord> updated = (current != null) ? new HashMap<>(current) : new HashMap<>();
            updated.put(normalizedPattern, new SrvRecord(targetHost.trim(), parsedPort, false));
            DNSUtil.setDnsOverride(updated);
            webManager.logEvent("Updated DNS override configuration.", "Configured DNS override '" + normalizedPattern + "' to '" + targetHost.trim() + ":" + parsedPort + "'.");
            response.sendRedirect("dns-overrides.jsp?success=true");
            return;
        }
    }

    // Retrieve current DNS overrides as an ordered snapshot (matching DNSUtil precedence rules).
    final Map<String, SrvRecord> configuredOverrides = DNSUtil.getDnsOverride();
    final List<Map.Entry<String, SrvRecord>> overrides = DNSUtil.getDnsOverrideEntriesByPrecedence();

    /*
     * Pre-fill the add/update form when the operator clicked the edit icon in the table.
     * The pattern from the URL parameter is lowercased and used to look up the existing entry;
     * if found, its values are placed in the form fields so the operator can modify and re-save.
     */
    if ((domainPattern == null || domainPattern.trim().isEmpty()) && editPattern != null && configuredOverrides != null) {
        final String normalizedEditPattern = editPattern.trim().toLowerCase(Locale.ROOT);
        final SrvRecord existingOverride = configuredOverrides.get(normalizedEditPattern);
        if (existingOverride != null) {
            domainPattern = normalizedEditPattern;
            targetHost    = existingOverride.getHostname();
            targetPort    = Integer.toString(existingOverride.getPort());
        }
    }

    // Expose all computed values to JSTL/EL for rendering in the HTML section below.
    pageContext.setAttribute("errors",        errors);
    pageContext.setAttribute("success",       errors.isEmpty() && success);
    pageContext.setAttribute("overrides",     overrides);
    pageContext.setAttribute("domainPattern", domainPattern == null ? "" : domainPattern);
    pageContext.setAttribute("targetHost",    targetHost    == null ? "" : targetHost);
    pageContext.setAttribute("targetPort",    targetPort    == null ? "" : targetPort);
%>
<html>
<head>
    <title><fmt:message key="dns.overrides.title"/></title>
    <meta name="pageID" content="dns-overrides"/>
</head>
<body>

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed"/></c:when>
                    <c:when test="${err.key eq 'domainPattern.required'}"><fmt:message key="dns.overrides.error.domain.required"/></c:when>
                    <c:when test="${err.key eq 'domainPattern.invalid-delimiters'}"><fmt:message key="dns.overrides.error.domain.invalid-delimiters"/></c:when>
                    <c:when test="${err.key eq 'targetHost.required'}"><fmt:message key="dns.overrides.error.target-host.required"/></c:when>
                    <c:when test="${err.key eq 'targetHost.invalid-delimiters'}"><fmt:message key="dns.overrides.error.target-host.invalid-delimiters"/></c:when>
                    <c:when test="${err.key eq 'targetHost.ipv6-not-supported'}"><fmt:message key="dns.overrides.error.target-host.ipv6-not-supported"/></c:when>
                    <c:when test="${err.key eq 'targetPort.required'}"><fmt:message key="dns.overrides.error.target-port.required"/></c:when>
                    <c:when test="${err.key eq 'targetPort.invalid'}"><fmt:message key="dns.overrides.error.target-port.invalid"/></c:when>
                    <c:otherwise>
                        <fmt:message key="dns.overrides.error.invalid">
                            <fmt:param><c:out value="${err.value}"/></fmt:param>
                        </fmt:message>
                    </c:otherwise>
                </c:choose>
            </admin:infobox>
        </c:forEach>
    </c:when>
    <c:when test="${success}">
        <admin:infobox type="success">
            <fmt:message key="dns.overrides.success"/>
        </admin:infobox>
    </c:when>
</c:choose>

<p>
    <fmt:message key="dns.overrides.info"/>
</p>

<fmt:message key="dns.overrides.current.title" var="currentOverridesBoxTitle"/>
<admin:contentBox title="${currentOverridesBoxTitle}">
    <p><fmt:message key="dns.overrides.current.description"/></p>

    <table class="jive-table">
        <tr>
            <th style="width: 45%"><fmt:message key="dns.overrides.column.pattern"/></th>
            <th style="width: 40%"><fmt:message key="dns.overrides.column.host"/></th>
            <th style="width: 10%"><fmt:message key="dns.overrides.column.port"/></th>
            <th style="width: 1%; white-space: nowrap"><fmt:message key="global.edit"/></th>
            <th style="width: 1%; white-space: nowrap"><fmt:message key="global.delete"/></th>
        </tr>
        <c:choose>
            <c:when test="${empty overrides}">
                <tr>
                    <td colspan="5" style="text-align: center"><fmt:message key="global.list.empty"/></td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach var="override" items="${overrides}">
                    <tr>
                        <td><code><c:out value="${override.key}"/></code></td>
                        <td><code><c:out value="${override.value.hostname}"/></code></td>
                        <td><c:out value="${override.value.port}"/></td>
                        <td style="text-align: center">
                            <c:url var="editUrl" value="dns-overrides.jsp">
                                <c:param name="editPattern" value="${override.key}"/>
                            </c:url>
                            <a href="${editUrl}" title="<fmt:message key="global.click_edit"/>"><img src="images/edit-16x16.gif" alt="<fmt:message key="global.click_edit"/>"></a>
                        </td>
                        <td style="text-align: center">
                            <c:url var="deleteUrl" value="dns-overrides.jsp">
                                <c:param name="deletePattern" value="${override.key}"/>
                                <c:param name="csrf" value="${csrf}"/>
                            </c:url>
                            <a href="${deleteUrl}" data-confirm="<fmt:message key="dns.overrides.delete.confirm"><fmt:param><c:out value="${override.key}"/></fmt:param></fmt:message>" onclick="return confirm(this.getAttribute('data-confirm'));" title="<fmt:message key="global.click_delete"/>"><img src="images/delete-16x16.gif" alt="<fmt:message key="global.click_delete"/>"></a>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </table>
</admin:contentBox>

<fmt:message key="dns.overrides.form.title" var="dnsOverrideFormTitle"/>
<admin:contentBox title="${dnsOverrideFormTitle}">
    <p><fmt:message key="dns.overrides.form.description"/></p>

    <form action="dns-overrides.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <table>
            <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="domainPattern"><b><fmt:message key="dns.overrides.pattern"/></b></label>
                </td>
                <td>
                    <input type="text" size="40" name="domainPattern" id="domainPattern" value="${fn:escapeXml(domainPattern)}" placeholder="<fmt:message key="dns.overrides.pattern.placeholder"/>" required ${not empty errors['domainPattern.required'] or not empty errors['domainPattern.invalid-delimiters'] ? 'autofocus="autofocus" style="background-color: #ffdddd;"' : ''}>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="targetHost"><b><fmt:message key="dns.overrides.target-host"/></b></label>
                </td>
                <td>
                    <input type="text" size="40" name="targetHost" id="targetHost" value="${fn:escapeXml(targetHost)}" required ${not empty errors['targetHost.required'] or not empty errors['targetHost.invalid-delimiters'] or not empty errors['targetHost.ipv6-not-supported'] ? 'autofocus="autofocus" style="background-color: #ffdddd;"' : ''}>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <label for="targetPort"><b><fmt:message key="dns.overrides.target-port"/></b></label>
                </td>
                <td>
                    <input type="number" size="8" name="targetPort" id="targetPort" value="${fn:escapeXml(targetPort)}" min="1" max="65535" required ${not empty errors['targetPort.required'] or not empty errors['targetPort.invalid'] ? 'autofocus="autofocus" style="background-color: #ffdddd;"' : ''}>
                </td>
            </tr>
            </tbody>
        </table>

        <input type="submit" name="save" value="<fmt:message key="global.save_settings"/>">
    </form>
</admin:contentBox>

</body>
</html>



