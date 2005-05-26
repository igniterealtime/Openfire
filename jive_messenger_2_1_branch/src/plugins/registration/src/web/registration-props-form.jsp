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
    boolean welcomeEnabled = ParamUtils.getBooleanParameter(request, "welcomeenabled");
    String welcomeMessage = ParamUtils.getParameter(request, "welcomemessage");

	RegistrationPlugin plugin = (RegistrationPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("registration");

    Map errors = new HashMap();
    if (save) {
    	
    	if (notificationEnabled) {
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
	    }
	    
	    if (welcomeEnabled) {
	        if (welcomeMessage == null) {
	            errors.put("missingWelcomeMessage", "missingWelcomeMessage");
	        } 
	    }
	    
        if (errors.size() == 0) {
        	plugin.setRegistrationNotificationEnabled(notificationEnabled);
            plugin.setContact(contactName);
            
            plugin.setRegistrationWelcomeEnabled(welcomeEnabled);
            plugin.setWelcomeMessage(welcomeMessage.trim());
            response.sendRedirect("registration-props-form.jsp?success=true");
            return;
        }
        
    }
    else {
        contactName = plugin.getContact();
        welcomeMessage = plugin.getWelcomeMessage();
    }

    if (errors.size() == 0) {
        contactName = plugin.getContact();
        welcomeMessage = plugin.getWelcomeMessage();
    }
    
    notificationEnabled = plugin.registrationNotificationEnabled();
    welcomeEnabled = plugin.registrationWelcomeEnabled();
%>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%
    String title = "User Registration";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "registration-props-form.jsp"));
    pageinfo.setPageID("registration-props-form");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Use the form below to edit user registration settings.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
	        <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
	        <td class="jive-icon-label">Service settings updated successfully.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label">Error saving the service settings.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="registration-props-form.jsp?save" method="post">

<fieldset>
    <legend>Registration Notification</legend>
    <div>
    
    <p>
    Enable this feature to have the contact person notified whenever a new user registers.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="false" id="not01"
             <%= ((notificationEnabled) ? "" : "checked") %>>
            </td>
            <td width="99%">
                <label for="not01"><b>Disabled</b></label> - Notifications will not be sent out.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="true" id="not02"
             <%= ((notificationEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Enabled</b></label> - Notifications will be sent out.
            </td>
        </tr>        
        <tr>
        	<td>&nbsp;</td>
	        <td align="left">Username:&nbsp;
	        <input type="text" size="30" maxlength="75" name="contactname" 
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
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Welcome Message</legend>
    <div>
    
    <p>
    Enable this feature to send a welcome message to new users after they have registered.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="welcomeenabled" value="false" id="wel01"
             <%= ((welcomeEnabled) ? "" : "checked") %>>
            </td>
            <td colspan="2">
                <label for="wel01"><b>Disabled</b></label> - Welcome message will not be sent out.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="welcomeenabled" value="true" id="wel02"
             <%= ((welcomeEnabled) ? "checked" : "") %>>
            </td>
            <td colspan="2">
                <label for="wel02"><b>Enabled</b></label> - Welcome message will be sent out.
            </td>
        </tr>        
        <tr>
        	<td width="1%">&nbsp;</td>
	        <td width="9%" valign="top">Message:&nbsp;</td>
	        <td width="90%">
	        	<textarea cols="45" rows="5" wrap="virtual" name="welcomemessage"><%= welcomeMessage %></textarea>
		        <% if (errors.containsKey("missingWelcomeMessage")) { %>
		            <span class="jive-error-text">
		            <br>Please enter a welcome message.
		            </span>
		        <% } %>
	        </td>
	    </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

<jsp:include page="bottom.jsp" flush="true" />
