<%@ page import="java.util.*,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.wildfire.user.*,
				 org.jivesoftware.wildfire.plugin.ContentFilterPlugin,
                 org.jivesoftware.util.*"
%>
<%@ page import="java.util.regex.Pattern"%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    
    //pattern options
    boolean patternsEnabled = ParamUtils.getBooleanParameter(request, "patternsenabled");
    String patterns =  ParamUtils.getParameter(request, "patterns");

    //mask options
	boolean maskEnabled = ParamUtils.getBooleanParameter(request, "maskenabled");
   	String mask =  ParamUtils.getParameter(request, "mask");

    //rejection options
    boolean rejectionNotificationEnabled = ParamUtils.getBooleanParameter(request, "rejectionnotificationenabled");
    String rejectionMsg = ParamUtils.getParameter(request, "rejectionMsg");
  
    //notification options  
    boolean notificationEnabled = ParamUtils.getBooleanParameter(request, "notificationenabled");
    String contactName = ParamUtils.getParameter(request, "contactname");
    
    //get handle to plugin
	ContentFilterPlugin plugin = (ContentFilterPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("contentfilter");

    //input validation
    Map<String, String> errors = new HashMap<String, String>();
    if (save) {
        
        if (patterns == null) {
    	    errors.put("missingPatterns", "missingPatterns");    	    
    	}
        else {
    	   
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
	            UserManager.getInstance().getUser(contactName);
		    } catch (UserNotFoundException unfe) {
		    	errors.put("userNotFound", "userNotFound");
		    }
	    }
	    	       	    	    
        if (errors.size() == 0) {
	        plugin.setPatternsEnabled(patternsEnabled);
	        plugin.setPatterns(patterns);
	        plugin.setMaskEnabled(maskEnabled);
	        plugin.setMask(mask);
        	plugin.setViolationNotificationEnabled(notificationEnabled);
            plugin.setViolationContact(contactName);
            plugin.setRejectionNotificationEnabled(rejectionNotificationEnabled);
            plugin.setRejectionMessage(rejectionMsg);            
            response.sendRedirect("contentfilter-props-edit-form.jsp?success=true");
            return;
        }
        
    }
    else {
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
    }
    
    patternsEnabled = plugin.isPatternsEnabled();
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
	        <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
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
        	<td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
        	<td class="jive-icon-label">Error saving the settings.</td>
        </tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="contentfilter-props-edit-form.jsp?save" method="post">

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
                <label for="not01"><b>Disabled</b></label> - Messages will not be filtered.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="patternsenabled" value="true" id="not02"
             <%= ((patternsEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Enabled</b></label> - Messages will be filtered.
            </td>
        </tr>
    	<tr>
        	<td>&nbsp;</td>
	        <td align="left">Patterns:&nbsp;
	        <input type="text" size="100" maxlength="100" name="patterns" 
	        	value="<%= (patterns != null ? patterns : "") %>">
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
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Content Mask</legend>
    <div>
    
    <p>
    Enable this feature to alter message content when there is a pattern match.
    </p>
    
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
    	<tr>
            <td width="1%">
            <input type="radio" name="maskenabled" value="false" id="not03"
             <%= ((maskEnabled) ? "" : "checked") %>>
            </td>
            <td width="99%">
                <label for="not01"><b>Disabled</b></label> - Messages will be rejected.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="maskenabled" value="true" id="not04"
             <%= ((maskEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="not02"><b>Enabled</b></label> - Messages will be masked.
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
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Rejection Notification</legend>
    <div>
    
    <p>
    Enable this feature to have the message sender notified whenever a message is rejected.
    NB: This feature is only operational if "Content Mask" feature is disabled.
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
	        <td align="left">Username:&nbsp;
	        <input type="text" size="20" maxlength="100" name="contactname" 
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

<input type="submit" value="Save Properties">
</form>

</body>
</html>