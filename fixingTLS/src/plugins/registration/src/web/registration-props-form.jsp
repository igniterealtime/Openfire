<%@ page
   import="java.util.*,
           org.jivesoftware.admin.*,
           org.jivesoftware.messenger.XMPPServer,
           org.jivesoftware.messenger.user.*,
           org.jivesoftware.messenger.plugin.RegistrationPlugin,
           org.jivesoftware.messenger.group.*,
           org.jivesoftware.util.*"
   errorPage="error.jsp"%>

<script lang="JavaScript" type="text/javascript">
function addIMContact() {
	document.regform.addIM.value = 'true';
	document.regform.submit();
}

function addEmailContact() {
	document.regform.addEmail.value = 'true';
	document.regform.submit();
}
</script>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager" />
<c:set var="admin" value="${admin.manager}" />
<%
	admin.init(request, response, session, application, out); 

	boolean save = request.getParameter("save") != null;
	boolean saveWelcome = request.getParameter("savemessage") != null;
	boolean saveGroup = request.getParameter("savegroup") != null;
	
	boolean imEnabled = ParamUtils.getBooleanParameter(request, "imenabled", false);	
	boolean emailEnabled = ParamUtils.getBooleanParameter(request, "emailenabled", false);
	boolean welcomeEnabled = ParamUtils.getBooleanParameter(request, "welcomeenabled", false);
	boolean groupEnabled = ParamUtils.getBooleanParameter(request, "groupenabled", false);
	
	String contactIM = ParamUtils.getParameter(request, "contactIM");
	boolean addIM = ParamUtils.getBooleanParameter(request, "addIM");
	boolean deleteIM = ParamUtils.getBooleanParameter(request, "deleteIM");

	String contactEmail = ParamUtils.getParameter(request, "contactEmail");
	boolean addEmail = ParamUtils.getBooleanParameter(request, "addEmail");
	boolean deleteEmail = ParamUtils.getBooleanParameter(request, "deleteEmail");    
	
	String welcomeMessage = ParamUtils.getParameter(request, "welcomemessage");
	String group = ParamUtils.getParameter(request, "groupname");

	RegistrationPlugin plugin = (RegistrationPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("registration");

   Map<String, String> errors = new HashMap<String, String>();
	if (addIM) {
    	if (contactIM == null) {
	    	errors.put("missingContact", "missingContact");
		}
    	else {
        	contactIM = contactIM.trim().toLowerCase();
        
			try  {
				admin.getUserManager().getUser(contactIM);
				plugin.addIMContact(contactIM);
				response.sendRedirect("registration-props-form.jsp?addSuccess=true");
				return;
			}
			catch (UserNotFoundException unfe) {
				errors.put("userNotFound", "userNotFound");
			}
		}
	}

	if (deleteIM) {
		plugin.removeIMContact(contactIM);
		response.sendRedirect("registration-props-form.jsp?deleteSuccess=true");
		return;
	}

	if (addEmail) {
    	if (contactEmail == null) {
	    	errors.put("missingContact", "missingContact");
 		}
    	else {
			if (plugin.isValidAddress(contactEmail)) {
				plugin.addEmailContact(contactEmail);
				response.sendRedirect("registration-props-form.jsp?addSuccess=true");
				return;
 			}
			else {
				errors.put("invalidAddress", "invalidAddress");
			}
		}
	}

	if (deleteEmail) {
		plugin.removeEmailContact(contactEmail);
		response.sendRedirect("registration-props-form.jsp?deleteSuccess=true");
		return;
	}
	
	if (save) {
		plugin.setIMNotificationEnabled(imEnabled);
		plugin.setEmailNotificationEnabled(emailEnabled);
		plugin.setWelcomeEnabled(welcomeEnabled);
		
		if (groupEnabled) {
		   group = plugin.getGroup();
		   if (group == null || group.trim().length() < 1) {
				errors.put("groupNotFound", "groupNotFound");
			} 
		    
			try {
			    admin.getGroupManager().getGroup(group);
			}
			catch (Exception e) {
				errors.put("groupNotFound", "groupNotFound");
			}
		}
		
		if (errors.size() == 0) {
			plugin.setGroupEnabled(groupEnabled);
			response.sendRedirect("registration-props-form.jsp?settingsSaved=true");
			return;
		}		
	}
	
	if (saveWelcome) {
	    if (welcomeMessage == null || welcomeMessage.trim().length() < 1) {
			errors.put("missingWelcomeMessage", "missingWelcomeMessage");
		} else {
			plugin.setWelcomeMessage(welcomeMessage);
			response.sendRedirect("registration-props-form.jsp?welcomeSaved=true");
			return;
		}
	}
	
	if (saveGroup && plugin.groupEnabled()) {
		if (group == null || group.trim().length() < 1) {
			errors.put("groupNotFound", "groupNotFound");
		} 
	    
		try {
		    admin.getGroupManager().getGroup(group);
		}
		catch (Exception e) {
			errors.put("groupNotFound", "groupNotFound");
		}
	    
		if (errors.size() == 0) {
			plugin.setGroup(group);
			response.sendRedirect("registration-props-form.jsp?groupSaved=true");
			return;
		}
	}
	
	if (saveGroup && !plugin.groupEnabled()) {
	    group = (group == null) ? "" : group;
	    plugin.setGroup(group);
	}
	
	imEnabled = plugin.imNotificationEnabled();
	emailEnabled = plugin.emailNotificationEnabled();
	welcomeEnabled = plugin.welcomeEnabled();
	groupEnabled = plugin.groupEnabled();
	
	welcomeMessage = plugin.getWelcomeMessage();
	group = plugin.getGroup();
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

<p>Use the form below to edit user registration settings.</p>

<form action="registration-props-form.jsp?save" name="regform" method="post">
<input type="hidden" name="addIM" value="">
<input type="hidden" name="addEmail" value="">

<fieldset>
<legend>Registration Settings</legend>
   <div>
   
   <p>Enable registration features using the checkboxes below.</p>
   
   <% if (ParamUtils.getBooleanParameter(request, "settingsSaved")) { %>
   
      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Settings saved successfully.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } %>
   
   <% if (errors.containsKey("groupNotFound")) { %>
   
      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
      <tbody>
         <tr>
            <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
            <td class="jive-icon-label">Please enter and save a valid group name in the Default Group section at the bottom of this page before enabling automatic group adding.</td>
         </tr>
      </tbody>
      </table>
      </div>
   
   <% } %>
   
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
      <tbody>
         <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="imenabled" <%=(imEnabled) ? "checked" : "" %> onclick="return enableWelcomeMessage();"></td>
            <td width="99%" align="left">Enable instant message registration notification.</td>
         </tr>
         <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="emailenabled" <%=(emailEnabled) ? "checked" : "" %> onclick="return enableWelcomeMessage();"></td>
            <td width="99%" align="left">Enable email registration notification.</td>
         </tr>
         <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="welcomeenabled" <%=(welcomeEnabled) ? "checked" : "" %> onclick="return enableWelcomeMessage();"></td>
            <td width="99%" align="left">Enable welcome message.</td>
         </tr>
         <tr>
            <td width="1%" align="center" nowrap><input type="checkbox" name="groupenabled" <%=(groupEnabled) ? "checked" : "" %> onclick="return enableWelcomeMessage();"></td>
            <td width="99%" align="left">Enable automatically adding of new users to a group.</td>
         </tr>
      </tbody>
   </table>
   </div>

<input type="submit" value="Save Settings"/>
</fieldset>

<br><br>

<fieldset>
   <legend>Registration Notification Contacts</legend>
   <div>
   
   <p>Add or remove contacts to be alerted when a new user registers.</p>
   
   <% if (ParamUtils.getBooleanParameter(request, "deleteSuccess")) { %>
   
      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Contact successfully removed.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } else if (ParamUtils.getBooleanParameter(request, "addSuccess")) { %>
   
      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Contact successfully added.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } else if (errors.containsKey("missingContact")) { %>
   
      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Missing contact.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } else if (errors.containsKey("userNotFound")) { %>
   
      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Contact not found.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } else if (errors.containsKey("invalidAddress")) { %>
   
      <div class="jive-error">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Invalid email address.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } %>
   
   <div>
   <label for="contacttf">Add IM Contact</label> 
   <input type="text" name="contactIM" size="30" maxlength="100" value="<%= (contactIM != null ? contactIM : "") %>" id="contacttf"/> 
   <input type="submit" value="Add" onclick="return addIMContact();"/>
   
   <br>
   <br>
   
   <div class="jive-table" style="width:400px;">
   <table cellpadding="0" cellspacing="0" border="0" width="100%">
      <thead>
         <tr>
            <th width="99%">IM Contact</th>
            <th width="1%" nowrap>Remove</th>
         </tr>
      </thead>
      <tbody>
         <% if (plugin.getIMContacts().size() == 0) { %>
   
            <tr>
               <td width="100%" colspan="2" align="center" nowrap>No contacts specified, use the form above to add one.</td>
            </tr>
   
         <% } %>
   
         <% for (String imContact : plugin.getIMContacts()) { %>
   
            <tr>
               <td width="99%"><%=imContact %></td>
               <td width="1%" align="center"><a
                  href="registration-props-form.jsp?deleteIM=true&contactIM=<%=imContact %>"
                  title="Delete Contact?"
                  onclick="return confirm('Are you sure you want to delete this contact?');"><img
                  src="images/delete-16x16.gif" width="16" height="16"
                  border="0"></a>
               </td>
            </tr>
   
         <% } %>
      </tbody>
   </table>
   </div>
   </div>
   
   <div>
   <label for="emailtf">Add Email Contact</label>
   <input type="text" name="contactEmail" size="30" maxlength="100" value="<%= (contactEmail != null ? contactEmail : "") %>" id="emailtf"/>
   <input type="submit" value="Add" onclick="return addEmailContact();"/>
   
   <br>
   <br>
   
   <div class="jive-table" style="width:400px;">
   <table cellpadding="0" cellspacing="0" border="0" width="100%">
      <thead>
         <tr>
            <th width="99%">Email Contact</th>
            <th width="1%" nowrap>Remove</th>
         </tr>
      </thead>
      <tbody>
         <% if (plugin.getEmailContacts().size() == 0) { %>
   
            <tr>
               <td width="100%" colspan="2" align="center" nowrap>No contacts
               specified, use the form above to add one.</td>
            </tr>
   
         <% } %>
   
         <% for (String emailContact : plugin.getEmailContacts()) { %>
   
            <tr>
               <td width="99%"><%=emailContact %></td>
               <td width="1%" align="center"><a
                  href="registration-props-form.jsp?deleteEmail=true&contactEmail=<%=emailContact %>"
                  title="Delete Contact?"
                  onclick="return confirm('Are you sure you want to delete this contact?');"><img
                  src="images/delete-16x16.gif" width="16" height="16"
                  border="0"></a>
               </td>
            </tr>
   
         <% } %>
      </tbody>
   </table>
   </div>
   </div>
   </div>
</fieldset>
</form>

<br><br>

<form action="registration-props-form.jsp?savemessage=true" method="post">
<fieldset>
   <legend>Welcome Message</legend>
   <div>
   
   <p>Enter the welcome message that will be sent to new users when they register.</p>
   
   <% if (ParamUtils.getBooleanParameter(request, "messageSaved")) { %>

      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Message saved successfully.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } %>
   
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
      <tbody>
         <tr>
            <td width="5%" valign="top">Message:&nbsp;</td>
            <td width="95%"><textarea cols="45" rows="5" wrap="virtual" name="welcomemessage"><%= welcomeMessage %></textarea> 
               <% if (errors.containsKey("missingWelcomeMessage")) { %>
                  <span class="jive-error-text"> <br>
                  Please enter a welcome message. 
                  </span>
               <% } %>
            </td>
         </tr>
      </tbody>
   </table>
   </div>
   
   <input type="submit" value="Save Message"/>
</fieldset>
</form>

<br><br>

<form action="registration-props-form.jsp?savegroup=true" method="post">
<fieldset>
   <legend>Default Group</legend>
   <div>

   <p>Enter the name of the group that all new users will be automatically added to.</p>
   
   <% if (ParamUtils.getBooleanParameter(request, "groupSaved")) { %>

      <div class="jive-success">
      <table cellpadding="0" cellspacing="0" border="0">
         <tbody>
            <tr>
               <td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
               <td class="jive-icon-label">Group saved successfully.</td>
            </tr>
         </tbody>
      </table>
      </div>
   
   <% } %>

   <label>Default Group</label> 
   <input type="text" name="groupname" size="30" maxlength="100" value="<%= (group != null ? group : "") %>"/>

	<% if (errors.containsKey("groupNotFound")) { %> 
      <span class="jive-error-text"> <br>
         Group not found or is invalid.
      </span> 
   <% } %>
   
   </div>
   
   <input type="submit" value="Save Group"/>
</fieldset>
</form>

<jsp:include page="bottom.jsp" flush="true" />
