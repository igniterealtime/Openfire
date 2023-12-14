<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  ~ Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

<%@ page import="java.util.*,
                 org.jivesoftware.util.*"
         errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%
    Map<String, String> errors = new HashMap<>();
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");
    String s2sTestingDomain = ParamUtils.getParameter( request, "server2server-testing-domain" );
    boolean s2sTest = request.getParameter("s2s-test") != null && s2sTestingDomain != null;

    if (s2sTest) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            s2sTest = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // Validate domain input;
    JID domain = null;
    if (s2sTest) {
        try {
            domain = new JID(s2sTestingDomain);
        } catch (IllegalArgumentException e) {
            // Be forgiving for some common copy/paste mistakes.
            String parsedValue = s2sTestingDomain.trim();
            if (parsedValue.startsWith("http://")) {
                parsedValue = parsedValue.substring("http://".length());
            }
            if (parsedValue.startsWith("https://")) {
                parsedValue = parsedValue.substring("https://".length());
            }
            if (parsedValue.endsWith("/")) {
                parsedValue = parsedValue.substring(0, parsedValue.length() - 1);
            }
            try {
                domain = new JID(parsedValue);
            } catch (IllegalArgumentException e2) {
                errors.put("s2sTestingDomain", "invalid");
            }
        }
    }

    if (XMPPServer.getInstance().isLocal(domain) || XMPPServer.getInstance().matchesComponent(domain)) {
        errors.put("s2sTestingDomain", "ours");
    }

    if (errors.isEmpty() && s2sTest)
    {
        final Map<String, String> results = new S2STestService(domain).run();

        pageContext.setAttribute("s2sDomain", domain.getDomain());
        pageContext.setAttribute("s2sTest", true);
        pageContext.setAttribute("stanzas", results.get("stanzas"));
        pageContext.setAttribute("logs", results.get("logs"));
        pageContext.setAttribute("certs", results.get("certs"));
    } else {
        pageContext.setAttribute("s2sDomain", s2sTestingDomain);
        pageContext.setAttribute("s2sTest", false);
    }

    pageContext.setAttribute("errors", errors);
%>

<html>
<head>
    <title><fmt:message key="server2server.settings.testing.title"/></title>
    <meta name="pageID" content="server-connectiontest"/>
    <script>
        function startSpinner() {
            document.getElementById('spinner').style.display = "inline-block";
            return true;
        }
    </script>
    <style>
        #spinner {
            border: 0.5rem solid #f3f3f3;
            border-top: 0.5rem solid #D76C0D;
            border-radius: 50%;
            width: 1rem;
            height: 1rem;
            animation: spin 2s linear infinite;
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
    </style>
</head>
<body>

    <!-- Display all errors -->
    <c:forEach var="err" items="${errors}">
        <admin:infobox type="error">
            <c:choose>
                <c:when test="${err.key eq 's2sTestingDomain'}"><fmt:message key="server2server.settings.testing.domain-invalid" /></c:when>
                <c:when test="${err.key eq 'csrf'}"><fmt:message key="global.csrf.failed" /></c:when>
                <c:otherwise>
                    <fmt:message key="server2server.settings.testing.error" />
                </c:otherwise>
            </c:choose>
        </admin:infobox>
    </c:forEach>

    <p>
        <fmt:message key="server2server.settings.testing.info" />
    </p>

    <!-- BEGIN 'S2S Testing' -->
    <fmt:message key="server2server.settings.testing.boxtitle" var="s2sTitle"/>
    <admin:contentBox title="${s2sTitle}">
        <form action="server-connectiontest.jsp" method="post" onsubmit="return startSpinner();">
            <table>
                <tr>
                    <td style="width: 1%; white-space: nowrap"><label for="server2server-testing-domain"><fmt:message key="server2server.settings.testing.domain"/></label></td>
                    <td>
                        <input type="hidden" name="csrf" value="${csrf}"/>
                        <input type="text" name="server2server-testing-domain" id="server2server-testing-domain" value="${s2sDomain}">
                        <input type="submit" name="s2s-test" id="s2s-test" value="<fmt:message key="global.test" />">
                        <div id="spinner" style="display: none; vertical-align: middle;"></div>
                    </td>
                </tr>

                <c:if test="${s2sTest}">
                    <tr>
                        <td style="width: 1%; white-space: nowrap"><label for="server2server-testing-stanzas"><fmt:message key="server2server.settings.testing.xmpp"/></label></td>
                        <td>
                            <textarea name="server2server-testing-stanzas" id="server2server-testing-stanzas" style="width: 100%" rows="12" readonly><c:out value="${stanzas}" /></textarea>
                        </td>
                    </tr>
                    <tr>
                        <td style="width: 1%; white-space: nowrap"><label for="server2server-testing-certs"><fmt:message key="server2server.settings.testing.certificates"/></label></td>
                        <td>
                            <textarea name="server2server-testing-certs" id="server2server-testing-certs" style="width: 100%" rows="12" readonly><c:out value="${certs}" /></textarea>
                        </td>
                    </tr>
                    <tr>
                        <td style="width: 1%; white-space: nowrap"><label for="server2server-testing-logs"><fmt:message key="server2server.settings.testing.logs"/></label></td>
                        <td>
                            <textarea name="server2server-testing-logs" id="server2server-testing-logs" style="width: 100%" rows="12" readonly><c:out value="${logs}" /></textarea>
                        </td>
                    </tr>
                </c:if>

            </table>
        </form>
    </admin:contentBox>

</body>
</html>

