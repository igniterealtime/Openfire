<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 java.util.*,
                 java.beans.BeanInfo,
                 java.beans.Introspector,
                 java.beans.PropertyDescriptor,
                 org.jivesoftware.messenger.JiveGlobals,

                 java.sql.Connection,
                 java.io.File,
                 java.sql.Statement,
                 java.sql.SQLException,

                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.database.DefaultConnectionProvider,
                 org.jivesoftware.database.DefaultConnectionProvider"
%>

<%@ include file="setup-global.jspf" %>

<%  // Get parameters
    String driver = ParamUtils.getParameter(request,"driver");
    String serverURL = ParamUtils.getParameter(request,"serverURL");
    String username = ParamUtils.getParameter(request,"username",true);
    String password = ParamUtils.getParameter(request,"password",true);
    int minConnections = ParamUtils.getIntParameter(request,"minConnections",-1);
    int maxConnections = ParamUtils.getIntParameter(request,"minConnections",-1);
    double connectionTimeout = ParamUtils.getDoubleParameter(request,"connectionTimeout",0.0);

    boolean doContinue = request.getParameter("continue") != null;

    // handle a continue request
    Map errors = new HashMap();
    if (doContinue) {
        // Error check
        if (driver == null || "sun.jdbc.odbc.JdbcOdbcDriver".equals(driver)
                || "com.internetcds.jdbc.tds.Driver".equals(driver))
        {
            errors.put("driver","Please enter a valid JDBC driver class.");
        }
        else {
            try {
                Class c = loadClass(driver);
            }
            catch (Throwable t) {
                errors.put("driver","Unable to load specified JDBC driver. Please verify the " +
                        "name of the driver is correct and that the driver is in the classpath " +
                        "of this server (usually the 'lib' directory). If you add a driver to " +
                        "your classpath you will neeed to restart the server.");
            }
        }
        if (serverURL == null) {
            errors.put("serverURL", "Please enter a valid JDBC URL.");
        }
        if (minConnections < 0) {
            errors.put("minConnections","Please enter a valid minimum number of connections.");
        }
        if (maxConnections < 0) {
            errors.put("maxConnections","Please enter a valid maximum number of connections.");
        }
        if (connectionTimeout <= 0.0) {
            errors.put("connectionTimeout","Please enter a valid connection timeout value.");
        }

        // if there were no errors, continue
        if (errors.size() == 0) {
            // set properties, test connection, etc

            // Force the standard jive connection provider to be used by deleting the current setting:
            JiveGlobals.setProperty("connectionProvider.className",
                    "org.jivesoftware.database.DefaultConnectionProvider");
            DefaultConnectionProvider conProvider = new DefaultConnectionProvider();
            try {
                conProvider.setDriver(driver);
                conProvider.setConnectionTimeout(connectionTimeout);
                conProvider.setMinConnections(minConnections);
                conProvider.setMaxConnections(maxConnections);
                conProvider.setServerURL(serverURL);
                conProvider.setUsername(username);
                conProvider.setPassword(password);

                 JiveGlobals.setProperty("database.defaultProvider.driver", driver);
        JiveGlobals.setProperty("database.defaultProvider.serverURL", serverURL);
        JiveGlobals.setProperty("database.defaultProvider.username", username);
        JiveGlobals.setProperty("database.defaultProvider.password", password);

        JiveGlobals.setProperty("database.defaultProvider.minConnections",
                Integer.toString(minConnections));
        JiveGlobals.setProperty("database.defaultProvider.maxConnections",
                Integer.toString(maxConnections));
        JiveGlobals.setProperty("database.defaultProvider.connectionTimeout",
                Double.toString(connectionTimeout));
            }
            catch (Exception e) {
                errors.put("general","Setting connection properties failed - please see the error "
                        + "log located in messengerHome/logs for more details.");
                Log.error(e);
            }
            // No errors setting the properties, so test the connection
            DbConnectionManager.setConnectionProvider(conProvider);
            if (testConnection(errors)) {
                // update the sidebar status
                session.setAttribute("jive.setup.sidebar.3","done");
                session.setAttribute("jive.setup.sidebar.4","in_progress");
                // success, move on
                response.sendRedirect("setup-admin-settings.jsp");
                return;
            }
        }
    }

    if (!doContinue) {
        // reset values of jdbc driver from props file
        driver = JiveGlobals.getProperty("database.defaultProvider.driver");
        serverURL = JiveGlobals.getProperty("database.defaultProvider.serverURL");
        username = JiveGlobals.getProperty("database.defaultProvider.username");
        password = JiveGlobals.getProperty("database.defaultProvider.password");
        try {
            minConnections = Integer.parseInt(
                    JiveGlobals.getProperty("database.defaultProvider.minConnections"));
        }
        catch (Exception e) {
            minConnections = 5;
        }
        try {
            maxConnections = Integer.parseInt(
                    JiveGlobals.getProperty("database.defaultProvider.maxConnections"));
        }
        catch (Exception e) {
            maxConnections = 15;
        }
        try {
            connectionTimeout = Double.parseDouble(
                    JiveGlobals.getProperty("database.defaultProvider.connectionTimeout"));
        }
        catch (Exception e) {
            connectionTimeout = 1.0;
        }
    }
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
Datasource Settings - Standard Connection
</p>

<p>
Specify a JDBC driver and connection properties to connect to your database. If you need more
information about this process please see the database documentation distributed with <fmt:message key="title" bundle="${lang}" />.
</p>

<%  if (errors.size() > 0) { %>

    <span class="jive-error-text">
    <%  if (errors.get("general") != null) { %>

        <%= errors.get("general") %>

    <%  } else { %>

        Failed to establish a database connection - please see specific errors listed below.

    <%  } %>
    </span>

<%  } %>

<%  // DB preset data
    List presets = new ArrayList();
    presets.add(new String[]{"MySQL","org.gjt.mm.mysql.Driver","jdbc:mysql://[host-name]:3306/[database-name]"});
    presets.add(new String[]{"Oracle","oracle.jdbc.driver.OracleDriver","jdbc:oracle:thin:@[host-name]:1521:[SID]"});
    presets.add(new String[]{"MSSQL","com.microsoft.jdbc.sqlserver.SQLServerDriver","jdbc:microsoft:sqlserver://[host-name]:1433"});
    presets.add(new String[]{"PostgreSQL","org.postgresql.Driver","jdbc:postgresql://[host-name]:5432/[database-name]"});
    presets.add(new String[]{"IBM DB2","COM.ibm.db2.jdbc.app.DB2Driver","jdbc:db2:[database-name]"});
%>
<script language="JavaScript" type="text/javascript">
var data = new Array();
<%  for (int i=0; i<presets.size(); i++) {
        String[] data = (String[])presets.get(i);
%>
    data[<%= i %>] = new Array('<%= data[0] %>','<%= data[1] %>','<%= data[2] %>');
<%  } %>
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

<form action="setup-datasource-standard.jsp" method="post" name="dbform"
 onsubmit="return checkSubmit();">

<table cellpadding="3" cellspacing="2" border="0">
<tr>
    <td colspan="2">
        Database Driver Presets:
        <select size="1" name="presets" onchange="populate(this.options[this.selectedIndex].value)">
            <option value="">Pick Database...
            <%  for (int i=0; i<presets.size(); i++) {
                    String[] data = (String[])presets.get(i);
            %>
                <option value="<%= i %>"> &#149; <%= data[0] %>
            <%  } %>
        </select>
        <br><br>
    </td>
</tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        JDBC Driver Class:
    </td>
    <td>
        <input type="text" name="driver" size="50" maxlength="150"
         value="<%= ((driver != null) ? driver : "") %>">
        <span class="jive-description">
        <br>
        The valid classname of your JDBC driver, ie: com.mydatabase.driver.MyDriver.
        </span>
        <%  if (errors.get("driver") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("driver") %>
            </span>

        <%  } %>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        Database URL:
    </td>
    <td>
        <input type="text" name="serverURL" size="50" maxlength="250"
         value="<%= ((serverURL != null) ? serverURL : "") %>">
        <span class="jive-description">
        <br>
        The valid URL used to connect to your database, ie: jdbc:mysql://host:port/database
        </span>
        <%  if (errors.get("serverURL") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("serverURL") %>
            </span>

        <%  } %>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        Username:
    </td>
    <td>
        <input type="text" name="username" size="20" maxlength="50"
         value="<%= ((username != null) ? username : "") %>">
        <span class="jive-description">
        <br>
        The user used to connect to your database - note, this may not be required and can be left
        blank.
        </span>
        <%  if (errors.get("username") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("username") %>
            </span>

        <%  } %>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        Password:
    </td>
    <td>
        <input type="password" name="password" size="20" maxlength="50"
         value="<%= ((password != null) ? password : "") %>">
        <span class="jive-description">
        <br>
        The password for the user account used for this database - note, this may not be required
        and can be left blank.
        </span>
        <%  if (errors.get("password") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("password") %>
            </span>

        <%  } %>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        Connections:
    </td>
    <td>
        Minimum: <input type="text" name="minConnections" size="5" maxlength="5"
         value="<%= ((minConnections != -1) ? ""+minConnections : "") %>">
        &nbsp;
        Maximum: <input type="text" name="maxConnections" size="5" maxlength="5"
         value="<%= ((maxConnections != -1) ? ""+maxConnections : "") %>">
        <span class="jive-description">
        <br>
        The minimum and maximum number of database connections the connection pool should maintain.
        </span>
        <%  if (errors.get("minConnections") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("minConnections") %>
            </span>

        <%  } %>
        <%  if (errors.get("maxConnections") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("maxConnections") %>
            </span>

        <%  } %>
    </td>
</tr>
<tr><td colspan="2">&nbsp;</td></tr>
<tr valign="top">
    <td class="jive-label" nowrap>
        Connection Timeout:
    </td>
    <td>
        <input type="text" name="connectionTimeout" size="5" maxlength="5"
         value="<%= connectionTimeout %>">
        <span class="jive-description">
        <br>
        The time (in days) before connections in the connection pool are recycled.
        </span>
        <%  if (errors.get("connectionTimeout") != null) { %>

            <br>
            <span class="jive-error-text">
            <%= errors.get("connectionTimeout") %>
            </span>

        <%  } %>
    </td>
</tr>
</table>

<br><br>

<hr size="0">

<div align="right">
    <input type="submit" name="continue" value=" Continue ">
    <br>
    Note, it might take between 30-60 seconds to connect to your database.
</div>

</form>

<%@ include file="setup-footer.jsp" %>