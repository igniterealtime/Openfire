<!--
  - Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  - http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->
<%@ page errorPage="error.jsp" %>
<%@ page import="org.igniterealtime.openfire.plugins.certificatemanager.DirectoryWatcher" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="java.net.URLDecoder" %>
<%@ page import="java.nio.file.Files" %>
<%@ page import="java.nio.file.InvalidPathException" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.nio.file.Paths" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="admin" prefix="admin" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>
<%
    Map<String, String> errors = new HashMap<>();

    boolean update = request.getParameter("update") != null;

    final Cookie csrfCookie = CookieUtils.getCookie( request, "csrf");
    String csrfParam = ParamUtils.getParameter( request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
            errors.put( "csrf", "csrf value is invalid (reload page and try again)." );
        }
    }
    csrfParam = StringUtils.randomString( 15 );
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if ( update && errors.isEmpty() )
    {
        final boolean enabled = ParamUtils.getBooleanParameter( request, "directorywatcherEnabled" );
        final boolean replace = ParamUtils.getBooleanParameter( request, "directorywatcherReplace" );
        final boolean delete  = ParamUtils.getBooleanParameter( request, "directorywatcherDelete" );
        String path = ParamUtils.getParameter( request, "directorywatcherPath" );

        if ( path != null && !path.isEmpty() )
        {
            path = URLDecoder.decode( path, "UTF-8" );
        }
        else
        {
            path = DirectoryWatcher.PROPERTY_WATCHED_PATH_DEFAULT;
        }

        try
        {
            final Path dir = Paths.get( path );
            if ( !Files.exists( dir ) )
            {
                errors.put( "path", "does not exist" );
            }
            else if ( !Files.isReadable( dir ) )
            {
                errors.put( "path", "not readable" );
            }
            else if ( delete && !Files.isWritable( dir ) )
            {
                errors.put( "path", "not writable" );
            }
        }
        catch ( InvalidPathException e )
        {
            errors.put( "path", "invalid path" );
        }

        if ( errors.isEmpty() )
        {
            JiveGlobals.setProperty( DirectoryWatcher.PROPERTY_ENABLED, Boolean.toString( enabled ) );
            JiveGlobals.setProperty( DirectoryWatcher.PROPERTY_REPLACE, Boolean.toString( replace ) );
            JiveGlobals.setProperty( DirectoryWatcher.PROPERTY_DELETE,  Boolean.toString( delete  ) );
            JiveGlobals.setProperty( DirectoryWatcher.PROPERTY_WATCHED_PATH, path );
            response.sendRedirect("certificate-management.jsp?success=true");
            return;
        }
    }
    // Read all updated values from the properties.
    pageContext.setAttribute( "directorywatcherIsEnabled", JiveGlobals.getBooleanProperty( DirectoryWatcher.PROPERTY_ENABLED, DirectoryWatcher.PROPERTY_ENABLED_DEFAULT ) );
    pageContext.setAttribute( "directorywatcherPath",      JiveGlobals.getProperty( DirectoryWatcher.PROPERTY_WATCHED_PATH,   DirectoryWatcher.PROPERTY_WATCHED_PATH_DEFAULT ) );
    pageContext.setAttribute( "directorywatcherIsReplace", JiveGlobals.getBooleanProperty( DirectoryWatcher.PROPERTY_REPLACE, DirectoryWatcher.PROPERTY_REPLACE_DEFAULT ) );
    pageContext.setAttribute( "directorywatcherIsDelete",  JiveGlobals.getBooleanProperty( DirectoryWatcher.PROPERTY_DELETE,  DirectoryWatcher.PROPERTY_DELETE_DEFAULT ) );
    pageContext.setAttribute( "errors", errors );
%>
<html>
<head>
    <title><fmt:message key="certificate-management.page.title"/></title>
    <meta name="pageID" content="certificate-management"/>
</head>
<body>

<c:forEach var="err" items="${errors}">
    <admin:infobox type="error">
        <c:choose>
            <c:when test="${err.key eq 'csrf'}">
                <fmt:message key="global.csrf.failed"/>
            </c:when>

            <c:when test="${err.key eq 'path' and err.value eq 'does not exist'}">
                <fmt:message key="error.path.exist"/>
            </c:when>
            <c:when test="${err.key eq 'path' and err.value eq 'not readable'}">
                <fmt:message key="error.path.readable"/>
            </c:when>
            <c:when test="${err.key eq 'path' and err.value eq 'not writable'}">
                <fmt:message key="error.path.writable"/>
            </c:when>
            <c:when test="${err.key eq 'path' and err.value eq 'invalid path'}">
                <fmt:message key="error.path.invalid"/>
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

<c:if test="${empty errors and param.success}">
    <admin:infoBox type="success">
        <fmt:message key="certificate-management.saved_successfully"/>
    </admin:infoBox>
</c:if>

<p>
    <fmt:message key="certificate-management.info"/>
</p>

<form action="certificate-management.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">

    <fmt:message key="certificate-management.directorywatcher.boxtitle" var="directorywatcherboxtitle"/>
    <admin:contentBox title="${directorywatcherboxtitle}">

        <c:set var="escaped">
            <c:out value="${directorywatcherPath}"/>
        </c:set>

        <p><fmt:message key="certificate-management.directorywatcher.info"/></p>

        <table cellpadding="3" cellspacing="0" border="0">
            <tr valign="middle">
                <td colspan="2"><input type="checkbox" name="directorywatcherEnabled" id="directorywatcherEnabled" onclick="applyDisplayable('directorywatcher')" ${directorywatcherIsEnabled ? 'checked' : ''}/><label for="directorywatcherEnabled"><fmt:message key="certificate-management.directorywatcher.label_enable"/></label></td>
            </tr>
            <tr valign="middle">
                <td width="1%" nowrap><label for="directorywatcherPath"><fmt:message key="certificate-management.directorywatcher.label_path"/></label></td>
                <td width="99%"><input type="text" size="60" name="directorywatcherPath" id="directorywatcherPath" value="${escaped}"/></td>
            </tr>
            <%--<tr valign="middle">--%>
                <%--<td colspan="2"><input type="checkbox" name="directorywatcherReplace" id="directorywatcherReplace" ${directorywatcherIsReplace ? 'checked' : ''}/><label for="directorywatcherReplace"><fmt:message key="certificate-management.directorywatcher.label_replace"/></label></td>--%>
            <%--</tr>--%>
            <tr valign="middle">
                <td colspan="2"><input type="checkbox" name="directorywatcherDelete" id="directorywatcherDelete" ${directorywatcherIsDelete ? 'checked' : ''}/><label for="directorywatcherDelete"><fmt:message key="certificate-management.directorywatcher.label_delete"/></label></td>
            </tr>
        </table>

    </admin:contentBox>

    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
</form>

</body>
</html>
