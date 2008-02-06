<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.openfire.plugin.PresencePlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    PresencePlugin plugin = (PresencePlugin)XMPPServer.getInstance().getPluginManager().getPlugin("presence");

    // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
	boolean presencePublic = ParamUtils.getBooleanParameter(request, "presencePublic");
    String unavailableStatus = ParamUtils.getParameter(request, "presenceUnavailableStatus", false);
    if (unavailableStatus == null) {
        unavailableStatus = plugin.getUnavailableStatus();
    }


    // Handle a save
    if (save) {
        plugin.setPresencePublic(presencePublic);
        plugin.setUnavailableStatus(unavailableStatus);
        response.sendRedirect("presence-service.jsp?success=true");
        return;
    }

    presencePublic = plugin.isPresencePublic();
%>

<html>
    <head>
        <title>Presence Service</title>
        <meta name="pageID" content="presence-service"/>
    </head>
    <body>

<div class="information">
    <%
        String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        int port = JiveGlobals.getXMLProperty("adminConsole.port", -1);
        int securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", -1);
        boolean secureOnly = (port == -1);

    %>

    Presence Service URL:<br>
    <tt><%= secureOnly?"https":"http"%>://<%=serverName%>:<%= secureOnly?securePort:port%>/plugins/presence/status</tt>
    <br><br>
    Example:<br>
    <tt><%= secureOnly?"https":"http"%>://<%=serverName%>:<%= secureOnly?securePort:port%>/plugins/presence/status?jid=admin@<%=serverName%></tt>  


</div>

<p>
Use the form below to configure user presence visibility. By default, user
presence should only be visible to those users that are authorized.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Presence service properties edited successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>

<form action="presence-service.jsp?save" method="post">

<fieldset>
    <legend>Presence visibility</legend>
    <div>
    <p>
    For security reasons, users control which users are authorized to see their presence. However,
    it is posible to configure the service so that anyone has access to all presence information.
    Use this option with caution.
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="presencePublic" value="true" id="rb01"
             <%= ((presencePublic) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Anyone</b> - Anyone may get presence information.</label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="presencePublic" value="false" id="rb02"
             <%= ((!presencePublic) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Subscribed</b> - Presence information is only visibile to authorized users.</label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br>
    
<fieldset>
    <legend>Plain Text 'Unavailable' Status Message</legend>
    <div>
    <p>
    In &quot;text&quot; mode the status message for unavailable users is &quot;Unavailable&quot;
    by default. It is possible to change the unavailable status message by setting this property.
    </p>
    <p>
        <input type="text" name="presenceUnavailableStatus" value="<%= unavailableStatus %>">
    </p>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

</body>
</html>