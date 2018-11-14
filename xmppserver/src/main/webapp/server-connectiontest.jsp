<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  ~ Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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

    if (errors.isEmpty() && s2sTest)
    {
        final Map<String, String> results = new S2STestService(s2sTestingDomain).run();

        pageContext.setAttribute("s2sDomain", s2sTestingDomain);
        pageContext.setAttribute("s2sTest", true);
        pageContext.setAttribute("stanzas", results.get("stanzas"));
        pageContext.setAttribute("logs", results.get("logs"));
        pageContext.setAttribute("certs", results.get("certs"));
    }

    pageContext.setAttribute("errors", errors);

%>

<html>
<head>
    <title><fmt:message key="server2server.settings.testing.title"/></title>
    <meta name="pageID" content="server-connectiontest"/>
</head>
<body>
    <c:if test="${not empty errors}">
        <admin:infobox type="error">
            <fmt:message key="server2server.settings.testing.error" />
        </admin:infobox>
    </c:if>
    <p>
        <fmt:message key="server2server.settings.testing.info" />
    </p>

    <!-- BEGIN 'S2S Testing' -->
    <fmt:message key="server2server.settings.testing.boxtitle" var="s2sTitle"/>
    <admin:contentBox title="${s2sTitle}">
        <form action="server-connectiontest.jsp" method="post">
            <table cellpadding="3" cellspacing="0" border="0">
                <tr valign="middle">
                    <td width="1%" nowrap><label for="server2server-testing-domain"><fmt:message key="server2server.settings.testing.domain"/></label></td>
                    <td width="99%">
                        <input type="hidden" name="csrf" value="${csrf}"/>
                        <input type="text" name="server2server-testing-domain" id="server2server-testing-domain" value="${s2sDomain}">
                        <input type="submit" name="s2s-test" value="<fmt:message key="global.test" />">
                    </td>
                </tr>

                <c:if test="${s2sTest}">
                    <tr valign="middle">
                        <td width="1%" nowrap><label for="server2server-testing-stanzas"><fmt:message key="server2server.settings.testing.xmpp"/></label></td>
                        <td width="99%">
                            <textarea name="server2server-testing-stanzas" id="server2server-testing-stanzas" style="width: 100%" rows="12"><c:out value="${stanzas}" /></textarea>
                        </td>
                    </tr>
                    <tr valign="middle">
                        <td width="1%" nowrap><label for="server2server-testing-certs"><fmt:message key="server2server.settings.testing.certificates"/></label></td>
                        <td width="99%">
                            <textarea name="server2server-testing-certs" id="server2server-testing-certs" style="width: 100%" rows="12"><c:out value="${certs}" /></textarea>
                        </td>
                    </tr>
                    <tr valign="middle">
                        <td width="1%" nowrap><label for="server2server-testing-logs"><fmt:message key="server2server.settings.testing.logs"/></label></td>
                        <td width="99%">
                            <textarea name="server2server-testing-logs" id="server2server-testing-logs" style="width: 100%" rows="12"><c:out value="${logs}" /></textarea>
                        </td>
                    </tr>
                </c:if>

            </table>
        </form>
    </admin:contentBox>

</body>
</html>

