<%@ page import="java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.openfire.plugin.UserServicePlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    String secret = ParamUtils.getParameter(request, "secret");
    boolean enabled = ParamUtils.getBooleanParameter(request, "enabled");

    UserServicePlugin plugin = (UserServicePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userservice");

    // Handle a save
    Map errors = new HashMap();
    if (save) {
        if (errors.size() == 0) {
                plugin.setEnabled(enabled);
        	plugin.setSecret(secret);
            response.sendRedirect("user-service.jsp?success=true");
            return;
        }
    }

    secret = plugin.getSecret();
    enabled = plugin.isEnabled();
%>

<html>
    <head>
        <title>User Service Properties</title>
        <meta name="pageID" content="user-service"/>
    </head>
    <body>


<p>
Use the form below to enable or disable the User Service and configure the secret key.
By default the User Service plugin is <strong>disabled</strong>, which means that
HTTP requests to the service will be ignored.
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            User service properties edited successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>

<form action="user-service.jsp?save" method="post">

<fieldset>
    <legend>User Service</legend>
    <div>
    <p>
    The addition, deletion and editing of users is not normally available outside of the admin console.
    This service lets those administration tasks be performed HTTP requests to provide
    simple integration with other applications.</p>

    <p>However, the presence of this service exposes a security risk. Therefore,
    a secret key is used to validate legitimate requests to this service. For
    full security, it's recommended that you deploy other security measures in front
    of the user service, such as restricted network access.
    </p>
    <ul>
        <input type="radio" name="enabled" value="true" id="rb01"
        <%= ((enabled) ? "checked" : "") %>>
        <label for="rb01"><b>Enabled</b> - User service requests will be processed.</label>
        <br>
        <input type="radio" name="enabled" value="false" id="rb02"
         <%= ((!enabled) ? "checked" : "") %>>
        <label for="rb02"><b>Disabled</b> - User service requests will be ignored.</label>
        <br><br>

        <label for="text_secret">Secret key:</label>
        <input type="text" name="secret" value="<%= secret %>" id="text_secret">
    </ul>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Settings">
</form>


</body>
</html>