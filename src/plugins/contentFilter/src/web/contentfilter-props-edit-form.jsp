<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer,
                 org.jivesoftware.openfire.user.*,
				org.jivesoftware.openfire.plugin.ContentFilterPlugin,
                 org.jivesoftware.util.*"
%>
<%@ page import="java.util.regex.Pattern"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean save = request.getParameter("save") != null;
    boolean reset = request.getParameter("reset") !=null;
    boolean success = request.getParameter("success") != null;
    
    //filter options
    boolean patternsEnabled = ParamUtils.getBooleanParameter(request, "patternsenabled");
    String patterns =  ParamUtils.getParameter(request, "patterns");
    String [] filterStatusChecked = ParamUtils.getParameters(request, "filterstatus");
    boolean filterStatusEnabled = filterStatusChecked.length > 0;
 
    //match options
    boolean allowOnMatch = ParamUtils.getBooleanParameter(request, "allowonmatch");
    String [] maskChecked = ParamUtils.getParameters(request, "maskenabled");
	boolean maskEnabled = maskChecked.length > 0;
   	String mask =  ParamUtils.getParameter(request, "mask");

    //rejection options
    boolean rejectionNotificationEnabled = ParamUtils.getBooleanParameter(request, "rejectionnotificationenabled");
    String rejectionMsg = ParamUtils.getParameter(request, "rejectionMsg");
  
    //notification options  
    boolean notificationEnabled = ParamUtils.getBooleanParameter(request, "notificationenabled");
    String contactName = ParamUtils.getParameter(request, "contactname");
    List<String> notificationOptions = Arrays.asList(ParamUtils.getParameters(request, "notificationcb"));
    boolean notificationByIMEnabled = notificationOptions.contains("notificationbyim");
    boolean notificationByEmailEnabled = notificationOptions.contains("notificationbyemail");
    boolean includeOriginalEnabled = notificationOptions.contains("notificationincludeoriginal");
    
    //get handle to plugin
	ContentFilterPlugin plugin = (ContentFilterPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("contentfilter");

    //input validation
    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
    
        if (patterns == null) {
            errors.put("missingPatterns", "missingPatterns");
        } else {
            String[] data = patterns.split(",");
            try {
                for (String aData : data) {
                    Pattern.compile(aData);
                }
            } catch (java.util.regex.PatternSyntaxException e) {
    			    errors.put("patternSyntaxException", e.getMessage());
    			}
    		}
    		    	
	    	if (mask == null) {
	    	    errors.put("missingMask", "missingMask");
	    	}
    	
	    	if (rejectionMsg == null) {
	    	    errors.put("missingRejectionMsg", "missingRejectionMsg");
	    	}
    	
	    	if (contactName == null) {
		    errors.put("missingContactName", "missingContactName");
		} else {
		    contactName = contactName.trim().toLowerCase();
		    try  {
		        User user = UserManager.getInstance().getUser(contactName);
		        if (notificationByEmailEnabled) {
		            //verify that the user has an email address
		            if (user.getEmail() == null) {
		                errors.put("userEmailNotConfigured", "userEmailNotConfigured");
		            }
		            //verify that the email server is configured
		            if (!JiveGlobals.getBooleanProperty("mail.configured", false)) {
		                errors.put("mailServerNotConfigured", "mailServerNotConfigured");
		            }
		        }
			} catch (UserNotFoundException unfe) {
			    errors.put("userNotFound", "userNotFound");
			}
		}
		
		if (!notificationByIMEnabled && !notificationByEmailEnabled) {
		    errors.put("notificationFormatNotConfigured", "notificationFormatNotConfigured");
		}
	    	       	    	    
	    if (errors.size() == 0) {
		    plugin.setPatternsEnabled(patternsEnabled);
		    plugin.setPatterns(patterns);
		    plugin.setFilterStatusEnabled(filterStatusEnabled);
		    plugin.setAllowOnMatch(allowOnMatch);
		    plugin.setMaskEnabled(maskEnabled);
		    plugin.setMask(mask);
	        plugin.setViolationNotificationEnabled(notificationEnabled);
	        plugin.setViolationContact(contactName);
	        plugin.setViolationNotificationByIMEnabled(notificationByIMEnabled);
	        plugin.setViolationNotificationByEmailEnabled(notificationByEmailEnabled);
	        plugin.setViolationIncludeOriginalPacketEnabled(includeOriginalEnabled);           
	        plugin.setRejectionNotificationEnabled(rejectionNotificationEnabled);
	        plugin.setRejectionMessage(rejectionMsg);            
	        response.sendRedirect("contentfilter-props-edit-form.jsp?success=true");
	        return;
	    }
    } else if (reset) {
      plugin.reset();
      response.sendRedirect("contentfilter-props-edit-form.jsp?success=true");
    } else {
        patterns = plugin.getPatterns();
        mask = plugin.getMask();   
        contactName = plugin.getViolationContact();
        rejectionMsg = plugin.getRejectionMessage();
    }

    if (errors.size() == 0) {
        patterns = plugin.getPatterns();
        mask = plugin.getMask();   
        contactName = plugin.getViolationContact();
        rejectionMsg = plugin.getRejectionMessage();
        notificationByIMEnabled = plugin.isViolationNotificationByIMEnabled();
        notificationByEmailEnabled = plugin.isViolationNotificationByEmailEnabled();
        includeOriginalEnabled = plugin.isViolationIncludeOriginalPacketEnabled();
    }
    
    patternsEnabled = plugin.isPatternsEnabled();
    filterStatusEnabled = plugin.isFilterStatusEnabled();
    allowOnMatch = plugin.isAllowOnMatch();
    maskEnabled = plugin.isMaskEnabled();
    notificationEnabled = plugin.isViolationNotificationEnabled();
    rejectionNotificationEnabled = plugin.isRejectionNotificationEnabled();

%>

<html>
    <head>
        <title>Content Filter</title>
        <meta name="pageID" content="contentfilter-props-edit-form"/>
    </head>
    <body>

<p>
Use the form below to edit content filter settings.<br>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
	        <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
	        <td class="jive-icon-label">Settings updated successfully.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr>
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        	<td class="jive-icon-label">Error saving the settings.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="contentfilter-props-edit-form.jsp" method="post">

<fieldset>
    <legend>Filter</legend>
    <div>
    
    <p>
    To enable the content filter you need to set up some regular expressions.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
    	    <td width="1%">
            <input type="radio" name="patternsenabled" value="false" id="not01"
             <%= ((patternsEnabled) ? "" : "checked") %>>
        </td>
        <td width="99%">
            <label for="not01"><b>Disabled</b></label> - Packets will not be filtered.
        </td>
    </tr>
    <tr>
        <td width="1%">
            <input type="radio" name="patternsenabled" value="true" id="not02"
             <%= ((patternsEnabled) ? "checked" : "") %>>
        </td>
        <td width="99%">
            <label for="not02"><b>Enabled</b></label> - Packets will be filtered.
        </td>
    </tr>
    	<tr>
        	<td>&nbsp;</td>
        	<td align="left">Patterns:&nbsp;</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
	    <td>
	        <textarea rows="10" cols="100" name="patterns"><%= (patterns != null ? patterns : "") %></textarea>
	        	<% if (errors.containsKey("missingPatterns")) { %>
	            <span class="jive-error-text">
	                <br>Please enter comma separated, regular expressions.
	            </span>
	            <% } else if (errors.containsKey("patternSyntaxException")) { %>
	            <span class="jive-error-text">
	                <br>Invalid regular expression: <%= errors.get("patternSyntaxException") %>. Please try again.
	            </span>
	            <% } %>
	    </td>
	</tr>
	<tr>
		<td>&nbsp;</td>
        <td><input type="checkbox" name="filterstatus" value="filterstatus" <%= filterStatusEnabled ? "checked" : "" %>/>Filter users presence status.</td>
	</tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>On Content Match</legend>
    <div>
    
    <p>
    Configure this feature to reject or allow (and optionally mask) packet content when there is a match.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="allowonmatch" value="false" id="not03"
             <%= ((allowOnMatch) ? "" : "checked") %>>
            </td>
            <td width="99%">
                <label for="not01"><b>Reject</b></label> - Packets will be rejected.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="allowonmatch" value="true" id="not04"
             <%= ((allowOnMatch) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Allow</b></label> - Packets will be allowed.
            </td>
        </tr>
        <tr>
        	<td>&nbsp;</td>
	        <td align="left">Mask:&nbsp;
	        <input type="text" size="100" maxlength="100" name="mask" 
	        	value="<%= (mask != null ? mask : "") %>">
	        	<% if (errors.containsKey("missingMask")) { %>
	            <span class="jive-error-text">
	                <br>Please enter a mask.
	            </span>
	            <% } %>
	        </td>
	    </tr>
	    <tr>
		<td>&nbsp;</td>
        <td><input type="checkbox" name="maskenabled" value="maskenabled" <%= maskEnabled ? "checked" : "" %>/>Enable mask.</td>
	</tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Rejection Notification</legend>
    <div>
    
    <p>
    Enable this feature to have the sender notified whenever a packet is rejected.
    NB: This feature is only operational if "On Content Match" is set to reject packets.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="rejectionnotificationenabled" value="false" id="not05"
             <%= ((rejectionNotificationEnabled) ? "" : "checked") %>>
            </td>
            <td width="99%">
                <label for="not01"><b>Disabled</b></label> - Notifications will not be sent out.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="rejectionnotificationenabled" value="true" id="not06"
             <%= ((rejectionNotificationEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Enabled</b></label> - Notifications will be sent out.
            </td>
        </tr>
         <tr>
        	<td>&nbsp;</td>
	        <td align="left">Rejection message:&nbsp;
	        <input type="text" size="100" maxlength="100" name="rejectionMsg" 
	        	value="<%= (rejectionMsg != null ? rejectionMsg : "") %>">
	        	<% if (errors.containsKey("missingRejectionMsg")) { %>
	            <span class="jive-error-text">
	                <br>Please enter a rejection message.
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
    <legend>Content Match Notification</legend>
    <div>
    
    <p>
    Enable this feature to have the contact person notified whenever there is a content match.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="false" id="not07"
             <%= ((notificationEnabled) ? "" : "checked") %>>
            </td>
            <td width="99%">
                <label for="not01"><b>Disabled</b></label> - Notifications will not be sent out.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="notificationenabled" value="true" id="not08"
             <%= ((notificationEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Enabled</b></label> - Notifications will be sent out.
            </td>
        </tr>        
        <tr>
        	    <td>&nbsp;</td>
	        <td align="left">Username:&nbsp
                <input type="text" size="20" maxlength="100" name="contactname" value="<%= (contactName != null ? contactName : "") %>">@<%= XMPPServer.getInstance().getServerInfo().getXMPPDomain() %>
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
	    <tr>
	        <td>&nbsp;</td>
	        <td>
                <input type="checkbox" name="notificationcb" value="notificationbyim" <%= notificationByIMEnabled ? "checked" : "" %>/>Notify by IM.
                <input type="checkbox" name="notificationcb" value="notificationbyemail" <%= notificationByEmailEnabled ? "checked" : "" %>/>Notify by Email.
	            <input type="checkbox" name="notificationcb" value="notificationincludeoriginal" <%= includeOriginalEnabled ? "checked" : "" %>/>Include original packet.
	            <% if (errors.containsKey("mailServerNotConfigured")) { %>
		            <span class="jive-error-text">
		            <br>Error, sending an email will fail because the mail server is not setup. Please go to the <a href="/system-email.jsp">mail settings page</a> and set the mail host.
		            </span>
		        <% } else if (errors.containsKey("userEmailNotConfigured")) { %>
		            <span class="jive-error-text">
		            <br>Please configure <a href="/user-properties.jsp?username=<%= contactName %>"><%= contactName %>'s</a> email address.
		            </span>
		        <% } else if (errors.containsKey("notificationFormatNotConfigured")) { %>
		            <span class="jive-error-text">
		            <br>Users must be notified by IM and/or Email.
		            </span>
		        <% } %>
	        </td>
	    </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="Save settings">
<input type="submit" name="reset" value="Restore factory settings*">
</form>

<br><br>

<em>*Restores the plugin to its factory state, you will lose all changes ever made to this plugin!</em>
</body>
</html>