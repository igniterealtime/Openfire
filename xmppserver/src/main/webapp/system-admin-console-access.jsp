<%--
  -
  - Copyright (C) 2022-2023 Ignite Realtime Foundation. All rights reserved.
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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.openfire.container.AdminConsolePlugin " %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jivesoftware.admin.AuthCheckFilter" %>
<%@ page import="com.github.jgonian.ipmath.Ipv4" %>
<%@ page import="com.github.jgonian.ipmath.Ipv4Range" %>
<%@ page import="com.github.jgonian.ipmath.Ipv6" %>
<%@ page import="com.github.jgonian.ipmath.Ipv6Range" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%! public boolean isValidIpOrRange(String value) {
    try {
        Ipv4.parse(value);
        return true;
    } catch (IllegalArgumentException e) {
        // Skip to next validation
    }
    try {
        Ipv6.parse(value);
        return true;
    } catch (IllegalArgumentException e) {
        // Skip to next validation
    }
    try {
        Ipv4Range.parse(value);
        return true;
    } catch (IllegalArgumentException e) {
        // Skip to next validation
    }
    try {
        Ipv6Range.parse(value);
        return true;
    } catch (IllegalArgumentException e) {
        // Skip to next validation
    }
    return false;
}
%>
<%  final Map<String, String> errors = new HashMap<>();

    // Get parameters
    final String formtype = ParamUtils.getParameter(request, "formtype");
    final String blockedIPs = request.getParameter("blockedIPs");
    final String allowedIPs = request.getParameter("allowedIPs");
    final boolean ignoreExcludes = ParamUtils.getBooleanParameter(request, "ignore-excludes");
    final boolean isXFFEnabled = ParamUtils.getBooleanParameter( request, "XFFEnabled", AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED.getValue() );
    final boolean isCSPEnabled = ParamUtils.getBooleanParameter( request, "CSPEnabled", AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED.getValue() );
    final String cspValue = ParamUtils.getParameter( request, "CSPValue", true);

    boolean save = ParamUtils.getParameter(request, "save") != null;
    boolean success = ParamUtils.getBooleanParameter(request, "success");

    // Handle a save
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            save = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (save) {
        switch (formtype)
        {
            case "ip":
                // do validation
                final Set<String> blockedSet = new HashSet<>();
                for (final String address : blockedIPs.split("[,\\s]+")) {
                    if (!address.isEmpty()) {
                        if (isValidIpOrRange(address)) {
                            blockedSet.add(address);
                        } else {
                            if (errors.containsKey("invalid-blocklist-ips")) {
                                errors.put("invalid-blocklist-ips", errors.get("invalid-blocklist-ips") + ", " + address);
                            } else {
                                errors.put("invalid-blocklist-ips", address);
                            }
                        }
                    }
                }
                final Set<String> allowedSet = new HashSet<>();
                for (final String address : allowedIPs.split("[,\\s]+")) {
                    if (!address.isEmpty()) {
                        if (isValidIpOrRange(address)) {
                            allowedSet.add(address);
                        } else {
                            if (errors.containsKey("invalid-allowlist-ips")) {
                                errors.put("invalid-allowlist-ips", errors.get("invalid-allowlist-ips") + ", " + address);
                            } else {
                                errors.put("invalid-allowlist-ips", address);
                            }
                        }
                    }
                }

                if (errors.isEmpty()) {
                    AuthCheckFilter.IP_ACCESS_BLOCKLIST.setValue(blockedSet);
                    AuthCheckFilter.IP_ACCESS_ALLOWLIST.setValue(allowedSet);
                    AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.setValue(ignoreExcludes);

                    // Log the event
                    webManager.logEvent("Updated Admin Console access configuration (Access Lists).", "Blocklist = {" + String.join(", ", blockedSet) + "}\nAllowlist = {" + String.join(", ", allowedSet) + "}\nIgnore excludes = " + ignoreExcludes);
                    response.sendRedirect("system-admin-console-access.jsp?success=true");
                    return;
                }
                break;

            case "xff":

                if (errors.isEmpty())
                {
                    AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED.setValue( isXFFEnabled );

                    final String xffHeader = ParamUtils.getParameter( request, "XFFHeader" );
                    if (xffHeader == null || xffHeader.trim().isEmpty()) {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_FOR.setValue(null);
                    } else {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_FOR.setValue(xffHeader.trim());
                    }

                    final String xffServerHeader = ParamUtils.getParameter( request, "XFFServerHeader" );
                    if (xffServerHeader == null || xffServerHeader.trim().isEmpty()) {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_SERVER.setValue(null);
                    } else {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_SERVER.setValue(xffServerHeader.trim());
                    }

                    final String xffHostHeader = ParamUtils.getParameter( request, "xffHostHeader" );
                    if (xffHostHeader == null || xffHostHeader.trim().isEmpty()) {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST.setValue(null);
                    } else {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST.setValue(xffHostHeader.trim());
                    }

                    final String name = ParamUtils.getParameter( request, "XFFHostName" );
                    if (name == null || name.trim().isEmpty()) {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST_NAME.setValue(null);
                    } else {
                        AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST_NAME.setValue(name.trim());
                    }

                    // Log the event
                    webManager.logEvent("Updated Admin Console access configuration (X-Forwarded-For).", "X-Forwarded-For enabled: " + isXFFEnabled + "\nHeader: " + xffHeader + "\nServer Header: " + xffServerHeader + "\nHost Name: " + name );
                    response.sendRedirect("system-admin-console-access.jsp?success=true");
                    return;
                }
                break;

            case "csp":
                if (errors.isEmpty())
                {
                    AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED.setValue(isCSPEnabled);

                    if (cspValue == null || cspValue.trim().isEmpty()) {
                        AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.setValue(null);
                    } else {
                        AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.setValue(cspValue.trim());
                    }

                    // Log the event
                    webManager.logEvent("Updated Admin Console access configuration (Content-Security-Policy).", "Content-Security-Policy enabled: " + isCSPEnabled + "\nHeader Value: " + cspValue);
                    response.sendRedirect("system-admin-console-access.jsp?success=true");
                    return;
                }
                break;

            default:
                errors.put("unknown formtype value", null);
        }
    }

    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("success", errors.isEmpty() && success);
    pageContext.setAttribute("blockedIPs", errors.isEmpty() ? String.join(", ", AuthCheckFilter.IP_ACCESS_BLOCKLIST.getValue()) : blockedIPs);
    pageContext.setAttribute("allowedIPs", errors.isEmpty() ? String.join(", ", AuthCheckFilter.IP_ACCESS_ALLOWLIST.getValue()) : allowedIPs);
    pageContext.setAttribute("ignoreExcludes", errors.isEmpty() ? AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.getValue() : ignoreExcludes);
    pageContext.setAttribute("formattedRemoteAddress", AuthCheckFilter.removeBracketsFromIpv6Address(pageContext.getRequest().getRemoteAddr()));

%>

<html>
<head>
    <title><fmt:message key="system.admin.console.access.title"/></title>
    <meta name="pageID" content="system-admin-console-access"/>
</head>
<body>

<admin:infobox type="warning">
    <fmt:message key="system.admin.console.access.iplists.warning">
        <!-- When AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED is set, this will also show any X-Forwarded-For value. -->
        <fmt:param><c:out value="${formattedRemoteAddress}"/></fmt:param>
    </fmt:message>
</admin:infobox>

<c:choose>
    <c:when test="${not empty errors}">
        <c:forEach var="err" items="${errors}">
            <admin:infobox type="error">
                <c:choose>
                    <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                    <c:when test="${err.key eq 'invalid-blocklist-ips'}"><fmt:message key="system.admin.console.access.iplists.blocklist.invalid-hint"><fmt:param><c:out value="${err.value}"/></fmt:param></fmt:message></c:when>
                    <c:when test="${err.key eq 'invalid-allowlist-ips'}"><fmt:message key="system.admin.console.access.iplists.allowlist.invalid-hint"><fmt:param><c:out value="${err.value}"/></fmt:param></fmt:message></c:when>
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
    <c:when test="${success}">
        <admin:infobox type="success">
            <fmt:message key="system.admin.console.access.success" />
        </admin:infobox>
    </c:when>
</c:choose>

<p>
    <fmt:message key="system.admin.console.access.info"/>
</p>

<fmt:message key="system.admin.console.access.iplists.title" var="iplists_boxtitle"/>
<admin:contentBox title="${iplists_boxtitle}">

    <p><fmt:message key="system.admin.console.access.iplists.help"/></p>

    <form action="system-admin-console-access.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="formtype" value="ip">

        <p><fmt:message key="system.admin.console.access.iplists.blocklist.info" /></p>
        <table>
            <tr>
                <td style="vertical-align: top"><b><label for="blockedIPs"><fmt:message key="system.admin.console.access.iplists.blocklist.label" /></label></b></td>
                <td><textarea name="blockedIPs" id="blockedIPs" cols="40" rows="3"><c:if test="${not empty blockedIPs}"><c:out value="${blockedIPs}"/></c:if></textarea></td>
            </tr>
        </table>

        <p><fmt:message key="system.admin.console.access.iplists.allowlist.info" /></p>
        <table>
            <tr>
                <td style="vertical-align: top"><b><label for="allowedIPs"><fmt:message key="system.admin.console.access.iplists.allowlist.label" /></label></b></td>
                <td><textarea name="allowedIPs" id="allowedIPs" cols="40" rows="3"><c:if test="${not empty allowedIPs}"><c:out value="${allowedIPs}"/></c:if></textarea></td>
            </tr>
        </table>

        <p><fmt:message key="system.admin.console.access.iplists.ignore-excludes.info" /></p>
        <p>
            <input type="checkbox" name="ignore-excludes" id="ignore-excludes" ${ignoreExcludes ? "checked" : ""}>
            <label for="ignore-excludes"><fmt:message key="system.admin.console.access.iplists.ignore-excludes.label" /></label>
        </p>

        <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
    </form>
</admin:contentBox>

<!-- XFF -->
<fmt:message key="system.admin.console.settings.xff.group" var="xff_boxtitle"/>
<admin:contentBox title="${xff_boxtitle}">

    <form action="system-admin-console-access.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="formtype" value="xff">

        <table>
            <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="XFFEnabled" value="true" id="rb07" ${AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED.value ? "checked" : ""}>
                </td>
                <td>
                    <label for="rb07"><b><fmt:message key="system.admin.console.xff.label_enable"/></b> - <fmt:message key="system.admin.console.xff.label_enable_info"/></label>
                    <table>
                        <tr>
                            <td><label for="XFFHeader"><fmt:message key="system.admin.console.xff.forwarded_for"/></label></td>
                            <td><input id="XFFHeader" type="text" size="40" name="XFFHeader" value="${fn:escapeXml(AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_FOR.value == null ? "" : AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_FOR.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFServerHeader"><fmt:message key="system.admin.console.xff.forwarded_server"/></label></td>
                            <td><input id="XFFServerHeader" type="text" size="40" name="XFFServerHeader" value="${fn:escapeXml(AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_SERVER.value == null ? "" : AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_SERVER.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostHeader"><fmt:message key="system.admin.console.xff.forwarded_host"/></label></td>
                            <td><input id="XFFHostHeader" type="text" size="40" name="XFFHostHeader" value="${fn:escapeXml(AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST.value == null ? "" : AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST.value)}"></td>
                        </tr>
                        <tr>
                            <td><label for="XFFHostName"><fmt:message key="system.admin.console.xff.host_name"/></label></td>
                            <td><input id="XFFHostName" type="text" size="40" name="XFFHostName" value="${fn:escapeXml(AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST_NAME.value == null ? "" : AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED_HOST_NAME.value)}"></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="XFFEnabled" value="false" id="rb08" ${AdminConsolePlugin.ADMIN_CONSOLE_FORWARDED.value ? "" : "checked"}>
                </td>
                <td>
                    <label for="rb08"><b><fmt:message key="system.admin.console.xff.label_disable"/></b> - <fmt:message key="system.admin.console.xff.label_disable_info"/></label>
                </td>
            </tr>
            </tbody>
        </table>

        <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
    </form>
</admin:contentBox>
<!-- XFF -->

<!-- Content-Security-Policy -->
<fmt:message key="system.admin.console.csp.group" var="csp_boxtitle"/>
<admin:contentBox title="${csp_boxtitle}">

    <form action="system-admin-console-access.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">
        <input type="hidden" name="formtype" value="csp">

        <table>
            <tbody>
            <tr>
                <td style="width: 1%; white-space: nowrap; vertical-align: top">
                    <input type="radio" name="CSPEnabled" value="true" id="rb09" ${AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED.value ? "checked" : ""}>
                </td>
                <td>
                    <label for="rb09"><b><fmt:message key="system.admin.console.csp.label_enable"/></b> - <fmt:message key="system.admin.console.csp.label_enable_info"/></label>
                    <table>
                        <tr><td><label for="CSPValue"><fmt:message key="system.admin.console.csp.value"/></label></td></tr>
                        <tr><td><input id="CSPValue" type="text" size="80" name="CSPValue" value="${fn:escapeXml(AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.value == null ? "" : AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE.value)}"></td></tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="width: 1%; white-space: nowrap">
                    <input type="radio" name="CSPEnabled" value="false" id="rb10" ${AdminConsolePlugin.ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED.value ? "" : "checked"}>
                </td>
                <td>
                    <label for="rb10"><b><fmt:message key="system.admin.console.csp.label_disable"/></b> - <fmt:message key="system.admin.console.csp.label_disable_info"/></label>
                </td>
            </tr>
            </tbody>
        </table>

        <input type="submit" name="save" value="<fmt:message key="global.save_settings" />">
    </form>
</admin:contentBox>
<!-- Content-Security-Policy -->

</body>
</html>
