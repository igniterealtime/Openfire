<%@ page contentType="text/html; charset=UTF-8" %>
<%--
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

<%@ page errorPage="error.jsp" import="org.jivesoftware.openfire.XMPPServer,
                                       org.jivesoftware.openfire.container.Plugin,
                                       org.jivesoftware.openfire.container.PluginManager,
                                       org.jivesoftware.openfire.container.PluginMetadataHelper,
                                       org.jivesoftware.openfire.update.AvailablePlugin"
    %>
<%@ page import="org.jivesoftware.openfire.update.UpdateManager" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="java.nio.file.Path" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jivesoftware.openfire.container.PluginMetadata" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="admin" prefix="admin" %>



<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%
    boolean downloadRequested = request.getParameter("download") != null;
    String url = request.getParameter("url");
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (downloadRequested) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            downloadRequested = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    final XMPPServer server = XMPPServer.getInstance();
    final UpdateManager updateManager = server.getUpdateManager();

    final String value = JiveGlobals.getProperty( "update.lastCheck" );
    pageContext.setAttribute( "lastCheck", value != null ? new Date( Long.parseLong( value ) ) : null );
    pageContext.setAttribute( "updateServiceEnabled", updateManager.isServiceEnabled() );
    pageContext.setAttribute( "notInstalledPlugins", updateManager.getNotInstalledPlugins() );

    if (downloadRequested) {
        // Download and install new plugin
        updateManager.downloadPlugin(url);
        // Log the event
        webManager.logEvent("downloaded new plugin from "+url, null);
    }

%>

<html>
<head>
<title><fmt:message key="plugin.available.title"/></title>
<meta name="pageID" content="available-plugins"/>

<style type="text/css">

.light-gray-border {
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
    -moz-border-radius: 3px;
}



.table-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0 1px 0;
    padding: 5px;
}

.row-header {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0;
    padding: 5px;
}

.table-header-left {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 0 1px 1px;
    padding: 5px;

}

.table-header-right {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
    font-weight: bold;
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 0;
    padding: 5px;
}

.line-bottom-border {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 9pt;
    border-color: #e3e3e3;
    border-style: solid;
    border-width: 0 0 1px 0;
    padding: 5px;
}


</style>

<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>
<script type="text/javascript">

    var downloading;
    function downloadPlugin(url, version, id) {
        downloading = true;
        document.getElementById(id + "-image").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        document.getElementById(id).style.background = "#FFFFCC";
        setTimeout("startDownload('" + url + "','" + version + "','" + id + "')", 1000);
    }

    function startDownload(url, version, id) {
        downloader.installPlugin(url, version, id, downloadComplete);
    }

    function downloadComplete(status) {
        downloading = false;
        if (!status.successfull) {
            document.getElementById(status.hashCode + "-image").innerHTML = '<img src="images/add-16x16.gif" border="0"/>';
            document.getElementById(status.hashCode).style.background = "#FFFFFF";
            document.getElementById("errorMessage").style.display = '';
            document.getElementById(status.hashCode).style.display = '';
            document.getElementById(status.hashCode + "-row").style.display = 'none';
            setTimeout("closeErrorMessage()", 5000);
        }
        else {
            document.getElementById(status.hashCode).style.display = 'none';
            document.getElementById(status.hashCode + "-row").style.display = '';
            setTimeout("fadeIt('" + status.hashCode + "')", 3000);
        }
    }

    function closeErrorMessage(){
        Effect.Fade("errorMessage");
    }

    function fadeIt(id) {
        Effect.Fade(id + "-row");
    }


    dwr.engine.setErrorHandler(handleError);

    function handleError(error) {
    }

    // Handle leaving of page validation.
    window.onbeforeunload = function (evt) {
        if (!downloading) {
            return;
        }
        var message = '<fmt:message key="plugin.available.cancel.redirect" />';
        if (typeof evt == 'undefined') {
            evt = window.event;
        }
        if (evt) {
            evt.returnValue = message;
        }
        return message;
    }

    function updatePluginsList(){
        document.getElementById("reloaderID").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        downloader.updatePluginsList(pluginsListUpdated);
    }

    function updatePluginsListNow(){
        document.getElementById("reloader2").innerHTML = '<img src="images/working-16x16.gif" border="0"/>';
        downloader.updatePluginsList(pluginsListUpdated);
    }

    function pluginsListUpdated(){
        window.location.href = "available-plugins.jsp";
    }


</script>
</head>

<body>

    <p>
        <fmt:message key="plugin.available.info"/>
    </p>

    <c:choose>
        <c:when test="${empty lastCheck}">
            <div style="padding:10px;background:#FFEBB5;border:1px solid #DEB24A;width:75%;">
                <fmt:message key="plugin.available.no.list" />&nbsp;<span id="reloaderID"><a href="javascript:updatePluginsList();"><fmt:message key="plugin.available.list" /></a></span>
            </div>
            <br/>
            <div style="width:75%;">
                <p>
                    <fmt:message key="plugin.available.no.list.description" />
                </p>
                <c:if test="${not updateServiceEnabled}">
                    <fmt:message key="plugin.available.auto.update.currently" /> <b><fmt:message key="plugin.available.auto.update.currently.disabled" /></b>. <a href="manage-updates.jsp"><fmt:message key="plugin.available.click.here" /></a> <fmt:message key="plugin.available.change" />
                </c:if>
            </div>
        </c:when>
        <c:otherwise>
            <div id="errorMessage" class="error" style="display:none;">
                <fmt:message key="plugin.available.error.downloading" />
            </div>

            <div class="light-gray-border" style="padding:10px;">
                <table cellpadding="0" cellspacing="0" border="0" width="100%">
                    <thead>
                        <tr style="background:#eee;">
                            <td class="table-header-left">&nbsp;</td>
                            <td nowrap colspan="2" class="table-header"><fmt:message key="plugin.available.open_source"/></td>
                            <td nowrap class="table-header"><fmt:message key="plugin.available.description"/></td>
                            <td nowrap class="table-header"><fmt:message key="plugin.available.version"/></td>
                            <td nowrap class="table-header"><fmt:message key="plugin.available.author"/></td>
                            <td nowrap class="table-header"><fmt:message key="plugin.available.file_size"/></td>
                            <td nowrap class="table-header-right"><fmt:message key="plugin.available.install"/></td>
                        </tr>
                    </thead>
                    <tbody>
                        <c:choose>
                            <c:when test="${empty notInstalledPlugins}">
                                <tr>
                                    <td align="center" colspan="8"><fmt:message key="plugin.available.no_plugin"/></td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach items="${notInstalledPlugins}" var="notInstalledPlugin">
                                    <tr id="${notInstalledPlugin.hashCode}">
                                        <td width="1%" class="line-bottom-border">
                                            <c:choose>
                                                <c:when test="${not empty notInstalledPlugin.icon}">
                                                    <img src="${fn:escapeXml(notInstalledPlugin.icon)}" width="16" height="16" alt="Plugin">
                                                </c:when>
                                                <c:otherwise>
                                                    <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td width="20%" nowrap class="line-bottom-border">
                                            <c:if test="${not empty notInstalledPlugin.name}">
                                                <c:out value="${notInstalledPlugin.name}"/>
                                            </c:if>
                                        </td>
                                        <td nowrap valign="top" class="line-bottom-border">
                                            <c:if test="${not empty notInstalledPlugin.readme}">
                                                <a href="${fn:escapeXml(notInstalledPlugin.readme)}"><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
                                            </c:if>
                                            <c:if test="${not empty notInstalledPlugin.changelog}">
                                                <a href="${fn:escapeXml(notInstalledPlugin.changelog)}"><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                                            </c:if>
                                        </td>
                                        <td width="60%" class="line-bottom-border">
                                            <c:if test="${not empty notInstalledPlugin.description}">
                                                <c:out value="${notInstalledPlugin.description}"/>
                                            </c:if>
                                        </td>
                                        <td width="5%" nowrap valign="top" class="line-bottom-border">
                                            <c:if test="${not empty notInstalledPlugin.version}">
                                                <c:out value="${notInstalledPlugin.version}"/>
                                            </c:if>
                                            <c:if test="${not empty notInstalledPlugin.releaseDate}">
                                                <br><c:out value="${notInstalledPlugin.releaseDate}"/>
                                            </c:if>
                                        </td>
                                        <td width="15%" nowrap valign="top" class="line-bottom-border">
                                            <c:if test="${not empty notInstalledPlugin.author}">
                                                <c:out value="${notInstalledPlugin.author}"/>
                                            </c:if>
                                        </td>
                                        <td width="15%" nowrap valign="top" class="line-bottom-border" align="right">
                                            <c:out value="${admin:byteFormat( notInstalledPlugin.fileSize )}"/>
                                        </td>
                                        <td width="1%" align="center" valign="top" class="line-bottom-border">
                                            <a href="javascript:downloadPlugin('${fn:escapeXml(notInstalledPlugin.downloadURL)}', '${notInstalledPlugin.version}', '${notInstalledPlugin.hashCode}')">
                                                <span id="${notInstalledPlugin.hashCode}-image">
                                                    <img src="images/add-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="plugin.available.download" />">
                                                </span>
                                            </a>
                                        </td>
                                    </tr>
                                    <tr id="${notInstalledPlugin.hashCode}-row" style="display:none;background: #E7FBDE;">
                                        <td width="1%" class="line-bottom-border">
                                            <img src="${fn:escapeXml(notInstalledPlugin.icon)}" width="16" height="16" alt=""/>
                                        </td>
                                        <td colspan="6" nowrap class="line-bottom-border">${admin:escapeHTMLTags(notInstalledPlugin.name)} <fmt:message key="plugin.available.installation.success" /></td>
                                        <td class="line-bottom-border" align="center">
                                            <img src="images/success-16x16.gif" height="16" width="16" alt=""/>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
            </div>

        </c:otherwise>
    </c:choose>

    <p>
        <c:if test="${not empty lastCheck}">
            <fmt:message key="plugin.available.autoupdate" /> <c:out value="${admin:formatDateTime(lastCheck)}" />.
        </c:if>
        <c:choose>
            <c:when test="${updateServiceEnabled}">
                <fmt:message key="plugin.available.autoupdate.on" />
            </c:when>
            <c:otherwise>
                <fmt:message key="plugin.available.autoupdate.off" />
            </c:otherwise>
        </c:choose>
        <span id="reloader2"><a href="javascript:updatePluginsListNow()"><fmt:message key="plugin.available.manual.update" /></a></span>
    </p>
</body>
</html>
