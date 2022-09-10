<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="org.jivesoftware.admin.AuthCheckFilter" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  final Map<String, String> errors = new HashMap<>();

    // Get parameters
    final String blockedIPs = request.getParameter("blockedIPs");
    final String allowedIPs = request.getParameter("allowedIPs");
    final boolean ignoreExcludes= ParamUtils.getBooleanParameter( request, "ignore-excludes" );

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
        // do validation
        final Pattern pattern = Pattern.compile("(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(?:(?:\\*|25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){2}" +
            "(?:\\*|25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");

        final Set<String> blockedSet = new HashSet<>();
        for (final String address : blockedIPs.split("[,\\s]+") ) {
            if (!address.isEmpty()) {
                if (pattern.matcher(address).matches()) {
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
        for (final String address : allowedIPs.split("[,\\s]+") ) {
            if (!address.isEmpty()) {
                if (pattern.matcher(address).matches()) {
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
            webManager.logEvent("Updated Admin Console access configuration.", "Blocklist = {" + String.join(", ", blockedSet) + "}\nAllowlist = {" + String.join(", ", allowedSet) + "}\nIgnore excludes = " + ignoreExcludes);
            response.sendRedirect("system-admin-console-access.jsp?success=true");
            return;
        }
    }

    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("success", errors.isEmpty() && success);
    pageContext.setAttribute("blockedIPs", String.join(", ", AuthCheckFilter.IP_ACCESS_BLOCKLIST.getValue()));
    pageContext.setAttribute("allowedIPs", String.join(", ", AuthCheckFilter.IP_ACCESS_ALLOWLIST.getValue()));
    pageContext.setAttribute("ignoreExcludes", AuthCheckFilter.IP_ACCESS_IGNORE_EXCLUDES.getValue());

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
        <fmt:param><c:out value="${pageContext.request.remoteAddr}"/></fmt:param>
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

    <form action="system-admin-console-access.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">

        <p><fmt:message key="system.admin.console.access.iplists.blocklist.info" /></p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td valign='top'><b><label for="blockedIPs"><fmt:message key="system.admin.console.access.iplists.blocklist.label" /></label></b></td>
                <td><textarea name="blockedIPs" id="blockedIPs" cols="40" rows="3"><c:if test="${not empty blockedIPs}"><c:out value="${blockedIPs}"/></c:if></textarea></td>
            </tr>
        </table>

        <p><fmt:message key="system.admin.console.access.iplists.allowlist.info" /></p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tr>
                <td valign='top'><b><label for="allowedIPs"><fmt:message key="system.admin.console.access.iplists.allowlist.label" /></label></b></td>
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

</body>
</html>
