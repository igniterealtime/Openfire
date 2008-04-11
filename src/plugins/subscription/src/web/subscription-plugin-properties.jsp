<%--
  - Copyright (C) 2005-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="java.util.*,
                 org.jivesoftware.openfire.user.*,
                 org.jivesoftware.openfire.XMPPServer,
				     org.jivesoftware.openfire.plugin.SubscriptionPlugin,
                 org.jivesoftware.util.*"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%
   boolean save = request.getParameter("save") != null;
   boolean success = request.getParameter("success") != null;
   String type = ParamUtils.getParameter(request, "type");
   String level = ParamUtils.getParameter(request, type);
   
   String username = ParamUtils.getParameter(request, "username");
   boolean addUser = ParamUtils.getBooleanParameter(request, "addUser");
   boolean deleteUser = ParamUtils.getBooleanParameter(request, "deleteUser");   

   SubscriptionPlugin plugin = (SubscriptionPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("subscription");

   Map<String, String> errors = new HashMap<String, String>();
   
	if (addUser) {
      if (username == null) {
          errors.put("missingUser", "missingUser");
      }
      else {
         username = username.trim().toLowerCase();
      
         try  {
            XMPPServer.getInstance().getUserManager().getUser(username);
            plugin.addWhiteListUser(username);
            response.sendRedirect("subscription-plugin-properties.jsp?addSuccess=true");
            return;
         }
         catch (UserNotFoundException unfe) {
            errors.put("userNotFound", "userNotFound");
         }
      }
   }

   if (deleteUser) {
      plugin.removeWhiteListUser(username);
      response.sendRedirect("subscription-plugin-properties.jsp?deleteSuccess=true");
      return;
   }
   
   
   if (save) {      
      plugin.setSubscriptionType(type);
      
      if (level != null) {
         plugin.setSubscriptionLevel(level);
      }
            
      response.sendRedirect("subscription-plugin-properties.jsp?success=true");
      return;
   }
   
   type = plugin.getSubscriptionType();
   level = plugin.getSubscriptionLevel();
%>

<html>
	<head>
	  <title>Subscription Service Properties</title>
	  <meta name="pageID" content="subscription-plugin-properties"/>
   </head>
   <body>
   
   <script language="JavaScript" type="text/javascript">
      function addUsername() {
         document.notifyform.addUser.value = 'true';
         document.notifyform.submit();
      }
   </script>

<p>Use the form below to set the subscription service properties.</p>

<% if (success) { %>

	<div class="jive-success">
	<table cellpadding="0" cellspacing="0" border="0">
	<tbody>
	   <tr>
         <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
         <td class="jive-icon-label">Service properties edited successfully.</td>
      </tr>
   </tbody>
   </table>
   </div>
   <br>
    
<% } %>

<form action="subscription-plugin-properties.jsp?save" name="notifyform" method="post">
<input type="hidden" name="addUser" value="">

<fieldset>
   <legend>Subscription Service Settings</legend>
   <div>
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
   <tbody>
      <tr>
         <td width="1%">
            <input type="radio" name="type" value="<%= SubscriptionPlugin.DISABLED %>" id="rb01"
               <%= (type.equals(SubscriptionPlugin.DISABLED) ? "checked" : "") %>>
         </td>
         <td width="99%">
            <label for="rb01"><strong>Disabled</strong></label> - No subscriptions requests will be intercepted.
         </td>
      </tr>      
      <tr>
         <td width="1%">
            <input type="radio" name="type" value="<%= SubscriptionPlugin.ACCEPT %>" id="rb02"
               <%= (type.equals(SubscriptionPlugin.ACCEPT) ? "checked" : "") %>>
         </td>
         <td width="99%">
            <label for="rb02"><strong>Accept</strong></label> - Subscription requests will be intercepted and accepted.
         </td>
      </tr>
      <tr valign="top">
	      <td width="1%" nowrap>&nbsp;</td>
         <td width="99%">

            <table cellpadding="4" cellspacing="0" border="0" width="100%">
               <tr>
                  <td width="1%">
                     <input type="radio" name="accept" value="<%= SubscriptionPlugin.LOCAL %>" id="rb03"
                        <%= (level.equals(SubscriptionPlugin.LOCAL) ? "checked" : "") %>>
                  </td>
                  <td width="99%">
                     <label for="rb03"><strong>Local</strong></label> - Only subscription requests sent by users <u>who have</u> an account on <i><%=XMPPServer.getInstance().getServerInfo().getXMPPDomain() %></i> will be intercepted and accepted.
                  </td>
               </tr>
               <tr>
                  <td width="1%">
                     <input type="radio" name="accept" value="<%= SubscriptionPlugin.ALL %>" id="rb04"
                        <%= (level.equals(SubscriptionPlugin.ALL) ? "checked" : "") %>>
                  </td>
                  <td width="99%">
                     <label for="rb04"><strong>All</strong></label> - All subscription requests will be intercepted and automatically accepted.
                  </td>
               </tr>
             </table>
         </td>
      </tr>
      <tr>
         <td width="1%">
            <input type="radio" name="type" value="<%= SubscriptionPlugin.REJECT %>" id="rb05"
               <%= (type.equals(SubscriptionPlugin.REJECT) ? "checked" : "") %>>
         </td>
         <td width="99%">
            <label for="rb05"><strong>Reject</strong></label> - Subscription requests will be intercepted and rejected.
         </td>
      </tr>
      <tr valign="top">
         <td width="1%" nowrap>&nbsp;</td>
         <td width="99%">
            <table cellpadding="4" cellspacing="0" border="0" width="100%">
               <tr>
                  <td width="1%">
                     <input type="radio" name="reject" value="<%= SubscriptionPlugin.LOCAL %>" id="rb06"
                        <%= (level.equals(SubscriptionPlugin.LOCAL) ? "checked" : "") %>>
                  </td>
                  <td width="99%">
                     <label for="rb06"><strong>Local</strong></label> - Only subscription requests sent by users <u>who do not have</u> an account on <i><%=XMPPServer.getInstance().getServerInfo().getXMPPDomain() %></i> will be intercepted and rejected.
                  </td>
               </tr>
               <tr>
                  <td width="1%">
                     <input type="radio" name="reject" value="<%= SubscriptionPlugin.ALL %>" id="rb07"
                        <%= (level.equals(SubscriptionPlugin.ALL) ? "checked" : "") %>>
                  </td>
                  <td width="99%">
                     <label for="rb07"><strong>All</strong></label> - All subscription requests will be intercepted and rejected.
                  </td>
                </tr>
             </table>
         </td>
      </tr>
   </tbody>
   </table>
   </div>
   
	<br>
   <input type="submit" value="Save Settings">
</fieldset>

<br><br>

<fieldset>
   <legend>White List</legend>
   <div>
   
   <p>Any user specified in the list below will continue to have full control over manually accepting and rejecting subscription requests.</p>
   
   <% if (ParamUtils.getBooleanParameter(request, "deleteSuccess")) { %>
   
   <div class="jive-success">
   <table cellpadding="0" cellspacing="0" border="0">
   <tbody>
      <tr>
         <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
         <td class="jive-icon-label">User successfully removed.</td>
      </tr>
   </tbody>
   </table>
   </div>
   
   <% } else if (ParamUtils.getBooleanParameter(request, "addSuccess")) { %>
   
   <div class="jive-success">
   <table cellpadding="0" cellspacing="0" border="0">
   <tbody>
      <tr>
         <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
         <td class="jive-icon-label">User successfully added.</td>
      </tr>
   </tbody>
   </table>
   </div>
   
   <% } else if (errors.containsKey("missingUser")) { %>
   
   <div class="jive-error">
   <table cellpadding="0" cellspacing="0" border="0">
   <tbody>
      <tr>
         <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
         <td class="jive-icon-label">Missing user.</td>
      </tr>
   </tbody>
   </table>
   </div>
   
   <% } else if (errors.containsKey("userNotFound")) { %>
   
   <div class="jive-error">
   <table cellpadding="0" cellspacing="0" border="0">
   <tbody>
      <tr>
         <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
         <td class="jive-icon-label">User not found.</td>
      </tr>
   </tbody>
   </table>
   </div>
   
   <% } %>
   
   <div>
   <label for="usertf">Add user:</label>
   <input type="text" name="username" size="30" maxlength="100" value="<%= (username != null ? username : "") %>" id="usertf"/>
   <input type="submit" value="Add" onclick="return addUsername();"/>
   
   <br><br>
   
   <div class="jive-table" style="width:400px;">
   <table cellpadding="0" cellspacing="0" border="0" width="100%">
   <thead>
      <tr>
         <th width="99%">User</th>
         <th width="1%" nowrap>Remove</th>
      </tr>
   </thead>
   <tbody>
   <% if (plugin.getWhiteListUsers().size() == 0) { %>
   
   <tr>
      <td width="100%" colspan="2" align="center" nowrap>No users specified, use the form above to add one.</td>
   </tr>
   
   <% } %>
   
   <% for (String user : plugin.getWhiteListUsers()) { %>
   
   <tr>
      <td width="99%"><%=user %></td>
      <td width="1%" align="center"><a
                     href="subscription-plugin-properties.jsp?deleteUser=true&username=<%=user %>"
                     title="Delete User?"
                     onclick="return confirm('Are you sure you want to delete this user?');"><img
                     src="images/delete-16x16.gif" width="16" height="16"
                     border="0" alt=""></a>
      </td>
   </tr>
   
   <% } %>
   </tbody>
   </table>
   </div>
   </div>
</fieldset>

</form>

</body>
</html>