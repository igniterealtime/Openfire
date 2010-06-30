<%--
  -	$RCSfile$
  -	$Revision: 32109 $
  -	$Date: 2006-07-12 21:59:06 -0700 (Wed, 12 Jul 2006) $
--%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ page import="org.jivesoftware.xmpp.workgroup.WorkgroupManager,
                 java.util.*,
                 org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.util.JiveGlobals"
%><%@ page import="org.jivesoftware.openfire.XMPPServer"%><%@ page import="org.jivesoftware.openfire.muc.MultiUserChatService"%>

<html>
    <head>
        <title>Workgroup Settings</title>
        <meta name="pageID" content="workgroup-settings"/>
        <!--<meta name="helpPage" content="edit_global_workgroup_settings.html"/>-->
    </head>
    <body>
<%

    // Get a workgroup manager
    WorkgroupManager wgManager = WorkgroupManager.getInstance();

    // If the workgroup manager is null, service is down so redirect:
    if (wgManager == null) {
        response.sendRedirect("error-serverdown.jsp");
        return;
    }
%>
<%  // Get parameters
    int maxChats = ParamUtils.getIntParameter(request,"maxChats",0);
    int minChats = ParamUtils.getIntParameter(request,"minChats",0);
    long requestTimeout = ParamUtils.getLongParameter(request,"requestTimeout",0);
    long offerTimeout = ParamUtils.getLongParameter(request,"offerTimeout",0);
    int rejectionTimeout = ParamUtils.getIntParameter(request,"rejectionTimeout",0);
    int maxOverflows = ParamUtils.getIntParameter(request,"maxOverflows",3);
    boolean canChangeName = ParamUtils.getBooleanParameter(request, "canChangeName");
    boolean save = request.getParameter("save") != null;

    Map errors = new HashMap();
    if (save) {
        if (maxChats <= 0) {
            errors.put("maxChats","");
        }
        if (minChats <= 0) {
            errors.put("minChats","");
        }
        if (minChats > maxChats) {
            errors.put("minChatsGreater","");
        }
        if (requestTimeout <= 0) {
            errors.put("requestTimeout","");
        }
        if (offerTimeout <= 0) {
            errors.put("offerTimeout","");
        }
        if (offerTimeout > requestTimeout) {
            errors.put("offerGreater","");
        }
        if (rejectionTimeout <= 0) {
            errors.put("rejectionTimeout","");
        }
        if (rejectionTimeout > requestTimeout) {
            errors.put("rejectionGreater","");
        }
        if (maxOverflows < 0) {
            errors.put("maxOverflows","");
        }

        if (errors.size() == 0) {
            wgManager.setDefaultMaxChats(maxChats);
            wgManager.setDefaultMinChats(minChats);
            wgManager.setDefaultRequestTimeout(requestTimeout * 1000);
            wgManager.setDefaultOfferTimeout(offerTimeout * 1000);
            JiveGlobals.setProperty("xmpp.live.rejection.timeout", Integer.toString(rejectionTimeout * 1000));
            JiveGlobals.setProperty("xmpp.live.request.overflow", Integer.toString(maxOverflows));
            JiveGlobals.setProperty("xmpp.live.agent.change-properties", canChangeName ? "true" : "false");
            // done, so redirect
            response.sendRedirect("workgroup-settings.jsp?success=true");
            return;
        }
    }

    if (errors.size() == 0) {
        maxChats = wgManager.getDefaultMaxChats();
        minChats = wgManager.getDefaultMinChats();
        requestTimeout = wgManager.getDefaultRequestTimeout() / 1000;
        offerTimeout = wgManager.getDefaultOfferTimeout() / 1000;
        rejectionTimeout = JiveGlobals.getIntProperty("xmpp.live.rejection.timeout", 20000) / 1000;
        maxOverflows = JiveGlobals.getIntProperty("xmpp.live.request.overflow", 3);
        canChangeName = JiveGlobals.getBooleanProperty("xmpp.live.agent.change-properties", true);
    }
%>
<style type="text/css">
    @import "style/style.css";
</style>
<table cellpadding="3" cellspacing="0" border="0" >
<tr><td colspan="2">
Use the form below to set properties that are global to all workgroups. The current set of
properties below only affect the default settings of newly created workgroups.
</td></tr></table>
<br>

<%  if (errors.get("general") != null) { %>

    <p class="jive-error-text">
    Error saving settings.
    </p>

<%  } %>

<%  if ("true".equals(request.getParameter("success"))) { %>

    <p class="jive-success-text">
    Settings updated successfully.
    </p>

<%  } %>

<form name="f" action="workgroup-settings.jsp" method="post">

<table width="100%" class="jive-table" cellpadding="3" cellspacing="0" border="0" width="600">
<tr>
    <th colspan="3">Global Settings</th>
</tr>
<tr valign="top">
    <td class="c1" nowrap>
        <b>Default maximum chat sessions per agent: *</b>

        <%  if (errors.get("maxChats") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } %>
    </td>
    <td class="c2">
        <input type="text" name="maxChats" size="5" maxlength="5" value="<%= maxChats %>">
    </td>
</tr>
<tr valign="top">
    <td class="c1" nowrap>
        <b>Default minimum chat sessions per agent: *</b>

        <%  if (errors.get("minChats") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } else if (errors.get("minChatsGreater") != null) { %>

            <span class="jive-error-text">
            <br>Min chats must be less than max chats.
            </span>

        <%  } %>
    </td>
    <td class="c2">
        <input type="text" name="minChats" size="5" maxlength="5" value="<%= minChats %>">
    </td>
</tr>
<tr valign="top">
    <td class="c1">
        <b>Request timeout: *</b>

        <%  if (errors.get("requestTimeout") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } %>
        <br>
        <span class="jive-description">
        The total time before an individual request will timeout if no agent accepts it.
        </span>
    </td>
    <td class="c2">
        <input type="text" name="requestTimeout" size="5" maxlength="5" value="<%= requestTimeout %>"> seconds
    </td>
</tr>
<tr valign="top">
    <td class="c1" nowrap>
        <b>Agent timeout to accept an offer: *</b>

        <%  if (errors.get("offerTimeout") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } else if (errors.get("offerGreater") != null) { %>

            <span class="jive-error-text">
            <br>Offer timeout must be less than request timeout.
            </span>

        <%  } %>
        <br>
        <span class="jive-description">
        The time each agent will be given to accept a chat request.
        </span>
    </td>
    <td class="c2">
        <input type="text" name="offerTimeout" size="5" maxlength="5" value="<%= offerTimeout %>"> seconds
    </td>
</tr>
<tr valign="top">
    <td class="c1">
        <b>Expire agent rejection: *</b>

        <%  if (errors.get("rejectionTimeout") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } else if (errors.get("rejectionGreater") != null) { %>

            <span class="jive-error-text">
            <br>Rejection timeout must be less than request timeout.
            </span>

        <%  } %>
        <br>
        <span class="jive-description">
        The time each rejection will last. Once expired new offers for the rejected request may be sent again.
        </span>
    </td>
    <td class="c2">
        <input type="text" name="rejectionTimeout" size="5" maxlength="5" value="<%= rejectionTimeout %>"> seconds
    </td>
</tr>
<tr valign="top">
    <td class="c1">
        <b>Times to overflow before canceling request: *</b>

        <%  if (errors.get("maxOverflows") != null) { %>

            <span class="jive-error-text">
            <br>Invalid number.
            </span>

        <%  } %>
        <br/>
        <span class="jive-description">
        Number of times a request may be moved to other queues before giving up and canceling the request.
        </span>
    </td>
    <td class="c2">
        <input type="text" name="maxOverflows" size="5" maxlength="5" value="<%= maxOverflows %>">
    </td>
</tr>
<tr valign="top">
    <td class="c1" nowrap>
        <b>Agents are allowed to change their names: *</b>
    </td>
    <td class="c2">
        <input type="checkbox" name="canChangeName" <%= (canChangeName ? "checked" : "") %>>
    </td>
</tr>
</table>
<br>

* Required field.

<br><br>

<input type="submit" name="save" value="Save Settings">

</form>

</body>
</html>