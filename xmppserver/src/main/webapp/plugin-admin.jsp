<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

<%@ page import="java.io.InputStream,
                 java.util.List,
                 org.apache.commons.fileupload.FileItem,
                 org.apache.commons.fileupload.FileItemFactory,
                 org.apache.commons.fileupload.FileUploadException,
                 org.apache.commons.fileupload.disk.DiskFileItemFactory,
                 org.apache.commons.fileupload.servlet.ServletFileUpload"
        %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.container.PluginManager" %>
<%@ page import="org.jivesoftware.openfire.update.UpdateManager" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.jivesoftware.util.*" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%
    final Logger Log = LoggerFactory.getLogger("plugin-admin.jsp");
    String deletePlugin = ParamUtils.getParameter(request, "deleteplugin");
    String reloadPlugin = ParamUtils.getParameter(request, "reloadplugin");
    boolean downloadRequested = request.getParameter("download") != null;
    boolean uploadPlugin = request.getParameter("uploadplugin") != null;
    String url = request.getParameter("url");
    boolean uploadEnabled = JiveGlobals.getBooleanProperty("plugins.upload.enabled", true);
    boolean contentTypeCheckEnabled = JiveGlobals.getBooleanProperty("plugins.upload.content-type-check.enabled", false);
    String expectedContentType = JiveGlobals.getProperty("plugins.upload.content-type-check.expected-value", "application/x-java-archive");
    boolean csrf_check = true;

    final PluginManager pluginManager = webManager.getXMPPServer().getPluginManager();
    final UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

    pageContext.setAttribute( "plugins", pluginManager.getMetadataExtractedPlugins() );
    pageContext.setAttribute( "pluginManager", pluginManager );
    pageContext.setAttribute( "updateManager", updateManager );
    pageContext.setAttribute( "uploadEnabled", uploadEnabled );
    pageContext.setAttribute( "serverVersion", XMPPServer.getInstance().getServerInfo().getVersion() );

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
        csrf_check = false;
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (csrf_check && downloadRequested) {
        // Download and install new version of plugin
        updateManager.downloadPlugin(url);
        // Log the event
        webManager.logEvent("downloaded plugin from "+url, null);
    }

    if (csrf_check && deletePlugin != null) {
        pluginManager.deletePlugin( deletePlugin );
        // Log the event
        webManager.logEvent("deleted plugin "+deletePlugin, null);
        response.sendRedirect("plugin-admin.jsp?deletesuccess=true");
        return;
    }

    if (csrf_check && reloadPlugin != null) {
        if ( pluginManager.reloadPlugin(reloadPlugin) ) {
            // Log the event
            webManager.logEvent("reloaded plugin "+reloadPlugin, null);
            response.sendRedirect("plugin-admin.jsp?reloadsuccess=true");
            return;
        } else {
            response.sendRedirect( "plugin-admin.jsp?reloadsuccess=false" );
            return;
        }
    }

    if (csrf_check && uploadEnabled && uploadPlugin) {
        boolean installed = false;

        // Create a factory for disk-based file items
        FileItemFactory factory = new DiskFileItemFactory();

        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            // Parse the request
            List<FileItem> items = upload.parseRequest(request);

            for (FileItem item : items) {
                String fileName = item.getName();
                String contentType = item.getContentType();
                Log.debug("Uploaded plugin '{}' content type: '{}'.", fileName, contentType );
                if (fileName == null) {
                    Log.error( "Ignoring uploaded file: No filename specified for file upload." );
                    continue;
                }

                if (contentTypeCheckEnabled && !expectedContentType.equalsIgnoreCase( contentType )) {
                    Log.error( "Ignoring uploaded file: Content type '{}' of uploaded file '{}' does not match expected content type '{}'", contentType, fileName, expectedContentType );
                    continue;
                }

                InputStream is = item.getInputStream();
                if (is != null) {
                    installed = XMPPServer.getInstance().getPluginManager().installPlugin(is, fileName);
                    if (!installed) {
                        Log.error("Plugin manager failed to install plugin: " + fileName);
                    }
                    is.close();
                    // Log the event
                    webManager.logEvent("uploaded plugin "+fileName, null);
                }
                else {
                    Log.error("Unable to open file stream for uploaded file: " + fileName);
                }
            }
        }
        catch (FileUploadException e) {
            Log.error("Unable to upload plugin file.", e);
        }
        if (installed) {
            response.sendRedirect("plugin-admin.jsp?uploadsuccess=true");
            return;
        } else {
            response.sendRedirect("plugin-admin.jsp?uploadsuccess=false");
            return;
        }
    }
%>



<html>
<head>
<title><fmt:message key="plugin.admin.title"/></title>
<meta name="pageID" content="plugin-settings"/>
<meta name="helpPage" content="manage_system_plugins.html"/>
<script src="dwr/engine.js" type="text/javascript"></script>
<script src="dwr/util.js" type="text/javascript"></script>
<script src="dwr/interface/downloader.js" type="text/javascript"></script>

<script type="text/javascript" >
    dwr.engine.setErrorHandler(handleError);

    function handleError(error) {
    }
</script>

<style type="text/css">

.textfield {
    font-size: 11px;
    font-family: verdana, serif;
    padding: 3px 2px;
    background: #efefef;
}

.text {
    font-size: 11px;
    font-family: verdana, arial, helvetica, sans-serif;
}

.small-label {
    font-size: 11px;
    font-weight: bold;
    font-family: verdana, arial, helvetica, sans-serif;
}

.small-label-link {
    font-size: 11px;
    font-weight: bold;
    font-family: verdana, serif;
    text-decoration: underline;
}

.light-gray-border {
    border-color: #ccc;
    border-style: solid;
    border-width: 1px 1px 1px 1px;
    padding: 5px;
    -moz-border-radius: 3px;
}

.light-gray-border-bottom {
    border: 0 solid #dcdcdc;
    border-bottom-width: 1px;
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

.table-font {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: 8pt;
}

tr.regular td,
tr.unsupported td,
tr.update td {
    text-align: left;
    font-family: verdana, arial, helvetica, sans-serif;
    padding: 5px;
    border-style: solid;
    border-width: 0;
}

tr.regular td {
    font-size: 9pt;
    border-color: #e3e3e3;
}

tr.update td {
    font-size: 8pt;
    background: #E7FBDE;
    border-color: #73CB73;
}

tr.unsupported td {
    font-size: 8pt;
    background: #FBCBCC;
    border-color: #CB2B18;
}

tr.singleline > td {
    border-top-width: 0;
    border-right-width: 0;
    border-bottom-width: 1px;
    border-left-width: 0;
}

tr.upperhalf > td {
    border-top-width: 1px;
}
tr.upperhalf > td:first-child {
    border-top-width: 1px;
    border-left-width: 1px;
}
tr.upperhalf > td:last-child {
    border-top-width: 1px;
    border-right-width: 1px;
}
tr.lowerhalf > td {
    border-bottom-width: 1px;
}
tr.lowerhalf > td:first-child {
    border-bottom-width: 1px;
    border-left-width: 1px;
}
tr.lowerhalf > td:last-child {
    border-right-width: 1px;
    border-bottom-width: 1px;
}
</style>


<script type="text/javascript">
    function download(url, version, hashCode) {
        document.getElementById(hashCode + "-row").style.display = 'none';
        document.getElementById(hashCode + "-update").style.display = '';
        downloader.installPlugin(url, version, hashCode, downloadComplete);
    }

    function downloadComplete(status) {
        document.getElementById(status.hashCode + "-row").style.display = 'none';
        document.getElementById(status.hashCode + "-update").style.display = '';
        document.getElementById(status.hashCode + "-image").innerHTML = '<img src="images/success-16x16.gif" border="0" alt=""/>';
        document.getElementById(status.hashCode + "-text").innerHTML = '<fmt:message key="plugin.admin.update.complete" />';
        document.getElementById(status.hashCode + "-version").innerHTML = '<span style="text-decoration: line-through;">' + document.getElementById(status.hashCode + "-version").innerHTML + '</span><br>' + status.version;
    }
</script>
</head>

<body>
    <c:if test="${param.deletesuccess eq 'true'}">
        <admin:infobox type="success">
            <fmt:message key="plugin.admin.deleted_success" />
        </admin:infobox>
    </c:if>
    <c:if test="${param.deletesuccess eq 'false'}">
        <admin:infobox type="error">
            <fmt:message key="plugin.admin.deleted_failure" />
        </admin:infobox>
    </c:if>
    <c:if test="${param.reloadsuccess eq 'true'}">
        <admin:infobox type="success">
            <fmt:message key="plugin.admin.reload_success" />
        </admin:infobox>
    </c:if>
    <c:if test="${param.reloadsuccess eq 'false'}">
        <admin:infobox type="success">
            <fmt:message key="plugin.admin.reload_failure" />
        </admin:infobox>
    </c:if>
    <c:if test="${param.uploadsuccess eq 'true'}">
        <admin:infobox type="success">
            <fmt:message key="plugin.admin.uploaded_success" />
        </admin:infobox>
    </c:if>
    <c:if test="${param.uploadsuccess eq 'false'}">
        <admin:infobox type="error">
            <fmt:message key="plugin.admin.uploaded_failure" />
        </admin:infobox>
    </c:if>
    <c:if test="${ webManager.XMPPServer.pluginManager.monitorTaskRunning }">
        <admin:infobox type="info">
            <fmt:message key="plugin.admin.monitortask_running" />
        </admin:infobox>
    </c:if>
    <p>
    <fmt:message key="plugin.admin.info"/>
</p>

<p>

<div class="light-gray-border" style="padding:10px;">
<table cellpadding="0" cellspacing="0" border="0" width="100%" class="update">
 <tr style="background:#eee;">

    <td nowrap colspan="3" class="table-header-left"><fmt:message key="plugin.admin.name"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.description"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.version"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.author"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.restart"/></td>
    <td nowrap class="table-header-right"><fmt:message key="global.delete"/></td>
</tr>

<tbody>

<!-- If only the admin plugin is installed, show "none". -->
<c:if test="${plugins.size() eq 1}">
    <tr>
        <td align="center" colspan="8" style="padding:5px;"><fmt:message key="plugin.admin.no_plugin"/></td>
    </tr>
</c:if>
<c:forEach items="${plugins}" var="entry">
    <c:set var="canonicalName" value="${entry.key}"/>
    <c:set var="plugin" value="${entry.value}"/>
    <c:if test="${canonicalName != 'admin'}">
        <c:set var="minServerVersionFail" value="${not empty plugin.minServerVersion and plugin.minServerVersion.isNewerThan(serverVersion.ignoringReleaseStatus())}"/>
        <c:set var="priorToServerVersionFail" value="${not empty plugin.priorToServerVersion and not plugin.priorToServerVersion.isNewerThan( serverVersion )}"/>
        <c:set var="unsupported" value="${ minServerVersionFail or priorToServerVersionFail }"/>
        <c:set var="update" value="${updateManager.getPluginUpdate( plugin.name, plugin.version) }"/>
        <c:choose>
            <c:when test="${unsupported}">
                <c:set var="colorClass" value="unsupported"/>
                <c:set var="shapeClass" value="upperhalf"/>
            </c:when>
            <c:when test="${not empty update}">
                <c:set var="colorClass" value="update"/>
                <c:set var="shapeClass" value="upperhalf"/>
            </c:when>
            <c:otherwise>
                <c:set var="colorClass" value="regular"/>
                <c:set var="shapeClass" value="singleline"/>
            </c:otherwise>
        </c:choose>

        <tr valign="top" class="${colorClass} ${shapeClass}">
            <td width="1%">
                <c:choose>
                    <c:when test="${not empty plugin.icon}">
                        <img src="geticon?plugin=${admin:urlEncode(plugin.canonicalName)}&showIcon=true&decorator=none" width="16" height="16" alt="Plugin">
                    </c:when>
                    <c:otherwise>
                        <img src="images/plugin-16x16.gif" width="16" height="16" alt="Plugin">
                    </c:otherwise>
                </c:choose>
            </td>
            <td width="20%" nowrap valign="top">
                <c:out value="${plugin.name}"/>
            </td>
            <td nowrap valign="top">
                <c:if test="${not empty plugin.readme}">
                    <a href="plugin-showfile.jsp?plugin=${fn:escapeXml(plugin.canonicalName)}&showReadme=true&decorator=none"><img src="images/doc-readme-16x16.gif" width="16" height="16" border="0" alt="README"></a>
                </c:if>
                <c:if test="${not empty plugin.changelog}">
                    <a href="plugin-showfile.jsp?plugin=${fn:escapeXml(plugin.canonicalName)}&showChangelog=true&decorator=none"><img src="images/doc-changelog-16x16.gif" width="16" height="16" border="0" alt="changelog"></a>
                </c:if>
            </td>
            <td width="60%" valign="top">
                <c:if test="${not empty plugin.description}">
                    <c:out value="${plugin.description}"/>
                </c:if>
            </td>
            <td width="5%" nowrap valign="top">
                <c:if test="${not empty plugin.version}">
                    <span <c:if test="${not empty update}">id="${update.hashCode()}-version"</c:if>>
                    <c:out value="${plugin.version}"/>
                    </span>
                </c:if>
            </td>
            <td width="15%" nowrap valign="top">
                <c:if test="${not empty plugin.author}">
                    <c:out value="${plugin.author}"/>
                </c:if>
            </td>
            <td width="1%" style="text-align: center" valign="top">
                <c:if test="${pluginManager.isLoaded(plugin.canonicalName)}">
                    <a href="plugin-admin.jsp?csrf=${csrf}&reloadplugin=${admin:urlEncode( plugin.canonicalName )}"
                       title="<fmt:message key="plugin.admin.click_reload" />"
                    ><img src="images/refresh-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.refresh" /> ${plugin.name}"></a>
                </c:if>
            </td>
            <td width="1%" style="text-align: center" valign="top">
                <a href="#" onclick="if (confirm('<fmt:message key="plugin.admin.confirm" />')) { location.replace('plugin-admin.jsp?csrf=${csrf}&deleteplugin=${admin:urlEncode( plugin.canonicalName )}'); } "
                   title="<fmt:message key="global.click_delete" />"
                        ><img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="<fmt:message key="global.delete" /> ${plugin.name}"></a>
            </td>
        </tr>

        <c:if test="${unsupported}">
            <!-- When the plugin is unsupported, but *also* has an update, make sure that there's no bottom border -->
            <c:set var="overrideStyle" value="${ not empty update ? 'border-bottom-width: 0' : ''}"/>

            <tr class="${colorClass} lowerhalf">
                <td style="${overrideStyle}">&nbsp;</td>
                <td style="${overrideStyle}" colspan="6" nowrap>
                    <span class="small-label">
                        <c:if test="${minServerVersionFail}">
                            <fmt:message key="plugin.admin.failed.minserverversion">
                                <fmt:param value="${plugin.minServerVersion}"/>
                            </fmt:message>
                        </c:if>
                        <c:if test="${priorToServerVersionFail}">
                            <fmt:message key="plugin.admin.failed.priortoserverversion">
                                <fmt:param value="${plugin.priorToServerVersion}"/>
                            </fmt:message>
                        </c:if>
                    </span>
                </td>
                <td style="${overrideStyle}">&nbsp;</td>
            </tr>

            <tr><td></td></tr>

            <!-- End of update section -->
        </c:if>

        <c:if test="${not empty update}">
            <!-- Has Updates, show show -->
            <tr id="${update.hashCode()}-row" class="${colorClass} lowerhalf">
                <td>&nbsp;</td>
                <td nowrap>
                <span class="small-label">
                    <fmt:message key="plugin.admin.version.available">
                        <fmt:param value="${update.latestVersion}" />
                    </fmt:message>
                    </span>
                </td>
                <td nowrap>
                    <c:if test="${not empty update.changelog}">
                        <span class="text">(<a href="${update.changelog}"><fmt:message key="plugin.admin.changelog" /></a>)</span>
                    </c:if>
                </td>
                <td>
                    <table>
                        <tr>
                            <td><a href="javascript:download('${update.URL}', '${update.latestVersion}', '${update.hashCode()}')"><img src="images/icon_update-16x16.gif" width="16" height="16" border="0" alt="changelog"></a></td>
                            <td><a href="javascript:download('${update.URL}', '${update.latestVersion}', '${update.hashCode()}')"><span class="small-label"><fmt:message key="plugin.admin.update" /></span></a></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3">&nbsp;</td>
                <td colspan="3">&nbsp;</td>
            </tr>

            <tr id="${update.hashCode()}-update" style="display:none;" class="${colorClass} lowerhalf">
                <td colspan="8" align="center">
                    <table>
                        <tr>
                            <td id="${update.hashCode()}-image"><img src="images/working-16x16.gif" border="0" alt=""/></td>
                            <td id="${update.hashCode()}-text" class="table-font"><fmt:message key="plugin.admin.updating" /></td>
                        </tr>
                    </table>
                </td>
            </tr>

            <tr><td></td></tr>

            <!-- End of update section -->
        </c:if>
    </c:if>

</c:forEach>

</tbody>
</table>
</div>

<c:if test="${uploadEnabled}">
    <br /><br />
    <div>
        <h3><fmt:message key="plugin.admin.upload_plugin" /></h3>
        <p><fmt:message key="plugin.admin.upload_plugin.info" /></p>
        <form action="plugin-admin.jsp?uploadplugin&amp;csrf=${csrf}" enctype="multipart/form-data" method="post">
            <input type="file" name="uploadfile" />
            <input type="submit" value="<fmt:message key="plugin.admin.upload_plugin" />" />
        </form>
    </div>
</c:if>

</body>
</html>
