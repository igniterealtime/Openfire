<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="error.jsp"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.keystore.CertificateStoreManager" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.Collection" %>
<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%--
  ~ Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<jsp:useBean id="now" class="java.util.Date"/>
<%  webManager.init(request, response, session, application, out );

    final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();

    final Map<String, String> errors = new HashMap<>();
    pageContext.setAttribute( "errors", errors );

    boolean backup = request.getParameter("backup") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (backup) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            backup = false;
            errors.put("csrf", "CSRF Failure!");
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if ( backup )
    {
        try
        {
            final Collection<Path> backupPaths = certificateStoreManager.backup();

            pageContext.setAttribute( "backupPaths", backupPaths );

            // Log the event
            webManager.logEvent( "Created backup of key store files.", String.join( System.lineSeparator(), backupPaths.stream().map( Path::toString ).collect( Collectors.toList() ) ) );
        }
        catch ( IOException ex )
        {
            errors.put( "backup", ex.getMessage() );
        }
    }
%>
<html>
<head>
    <title><fmt:message key="ssl.certificates.store-backup.title"/></title>
    <meta name="pageID" content="security-certificate-store-backup"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'backup'}">
                <fmt:message key="ssl.certificates.store-backup.error"/>
            </c:when>
            <c:otherwise>
                <c:if test="${not empty err.value}">
                    <fmt:message key="admin.error"/>: <c:out value="${err.value}"/>
                </c:if>
                (<c:out value="${err.key}"/>)
            </c:otherwise>
        </c:choose>
    </admin:infobox>
</c:forEach>

<!-- Display success report, but only if there were no errors. -->
<c:if test="${not empty backupPaths and empty errors}">
    <admin:infoBox type="success">
        <fmt:message key="ssl.certificates.store-backup.success"/>
        <ul>
            <c:forEach items="${backupPaths}" var="backupPath">
                <li><c:out value="${backupPath}"/></li>
            </c:forEach>
        </ul>
    </admin:infoBox>
</c:if>

<p>
    <fmt:message key="ssl.certificates.store-backup.info"/>

    <form action="security-certificate-store-backup.jsp" method="post">
        <input type="hidden" name="csrf" value="${csrf}">

        <br/>

        <input type="submit" name="backup" value="<fmt:message key="ssl.certificates.store-backup.create-backup" />">

    </form>

</p>


</body>
</html>
