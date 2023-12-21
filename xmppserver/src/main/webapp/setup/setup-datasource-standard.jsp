<%--
  -
  - Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%@ page import="org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.DefaultConnectionProvider,
                 org.jivesoftware.openfire.XMPPServer,
                 java.lang.Double,
                 java.lang.Exception,
                 java.lang.Integer,
                 java.lang.String,
                 java.lang.Throwable"
%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.time.Duration" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%  // Get parameters
    String driver = ParamUtils.getParameter(request,"driver");
    String serverURL = ParamUtils.getParameter(request,"serverURL");
    String username = ParamUtils.getParameter(request,"username",true);
    String password = ParamUtils.getParameter(request,"password",true);
    int minConnections = ParamUtils.getIntParameter(request,"minConnections",-1);
    int maxConnections = ParamUtils.getIntParameter(request,"maxConnections",-1);
    double connectionTimeout = ParamUtils.getDoubleParameter(request,"connectionTimeout",0.0);

    boolean doContinue = request.getParameter("continue") != null;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    Map<String,String> errors = new HashMap<>();

    if (doContinue) {
        if ( csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals( csrfParam ) ) {
            doContinue = false;
            errors.put( "general", "CSRF Failure!" );
        }
    }

    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    // handle a continue request
    if (doContinue) {
        // Error check
        if (driver == null || "sun.jdbc.odbc.JdbcOdbcDriver".equals(driver)
                || "com.internetcds.jdbc.tds.Driver".equals(driver))
        {
            errors.put("driver","Please enter a valid JDBC driver class.");
        }
        else {
            try {
                ClassUtils.forName(driver);
            }
            catch (Throwable t) {
                errors.put("driver","Unable to load the specified JDBC driver. Please verify the " +
                        "name of the driver is correct and that the driver is in the classpath " +
                        "of this server (usually the 'lib' directory). If you add a driver to " +
                        "your classpath you will neeed to restart the server.");
            }
        }
        if (serverURL == null) {
            errors.put("serverURL", "Please enter a valid JDBC URL.");
        }
        if (minConnections < 3) {
            errors.put("minConnections","The minimum connection pool size is three connections.");
        }
        if (maxConnections < minConnections) {
            errors.put("maxConnections","The maximum number of connections cannot be less than the minimum.");
        }
        if (connectionTimeout <= 0.0) {
            errors.put("connectionTimeout","Please enter a valid connection timeout value.");
        }

        // if there were no errors, continue
        if (errors.size() == 0) {
            // set properties, test connection, etc

            // Force the standard jive connection provider to be used by deleting the current setting:
            JiveGlobals.setXMLProperty("connectionProvider.className",
                    "org.jivesoftware.database.DefaultConnectionProvider");
            DefaultConnectionProvider conProvider = new DefaultConnectionProvider();
            try {
                conProvider.setDriver(driver);
                conProvider.setConnectionTimeout(Duration.ofMinutes( Math.round(connectionTimeout * 24 * 60) ));
                conProvider.setMinConnections(minConnections);
                conProvider.setMaxConnections(maxConnections);
                conProvider.setServerURL(serverURL);
                conProvider.setUsername(username);
                conProvider.setPassword(password);
                conProvider.setTestSQL(DbConnectionManager.getTestSQL(driver));

                JiveGlobals.setXMLProperty("database.defaultProvider.driver", driver);
                JiveGlobals.setXMLProperty("database.defaultProvider.serverURL", serverURL);
                JiveGlobals.setXMLProperty("database.defaultProvider.username", username);
                JiveGlobals.setXMLProperty("database.defaultProvider.password", password);
                JiveGlobals.setXMLProperty("database.defaultProvider.testSQL", DbConnectionManager.getTestSQL(driver));

                JiveGlobals.setXMLProperty("database.defaultProvider.minConnections",
                        Integer.toString(minConnections));
                JiveGlobals.setXMLProperty("database.defaultProvider.maxConnections",
                        Integer.toString(maxConnections));
                JiveGlobals.setXMLProperty("database.defaultProvider.connectionTimeout",
                Double.toString(connectionTimeout));
            }
            catch (Exception e) {
                errors.put("general","Setting connection properties failed - please see the error "
                        + "log located in home/logs for more details.");
                LoggerFactory.getLogger("setup-datasource-standard.jsp").error("Setting connection properties failed.", e);
            }
            // No errors setting the properties, so test the connection
            DbConnectionManager.setConnectionProvider(conProvider);
            if (DbConnectionManager.testConnection(errors)) {
                // Success, move on
                response.sendRedirect("setup-profile-settings.jsp");
                return;
            }
        }
    }

    if (!doContinue) {
        // reset values of jdbc driver from props file
        driver = JiveGlobals.getXMLProperty("database.defaultProvider.driver");
        serverURL = JiveGlobals.getXMLProperty("database.defaultProvider.serverURL");
        username = JiveGlobals.getXMLProperty("database.defaultProvider.username");
        password = JiveGlobals.getXMLProperty("database.defaultProvider.password");
        try {
            minConnections = Integer.parseInt(
                    JiveGlobals.getXMLProperty("database.defaultProvider.minConnections"));
        }
        catch (Exception e) {
            minConnections = 5;
        }
        try {
            maxConnections = Integer.parseInt(
                    JiveGlobals.getXMLProperty("database.defaultProvider.maxConnections"));
        }
        catch (Exception e) {
            maxConnections = 25;
        }
        try {
            connectionTimeout = Double.parseDouble(
                    JiveGlobals.getXMLProperty("database.defaultProvider.connectionTimeout"));
        }
        catch (Exception e) {
            connectionTimeout = 1.0;
        }
    }

    pageContext.setAttribute( "errors", errors );
    pageContext.setAttribute( "driver", driver );
    pageContext.setAttribute( "serverURL", serverURL );
    pageContext.setAttribute( "username", username );
    pageContext.setAttribute( "password", password );
    pageContext.setAttribute( "minConnections", minConnections );
    pageContext.setAttribute( "maxConnections", maxConnections );
    pageContext.setAttribute( "connectionTimeout", connectionTimeout );
%>

<html>
<head>
    <title><fmt:message key="setup.datasource.standard.title" /></title>
    <meta name="currentStep" content="2"/>
</head>
<body>

    <h1>
    <fmt:message key="setup.datasource.standard.title" />
    </h1>

    <p>
    <fmt:message key="setup.datasource.standard.info" /> <fmt:message key="title" />.
    </p>

    <p>
    <b><fmt:message key="setup.datasource.standard.info2" /> </b><fmt:message key="setup.datasource.standard.info3" /> <tt>[Openfire_HOME]/resources/database</tt>.
    </p>

    <c:if test="${not empty errors}">
       <div class="error">
           <c:choose>
               <c:when test="${not empty errors['general']}">
                   <c:out value="${errors['general']}"/>
               </c:when>
               <c:otherwise>
                   <fmt:message key="setup.datasource.standard.failed_connect" />
               </c:otherwise>
           </c:choose>
       </div>
    </c:if>


    <!-- BEGIN jive-contentBox -->
    <div class="jive-contentBox">


<%  // DB preset data
    final List<String[]> presets = new ArrayList<String []>();
    presets.add(new String[]{"MySQL","com.mysql.cj.jdbc.Driver","jdbc:mysql://HOSTNAME:3306/DATABASENAME?rewriteBatchedStatements=true&characterEncoding=UTF-8&characterSetResults=UTF-8&serverTimezone=UTC"});
    presets.add(new String[]{"Oracle","oracle.jdbc.driver.OracleDriver","jdbc:oracle:thin:@HOSTNAME:1521:SID"});
    presets.add(new String[]{"Microsoft SQL Server (legacy)","net.sourceforge.jtds.jdbc.Driver","jdbc:jtds:sqlserver://HOSTNAME/DATABASENAME;appName=Openfire"});
    presets.add(new String[]{"PostgreSQL","org.postgresql.Driver","jdbc:postgresql://HOSTNAME:5432/DATABASENAME"});
    presets.add(new String[]{"IBM DB2","com.ibm.db2.jcc.DB2Driver","jdbc:db2://HOSTNAME:50000/DATABASENAME"});
    presets.add(new String[]{"Microsoft SQL Server","com.microsoft.sqlserver.jdbc.SQLServerDriver","jdbc:sqlserver://HOSTNAME:1433;databaseName=DATABASENAME;applicationName=Openfire"});
    pageContext.setAttribute("presets", presets );
%>
<script>
var data = [];
<c:set var="i" value="0"/>
<c:forEach items="${presets}" var="preset">
data[${i}] = ['${preset[0]}','${preset[1]}','${preset[2]}'];
<c:set var="i" value="${i+1}"/>
</c:forEach>

function populate(i) {
    document.dbform.driver.value=data[i][1];
    document.dbform.serverURL.value=data[i][2];
}
var submitted = false;
function checkSubmit() {
    if (!submitted) {
        submitted = true;
        return true;
    }
    return false;
}
</script>

<form action="setup-datasource-standard.jsp" method="post" name="dbform" onsubmit="return checkSubmit();">
<input type="hidden" name="csrf" value="${csrf}">

<table cellpadding="3" cellspacing="2">
<tr>
    <td nowrap align="right"><label for="presets"><fmt:message key="setup.datasource.standard.label" /></label>:</td>
    <td>
        <select size="1" name="presets" id="presets" onchange="populate(this.options[this.selectedIndex].value)">
            <option value=""><fmt:message key="setup.datasource.standard.pick_database" /></option>
            <c:forEach items="${presets}" var="preset" varStatus="status">
                <option value="${status.index}" ${preset[1] eq driver ? 'selected' : ''}>
                    &#149; <c:out value="${preset[0]}"/>
                </option>
            </c:forEach>
        </select>
    </td>
</tr>
<tr>
    <td nowrap align="right">
        <label for="driver"><fmt:message key="setup.datasource.standard.jdbc" /></label>
    </td>
    <td>
        <input type="text" name="driver" id="driver" size="75" maxlength="150" value="${fn:escapeXml(not empty driver ? driver : '')}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.jdbc_info"/></span></div>
        <c:if test="${not empty errors['driver']}">
            <span class="jive-error-text">
                <c:out value="${errors['driver']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td nowrap align="right">
        <label for="serverURL"><fmt:message key="setup.datasource.standard.url" /></label>
    </td>
    <td>
        <input type="text" name="serverURL" id="serverURL" size="75" maxlength="250" value="${not empty serverURL ? fn:escapeXml(serverURL) : ''}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.valid_url"/></span></div>
        <c:if test="${not empty errors['serverURL']}">
            <span class="jive-error-text">
                <c:out value="${errors['serverURL']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr>
    <td nowrap align="right">
        <label for="username"><fmt:message key="setup.datasource.standard.username" /></label>
    </td>
    <td>
        <input type="text" name="username" id="username" size="20" maxlength="50" value="${fn:escapeXml(not empty username ? username : '')}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.username_info"/></span></div>
        <c:if test="${not empty errors['username']}">
            <span class="jive-error-text">
                <c:out value="${errors['username']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td nowrap align="right">
        <label for="password"><fmt:message key="setup.datasource.standard.password" /></label>
    </td>
    <td>
        <input type="password" name="password" id="password" size="20" maxlength="50" value="${fn:escapeXml(not empty password ? password : '')}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.password_info"/></span></div>
        <c:if test="${not empty errors['password']}">
            <span class="jive-error-text">
                <c:out value="${errors['password']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr>
    <td nowrap align="right">
        <label for="minConnections"><fmt:message key="setup.datasource.standard.min_connections" /></label>
    </td>
    <td>
        <input type="number" min="0" name="minConnections" id="minConnections" size="5" maxlength="5" value="${fn:escapeXml(not empty minConnections and minConnections > -1 ? minConnections : '')}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.pool"/></span></div>
        <c:if test="${not empty errors['minConnections']}">
            <span class="jive-error-text">
                <c:out value="${errors['minConnections']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td nowrap align="right">
        <label for="maxConnections"><fmt:message key="setup.datasource.standard.max_connections" /></label>
    </td>
    <td>
        <input type="number" min="1" name="maxConnections" id="maxConnections" size="5" maxlength="5" value="${fn:escapeXml(not empty maxConnections and maxConnections > -1 ? maxConnections : '')}">
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.pool"/></span></div>
        <c:if test="${not empty errors['maxConnections']}">
            <span class="jive-error-text">
                <c:out value="${errors['maxConnections']}"/>
            </span>
        </c:if>
    </td>
</tr>
<tr>
    <td nowrap align="right">
        <label for="connectionTimeout"><fmt:message key="setup.datasource.standard.timeout" /></label>
    </td>
    <td>
        <input type="text" name="connectionTimeout" id="connectionTimeout" size="5" maxlength="5" value="${fn:escapeXml(not empty connectionTimeout ? connectionTimeout : '')}"> <span style="display: block; float: left; padding: 2px 5px 0px 2px;"><fmt:message key="setup.datasource.standard.timeout.days" /></span>
        <div class="openfire-helpicon-with-tooltip"><span class="helpicon"></span><span class="tooltiptext"><fmt:message key="setup.datasource.standard.timeout_info"/></span></div>
        <c:if test="${not empty errors['connectionTimeout']}">
            <span class="jive-error-text">
                <c:out value="${errors['connectionTimeout']}"/>
            </span>
        </c:if>
    </td>
</tr>
</table>

<br>

        <div align="right"><div class="jive-description" style="padding-bottom:10px;">
            <fmt:message key="setup.datasource.standard.note" /></div>
            <input type="Submit" name="continue" value="<fmt:message key="global.continue" />" id="jive-setup-save">
        </div>
    </form>

    </div>
    <!-- END jive-contentBox -->


</body>
</html>
