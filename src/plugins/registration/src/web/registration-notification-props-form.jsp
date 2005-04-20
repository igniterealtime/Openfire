<%@ page import="java.util.*,
                 org.jivesoftware.admin.*,
                 org.jivesoftware.messenger.XMPPServer,
                 org.jivesoftware.messenger.user.*,
				 org.jivesoftware.messenger.plugin.RegistrationPlugin,
                 org.jivesoftware.util.*"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<c:set var="admin" value="${admin.manager}" />
<% admin.init(request, response, session, application, out ); %>

<%
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    boolean notificationEnabled = ParamUtils.getBooleanParameter(request, "notificationenabled");
    String contactName = ParamUtils.getParameter(request, "contactname");

	RegistrationPlugin plugin = (RegistrationPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("registrationnotification");

    Map errors = new HashMap();
    if (save) {
    	
    	if (notificationEnabled) {
    		plugin.setServiceEnabled(notificationEnabled);
            response.sendRedirect("registration-notification-props-form.jsp?success=true");
            return;
    	} 
    	else {    	
	    	if (contactName == null) {
	            errors.put("missingContactName", "missingContactName");
	        }
	        else {	        
	        	contactName = contactName.trim().toLowerCase();
	        
		        try  {
		        	UserManager.getInstance().getUser(contactName);
		    	} catch (UserNotFoundException unfe) {
		    		errors.put("userNotFound", "userNotFound");
		    	}
	        }    	
	        
	        if (errors.size() == 0) {
	        	plugin.setServiceEnabled(notificationEnabled);
	            plugin.setContact(contactName);
	            response.sendRedirect("registration-notification-props-form.jsp?success=true");
	            return;
	        }
        }
    }
    else {
        contactName = plugin.getContact();
    }

    if (errors.size() == 0) {
        contactName = plugin.getContact();
    }
    
    notificationEnabled = plugin.serviceEnabled();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    String title = "User Registration Notification";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "registration-notification-props-form.jsp"));
    pageinfo.setPageID("registration-notification-props-form");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to edit user registration notification settings. The contact person will the be the user that recieves a notification whenever a new user attempts to register.<br>
Note: This service does not detect the addition of users via the admin console.
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
            Service settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label">Error saving the contact username.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="registration-notification-props-form.jsp?save" method="post">

<fieldset>
    <legend>Service Enabled</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="true" id="rb01"
             <%= ((notificationEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b></label> - Notifications will be sent out.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="false" id="rb02"
             <%= ((!notificationEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Disabled</b></label> - Notifications will not be sent out.
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Contact Person</legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0">

    <tr>
        <td class="c1">Username:</td>
        <td>
        <input type="text" size="30" maxlength="75" name="contactname" <%= notificationEnabled ? "" : "disabled" %>" 
        	value="<%= (contactName != null ? contactName : "") %>">@<%= XMPPServer.getInstance().getServerInfo().getName() %>
        <% if (errors.containsKey("missingContactName")) { %>
            <span class="jive-error-text">
            <br>Please enter a username.
            </span>
        <% } else if (errors.containsKey("userNotFound")) { %>
            <span class="jive-error-text">
            <br>Could not find user. Please try again.
            </span>
        <% } %>
        </td>
    </tr>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />
