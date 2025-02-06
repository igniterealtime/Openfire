<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  - Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
<%--@elvariable id="webManager" type="org.jivesoftware.util.WebManager"--%>
<%--@elvariable id="pluginManager" type="org.jivesoftware.openfire.container.PluginManager"--%>
<%--@elvariable id="updateManager" type="org.jivesoftware.openfire.update.UpdateManager"--%>
<%--@elvariable id="plugins" type="java.util.Map<java.lang.String, org.jivesoftware.openfire.container.PluginMetadata>"--%>
<%--@elvariable id="uploadEnabled" type="java.lang.Boolean"--%>
<%--@elvariable id="csrf" type="java.lang.String"--%>
<%@ page import="org.jivesoftware.admin.AuthCheckFilter" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<head>
<title><fmt:message key="plugin.admin.title"/></title>
<meta name="pageID" content="plugin-settings"/>
<meta name="helpPage" content="manage_system_plugins.html"/>
<script src="dwr/engine.js"></script>
<script src="dwr/util.js"></script>
<script src="dwr/interface/downloader.js"></script>

<script >
    dwr.engine.setErrorHandler(handleError);

    function handleError(error) {
    }
</script>

<style>

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
tr.warning td,
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

tr.regular:hover {
    background-color: white;
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

tr.warning td {
    font-size: 8pt;
    background: #fbe5cb;
    border-color: #cb7718;
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
.table-data:hover {
    background-color: white;
}
</style>


<script>
    function download(url, version, hashCode) {
        document.getElementById(hashCode + "-row").style.display = 'none';
        document.getElementById(hashCode + "-update").style.display = '';
        downloader.installPlugin('${admin:escapeHTMLTags(webManager.user.username).replaceAll("'", "&quot;")}', url, version, hashCode, downloadComplete);
    }

    function downloadComplete(status) {
        document.getElementById(status.hashCode + "-row").style.display = 'none';
        document.getElementById(status.hashCode + "-update").style.display = '';
        document.getElementById(status.hashCode + "-image").innerHTML = '<img src="images/success-16x16.gif" alt=""/>';
        document.getElementById(status.hashCode + "-text").innerHTML = '<fmt:message key="plugin.admin.update.complete" />';
        document.getElementById(status.hashCode + "-version").innerHTML = '<span style="text-decoration: line-through;">' + document.getElementById(status.hashCode + "-version").innerHTML + '</span><br>' + status.version;
    }
</script>
</head>

<body>
    <c:if test="${param.csrfError eq 'true'}">
        <admin:infobox type="error">
            <fmt:message key="global.csrf.failed" />
        </admin:infobox>
    </c:if>
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
    <c:if test="${ AuthCheckFilter.excludesIncludeWildcards() && !AuthCheckFilter.ALLOW_WILDCARDS_IN_EXCLUDES.getValue() }">
        <admin:infobox type="warning">
            <fmt:message key="plugin.admin.wildcards-exists" />
        </admin:infobox>
    </c:if>
    <p>
    <fmt:message key="plugin.admin.info"/>
</p>

<p>

<div class="light-gray-border" style="padding:10px;">
<table style="width: 100%" class="update">
 <tr style="background:#eee;">
    <td class="table-header-left">&nbsp;</td>
    <td class="table-header"><fmt:message key="plugin.admin.name"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.version"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.author"/></td>
    <td nowrap class="table-header"><fmt:message key="plugin.admin.restart"/></td>
    <td nowrap class="table-header-right"><fmt:message key="global.delete"/></td>
</tr>

<tbody>

<!-- If only the admin plugin is installed, show "none". -->
<c:if test="${plugins.size() eq 1}">
    <tr>
        <td style="text-align: center; padding:5px;" colspan="8"><fmt:message key="plugin.admin.no_plugin"/></td>
    </tr>
</c:if>
<c:forEach items="${plugins}" var="entry">
    <c:set var="canonicalName" value="${entry.key}"/>
    <c:set var="plugin" value="${entry.value}"/>
    <c:if test="${canonicalName != 'admin'}">
        <c:set var="loadWarning" value="${pluginManager.getLoadWarning(canonicalName)}"/>
        <c:set var="unsupported" value="${not empty loadWarning and !pluginManager.isLoaded(canonicalName)}"/>
        <c:set var="loadedWithWarning" value="${not empty loadWarning and pluginManager.isLoaded(canonicalName)}"/>
        <c:set var="update" value="${updateManager.getPluginUpdate( plugin.name, plugin.version) }"/>
        <c:choose>
            <c:when test="${unsupported}">
                <c:set var="colorClass" value="unsupported"/>
                <c:set var="shapeClass" value="upperhalf"/>
            </c:when>
            <c:when test="${loadedWithWarning}">
                <c:set var="colorClass" value="warning"/>
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

        <tr class="${colorClass} ${shapeClass}">
            <td style="width: 1%">
                <c:choose>
                    <c:when test="${not empty plugin.icon}">
                        <img src="geticon?plugin=${admin:urlEncode(plugin.canonicalName)}&showIcon=true&decorator=none" width="16" height="16" alt="Plugin">
                    </c:when>
                    <c:otherwise>
                        <img src="images/plugin-16x16.gif" alt="Plugin">
                    </c:otherwise>
                </c:choose>
            </td>
            <td>
                <b><c:out value="${plugin.name}"/></b><br/>
                <c:if test="${not empty plugin.description}">
                    <c:out value="${plugin.description}"/><br/>
                </c:if>
                <c:if test="${not empty plugin.readme}">
                    <a href="plugin-showfile.jsp?plugin=${fn:escapeXml(plugin.canonicalName)}&showReadme=true&decorator=none"
                        target="_blank">
                        <fmt:message key="plugin.admin.documentation" />
                    </a>
                </c:if>
            </td>
            <td style="width: 10%; white-space: nowrap;">
                <c:if test="${not empty plugin.version}">
                    <span <c:if test="${not empty update}">id="${update.hashCode()}-version"</c:if>>
                        <c:out value="${plugin.version}"/>
                    </span><br/>
                </c:if>
                <c:if test="${not empty plugin.changelog}">
                    <a href="plugin-showfile.jsp?plugin=${fn:escapeXml(plugin.canonicalName)}&showChangelog=true&decorator=none"
                        target="_blank">
                        <fmt:message key="plugin.admin.changelog" />
                    </a>
                </c:if>
            </td>
            <td style="width: 10%;">
                <c:if test="${not empty plugin.author}">
                    <c:out value="${plugin.author}"/>
                </c:if>
            </td>
            <td style="width: 1%; text-align: center;">
                <c:if test="${pluginManager.isLoaded(plugin.canonicalName)}">
                    <form action="plugin-admin.jsp" method="post">
                        <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
                        <input type="hidden" name="reloadplugin" value="${admin:escapeHTMLTags(plugin.canonicalName)}"/>
                        <input type="image" src="images/refresh-16x16.gif" alt="<fmt:message key="global.refresh" /> ${plugin.name}"/>
                    </form>
                </c:if>
            </td>
            <td style="width: 1%; text-align: center;">
                <form action="plugin-admin.jsp" method="post">
                    <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
                    <input type="hidden" name="deleteplugin" value="${admin:escapeHTMLTags(plugin.canonicalName)}"/>
                    <input type="image" src="images/delete-16x16.gif" alt="<fmt:message key="global.delete" /> ${plugin.name}" onclick="return confirm('<fmt:message key="plugin.admin.confirm" />')"/>
                </form>
            </td>
        </tr>

        <c:if test="${unsupported or loadedWithWarning}">
            <!-- When the plugin is unsupported, but *also* has an update, make sure that there's no bottom border -->
            <c:set var="overrideStyle" value="${ not empty update ? 'border-bottom-width: 0' : ''}"/>

            <tr class="${colorClass} lowerhalf">
                <td style="${overrideStyle}">&nbsp;</td>
                <td style="${overrideStyle}" colspan="6" nowrap>
                    <span class="small-label">
                        <c:if test="${not empty loadWarning}">
                            <c:out value="${loadWarning}"/>
                        </c:if>
                    </span>
                </td>
            </tr>
            <!-- End of unsupported section -->
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
                            <td><a href="javascript:download('${update.URL}', '${update.latestVersion}', '${update.hashCode()}')"><img src="images/icon_update-16x16.gif" alt="changelog"></a></td>
                            <td><a href="javascript:download('${update.URL}', '${update.latestVersion}', '${update.hashCode()}')"><span class="small-label"><fmt:message key="plugin.admin.update" /></span></a></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3">&nbsp;</td>
                <td colspan="3">&nbsp;</td>
            </tr>

            <tr id="${update.hashCode()}-update" style="display:none;" class="${colorClass} lowerhalf">
                <td colspan="8" style="text-align: center">
                    <table>
                        <tr>
                            <td id="${update.hashCode()}-image"><img src="images/working-16x16.gif" alt=""/></td>
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
        <form action="plugin-admin.jsp?uploadplugin&amp;csrf=${admin:escapeHTMLTags(csrf)}" enctype="multipart/form-data" method="post">
            <input type="file" name="uploadfile" />
            <input type="submit" value="<fmt:message key="plugin.admin.upload_plugin" />" />
        </form>
    </div>
</c:if>

</body>
</html>
