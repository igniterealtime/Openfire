<%--
  -	$RCSfile$
  -	$Revision: $
  -	$Date: $
  -
  - Copyright (C) 2006 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.update.UpdateManager,
                 java.util.HashMap,
                 java.util.Map"
         errorPage="error.jsp"
        %>


<html>
    <head>
        <title><fmt:message key="manage-updates.title"/></title>
        <meta name="pageID" content="manage-updates"/>
    </head>
    <body>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean notificationUpdate = request.getParameter("notificationUpdate") != null;
    boolean serviceEnabled = ParamUtils.getBooleanParameter(request, "serviceEnabled");
    boolean notificationsEnabled = ParamUtils.getBooleanParameter(request, "notificationsEnabled");
    boolean updateSucess = false;

    UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

    // Update the session kick policy if requested
    Map<String, String> errors = new HashMap<String, String>();
    if (update) {
        updateManager.setServiceEnabled(serviceEnabled);
        updateSucess = true;
        updateManager.setNotificationEnabled(notificationsEnabled);
        updateSucess = true;
    }

    // Set page vars
    if (errors.size() == 0) {
        serviceEnabled = updateManager.isServiceEnabled();
        notificationsEnabled = updateManager.isNotificationEnabled();
    }
    else {
    }
%>

<p>
<fmt:message key="manage-updates.info"/>
</p>

<%  if (!errors.isEmpty()) { %>

    <div class="error">
    </div>
    <br>

<%  }
else if (updateSucess) { %>

    <div class="success">
        <fmt:message key="manage-updates.config.updated"/>
    </div><br>

<%  } %>

<form action="manage-updates.jsp" method="post">

<fieldset>
    <legend><fmt:message key="manage-updates.enabled.legend"/></legend>

    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="serviceEnabled" value="false" id="rb01"
                <%= (!serviceEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b><fmt:message key="manage-updates.label_disable"/></b> - <fmt:message key="manage-updates.label_disable_info"/>
                </label>
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="serviceEnabled" value="true" id="rb02"
                <%= (serviceEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b><fmt:message key="manage-updates.label_enable"/></b> - <fmt:message key="manage-updates.label_enable_info"/>
                </label>
            </td>
        </tr>
    </tbody>
    </table>

</fieldset>
<br/><br/>
    <fieldset>
    <legend><fmt:message key="manage-updates.notif.enabled.legend"/></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="notificationsEnabled" value="false" id="rb01"
                <%= (!notificationsEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01">
                <b><fmt:message key="manage-updates.notif.label_disable"/></b> - <fmt:message key="manage-updates.notif.label_disable_info"/>
                </label>
            </td>
        </tr>
        <tr valign="middle">
            <td width="1%" nowrap>
                <input type="radio" name="notificationsEnabled" value="true" id="rb02"
                <%= (notificationsEnabled ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02">
                <b><fmt:message key="manage-updates.notif.label_enable"/></b> - <fmt:message key="manage-updates.notif.label_enable_info"/>
                </label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>
<br>

<input type="submit" name="update" value="<fmt:message key="global.save_settings" />">

</form>



</body>
</html>
